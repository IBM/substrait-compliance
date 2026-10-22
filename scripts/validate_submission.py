#!/usr/bin/env python3
"""
Check leaderboard submissions (submissions/<engine>/<version>/) and preview their scores.

    # one or more folders, in this checkout
    python scripts/validate_submission.py submissions/velox/1.4.0

    # every submission in this checkout
    python scripts/validate_submission.py --all

    # a pull request: suites, schemas and rules from main (--repo-root), the submitted
    # files from the pull request (--data-root), and the list of files it changes
    python base/scripts/validate_submission.py --repo-root base --data-root pr \
        --changed-files changed.txt

In pull-request mode every changed file must be inside one engine's folder,
submissions/<engine>/, and every submission folder the pull request adds or changes
must pass. Each passing submission is scored with the leaderboard's own rules and
shown with where it would rank among the other submissions. With GITHUB_STEP_SUMMARY
set, the report is also written to the job summary. Exit status 1 if anything fails.
"""

import argparse
import contextlib
import io
import json
import os
import re
import secrets
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import submissions as sub  # noqa: E402


def safe_line(line: str) -> str:
    """One output line: submitted text cannot add lines (or, with ::stop-commands::,
    workflow commands)."""
    line = re.sub(r"[\r\n\x00-\x08\x0b-\x1f]", " ", line)
    return line[:600] + ("…" if len(line) > 600 else "")


def summary_line(line: str) -> str:
    """A line for the job summary (Markdown): no raw HTML from submitted text."""
    return safe_line(line).replace("<", "&lt;").replace(">", "&gt;")


def changed_submission_folders(changed, data_root: Path):
    """(folders to check, errors) for the files a pull request changes."""
    errors, engines, folders = [], set(), set()
    for path in changed:
        if path == (sub.SUBMISSIONS / "README.md").as_posix():
            continue  # the guide itself, edited by maintainers
        parts = path.split("/")
        if len(parts) < 4 or parts[0] != sub.SUBMISSIONS.as_posix():
            errors.append(f"{path}: a submission pull request may only change files in "
                          "submissions/<engine>/<version>/ (open other changes separately)")
            continue
        engines.add(parts[1])
        folder = Path(*parts[:3])
        if (data_root / folder).is_dir():
            folders.add(folder)
    if len(engines) > 1:
        errors.append(f"one engine per pull request; this one changes {', '.join(sorted(engines))}")
    return sorted(folders), errors


def preview(root: Path, data_root: Path, checks, registry):
    """Markdown rankings: the checked submissions among the others on main."""
    import combine_engine_reports
    import generate_leaderboard
    import collect_submissions
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        checked_engines = {c.folder.parts[1] for c in checks}
        others = [c for c in collect_submissions.latest_checks(root, root, registry)
                  if c.folder.parts[1] not in checked_engines]
        for i, c in enumerate(others + list(checks)):
            reports = c.write_reports(tmp / f"reports-{i}")
            args = ["--engine", c.manifest["engine"]["name"], "--consumer", c.manifest["engine"]["consumer"],
                    "--source", "submitted", "--run-url", c.manifest["run"]["url"],
                    *sub.combine_args(registry, reports), "--output", str(tmp / f"{i}.json")]
            with contextlib.redirect_stdout(io.StringIO()):
                combine_engine_reports.main(args)
        reports = [json.loads(p.read_text()) for p in sorted(tmp.glob("*.json"))]
        model = generate_leaderboard.build_model(reports)
        return generate_leaderboard.md_table(model["rankings"])


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folders", nargs="*", type=Path)
    ap.add_argument("--all", action="store_true", help="check every submission")
    ap.add_argument("--repo-root", type=Path, default=Path("."),
                    help="checkout providing suites, registry, schemas (main in a PR check)")
    ap.add_argument("--data-root", type=Path, help="checkout holding the submissions (default: --repo-root)")
    ap.add_argument("--changed-files", type=Path, help="file listing the paths a pull request changes")
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args(argv)
    root = args.repo_root.resolve()
    data_root = (args.data_root or args.repo_root).resolve()
    registry = sub.load_registry(root)

    errors = []
    folders = list(args.folders)
    if args.changed_files:
        changed = [ln.strip() for ln in args.changed_files.read_text().splitlines() if ln.strip()]
        found, errs = changed_submission_folders(changed, data_root)
        folders += found
        errors += errs
        if not changed:
            errors.append("the pull request changes no files")
    if args.all:
        folders += sub.submission_folders(data_root)
    folders = sorted(set(folders))
    if not folders and not errors and not args.changed_files:
        ap.error("nothing to check (give folders, --all or --changed-files)")

    hashes, cases = {}, {}
    checks = [sub.check_submission(root, f, registry, hashes, cases, data_root=data_root) for f in folders]

    lines = ["## Leaderboard submission check", ""]
    if args.changed_files and not folders and not errors:
        lines.append("- ✅ The pull request changes no submission folder (only removals or the guide).")
    for e in errors:
        lines.append(f"- ❌ {e}")
    for c in checks:
        if c.ok:
            eng = c.manifest["engine"]
            lines.append(f"- ✅ `{c.folder}`: {eng['name']} {eng['version']}, "
                         f"{len(c.reports)} suite(s): {', '.join(f'`{n}`' for n in c.reports)}")
        else:
            lines.append(f"- ❌ `{c.folder}`:")
            lines += [f"  - {e}" for e in c.errors]
        lines += [f"  - ⚠️ {w}" for w in c.warnings]
    passed = [c for c in checks if c.ok]
    ok = not errors and all(c.ok for c in checks)
    if passed and not args.no_preview:
        lines += ["", "### Preview: ranking among the submissions on main", "",
                  "Scored with the leaderboard's rules. Engines run in this repository's CI are added "
                  "when the leaderboard is published.", ""]
        lines += preview(root, data_root, passed, registry)
    lines += ["", "✅ All checks passed." if ok else "❌ Fix the problems above; see submissions/README.md."]
    text = "\n".join(safe_line(ln) for ln in lines) + "\n"
    if os.environ.get("GITHUB_ACTIONS"):
        # nothing a submission puts into a message may act as a workflow command
        token = secrets.token_hex(16)
        print(f"::stop-commands::{token}")
        print(text)
        print(f"::{token}::")
    else:
        print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("\n".join(summary_line(ln) for ln in lines) + "\n")
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
