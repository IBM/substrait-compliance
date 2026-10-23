#!/usr/bin/env python3
"""
Run a plan test suite (e.g. TPC-H) on an engine through the engine's own Substrait
consumer, and score it exactly as the Java runner scores DuckDB.

    scripts/run_plan_compliance.py --engine datafusion \\
        --test-suite test-suites/tpch/metadata.yaml --output output/reports/datafusion-tpch.json

Engines: datafusion (Apache DataFusion Python bindings: datafusion.substrait).
DuckDB's TPC-H runs use the Java SDK (examples/duckdb-java); this script mirrors that
path so both engines are judged by the same rules:

* Input tables are header-less CSVs typed from each plan's own ReadRel base_schema
  (as examples/duckdb-java/PlanSchemas.java does), with values trimmed and '' /
  'null' read as NULL (as the Java loader does), registered under the plan's table
  names. Every case gets a fresh session.
* Expected outputs are read like YamlTestSuiteLoader.loadCsvTable: the delimiter is
  detected, a typed header ``name:type`` gives names and types, and values are
  parsed by those types.
* Results are compared like ComplianceRunner.describeMismatch: same row count and
  column count, column names case-insensitively, column types by family (all
  numerics are one family; dates/times compare as strings), rows in order, numbers
  within max(1e-9, 1e-9 * magnitude), everything else as text. A column the plan
  declares as decimal(P,S) also matches within 10^-S: the answer is defined to S places.
* An engine error is FAILED; a case without an expected output is SKIPPED.

The JSON report has the same fields as the Java runner's report, so
scripts/combine_engine_reports.py accepts it.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import yaml

NUMERIC_ABS_TOLERANCE = 1e-9
NUMERIC_REL_TOLERANCE = 1e-9

# --------------------------------------------------------------- plan schemas


def read_plan_json(path: Path) -> dict:
    """Plan JSON with the leading // comment lines the suites use."""
    text = "\n".join(line for line in path.read_text(encoding="utf-8").splitlines()
                     if not line.lstrip().startswith("//"))
    return json.loads(text)


def named_table_schemas(plan: dict) -> Dict[str, List[Tuple[str, dict]]]:
    """{table name: [(column, substrait type json)]} from every ReadRel named_table."""
    tables: Dict[str, List[Tuple[str, dict]]] = {}

    def walk(node):
        if isinstance(node, dict):
            read = node.get("read")
            if isinstance(read, dict) and "namedTable" in read and "baseSchema" in read:
                name = ".".join(read["namedTable"]["names"])
                schema = read["baseSchema"]
                columns = list(zip(schema["names"], schema["struct"]["types"]))
                if name in tables and [c for c, _ in tables[name]] != [c for c, _ in columns]:
                    raise ValueError(f"table {name} is read with two different schemas")
                tables[name] = columns
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(plan)
    return tables


# ------------------------------------------------- declared output types

# The output column types a plan declares, propagated relation by relation: a read's base
# schema; filter, sort and fetch keep their input's columns, project adds its expressions;
# an aggregate yields its grouping expressions, then its measures; joins concatenate both
# sides; emit mappings select and reorder. An expression's type is its declared outputType
# (functions, aggregates), the referenced column's type, a cast's target or a literal's
# type. Mirrored by PlanOutputTypes.java.

LITERAL_TYPES = {
    "boolean": {"bool": {}}, "i8": {"i8": {}}, "i16": {"i16": {}}, "i32": {"i32": {}},
    "i64": {"i64": {}}, "fp32": {"fp32": {}}, "fp64": {"fp64": {}}, "string": {"string": {}},
    "date": {"date": {}}, "timestamp": {"timestamp": {}}, "intervalYearToMonth": {"intervalYear": {}},
    "intervalDayToSecond": {"intervalDay": {}},
}


def literal_type(lit: dict) -> Optional[dict]:
    for key, value in lit.items():
        if key in LITERAL_TYPES:
            return LITERAL_TYPES[key]
        if key == "decimal":
            return {"decimal": {"precision": value.get("precision"), "scale": value.get("scale")}}
        if key == "varChar":
            return {"varchar": {"length": value.get("length")}}
        if key == "fixedChar":
            return {"fixedChar": {}}
        if key == "null":
            return value
    return None


