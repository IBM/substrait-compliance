#!/usr/bin/env python3
"""
Run the TPC-H Substrait plans through DuckDB locally and compare with the expected
results, for both plan encodings side by side:

    plans/                    current Substrait encoding
    plans-legacy-encoding/    original plans (removed grouping / fetch fields)

Mirrors what CI does: input tables are created from each plan's ReadRel schema,
plans run via the substrait extension's from_substrait(), and results are compared
the way the SDK's ComplianceRunner does (row/column counts, case-insensitive
column names, relative numeric tolerance 1e-9, trimmed strings).

Setup (once):
    python3 -m venv ~/.venvs/duckdb-substrait
    ~/.venvs/duckdb-substrait/bin/pip install duckdb==1.5.6
Run from the repository root:
    ~/.venvs/duckdb-substrait/bin/python scripts/tpch_duckdb_local.py

Writes a machine-readable copy of the results to .ci-reports/local-tpch.json.
"""

import argparse
import csv
import json
import math
import sys
from decimal import Decimal
from pathlib import Path

SUITE = Path(__file__).resolve().parent.parent / "test-suites" / "tpch"
ENCODINGS = [("current", "plans"), ("legacy", "plans-legacy-encoding")]
REL_TOL = ABS_TOL = 1e-9


def plan_json(path):
    return json.loads("\n".join(l for l in path.read_text().splitlines() if not l.lstrip().startswith("//")))


def read_schemas(node, out):
    if isinstance(node, dict):
        if "namedTable" in node and "baseSchema" in node:
            out.setdefault(node["namedTable"]["names"][-1].lower(), node["baseSchema"])
        for v in node.values():
            read_schemas(v, out)
    elif isinstance(node, list):
        for v in node:
            read_schemas(v, out)
    return out


def sql_type(t):
    kind, spec = next(iter(t.items()))
    if kind == "decimal":
        return f"DECIMAL({spec.get('precision')},{spec.get('scale', 0)})"
    return {"bool": "BOOLEAN", "i8": "TINYINT", "i16": "SMALLINT", "i32": "INTEGER", "i64": "BIGINT",
            "fp32": "FLOAT", "fp64": "DOUBLE", "date": "DATE"}.get(kind, "VARCHAR")


def load_tables(con, schemas):
    for table, schema in schemas.items():
        cols = list(zip(schema["names"], (sql_type(t) for t in schema["struct"]["types"])))
        col_defs = ", ".join(f'"{n}" {t}' for n, t in cols)
        con.execute(f'CREATE OR REPLACE TABLE "{table}" ({col_defs})')
        columns = "{" + ", ".join(f"'{n}': '{t}'" for n, t in cols) + "}"
        con.execute(f"INSERT INTO \"{table}\" SELECT * FROM read_csv('{SUITE / 'data' / (table + '.csv')}', "
                    f"delim='|', header=false, quote='', columns={columns})")


def read_expected(path):
    with path.open(newline="") as f:
        rows = list(csv.reader(f, delimiter="|"))
    header = [h.split(":", 1) for h in rows[0]]
    names = [h[0] for h in header]
    types = [h[1] if len(h) > 1 else "string" for h in header]
    return names, types, rows[1:]


def as_number(v):
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float, Decimal)):
        return float(v)
    try:
        return float(str(v))
    except ValueError:
        return None


def values_match(expected, actual):
    if expected in ("", None) and actual is None:
        return True
    e, a = as_number(expected), as_number(actual)
    if e is not None and a is not None:
        if math.isnan(e) or math.isnan(a):
            return math.isnan(e) and math.isnan(a)
        return abs(e - a) <= max(ABS_TOL, REL_TOL * max(abs(e), abs(a)))
    return str(expected).strip() == str(actual).strip()


def compare(exp_names, exp_rows, act_names, act_rows):
    if len(exp_rows) != len(act_rows):
        return f"expected {len(exp_rows)} rows, got {len(act_rows)}"
    if len(exp_names) != len(act_names):
        return f"expected columns {exp_names}, got {act_names}"
    if [n.lower() for n in exp_names] != [n.lower() for n in act_names]:
        return f"expected column names {exp_names}, got {act_names}"
    for r, (er, ar) in enumerate(zip(exp_rows, act_rows)):
        for c, (ev, av) in enumerate(zip(er, ar)):
            if not values_match(ev, av):
                return f"row {r}, column {exp_names[c]}: expected {ev}, got {av}"
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check-data", action="store_true",
                    help="only load tables and expected results (no substrait extension needed)")
    args = ap.parse_args()

    import duckdb
    con = duckdb.connect()
    if not args.check_data:
        try:
            con.execute("INSTALL substrait FROM community")
            con.execute("LOAD substrait")
        except Exception as exc:
            sys.exit(f"Could not load DuckDB's substrait extension: {exc}")

    results, summary = [], {name: 0 for name, _ in ENCODINGS}
    for i in range(1, 23):
        q = f"q{i:02d}"
        exp_names, _, exp_rows = read_expected(SUITE / "expected" / f"{q}.csv")
        row = {"query": q}
        for name, folder in ENCODINGS:
            plan_dir = SUITE / folder
            load_tables(con, read_schemas(plan_json(plan_dir / f"{q}.json"), {}))
            if args.check_data:
                row[name] = {"status": "LOADED", "detail": f"{len(exp_rows)} expected rows"}
                continue
            try:
                rel = con.execute("SELECT * FROM from_substrait(?)", [(plan_dir / f"{q}.bin").read_bytes()])
                act_names = [d[0] for d in rel.description]
                mismatch = compare(exp_names, exp_rows, act_names, rel.fetchall())
                row[name] = {"status": "PASS" if mismatch is None else "FAIL", "detail": mismatch or ""}
            except Exception as exc:
                row[name] = {"status": "ERROR", "detail": str(exc).splitlines()[0]}
            summary[name] += row[name]["status"] == "PASS"
        results.append(row)

    print(f"DuckDB {duckdb.__version__} — TPC-H via Substrait\n")
    print(f"{'query':6} {'current encoding':18} {'legacy encoding':18} detail (current / legacy)")
    for row in results:
        cur, leg = row["current"], row["legacy"]
        detail = cur["detail"] if cur["detail"] == leg["detail"] else f"{cur['detail']} / {leg['detail']}"
        print(f"{row['query']:6} {cur['status']:18} {leg['status']:18} {detail[:110]}")
    if not args.check_data:
        print(f"\nPassed: current {summary['current']}/22, legacy {summary['legacy']}/22")
    out = SUITE.parent.parent / ".ci-reports" / "local-tpch.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps({"duckdb": duckdb.__version__, "results": results, "passed": summary}, indent=2))
    print(f"Saved {out.relative_to(SUITE.parent.parent)}")


if __name__ == "__main__":
    main()
