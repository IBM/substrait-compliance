#!/usr/bin/env python3
"""
Check whether a newer release of an engine run in CI is available, and update its pins.

    check_engine_releases.py --engine duckdb [--apply] [--output "$GITHUB_OUTPUT"]

The pinned versions are read from the engine's compliance workflow. A release is taken
only when everything CI needs for it is published:

  * duckdb: the `duckdb` Python package, the `org.duckdb:duckdb_jdbc` driver of the same
    version, and the substrait community extension built for that version;
  * datafusion: the `datafusion` Python package.

With --apply, the pins are updated in the workflows, the example scripts and the
setup commands in the docs. Prose that quotes results of a version (docs/INTRODUCTION.md)
and the CHANGELOG are left alone. With --output, `version`, `branch`, `title` and
`workflows` are written for the calling workflow (empty when there is nothing to do).
Used by .github/workflows/engine-release-check.yml.
"""

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

PYPI = "https://pypi.org/pypi/{}/json"
MAVEN_METADATA = "https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/maven-metadata.xml"
DUCKDB_EXTENSION = "https://community-extensions.duckdb.org/v{}/linux_amd64/substrait.duckdb_extension.gz"

# pin name -> (file the current version is read from, pattern with the version as group 1)
PINS = {
    "duckdb": (".github/workflows/duckdb-compliance.yml", r'DUCKDB_PY_VERSION: "([^"]+)"'),
    "duckdb_jdbc": (".github/workflows/duckdb-compliance.yml", r'DUCKDB_JDBC_VERSION: "([^"]+)"'),
    "datafusion": (".github/workflows/datafusion-compliance.yml", r'DATAFUSION_PY_VERSION: "([^"]+)"'),
}

# pin name -> (files, text around the version, written with <v> for it). Every listed file must
# contain the text with the current version, so a moved pin is noticed, not skipped.
UPDATES = {
    "duckdb": [
        ([".github/workflows/duckdb-compliance.yml"], 'DUCKDB_PY_VERSION: "<v>"'),
        (["docs/CI_COMPLIANCE_WORKFLOW_GUIDE.md", "scripts/duckdb_benchmark_local.py"], "duckdb==<v>"),
    ],
    "duckdb_jdbc": [
        ([".github/workflows/duckdb-compliance.yml"], 'DUCKDB_JDBC_VERSION: "<v>"'),
        ([".github/workflows/sdk-build-test.yml", "docs/CI_COMPLIANCE_WORKFLOW_GUIDE.md",
          "examples/duckdb-java/README.md"], "duckdb_jdbc-<v>.jar"),
        (["examples/duckdb-java/compile.sh", "examples/duckdb-java/download-deps.sh"], ":-<v>}"),
        (["examples/duckdb-java/download-deps.sh"], "or <v>)"),
        (["examples/duckdb-java/README.md"], "| <v> |"),
        (["docs/CI_COMPLIANCE_WORKFLOW_GUIDE.md"], "download-deps.sh <v>"),
    ],
    "datafusion": [
        ([".github/workflows/datafusion-compliance.yml"], 'DATAFUSION_PY_VERSION: "<v>"'),
        (["README.md"], "datafusion==<v>"),
        (["examples/leaderboard-adapter/README.md"], '"DataFusion", "<v>"'),
    ],
}

ENGINES = {
    "duckdb": {"name": "DuckDB", "pins": ["duckdb", "duckdb_jdbc"],
               "workflows": ["duckdb-compliance.yml", "sdk-build-test.yml"]},
    "datafusion": {"name": "DataFusion", "pins": ["datafusion"],
                   "workflows": ["datafusion-compliance.yml"]},
}


def version_key(version):
    """(1, 5, 6) for '1.5.6'; None for anything that is not a plain release."""
    if not re.fullmatch(r"\d+(\.\d+)*", version):
        return None
    return tuple(int(part) for part in version.split("."))