def expr_type(e: dict, inputs: List[Optional[dict]]) -> Optional[dict]:
    if "selection" in e:
        sel = e["selection"]
        if "outerReference" in sel:
            return None
        ref = sel.get("directReference", {}).get("structField")
        if ref is None or "child" in sel.get("directReference", {}).get("structField", {}):
            return None
        i = ref.get("field", 0)
        return inputs[i] if i < len(inputs) else None
    if "scalarFunction" in e:
        return e["scalarFunction"].get("outputType")
    if "windowFunction" in e:
        return e["windowFunction"].get("outputType")
    if "literal" in e:
        return literal_type(e["literal"])
    if "cast" in e:
        return e["cast"].get("type")
    if "ifThen" in e:
        ifs = e["ifThen"].get("ifs", [])
        return expr_type(ifs[0]["then"], inputs) if ifs else expr_type(e["ifThen"]["else"], inputs)
    if "singularOrList" in e:
        return {"bool": {}}
    if "subquery" in e:
        sq = e["subquery"]
        if "scalar" in sq:
            out = rel_types(sq["scalar"]["input"])
            return out[0] if out else None
        return {"bool": {}}
    return None


def emit(rel: dict, cols: List[Optional[dict]]) -> List[Optional[dict]]:
    mapping = rel.get("common", {}).get("emit", {}).get("outputMapping")
    return [cols[i] for i in mapping] if mapping is not None else cols


def rel_types(node: dict) -> List[Optional[dict]]:
    kind, rel = next(iter(node.items()))
    if kind == "read":
        cols = list(rel["baseSchema"]["struct"]["types"])
    elif kind in ("filter", "sort", "fetch"):
        cols = rel_types(rel["input"])
    elif kind == "project":
        base = rel_types(rel["input"])
        cols = base + [expr_type(e, base) for e in rel.get("expressions", [])]
    elif kind == "aggregate":
        base = rel_types(rel["input"])
        groupings = rel.get("groupings", [])
        if rel.get("groupingExpressions"):  # 0.107: aggregate-level expressions
            keys = [expr_type(e, base) for e in rel["groupingExpressions"]]
        elif len(groupings) <= 1:  # older plans: the grouping's own expressions
            keys = [expr_type(e, base) for e in (groupings[0].get("groupingExpressions", []) if groupings else [])]
        else:
            raise ValueError("grouping sets in the older encoding")
        cols = keys + [m["measure"].get("outputType") for m in rel.get("measures", [])]
    elif kind in ("join", "cross"):
        cols = rel_types(rel["left"]) + rel_types(rel["right"])
    else:
        raise ValueError(f"unsupported relation {kind}")
    return emit(rel, cols)


def output_types(plan: dict) -> List[Optional[dict]]:
    root = next(r["root"] for r in plan["relations"] if "root" in r)
    return rel_types(root["input"])


def declared_scales(plan: dict) -> Optional[List[Optional[int]]]:
    """The scale of each output column the plan declares as decimal (None for other
    columns), or None when the plan's output types cannot be worked out."""
    try:
        types = output_types(plan)
    except (KeyError, IndexError, TypeError, ValueError, StopIteration):
        return None
    scales = []
    for t in types:
        dec = t.get("decimal") if isinstance(t, dict) else None
        scales.append(int(dec["scale"]) if isinstance(dec, dict) and dec.get("scale") is not None else None)
    return scales


def arrow_type(substrait_type: dict):
    import pyarrow as pa
    kind, params = next(iter(substrait_type.items()))
    simple = {
        "bool": pa.bool_(), "i8": pa.int8(), "i16": pa.int16(), "i32": pa.int32(), "i64": pa.int64(),
        "fp32": pa.float32(), "fp64": pa.float64(), "string": pa.string(), "varchar": pa.string(),
        "fixedChar": pa.string(), "date": pa.date32(),
    }
    if kind in simple:
        return simple[kind]
    if kind == "decimal":
        return pa.decimal128(int(params["precision"]), int(params["scale"]))
    raise ValueError(f"unsupported input column type {kind}")


def detect_delimiter(line: str) -> str:
    return "|" if line.count("|") > line.count(",") else ","


