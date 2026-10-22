#!/usr/bin/env python3
"""
Verify plans produced by scripts/modernize_substrait_plans.py against the originals.

For every ORIG_DIR/qNN.{json,bin} and NEW_DIR/qNN.{json,bin}:
  1. NEW .bin and NEW .json are the same plan.
  2. Clearing the converted fields (aggregate grouping_expressions / expression_references,
     FetchRel offset_expr / count_expr) and the plan version from NEW gives exactly ORIG,
     with ORIG's removed fields discarded: nothing else changed.
  3. Every grouping set's keys, resolved through expression_references, are the original
     keys in the original order (compared as protobuf messages, matched by position in
     the plan tree), and every LIMIT/OFFSET value is unchanged.
  4. NEW declares the target Substrait version.

Usage:
    verify_modernized_plans.py ORIG_DIR NEW_DIR        (needs substrait-protobuf==0.107.0)
"""

import json
import sys
from pathlib import Path

from google.protobuf import json_format
from substrait import algebra_pb2, plan_pb2

TARGET = (0, 107, 0)


def load_json(path):
    return json.loads("\n".join(l for l in Path(path).read_text().splitlines() if not l.lstrip().startswith("//")))


def clear_converted(msg):
    if isinstance(msg, algebra_pb2.AggregateRel):
        del msg.grouping_expressions[:]
        for g in msg.groupings:
            del g.expression_references[:]
    if isinstance(msg, algebra_pb2.FetchRel):
        msg.ClearField("offset_expr")
        msg.ClearField("count_expr")
    for fd, val in msg.ListFields():
        if fd.message_type is None or fd.message_type.GetOptions().map_entry:
            continue
        for v in (val if fd.is_repeated else [val]):
            clear_converted(v)


def collect(node, path, aggs, fetches):
    if isinstance(node, dict):
        for k, v in node.items():
            p = path + (k,)
            if k == "aggregate" and isinstance(v, dict) and "measures" in v:
                aggs[p] = v
            if k == "fetch" and isinstance(v, dict):
                fetches[p] = v
            collect(v, p, aggs, fetches)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            collect(v, path + (i,), aggs, fetches)


def expr(d):
    m = algebra_pb2.Expression()
    json_format.ParseDict(d, m)
    return m


def verify(orig_dir, new_dir):
    problems, stats = [], {"plans": 0, "grouping keys": 0, "limits": 0}
    for new_json_path in sorted(Path(new_dir).glob("*.json")):
        q = new_json_path.stem
        stats["plans"] += 1
        new = plan_pb2.Plan()
        new.ParseFromString((Path(new_dir) / f"{q}.bin").read_bytes())
        from_json = plan_pb2.Plan()
        json_format.ParseDict(load_json(new_json_path), from_json)
        if from_json != new:
            problems.append(f"{q}: .bin and .json differ")
        v = new.version
        if (v.major_number, v.minor_number, v.patch_number) != TARGET:
            problems.append(f"{q}: declares version {v.major_number}.{v.minor_number}.{v.patch_number}")

        orig = plan_pb2.Plan()
        orig.ParseFromString((Path(orig_dir) / f"{q}.bin").read_bytes())
        orig.DiscardUnknownFields()
        stripped = plan_pb2.Plan()
        stripped.CopyFrom(new)
        clear_converted(stripped)
        stripped.ClearField("version")
        orig.ClearField("version")
        if stripped != orig:
            problems.append(f"{q}: content other than the converted fields changed")

        oa, of, na, nf = {}, {}, {}, {}
        collect(load_json(Path(orig_dir) / f"{q}.json"), (), oa, of)
        collect(json_format.MessageToDict(new), (), na, nf)
        if oa.keys() != na.keys() or of.keys() != nf.keys():
            problems.append(f"{q}: aggregate/fetch positions differ")
            continue
        for p, o in oa.items():
            top = na[p].get("groupingExpressions", [])
            og, ng = o.get("groupings", []), na[p].get("groupings", [])
            if len(og) != len(ng):
                problems.append(f"{q}: grouping set count differs at {p}")
                continue
            for a, b in zip(og, ng):
                old_keys = [expr(e) for e in a.get("groupingExpressions", [])]
                new_keys = [expr(top[r]) for r in b.get("expressionReferences", [])]
                stats["grouping keys"] += len(old_keys)
                if old_keys != new_keys:
                    problems.append(f"{q}: grouping keys differ at {p}")
        for p, o in of.items():
            n = nf[p]
            stats["limits"] += 1
            old = (int(o.get("offset", 0)), int(o.get("count", -1)))
            new_vals = (int(n["offsetExpr"]["literal"]["i64"]) if "offsetExpr" in n else 0,
                        int(n["countExpr"]["literal"]["i64"]) if "countExpr" in n else -1)
            if old != new_vals:
                problems.append(f"{q}: limit/offset {old} became {new_vals}")
    return problems, stats


def main(argv):
    if len(argv) != 3:
        sys.exit(__doc__)
    problems, stats = verify(argv[1], argv[2])
    print(f"{argv[2]}: {stats['plans']} plans, {stats['grouping keys']} grouping keys and "
          f"{stats['limits']} limits compared; {len(problems)} problems")
    for p in problems:
        print("  " + p)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main(sys.argv)
