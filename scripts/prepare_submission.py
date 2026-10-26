#!/usr/bin/env python3
"""
Package an engine's compliance reports as a leaderboard submission.

Run from a checkout of this repository (the suites the reports were produced with):

    python scripts/prepare_submission.py \
        --engine "Velox" --version 1.4.0 --consumer "velox-substrait 1.4.0" \
        --run-url https://github.com/acme/velox/actions/runs/123 \
        --report functions-substrait-0.107=out/functions.json \
        --report tpch=out/tpch.json \
        --report functions-substrait-0.107-sql=out/functions-sql.json

or --reports-dir DIR with files named <suite>.json. Writes
submissions/<engine>/<version>/manifest.json and reports/<suite>.json and checks the
result with the same rules as the pull-request check (scripts/validate_submission.py).
Open a pull request with that folder; see submissions/README.md.

Each suite's hash is the one the runner recorded in its report (the files it actually
ran). For reports without one (e.g. the Java runner's), it is computed from this
checkout, which must then be the one the suites were run from.

If the check fails, the folder is removed again and the problems are listed.
"""

import argparse
import json
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import submissions as sub  # noqa: E402

MAX_MESSAGE = 2000  # per-case messages are shortened to keep submissions small


def _shorten(report: dict) -> dict:
    for key in ("testResults", "nonPassingResults"):
        for r in report.get(key) or []:
            for field in ("message", "reason"):
                v = r.get(field)
                if isinstance(v, str) and len(v) > MAX_MESSAGE:
                    r[field] = v[:MAX_MESSAGE - 1] + "…"
    return report


def _git_head(root: Path):
    try:
        return subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True,
                              text=True, check=True).stdout.strip() or None
    except (OSError, subprocess.CalledProcessError):
        return None


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", required=True, help="engine name as shown on the leaderboard")
    ap.add_argument("--version", required=True, help="engine version")
    ap.add_argument("--consumer", required=True, help="the engine's Substrait consumer and version")
    ap.add_argument("--homepage")
    ap.add_argument("--run-url", required=True, help="https link to the CI run that produced the reports")
    ap.add_argument("--run-date", help="ISO 8601 time of the run (default: now)")
    ap.add_argument("--commit", help="commit of the engine repository that was tested")
    ap.add_argument("--sdk-language")
    ap.add_argument("--sdk-version")
    ap.add_argument("--report", action="append", default=[], metavar="SUITE=PATH")
    ap.add_argument("--reports-dir", type=Path, help="directory of <suite>.json reports")
    ap.add_argument("--repo-root", type=Path, default=Path("."), help="checkout of this repository")
    ap.add_argument("--submissions-root", type=Path,
                    help="where to write submissions/<engine>/<version>/ (default: --repo-root)")
    ap.add_argument("--force", action="store_true", help="replace an existing submission folder")
    args = ap.parse_args(argv)

    root = args.repo_root.resolve()
    data_root = (args.submissions_root or args.repo_root).resolve()
    registry = sub.load_registry(root)
    reports = {}
    for spec in args.report:
        name, _, path = spec.partition("=")
        if not path:
            ap.error(f"--report must be SUITE=PATH, got {spec!r}")
        reports[name] = Path(path)
    if args.reports_dir:
        for f in sorted(args.reports_dir.glob("*.json")):
            if f.stem in registry:
                reports.setdefault(f.stem, f)
            else:
                print(f"ℹ️  {f.name}: not an accepted suite, left out")
    if not reports:
        ap.error("no reports given (--report SUITE=PATH or --reports-dir)")
    unknown = sorted(set(reports) - set(registry))
    if unknown:
        ap.error(f"not accepted suites: {', '.join(unknown)} (accepted: {', '.join(registry)})")

    folder = sub.SUBMISSIONS / sub.slugify(args.engine) / sub.version_dir(args.version)
    out = data_root / folder
    if out.exists() and not args.force:
        ap.error(f"{folder} already exists (use --force to replace it)")
    (out / "reports").mkdir(parents=True, exist_ok=True)
    for old in out.glob("reports/*.json"):
        old.unlink()

    suites = []
    for name in sorted(reports, key=lambda n: (sub.ROLES.index(registry[n]["role"]), n)):
        report = _shorten(json.loads(reports[name].read_text()))
        (out / "reports" / f"{name}.json").write_text(json.dumps(report, separators=(",", ":")) + "\n")
        recorded = report.get("suiteHash")
        if not recorded:
            print(f"ℹ️  {name}: the report records no suite hash; using this checkout's files")
        suites.append({"name": name, "suiteHash": recorded or sub.suite_hash(root, registry[name])})

    run = {"url": args.run_url,
           "date": args.run_date or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    if args.commit:
        run["commit"] = args.commit
    head = _git_head(root)
    if head:
        run["complianceCommit"] = head
    engine = {"name": args.engine, "version": args.version, "consumer": args.consumer}
    if args.homepage:
        engine["homepage"] = args.homepage
    manifest = {"schemaVersion": 1, "engine": engine, "run": run, "suites": suites}
    if args.sdk_language or args.sdk_version:
        manifest["sdk"] = {k: v for k, v in (("language", args.sdk_language), ("version", args.sdk_version)) if v}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    check = sub.check_submission(root, folder, registry, data_root=data_root, check_skips=True)
    for w in check.warnings:
        print(f"⚠️  {w}")
    if not check.ok:
        print(f"❌ {folder} does not pass the submission check (folder removed):")
        for e in check.errors:
            print(f"   - {e}")
        shutil.rmtree(out)
        if not any(out.parent.iterdir()):
            out.parent.rmdir()
        sys.exit(1)
    print(f"✅ {folder}: {len(suites)} suite(s), ready for a pull request")
    print(folder)


if __name__ == "__main__":
    main()