def load_input_table(csv_path: Path, columns: List[Tuple[str, dict]]):
    """Header-less (or headed) CSV typed by the plan's schema. Values are read the way
    the Java loader hands them to DuckDB: trimmed, with '' and 'null' as NULL."""
    import pyarrow as pa
    import pyarrow.compute as pc
    import pyarrow.csv as pacsv
    with csv_path.open(encoding="utf-8") as fh:
        first = fh.readline()
    delimiter = detect_delimiter(first)
    names = [name for name, _ in columns]
    header = [v.strip().split(":")[0].lower() for v in first.rstrip("\n").split(delimiter)]
    skip = 1 if header == [n.lower() for n in names] else 0
    raw = pacsv.read_csv(
        csv_path,
        read_options=pacsv.ReadOptions(column_names=names, skip_rows=skip),
        parse_options=pacsv.ParseOptions(delimiter=delimiter),
        convert_options=pacsv.ConvertOptions(column_types={n: pa.string() for n in names},
                                             strings_can_be_null=False),
    )
    arrays = []
    for name, substrait_type in columns:
        col = pc.utf8_trim_whitespace(raw.column(name))
        is_null = pc.or_(pc.equal(col, ""), pc.equal(pc.utf8_lower(col), "null"))
        col = pc.if_else(is_null, pa.scalar(None, pa.string()), col)
        arrays.append(pc.cast(col, arrow_type(substrait_type)))
    return pa.table(arrays, names=names)


# ------------------------------------------------------- expected (Java loader)


def parse_delimited_line(line: str, delimiter: str) -> List[str]:
    values, current, in_quotes, i = [], [], False, 0
    while i < len(line):
        ch = line[i]
        if ch == '"':
            if in_quotes and i + 1 < len(line) and line[i + 1] == '"':
                current.append('"')
                i += 1
            else:
                in_quotes = not in_quotes
        elif ch == delimiter and not in_quotes:
            values.append("".join(current).strip())
            current = []
        else:
            current.append(ch)
        i += 1
    values.append("".join(current).strip())
    return [v[1:-1] if len(v) >= 2 and v[0] == '"' and v[-1] == '"' else v for v in values]


def convert_value(raw: str, column_type: str):
    value = raw.strip()
    if not value or value.lower() == "null":
        return None
    t = (column_type or "string").lower()
    try:
        if t in ("int", "integer", "i8", "i16", "i32", "long", "bigint", "i64"):
            return int(value)
        if t in ("float", "fp32", "double", "fp64", "decimal"):
            return float(value)
        if t in ("bool", "boolean"):
            return value.lower() == "true"
    except ValueError:
        return value
    return value


def load_expected(csv_path: Path):
    """(names, types, rows) as YamlTestSuiteLoader.loadCsvTable reads them."""
    lines = [line.rstrip("\n") for line in csv_path.read_text(encoding="utf-8").splitlines()]
    if not lines:
        raise ValueError(f"CSV file is empty: {csv_path}")
    delimiter = detect_delimiter(lines[0])
    header = parse_delimited_line(lines[0], delimiter)
    typed = header and all(0 < h.strip().find(":") < len(h.strip()) - 1 for h in header)
    if typed:
        names = [h.strip().split(":", 1)[0].strip() for h in header]
        types = [h.strip().split(":", 1)[1].strip().lower() for h in header]
        data = lines[1:]
    else:
        names = [f"column_{i + 1}" for i in range(len(header))]
        types = ["string"] * len(header)
        data = lines
    rows = []
    for n, line in enumerate(data, 2 if typed else 1):
        if not line.strip():
            continue
        values = parse_delimited_line(line, delimiter)
        if len(values) != len(names):
            raise ValueError(f"CSV row {n} in {csv_path} has {len(values)} values, expected {len(names)}")
        rows.append([convert_value(v, t) for v, t in zip(values, types)])
    return names, types, rows


# ------------------------------------------------ comparison (ComplianceRunner)

NUMERIC = {"int", "integer", "i8", "i16", "i32", "int2", "int4", "smallint", "tinyint", "long", "bigint",
           "i64", "int8", "hugeint", "int128", "utinyint", "usmallint", "uinteger", "ubigint", "uhugeint",
           "float", "fp32", "real", "float4", "double", "fp64", "decimal", "numeric", "float8", "number"}
STRINGISH = {"varchar", "char", "text", "string", "utf8", "date", "timestamp", "timestamptz", "time"}


def normalize_type(t: Optional[str]) -> Optional[str]:
    if t is None:
        return None
    n = t.strip().lower().split("(", 1)[0].strip()
    if n in NUMERIC:
        return "numeric"
    if n in ("bool", "boolean"):
        return "boolean"
    if n in STRINGISH:
        return "string"
    return n


