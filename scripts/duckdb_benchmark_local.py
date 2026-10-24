#!/usr/bin/env python3
"""
Run a benchmark suite's Substrait plans (TPC-H) through DuckDB locally and
compare with the expected results, for every plan set of the suite side by side.
Plan sets are discovered from test-suites/<suite>/metadata.yaml and
metadata-substrait-*.yaml, e.g. for TPC-H:

    tpch                  plans/                  Substrait 0.107.0
    tpch-substrait-0.81   plans-substrait-0.81/   Substrait 0.81.0

Extra, uncommitted plan sets can be added with --extra-plans NAME=DIR.

Mirrors what CI does: input tables are created from each plan's ReadRel schema,
plans run via the substrait extension's from_substrait(), and results are compared
the way the SDK's ComplianceRunner does (row/column counts, case-insensitive
column names, relative numeric tolerance 1e-9, trimmed strings).

Setup (once):
    python3 -m venv ~/.venvs/duckdb-substrait
    ~/.venvs/duckdb-substrait/bin/pip install duckdb==1.5.6
Run from the repository root:
    ~/.venvs/duckdb-substrait/bin/python scripts/duckdb_benchmark_local.py                 # TPC-H

Writes a machine-readable copy of the results to .ci-reports/local-<suite>.json.
"""

import argparse
import csv
import json
import math
import sys
from decimal import Decimal
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SUITE = REPO / "test-suites" / "tpch"          # set from --suite in main()
REL_TOL = ABS_TOL = 1e-9


def plan_version(folder):
    v = plan_json(sorted(folder.glob("*.json"))[0]).get("version", {})
    return f"{v.get('majorNumber', 0)}.{v.get('minorNumber', 0)}.{v.get('patchNumber', 0)}"


def query_ids():
    import re
    return re.findall(r'^\s*-\s*id:\s*"([^"]+)"', (SUITE / "metadata.yaml").read_text(), re.M)


def discover_suites():
    """[(suite name, Substrait version, plan folder)] from the suite metadata files."""
    import re
    suites = []
    for meta in [SUITE / "metadata.yaml"] + sorted(SUITE.glob("metadata-substrait-*.yaml")):
        text = meta.read_text()
        name = re.search(r'^name:\s*"([^"]+)"', text, re.M).group(1)
        folder = re.search(r'planBinary:\s*"([^"/]+)/', text).group(1)
        # The spec version is recorded in every plan's version field.
        suites.append((name, plan_version(SUITE / folder), SUITE / folder))
    return suites


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
        path = SUITE / "data" / (table + ".csv")
        with path.open() as fh:
            first = fh.readline().rstrip("\n")
        delim = "|" if "|" in first else ","
        # A header row names the first column instead of holding a value for it.
        header = first.split(delim)[0].strip().lower() == cols[0][0].lower()
        # TPC-H ('|') has no quoting; comma-delimited files may quote text fields.
        quote = "" if delim == "|" else '"'
        con.execute(f"INSERT INTO \"{table}\" SELECT * FROM read_csv('{path}', delim='{delim}', "
                    f"header={str(header).lower()}, quote='{quote}', columns={columns})")


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
    ap.add_argument("--suite", default="tpch", help="suite folder under test-suites/ (tpch)")
    ap.add_argument("--extra-plans", action="append", default=[], metavar="NAME=DIR",
                    help="also run the plans in DIR as plan set NAME (e.g. original plans from git)")
    ap.add_argument("--check-data", action="store_true",
                    help="only load tables and expected results (no substrait extension needed)")
    args = ap.parse_args()
    global SUITE
    SUITE = REPO / "test-suites" / args.suite

    import duckdb
    con = duckdb.connect()
    if not args.check_data:
        try:
            con.execute("INSTALL substrait FROM community")
            con.execute("LOAD substrait")
        except Exception as exc:
            sys.exit(f"Could not load DuckDB's substrait extension: {exc}")

    suites = discover_suites()
    for spec in args.extra_plans:
        name, _, folder = spec.partition("=")
        suites.append((name, plan_version(Path(folder)), Path(folder)))
    ids = query_ids()
    results, summary = [], {name: 0 for name, _, _ in suites}
    for q in ids:
        exp_names, _, exp_rows = read_expected(SUITE / "expected" / f"{q}.csv")
        row = {"query": q}
        for name, _, folder in suites:
            plan_dir = folder
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

    print(f"DuckDB {duckdb.__version__} — {args.suite.upper()} via Substrait\n")
    headers = [f"{name} ({version})" for name, version, _ in suites]
    width = max(len(h) for h in headers) + 2
    print(f"{'query':6} " + "".join(f"{h:{width}}" for h in headers) + "detail")
    for row in results:
        cells = [row[name] for name, _, _ in suites]
        details = []
        for (name, _, _), cell in zip(suites, cells):
            if cell["detail"] and cell["detail"] not in details:
                details.append(cell["detail"])
        print(f"{row['query']:6} " + "".join(f"{c['status']:{width}}" for c in cells) + " / ".join(details)[:110])
    if not args.check_data:
        print("\nPassed: " + ", ".join(f"{h} {summary[n]}/{len(ids)}" for h, (n, _, _) in zip(headers, suites)))
    out = REPO / ".ci-reports" / f"local-{args.suite}.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps({"duckdb": duckdb.__version__,
                               "suites": [{"name": n, "substraitVersion": v, "plans": str(f)} for n, v, f in suites],
                               "results": results, "passed": summary}, indent=2))
    print(f"Saved {out.relative_to(REPO)}")


if __name__ == "__main__":
    main()
