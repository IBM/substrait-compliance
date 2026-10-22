#!/usr/bin/env python3
"""
Combine per-suite compliance reports for one engine into a single leaderboard entry.

    combine_engine_reports.py --engine DuckDB \
        --suite functions=output/reports/duckdb-functions.json \
        --suite tpch=output/reports/duckdb-tpch-report.json \
        --output output/leaderboard/duckdb.json

The headline pass rate is passed / (passed + failed) summed across suites; skipped
tests are reported but never counted as passes. Each suite's own figures are kept
under "suites" so the leaderboard can show the breakdown.
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def rate(passed, failed):
    executed = passed + failed
    return round(passed * 100.0 / executed, 1) if executed else None


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", required=True)
    ap.add_argument("--suite", action="append", required=True, metavar="NAME=REPORT.json")
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args(argv)

    suites, versions = {}, set()
    for spec in args.suite:
        name, _, path = spec.partition("=")
        if not path:
            ap.error(f"--suite must be NAME=PATH, got {spec!r}")
        report = json.loads(Path(path).read_text())
        if report.get("engineName") not in (None, args.engine):
            sys.exit(f"❌ {path} is for engine {report.get('engineName')!r}, not {args.engine!r}")
        passed, failed = int(report.get("passed", 0)), int(report.get("failed", 0))
        skipped, total = int(report.get("skipped", 0)), int(report.get("totalTests", 0))
        if passed + failed == 0:
            sys.exit(f"❌ {path} executed no tests; refusing to publish it")
        suites[name] = {
            "engineVersion": report.get("engineVersion"),
            "totalTests": total, "passed": passed, "failed": failed, "skipped": skipped,
            "passRate": rate(passed, failed),
        }
        if report.get("engineVersion"):
            versions.add(str(report["engineVersion"]))

    totals = {k: sum(s[k] for s in suites.values()) for k in ("totalTests", "passed", "failed", "skipped")}
    combined = {
        "engineName": args.engine,
        "engineVersion": " / ".join(sorted(versions)) or "unknown",
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        **totals,
        "passRate": rate(totals["passed"], totals["failed"]),
        "suites": suites,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(combined, indent=2))
    print(f"✅ {args.engine}: {combined['passRate']}% "
          f"({totals['passed']} passed, {totals['failed']} failed, {totals['skipped']} skipped) -> {args.output}")


if __name__ == "__main__":
    main()
