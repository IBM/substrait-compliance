#!/usr/bin/env python3
"""
Rewrite Substrait plans that use encodings removed from the current Substrait spec
into the current encoding, without changing what the plans compute.

Conversions (only these; anything else unknown to the current spec aborts):

  * AggregateRel grouping keys
      old: groupings[i].grouping_expressions = [e0, e1, ...]
      new: aggregate.grouping_expressions = [unique expressions, in first-seen order]
           groupings[i].expression_references = [indexes into that list]
  * FetchRel limit / offset
      old: offset: N, count: M            (count -1 = no limit)
      new: offset_expr / count_expr = i64 literals (omitted for offset 0 / count -1)
  * Removed fields that are always empty in these plans are dropped:
      ScalarFunction.args, AggregateFunction.args, WindowFunction.args  (must be [])
  * Superseded extension URIs are dropped; every function already carries the
    current URN reference (checked):
      Plan.extension_uris, ExtensionFunction.extension_uri_reference

The output plans are validated strictly against the installed Substrait protobuf
definitions and their `version` field is set to that spec version (see TARGET_VERSION),
with the producer recorded as "<original producer>; converted by scripts/modernize_substrait_plans.py".

Usage:
    modernize_substrait_plans.py SRC_DIR DST_DIR      # reads SRC_DIR/*.json, writes DST_DIR/*.{json,bin}

Requires the exact protobuf package for the target spec version:
    pip install "substrait-protobuf==0.107.0"
"""

import copy
import json
import sys
from pathlib import Path

import importlib.metadata

from google.protobuf import json_format
from substrait import plan_pb2

TARGET_VERSION = "0.107.0"
PRODUCER_SUFFIX = "converted by scripts/modernize_substrait_plans.py"

CONVERTED_NOTE = (f"// Converted to Substrait {TARGET_VERSION} (aggregate grouping_expressions + "
                  "expression_references, FetchRel offset_expr/count_expr) by "
                  "scripts/modernize_substrait_plans.py")


def read_plan_json(path):
    lines = path.read_text().splitlines()
    header = [l for l in lines if l.lstrip().startswith("//")]
    body = "\n".join(l for l in lines if not l.lstrip().startswith("//"))
    return header, json.loads(body)


def i64_literal(value):
    return {"literal": {"i64": str(value), "nullable": False}}


def convert(node, stats):
    """Return a converted deep copy of node (a parsed JSON plan or sub-tree)."""
    if isinstance(node, list):
        return [convert(v, stats) for v in node]
    if not isinstance(node, dict):
        return node
    out = {}
    for key, value in node.items():
        if key == "args" and value == []:
            # ScalarFunction/AggregateFunction/WindowFunction.args: removed, always empty here
            stats["empty args dropped"] += 1
            continue
        if key == "args":
            raise ValueError(f"non-empty removed 'args' field: {json.dumps(value)[:120]}")
        if key == "extensionUriReference" and "extensionUrnReference" in node:
            stats["extension URI references dropped"] += 1
            continue
        out[key] = convert(value, stats)

    agg = out.get("aggregate")
    if isinstance(agg, dict) and "measures" in agg:
        out["aggregate"] = convert_aggregate(agg, stats)
    fetch = out.get("fetch")
    if isinstance(fetch, dict):
        out["fetch"] = convert_fetch(fetch, stats)
    return out


def convert_aggregate(agg, stats):
    agg = dict(agg)
    top = list(agg.get("groupingExpressions") or [])
    keys = [json.dumps(e, sort_keys=True) for e in top]
    new_groupings = []
    for grouping in agg.get("groupings", []):
        grouping = dict(grouping)
        old = grouping.pop("groupingExpressions", None) or []
        refs = list(grouping.get("expressionReferences") or [])
        if old and refs:
            raise ValueError("grouping uses both the old and the new encoding")
        for expr in old:
            k = json.dumps(expr, sort_keys=True)
            if k not in keys:
                keys.append(k)
                top.append(expr)
            refs.append(keys.index(k))
        if old:
            stats["groupings converted"] += 1
        grouping["expressionReferences"] = refs
        new_groupings.append(grouping)
    agg["groupings"] = new_groupings
    agg["groupingExpressions"] = top
    return agg


def convert_fetch(fetch, stats):
    fetch = dict(fetch)
    if "offset" in fetch or "count" in fetch:
        offset = int(fetch.pop("offset", 0) or 0)
        count = int(fetch.pop("count", -1) if "count" in fetch else -1)
        if "offsetExpr" in fetch or "countExpr" in fetch:
            raise ValueError("fetch uses both the old and the new encoding")
        if offset != 0:
            fetch["offsetExpr"] = i64_literal(offset)
        if count != -1:
            fetch["countExpr"] = i64_literal(count)
        stats["fetches converted"] += 1
    return fetch


def convert_file(src, dst_dir, stats):
    header, plan_json = read_plan_json(src)
    plan_json = convert(copy.deepcopy(plan_json), stats)
    if plan_json.pop("extensionUris", None) is not None:
        stats["plan extension URI lists dropped"] += 1
    plan = plan_pb2.Plan()
    # Strict: any field still unknown to the target spec is an error, not silently dropped.
    json_format.ParseDict(plan_json, plan, ignore_unknown_fields=False)
    # Record the spec version the plan now conforms to, and that it was converted.
    major, minor, patch = (int(x) for x in TARGET_VERSION.split("."))
    original_producer = plan.version.producer or "unknown producer"
    plan.version.major_number, plan.version.minor_number, plan.version.patch_number = major, minor, patch
    plan.version.git_hash = ""
    if PRODUCER_SUFFIX not in original_producer:
        plan.version.producer = f"{original_producer}; {PRODUCER_SUFFIX}"
    dst_dir.mkdir(parents=True, exist_ok=True)
    (dst_dir / src.with_suffix(".bin").name).write_bytes(plan.SerializeToString(deterministic=True))
    text = json_format.MessageToJson(plan, indent=2)
    (dst_dir / src.name).write_text("\n".join(header + [CONVERTED_NOTE, "", text]) + "\n")
    return plan


def main(argv):
    if len(argv) != 3:
        sys.exit(__doc__)
    src_dir, dst_dir = Path(argv[1]), Path(argv[2])
    installed = importlib.metadata.version("substrait-protobuf")
    if installed != TARGET_VERSION:
        sys.exit(f"substrait-protobuf {installed} is installed; this script targets {TARGET_VERSION}:\n"
                 f'    pip install "substrait-protobuf=={TARGET_VERSION}"')
    stats = {k: 0 for k in ("groupings converted", "fetches converted", "empty args dropped",
                            "extension URI references dropped", "plan extension URI lists dropped")}
    files = sorted(src_dir.glob("*.json"))
    if not files:
        sys.exit(f"no .json plans in {src_dir}")
    for src in files:
        convert_file(src, dst_dir, stats)
    print(f"{len(files)} plans converted to Substrait {TARGET_VERSION}: {src_dir} -> {dst_dir}")
    for k, v in stats.items():
        print(f"  {k}: {v}")


if __name__ == "__main__":
    main(sys.argv)
