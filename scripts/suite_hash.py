#!/usr/bin/env python3
"""
Print the hash of each accepted suite's files (test-suites/registry.json).

    python scripts/suite_hash.py                 # every suite
    python scripts/suite_hash.py tpch --json     # one suite, as JSON

A submission records these hashes; the pull-request check accepts it only when they
match the suites on main, so every result on the leaderboard was run on the same files.
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import submissions as sub  # noqa: E402


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("suites", nargs="*")
    ap.add_argument("--repo-root", type=Path, default=Path("."))
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args(argv)
    registry = sub.load_registry(args.repo_root)
    names = args.suites or list(registry)
    unknown = [n for n in names if n not in registry]
    if unknown:
        ap.error(f"unknown suite(s): {', '.join(unknown)}")
    hashes = {n: sub.suite_hash(args.repo_root, registry[n]) for n in names}
    if args.json:
        print(json.dumps(hashes, indent=2))
    else:
        for n, h in hashes.items():
            print(f"{h}  {n}")


if __name__ == "__main__":
    main()