def fetch(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read().decode("utf-8")


def exists(url):
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status == 200
    except urllib.error.HTTPError:
        return False


def latest_pypi(package):
    """The latest release on PyPI (PyPI's own `info.version` skips pre-releases)."""
    return json.loads(fetch(PYPI.format(package)))["info"]["version"]


def latest_jdbc(duckdb_version, metadata):
    """The highest duckdb_jdbc release for a DuckDB version (e.g. 1.5.6.0 for 1.5.6)."""
    versions = re.findall(r"<version>([^<]+)</version>", metadata)
    matching = [v for v in versions if v.startswith(duckdb_version + ".") and version_key(v)
                and len(version_key(v)) == len(version_key(duckdb_version)) + 1]
    return max(matching, key=version_key) if matching else None


def current_pins(root):
    pins = {}
    for pin, (path, pattern) in PINS.items():
        match = re.search(pattern, (root / path).read_text(encoding="utf-8"))
        if not match:
            raise SystemExit(f"❌ {pin}: no `{pattern}` in {path}")
        pins[pin] = match.group(1)
    return pins


def check(engine, pins, lookup=None):
    """Return (new versions by pin, message). An empty dict means no update to take."""
    lookup = lookup or {"pypi": latest_pypi, "maven": lambda: fetch(MAVEN_METADATA), "exists": exists}
    package = "duckdb" if engine == "duckdb" else "datafusion"
    current, latest = pins[package], lookup["pypi"](package)
    if version_key(latest) is None or version_key(latest) <= version_key(current):
        return {}, f"{ENGINES[engine]['name']} {current} is the latest release."
    if engine == "datafusion":
        return {"datafusion": latest}, f"DataFusion {latest} is available (pinned: {current})."
    jdbc = latest_jdbc(latest, lookup["maven"]())
    if not jdbc:
        return {}, f"DuckDB {latest} is released, but its JDBC driver is not on Maven Central yet."
    if not lookup["exists"](DUCKDB_EXTENSION.format(latest)):
        return {}, f"DuckDB {latest} is released, but the substrait extension is not built for it yet."
    return ({"duckdb": latest, "duckdb_jdbc": jdbc},
            f"DuckDB {latest} is available (pinned: {current}), with JDBC {jdbc} and the substrait extension.")


def apply(root, pins, new):
    """Replace each pin's current version with the new one in every listed file.

    Nothing is written unless every listed file still contains its pin."""
    texts = {}
    for pin, version in new.items():
        for files, template in UPDATES[pin]:
            old_text, new_text = template.replace("<v>", pins[pin]), template.replace("<v>", version)
            for name in files:
                text = texts.get(name) or (root / name).read_text(encoding="utf-8")
                if old_text not in text:
                    raise SystemExit(f"❌ {name}: `{old_text}` not found; update UPDATES in {__file__}")
                texts[name] = text.replace(old_text, new_text)
    for name, text in texts.items():
        (root / name).write_text(text, encoding="utf-8")
    return sorted(texts)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", choices=sorted(ENGINES), required=True)
    ap.add_argument("--apply", action="store_true", help="update the pins in the repository")
    ap.add_argument("--output", type=Path, help="file to append key=value outputs to ($GITHUB_OUTPUT)")
    ap.add_argument("--root", type=Path, default=REPO_ROOT, help=argparse.SUPPRESS)
    args = ap.parse_args(argv)

    pins = current_pins(args.root)
    new, message = check(args.engine, pins)
    print(("⬆️  " if new else "✅ ") + message)

    outputs = {"version": "", "branch": "", "title": "", "workflows": ""}
    if new:
        version = new["duckdb" if args.engine == "duckdb" else "datafusion"]
        name = ENGINES[args.engine]["name"]
        outputs = {"version": version,
                   "branch": f"engine-update/{args.engine}-{version}",
                   "title": f"chore(ci): run {name} {version}",
                   "workflows": " ".join(ENGINES[args.engine]["workflows"])}
        if args.apply:
            for name in apply(args.root, pins, new):
                print(f"   updated {name}")
    if args.output:
        with open(args.output, "a", encoding="utf-8") as out:
            out.writelines(f"{key}={value}\n" for key, value in outputs.items())
    return 0


if __name__ == "__main__":
    sys.exit(main())
