#!/usr/bin/env python3
"""
Fail CI when an engine's compliance drops compared with its last published result.

    check_compliance_regression.py --current output/leaderboard/duckdb.json \
        --baseline baseline/duckdb.json --max-drop 1.0

Both files are leaderboard entries written by scripts/combine_engine_reports.py. The
overall score and each scoring category (functions, queries) are compared; the check
fails if any of them is more than --max-drop percentage points lower than the baseline.
Rises are reported and never fail.

There is nothing to compare against - and the check passes with a notice - when the
baseline file is missing (first run), is in the older report format without scoring
categories, or the engine's scored suites changed (a category then is compared only if
it is made of the same suites).

This replaces a fixed minimum pass rate: engines start from very different places, and
what CI should catch is a change that makes an engine's results worse.
"""

import argparse
import json
import os
import sys
from pathlib import Path


def load(path):
    try:
        return json.loads(Path(path).read_text())
    except (OSError, ValueError):
        return None


def compare(current, baseline, max_drop):
    """Return (rows, failures, notes). rows: (label, before, after, delta, status)."""
    rows, failures, notes = [], [], []
    if not baseline:
        return rows, failures, ["No published result to compare against yet; check skipped."]
    if baseline.get("schemaVersion", 1) < 2 or not isinstance(baseline.get("categories"), dict):
        return rows, failures, ["The published result uses the older report format (no scoring "
                                "categories); check skipped until a new-format result is published."]

    def check(label, before, after):
        if before is None or after is None:
            rows.append((label, before, after, None, "not compared"))
            return
        delta = round(after - before, 1)
        status = "regressed" if delta < -max_drop else ("improved" if delta > 0 else "ok")
        rows.append((label, before, after, delta, status))
        if status == "regressed":
            failures.append(f"{label}: {before}% → {after}% ({delta:+.1f} pts)")

    same_scoring = True
    for cat, cur in (current.get("categories") or {}).items():
        base = (baseline.get("categories") or {}).get(cat) or {}
        if sorted(cur.get("suites") or []) != sorted(base.get("suites") or []):
            same_scoring = False
            notes.append(f"{cat}: scored suites changed ({base.get('suites') or []} → "
                         f"{cur.get('suites') or []}); not compared.")
            rows.append((cat, base.get("passRate"), cur.get("passRate"), None, "not compared"))
            continue
        check(cat, base.get("passRate"), cur.get("passRate"))
    if same_scoring:
        check("overall", baseline.get("passRate"), current.get("passRate"))
    else:
        rows.insert(0, ("overall", baseline.get("passRate"), current.get("passRate"), None, "not compared"))
    return rows, failures, notes


def render(engine, rows, failures, notes, max_drop, baseline_desc):
    fmt = lambda v: "—" if v is None else f"{v}%"  # noqa: E731
    lines = [f"### Compliance regression check: {engine}", "",
             f"Compared with {baseline_desc}; fails on a drop of more than {max_drop} pts.", ""]
    if rows:
        lines += ["| Score | Published | This run | Change | |", "|---|---:|---:|---:|---|"]
        icon = {"regressed": "❌", "improved": "⬆️", "ok": "✅", "not compared": "➖"}
        for label, before, after, delta, status in rows:
            d = "—" if delta is None else f"{delta:+.1f} pts"
            lines.append(f"| {label} | {fmt(before)} | {fmt(after)} | {d} | {icon[status]} {status} |")
        lines.append("")
    lines += [f"- {n}" for n in notes]
    if failures:
        lines += ["", "**Regressed:** " + "; ".join(failures)]
    return "\n".join(lines) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--current", type=Path, required=True)
    ap.add_argument("--baseline", type=Path, help="last published entry (may be missing)")
    ap.add_argument("--baseline-desc", default="the last published result")
    ap.add_argument("--max-drop", type=float, default=1.0,
                    help="largest allowed drop, in percentage points (default 1.0)")
    ap.add_argument("--allow-regression", action="store_true",
                    help="report regressions but do not fail (for intentional changes)")
    args = ap.parse_args(argv)

    current = load(args.current)
    if not current:
        sys.exit(f"❌ cannot read {args.current}")
    baseline = load(args.baseline) if args.baseline else None
    rows, failures, notes = compare(current, baseline, args.max_drop)
    if failures and args.allow_regression:
        notes.append("Regression accepted for this run (--allow-regression).")
    text = render(current.get("engineName", "engine"), rows, failures, notes, args.max_drop, args.baseline_desc)
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(text)
    if failures and not args.allow_regression:
        sys.exit(1)


if __name__ == "__main__":
    main()
