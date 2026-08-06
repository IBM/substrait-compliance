#!/usr/bin/env python3
"""
Generate expected-output CSV files for all 99 TPC-DS queries.

Executes each query against the sample data in data/ using DuckDB, then
writes the result to expected/qNN.csv in the typed-column format used by
the compliance runner:

    col1_name:type|col2_name:type|…
    val1|val2|…

Usage
-----
    python3 generate_expected.py [--query Q] [--data-dir DIR]

Options
-------
    --query Q       Only regenerate query Q (e.g. "q01"). Default: all 99.
    --data-dir DIR  Path to the CSV data directory (default: data/).
    --dry-run       Print SQL and column info without writing files.

Canonical parameter substitutions
----------------------------------
All 99 queries are parameterised with TPC-DS-standard placeholder tokens.
The substitutions below follow the TPC-DS specification for scale factor 0.01
and are validated against the sample data shipped in data/.

    [YEAR]          2000   (year with most rows in store_sales)
    [YEAR2]         2001   ([YEAR] + 1, used in week-over-week queries)
    [MONTH]         11     (month with highest store_sales row count)
    [QUARTER]       4      (quarter with highest sales row count)
    [STATE]         'TN'   (only state present in the sample store table)
    [MANUFACT]      9      (i_manufact_id present in sample item table)
    [GENDER]        'F'    (cd_gender value present in customer_demographics)
    [MARITAL_STATUS] 'S'   (cd_marital_status present in customer_demographics)
    [EDUCATION]     'College'  (cd_education_status present in customer_demographics)
    [CHANNEL_EMAIL] 'N'    (p_channel_email value present in promotion table)
    [SALES]         1000   (a mid-range sales threshold used in HAVING clauses)
    [LIMIT]         100    (standard TPC-DS validation result-set cap)
"""

import argparse
import pathlib
import sys

try:
    import duckdb
except ImportError:
    sys.exit("DuckDB is required: pip install duckdb")

# ── Canonical parameter substitutions ─────────────────────────────────────────

PARAMS: dict[str, str] = {
    "[YEAR]":           "2000",
    "[YEAR2]":          "2001",
    "[MONTH]":          "11",
    "[QUARTER]":        "4",
    "[STATE]":          "'TN'",
    "[MANUFACT]":       "9",
    "[GENDER]":         "'F'",
    "[MARITAL_STATUS]": "'S'",
    "[EDUCATION]":      "'College'",
    "[CHANNEL_EMAIL]":  "'N'",
    "[SALES]":          "1000",
    "[LIMIT]":          "100",
}


def _apply_params(sql: str) -> str:
    for token, value in PARAMS.items():
        sql = sql.replace(token, value)
    return sql


# ── DuckDB type → compliance type string ──────────────────────────────────────

def _duck_type_to_compliance(duck_type: str) -> str:
    """Map a DuckDB type name (from the Relation API) to a compliance type."""
    t = str(duck_type).upper()
    if t in ("TINYINT", "SMALLINT", "INTEGER", "INT2", "INT4", "UINT8",
             "UINT16", "UINT32", "USMALLINT", "UTINYINT"):
        return "integer"
    if t in ("BIGINT", "INT8", "UBIGINT", "HUGEINT", "UHUGEINT"):
        return "bigint"
    if t in ("FLOAT", "REAL", "FLOAT4"):
        return "float"
    if t in ("DOUBLE", "FLOAT8") or t.startswith("DECIMAL") or t.startswith("NUMERIC"):
        return "double"
    if t in ("BOOLEAN", "BOOL"):
        return "boolean"
    return "string"


# ── Row serialiser ─────────────────────────────────────────────────────────────

def _format_value(v) -> str:
    if v is None:
        return ""
    if isinstance(v, float):
        # Avoid scientific notation for very small/large values that round-trip cleanly
        formatted = f"{v:.10g}"
        return formatted
    return str(v)


# ── Main generation logic ──────────────────────────────────────────────────────

def generate(
    queries_dir: pathlib.Path,
    data_dir: pathlib.Path,
    expected_dir: pathlib.Path,
    only: str | None,
    dry_run: bool,
) -> None:
    con = duckdb.connect()

    # Load all data tables
    print("Loading data tables...")
    for csv_file in sorted(data_dir.glob("*.csv")):
        try:
            con.execute(
                f"CREATE TABLE {csv_file.stem} AS "
                f"SELECT * FROM read_csv_auto('{csv_file}', header=True)"
            )
        except Exception as exc:
            print(f"  SKIP {csv_file.stem}: {exc}")

    expected_dir.mkdir(parents=True, exist_ok=True)

    passed = failed = skipped = 0

    for qnum in range(1, 100):
        qid = f"q{qnum:02d}"
        if only and only != qid:
            continue

        sql_file = queries_dir / f"query{qnum:02d}.sql"
        if not sql_file.exists():
            print(f"  [{qid}] SKIP — SQL file not found")
            skipped += 1
            continue

        raw_sql = sql_file.read_text()
        sql = _apply_params(raw_sql)

        if dry_run:
            print(f"  [{qid}] DRY-RUN\n{sql[:200]}…")
            continue

        out_file = expected_dir / f"{qid}.csv"
        try:
            # Use the Relation API so we get proper DuckDB type objects,
            # not the lossy DBAPI type_code integers.
            rel = con.sql(f"({sql})")
            col_names = list(rel.columns)
            col_types = [_duck_type_to_compliance(t) for t in rel.dtypes]
            rows = rel.fetchall()

            header = "|".join(f"{n}:{t}" for n, t in zip(col_names, col_types))
            lines = [header]
            for row in rows:
                lines.append("|".join(_format_value(v) for v in row))

            out_file.write_text("\n".join(lines) + "\n")
            print(f"  [{qid}] OK — {len(rows)} rows, {len(col_names)} cols → {out_file.name}")
            passed += 1

        except Exception as exc:
            print(f"  [{qid}] FAILED — {exc}")
            failed += 1

    print(f"\nDone. passed={passed}  failed={failed}  skipped={skipped}")
    if failed:
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--query", metavar="Q", help="Only generate one query (e.g. q01)")
    parser.add_argument("--data-dir", metavar="DIR", default="data",
                        help="Directory containing CSV data files (default: data/)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print SQL without writing files")
    args = parser.parse_args()

    root = pathlib.Path(__file__).parent
    queries_dir  = root / "queries"
    data_dir     = pathlib.Path(args.data_dir) if pathlib.Path(args.data_dir).is_absolute() \
                   else root / args.data_dir
    expected_dir = root / "expected"

    generate(
        queries_dir=queries_dir,
        data_dir=data_dir,
        expected_dir=expected_dir,
        only=args.query,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
