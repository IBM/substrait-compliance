#!/usr/bin/env python3
"""
Turn the merged leaderboard submissions into leaderboard entries.

    python scripts/collect_submissions.py --output compliance-reports

For each engine in submissions/, the latest submission (by its run date) is checked
again and combined with scripts/combine_engine_reports.py into
<output>/submitted-<engine>.json, marked as submitted with a link to the team's run.
A submission whose suites have changed since it was merged stays on the leaderboard,
marked as run on an earlier revision. A submission that no longer passes the checks
is left out with a warning, never published.
"""

import argparse
import sys
import tempfile
from pathlib import Path
from typing import Dict, List

sys.path.insert(0, str(Path(__file__).resolve().parent))
import submissions as sub  # noqa: E402


def latest_checks(root: Path, data_root: Path, registry: Dict[str, dict], warn=print) -> List[sub.Check]:
    """The latest passing submission of each engine."""
    by_engine: Dict[str, List[sub.Check]] = {}
    hashes, cases = {}, {}
    for folder in sub.submission_folders(data_root):
        c = sub.check_submission(root, folder, registry, hashes, cases, data_root=data_root,
                                 allow_older_suites=True)
        for w in c.warnings:
            warn(f"⚠️  {w}")
        if not c.ok:
            warn(f"⚠️  {folder} left out: " + "; ".join(c.errors))
            continue
        by_engine.setdefault(folder.parts[1], []).append(c)
    latest = []
    for engine, checks in sorted(by_engine.items()):
        latest.append(max(checks, key=lambda c: (sub.run_time(c.manifest), c.folder.parts[2])))
    return latest


def main(argv=None):
    import combine_engine_reports
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--output", type=Path, required=True, help="directory for the entries")
    ap.add_argument("--repo-root", type=Path, default=Path("."))
    ap.add_argument("--submissions-root", type=Path, help="checkout holding submissions/ (default: --repo-root)")
    args = ap.parse_args(argv)
    root = args.repo_root.resolve()
    data_root = (args.submissions_root or args.repo_root).resolve()
    registry = sub.load_registry(root)
    args.output.mkdir(parents=True, exist_ok=True)
    checks = latest_checks(root, data_root, registry)
    tmp = Path(tempfile.mkdtemp(prefix="submissions-"))
    for c in checks:
        eng = c.manifest["engine"]
        reports = c.write_reports(tmp / c.folder.parts[1])  # as scored: per-extension figures recomputed
        combine_engine_reports.main([
            "--engine", eng["name"], "--consumer", eng["consumer"],
            "--source", "submitted", "--run-url", c.manifest["run"]["url"],
            "--submission", c.folder.as_posix(),
            *[a for n in c.older_suites for a in ("--older-suite", n)],
            *sub.combine_args(registry, reports),
            "--output", str(args.output / f"submitted-{c.folder.parts[1]}.json"),
        ])
    print(f"📥 {len(checks)} submitted engine(s) collected into {args.output}")


if __name__ == "__main__":
    main()