def numbers_close(expected: float, actual: float, declared_scale: Optional[int] = None) -> bool:
    """Within max(1e-9, 1e-9 * magnitude); for a column the plan declares as decimal with
    scale S, within one unit in the last declared place (10^-S) as well: the answer is only
    defined to S places, and engines may round or keep extra digits."""
    if math.isnan(expected) or math.isnan(actual):
        return math.isnan(expected) and math.isnan(actual)
    if math.isinf(expected) or math.isinf(actual):
        return expected == actual
    magnitude = max(abs(expected), abs(actual))
    tolerance = max(NUMERIC_ABS_TOLERANCE, NUMERIC_REL_TOLERANCE * magnitude)
    if declared_scale is not None:
        tolerance = max(tolerance, 10.0 ** -declared_scale * (1 + NUMERIC_REL_TOLERANCE))
    return abs(expected - actual) <= tolerance


def _is_number(v) -> bool:
    return isinstance(v, (int, float, Decimal)) and not isinstance(v, bool)


def java_string(v) -> str:
    """String.valueOf as the Java runner sees JDBC values."""
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, datetime):
        return v.isoformat(sep=" ")
    if isinstance(v, date):
        return v.isoformat()
    return str(v)


def values_match(expected, actual, declared_scale: Optional[int] = None) -> bool:
    if expected is None or actual is None:
        return expected is None and actual is None
    if _is_number(expected) and _is_number(actual):
        return numbers_close(float(expected), float(actual), declared_scale)
    if _is_number(expected) and isinstance(actual, str):
        try:
            return numbers_close(float(expected), float(actual), declared_scale)
        except ValueError:
            pass
    if _is_number(actual) and isinstance(expected, str):
        try:
            return numbers_close(float(expected), float(actual), declared_scale)
        except ValueError:
            pass
    if isinstance(expected, bool) or isinstance(actual, bool):
        return java_string(expected).lower() == "true" and java_string(actual).lower() == "true" or \
            java_string(expected).lower() != "true" and java_string(actual).lower() != "true"
    return java_string(expected) == java_string(actual)


def describe_mismatch(expected, actual, declared_scales: Optional[List[Optional[int]]] = None) -> Optional[str]:
    """None when the outputs match, else the first difference (as describeMismatch).
    declared_scales: per output column, the decimal scale the plan declares (or None)."""
    e_names, e_types, e_rows = expected
    a_names, a_types, a_rows = actual
    if len(e_rows) != len(a_rows):
        preview = f"; first row {a_rows[0]}" if a_rows else ""
        return f"expected {len(e_rows)} rows, got {len(a_rows)}{preview}"
    if len(e_names) != len(a_names):
        return f"expected {len(e_names)} columns {e_names}, got {len(a_names)} {a_names}"
    if [n.strip().lower() for n in e_names] != [n.strip().lower() for n in a_names]:
        return f"expected column names {e_names}, got {a_names}"
    if [normalize_type(t) for t in e_types] != [normalize_type(t) for t in a_types]:
        return f"expected column types {e_types}, got {a_types}"
    for r, (e_row, a_row) in enumerate(zip(e_rows, a_rows)):
        for c, (e, a) in enumerate(zip(e_row, a_row)):
            scale = declared_scales[c] if declared_scales and c < len(declared_scales) else None
            if not values_match(e, a, scale):
                return (f"row {r}, column {e_names[c]}: expected {e!r}, got {a!r} "
                        f"(actual type {a_types[c]})")
    return None


# -------------------------------------------------------------------- engines


def arrow_type_name(t) -> str:
    import pyarrow as pa
    if pa.types.is_boolean(t):
        return "boolean"
    if pa.types.is_integer(t):
        return "bigint" if t.bit_width == 64 else "integer"
    if pa.types.is_floating(t):
        return "double" if t.bit_width == 64 else "float"
    if pa.types.is_decimal(t):
        return "decimal"
    if pa.types.is_date(t):
        return "date"
    if pa.types.is_timestamp(t):
        return "timestamp"
    if pa.types.is_time(t):
        return "time"
    if pa.types.is_string(t) or pa.types.is_large_string(t) or str(t) == "string_view":
        return "varchar"
    return str(t)


