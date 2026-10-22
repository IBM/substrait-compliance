#!/usr/bin/env python3
"""
Combine per-suite compliance reports for one engine into a single leaderboard entry.

    combine_engine_reports.py --engine DuckDB \
        --consumer "substrait community extension" \
        --functions functions-substrait-0.107=output/reports/duckdb-functions-substrait-0.107.json \
        --queries tpch=output/reports/duckdb-tpch.json \
        --reference functions-substrait-0.107-sql=output/reports/duckdb-functions-substrait-0.107-sql.json \
        --reference tpch-substrait-0.81=output/reports/duckdb-tpch-substrait-0.81.json \
        --output output/leaderboard/duckdb.json

Scoring (schema 2):

* Scored suites belong to a category: --functions (function cases run as Substrait
  plans) or --queries (whole query plans, e.g. TPC-H). A category's pass rate is
  passed / (passed + failed) over its suites; skipped tests never count as passes.
* The overall score is the plain average of the category pass rates, so each
  category weighs the same however many cases it has. A category an engine has no
  results for counts as 0%, so leaving a category out never raises a score.
* --reference suites are listed with their figures (and per-extension breakdown)
  but are not scored: SQL-level runs, extended cases, older plan encodings, the
  legacy function suite. --informational is accepted as an alias.

The entry keeps "passRate" (= the overall score) and the summed counts of the scored
suites at the top level, so existing consumers of the entry keep working.
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 2
CATEGORIES = ("functions", "queries")


def rate(passed, failed):
    executed = passed + failed
    return round(passed * 100.0 / executed, 1) if executed else None


def per_extension(report):
    """Per-extension figures of a function report (its "categories": one per suite
    directory, e.g. arithmetic, string), or None for reports without them."""
    cats = report.get("categories")
    if not isinstance(cats, dict):
        return None
    out = {}
    for name, c in sorted(cats.items()):
        if not isinstance(c, dict):
            continue
        p, f, s = int(c.get("passed", 0)), int(c.get("failed", 0)), int(c.get("skipped", 0))
        out[name] = {"totalTests": int(c.get("total", p + f + s)), "passed": p, "failed": f,
                     "skipped": s, "passRate": rate(p, f)}
    return out or None


def load_suite(spec, engine, ap):
    name, _, path = spec.partition("=")
    if not name or not path:
        ap.error(f"suites must be given as NAME=PATH, got {spec!r}")
    report = json.loads(Path(path).read_text())
    if report.get("engineName") not in (None, engine):
        sys.exit(f"❌ {path} is for engine {report.get('engineName')!r}, not {engine!r}")
    passed, failed = int(report.get("passed", 0)), int(report.get("failed", 0))
    skipped, total = int(report.get("skipped", 0)), int(report.get("totalTests", 0))
    if passed + failed == 0:
        sys.exit(f"❌ {path} executed no tests; refusing to publish it")
    suite = {
        "engineVersion": report.get("engineVersion"),
        "totalTests": total, "passed": passed, "failed": failed, "skipped": skipped,
        "passRate": rate(passed, failed),
    }
    if report.get("mode"):
        suite["mode"] = report["mode"]
    if report.get("engineCrashes"):
        suite["engineCrashes"] = int(report["engineCrashes"])
    extensions = per_extension(report)
    if extensions:
        suite["extensions"] = extensions
    return name, suite


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", required=True)
    ap.add_argument("--consumer", help="the engine's Substrait consumer, as shown on the leaderboard")
    ap.add_argument("--functions", action="append", default=[], metavar="NAME=REPORT.json",
                    help="scored: function cases run as Substrait plans")
    ap.add_argument("--queries", action="append", default=[], metavar="NAME=REPORT.json",
                    help="scored: query plans (TPC-H, ...)")
    ap.add_argument("--reference", "--informational", action="append", default=[], dest="reference",
                    metavar="NAME=REPORT.json", help="shown on the leaderboard, not scored")
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args(argv)
    if not (args.functions or args.queries):
        ap.error("give at least one scored suite (--functions or --queries)")

    suites, versions = {}, set()
    roles = [(s, "functions") for s in args.functions] + [(s, "queries") for s in args.queries] \
        + [(s, None) for s in args.reference]
    for spec, category in roles:
        name, suite = load_suite(spec, args.engine, ap)
        if name in suites:
            ap.error(f"suite {name!r} given twice")
        if category:
            suite["category"] = category
        else:
            suite["informational"] = True  # shown, not scored
        suites[name] = suite
        if suite.get("engineVersion"):
            versions.add(str(suite["engineVersion"]))

    categories = {}
    for cat in CATEGORIES:
        members = [n for n, s in suites.items() if s.get("category") == cat]
        p = sum(suites[n]["passed"] for n in members)
        f = sum(suites[n]["failed"] for n in members)
        s = sum(suites[n]["skipped"] for n in members)
        categories[cat] = {"suites": members, "passed": p, "failed": f, "skipped": s,
                           "passRate": rate(p, f) if members else None}
    # Average the exact category rates (not the rounded ones) so rounding happens once.
    exact = [c["passed"] / (c["passed"] + c["failed"]) * 100.0 if c["passed"] + c["failed"] else 0.0
             for c in categories.values()]
    overall = round(sum(exact) / len(CATEGORIES), 1)

    scored = [s for s in suites.values() if s.get("category")]
    totals = {k: sum(s[k] for s in scored) for k in ("totalTests", "passed", "failed", "skipped")}
    combined = {
        "schemaVersion": SCHEMA_VERSION,
        "engineName": args.engine,
        "engineVersion": " / ".join(sorted(versions)) or "unknown",
        **({"consumer": args.consumer} if args.consumer else {}),
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "passRate": overall,
        "categories": categories,
        **totals,
        "suites": suites,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(combined, indent=2))
    cat_txt = ", ".join(f"{c} {categories[c]['passRate'] if categories[c]['suites'] else 'not run'}"
                        f"{'%' if categories[c]['suites'] else ''}" for c in CATEGORIES)
    print(f"✅ {args.engine}: overall {overall}% ({cat_txt}) -> {args.output}")


if __name__ == "__main__":
    main()