class DataFusionPlanEngine:
    name = "DataFusion"

    def __init__(self):
        try:
            import datafusion
            from datafusion import substrait  # noqa: F401
        except ImportError as exc:  # never fall back to made-up numbers
            raise SystemExit(f"❌ datafusion (with Substrait support) is not installed: {exc}")
        self.version = datafusion.__version__

    def execute(self, plan_bytes: bytes, tables: Dict[str, object]):
        import datafusion
        from datafusion import substrait as ss
        ctx = datafusion.SessionContext()
        for name, table in tables.items():
            # quoted, so DataFusion keeps the plan's (upper-case) table name
            ctx.register_record_batches(f'"{name}"', [table.to_batches()])
        plan = ss.Serde.deserialize_bytes(plan_bytes)
        logical = ss.Consumer.from_substrait_plan(ctx, plan)
        out = ctx.create_dataframe_from_logical_plan(logical).to_arrow_table()
        names = out.schema.names
        types = [arrow_type_name(f.type) for f in out.schema]
        rows = [list(r.values()) for r in out.to_pylist()]
        return names, types, rows


ENGINES = {"datafusion": DataFusionPlanEngine}


def resolve_engine(spec: str):
    """An engine class: a built-in name (datafusion) or "package.module:ClassName" for an
    engine defined outside this repository (importable on PYTHONPATH)."""
    if spec in ENGINES:
        return ENGINES[spec]
    module, sep, cls_name = spec.partition(":")
    if not sep or not module or not cls_name:
        raise SystemExit(f"❌ unknown engine {spec!r}: use one of {', '.join(sorted(ENGINES))} "
                         "or package.module:ClassName")
    import importlib
    try:
        cls = getattr(importlib.import_module(module), cls_name)
    except (ImportError, AttributeError) as exc:
        raise SystemExit(f"❌ cannot load engine {spec!r}: {exc}")
    return cls


# ------------------------------------------------------------------------ main


def run_suite(engine, suite_path: Path):
    meta = yaml.safe_load(suite_path.read_text(encoding="utf-8"))
    base = suite_path.parent
    results = []
    for case in meta.get("testCases", []):
        test_id = case["id"]
        started = time.time()
        try:
            if not case.get("expectedOutput") or not (base / case["expectedOutput"]).exists():
                results.append({"testId": test_id, "status": "SKIPPED", "executionTimeMs": 0,
                                "message": "No expected output — cannot verify correctness"})
                continue
            expected = load_expected(base / case["expectedOutput"])
            plan_json = case.get("planJson")
            if not plan_json:
                raise ValueError("planJson is needed to type the input tables")
            plan = read_plan_json(base / plan_json)
            schemas = named_table_schemas(plan)
            files = {t["name"].lower(): base / t["file"] for t in case.get("inputTables", [])}
            tables = {}
            for name, columns in schemas.items():
                csv_path = files.get(name.lower())
                if csv_path is None:
                    raise ValueError(f"no input file for table {name}")
                tables[name] = load_input_table(csv_path, columns)
            actual = engine.execute((base / case["planBinary"]).read_bytes(), tables)
            mismatch = describe_mismatch(expected, actual, declared_scales(plan))
            status, message = ("PASSED", None) if mismatch is None else ("FAILED", "Output mismatch: " + mismatch)
        except Exception as exc:  # noqa: BLE001 - the engine's error is the result
            status, message = "FAILED", f"Exception: {str(exc).splitlines()[0][:400]}"
        result = {"testId": test_id, "status": status, "executionTimeMs": int((time.time() - started) * 1000)}
        if message:
            result["message"] = message
        results.append(result)
    return meta, results


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", required=True,
                    help="datafusion, or package.module:ClassName for your own engine class")
    ap.add_argument("--test-suite", type=Path, required=True, help="suite metadata.yaml")
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args(argv)

    engine = resolve_engine(args.engine)()
    meta, results = run_suite(engine, args.test_suite)
    counts = {s: sum(r["status"] == s for r in results) for s in ("PASSED", "FAILED", "SKIPPED")}
    executed = counts["PASSED"] + counts["FAILED"]
    if executed == 0:
        raise SystemExit("❌ No test was executed; refusing to write an empty report")
    import submissions
    report = {
        "engineName": engine.name,
        "suiteHash": submissions.hash_suite_path(args.test_suite, "metadata"),
        "engineVersion": engine.version,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "testSuiteName": meta.get("name", args.test_suite.stem),
        "totalTests": len(results),
        "passed": counts["PASSED"], "failed": counts["FAILED"], "skipped": counts["SKIPPED"],
        "passRate": round(counts["PASSED"] * 100.0 / executed, 1),
        "testResults": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2))
    for r in results:
        print(f"{r['testId']:<6} {r['status']:<8} {r.get('message', '')[:150]}")
    print(f"{engine.name} {engine.version}: {counts['PASSED']}/{executed} passed "
          f"({report['passRate']}%), {counts['SKIPPED']} skipped -> {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
