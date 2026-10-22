"""
Shared code for leaderboard submissions (see submissions/README.md).

A submission is a folder submissions/<engine>/<engine-version>/ holding a
manifest.json and one report per suite under reports/. This module knows the
suite registry (test-suites/registry.json), computes suite hashes, lists each
suite's case ids, and checks one submission; the scripts prepare_submission.py,
validate_submission.py and collect_submissions.py are thin command lines over it.

Nothing in a submission is ever executed: it is read as JSON only. Only the totals
and per-case results of a report are used; everything the leaderboard shows per
extension is recomputed here from the per-case results and the suite itself.
"""

from __future__ import annotations

import copy
import functools
import hashlib
import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Dict, List, Optional

REGISTRY = Path("test-suites/registry.json")
MANIFEST_SCHEMA = Path("schemas/submission-manifest.v1.json")
REPORT_SCHEMA = Path("schemas/suite-report.v1.json")
SUBMISSIONS = Path("submissions")
MAX_FILE_BYTES = 5 * 1024 * 1024
ROLES = ("functions", "queries", "reference")
MAX_CLOCK_SKEW = timedelta(days=1)


# ---------------------------------------------------------------------------- names

def slugify(name: str) -> str:
    """Engine folder name: lower case, [a-z0-9-]."""
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    if not slug:
        raise ValueError(f"engine name {name!r} has no letters or digits")
    return slug


def version_dir(version: str) -> str:
    """Version folder name: the version with anything outside [A-Za-z0-9._-] replaced."""
    v = re.sub(r"[^A-Za-z0-9._-]+", "_", version.strip()).strip("._")
    if not v:
        raise ValueError(f"engine version {version!r} has no letters or digits")
    return v


def run_time(manifest: dict) -> datetime:
    """The manifest's run date as an aware datetime (ValueError if it is not one)."""
    text = manifest["run"]["date"]
    # fromisoformat before Python 3.11 takes neither "Z" nor fractions other than 3 or 6 digits
    t = datetime.fromisoformat(re.sub(r"\.\d+", "", text).replace("Z", "+00:00"))
    if t.tzinfo is None:
        raise ValueError(f"run date {text!r} has no time zone")
    return t


# ---------------------------------------------------------------------------- registry

def _registry_json(root: Path) -> dict:
    return json.loads((root / REGISTRY).read_text())


def load_registry(root: Path) -> Dict[str, dict]:
    return _registry_json(root)["suites"]


def reserved_engine_names(root: Path) -> List[str]:
    """Engines run by this repository's own CI; submissions cannot use their names."""
    return list(_registry_json(root).get("reservedEngineNames") or [])


def _metadata_files(metadata: Path) -> List[Path]:
    import yaml  # PyYAML ships with the SDK's dependencies
    data = yaml.safe_load(metadata.read_text())
    base = metadata.parent
    files = {metadata}
    for tc in data.get("testCases") or []:
        for key in ("planBinary", "planJson", "expectedOutput"):
            if tc.get(key):
                files.add(base / tc[key])
        for t in tc.get("inputTables") or []:
            if t.get("file"):
                files.add(base / t["file"])
    return sorted(files)


def _suite_files(path: Path, kind: str) -> List[Path]:
    if kind == "cases":
        return sorted(path.rglob("*.test"))
    if kind == "metadata":
        return _metadata_files(path)
    raise ValueError(f"unknown suite kind {kind!r}")


def suite_files(root: Path, spec: dict) -> List[Path]:
    return _suite_files(root / spec["path"], spec["kind"])


TEXT_SUFFIXES = {".test", ".yaml", ".yml", ".csv", ".json", ".txt", ".md"}


def hash_suite_path(path: Path, kind: str) -> str:
    """sha256 over a suite's files - a folder of .test files ("cases") or a metadata
    file and the files it names ("metadata") - with paths relative to the suite, so the
    hash does not depend on where the suite is checked out. Text files are hashed with
    LF line endings, so a Windows checkout gives the same hash. The runners record this
    hash of the files they ran in their reports."""
    path = Path(path)
    base = path if kind == "cases" else path.parent
    h = hashlib.sha256()
    for f in _suite_files(path, kind):
        rel = Path(os.path.relpath(f, base)).as_posix()
        data = f.read_bytes()
        if f.suffix.lower() in TEXT_SUFFIXES:
            data = data.replace(b"\r\n", b"\n")
        h.update(rel.encode() + b"\0" + str(len(data)).encode() + b"\0" + data)
    return "sha256:" + h.hexdigest()


def suite_hash(root: Path, spec: dict) -> str:
    return hash_suite_path(root / spec["path"], spec["kind"])


def suite_case_ids(root: Path, spec: dict) -> List[str]:
    """Ids of every case: "<file relative to the suite folder>:<line>" for function
    suites, the test id (q01, ...) for metadata suites."""
    path = (Path(root) / spec["path"]).resolve()
    return list(_case_ids(str(path), spec["kind"], hash_suite_path(path, spec["kind"]), str(Path(root).resolve())))


@functools.lru_cache(maxsize=32)
def _case_ids(path_str: str, kind: str, content_hash: str, root_str: str) -> tuple:
    """Cached by the suite's content hash, so a changed suite is parsed again."""
    path = Path(path_str)
    if kind == "metadata":
        import yaml
        data = yaml.safe_load(path.read_text())
        return tuple(str(tc["id"]) for tc in data.get("testCases") or [])
    try:
        from substrait_compliance.function_cases import parse_case_file
    except ImportError:  # SDK not installed: use the one in the checkout being checked against
        import sys
        sys.path.insert(0, str(Path(root_str) / "sdk" / "python"))
        from substrait_compliance.function_cases import parse_case_file
    ids = []
    for f in _suite_files(path, kind):
        rel = f.relative_to(path).as_posix()
        ids += [f"{rel}:{tc.line}" for tc in parse_case_file(str(f)).testcases]
    return tuple(ids)


def case_id_from_result(spec: dict, result: dict) -> Optional[str]:
    """The case id of one entry of a report's nonPassingResults / testResults."""
    if spec["kind"] == "metadata":
        return str(result.get("testId") or result.get("id") or "") or None
    file, line = result.get("file"), result.get("line")
    if not file or line is None:
        return None
    marker = spec["path"].rstrip("/") + "/"
    file = str(file).replace("\\", "/")
    rel = file.split(marker, 1)[1] if marker in file else file  # runners may prefix a checkout folder
    return f"{rel}:{line}"


def _extension_of(case_id: str) -> str:
    """Extension (suite folder) of a function case id, e.g. arithmetic/add.test:3 -> arithmetic."""
    path = case_id.rsplit(":", 1)[0]
    return path.split("/", 1)[0] if "/" in path else "(top level)"


def derived_categories(ids: List[str], spec: dict, report: dict) -> Dict[str, dict]:
    """Per-extension figures recomputed from the per-case results and the suite's cases."""
    status: Dict[str, str] = {}
    if "testResults" in report:
        for r in report["testResults"]:
            s = str(r.get("status", "")).lower()
            status[case_id_from_result(spec, r)] = "failed" if s == "error" else s
    else:
        for r in report.get("nonPassingResults") or []:
            status[case_id_from_result(spec, r)] = str(r.get("status", "")).lower()
    cats: Dict[str, dict] = {}
    for cid in ids:
        c = cats.setdefault(_extension_of(cid), {"total": 0, "passed": 0, "failed": 0, "skipped": 0})
        c["total"] += 1
        c[status.get(cid, "passed")] += 1
    return dict(sorted(cats.items()))


# ---------------------------------------------------------------------------- checking

@dataclass
class Check:
    """Result of checking one submission folder."""
    folder: Path
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    manifest: Optional[dict] = None
    reports: Dict[str, dict] = field(default_factory=dict)  # suite name -> normalized report
    older_suites: List[str] = field(default_factory=list)  # run on an earlier revision of these

    @property
    def ok(self) -> bool:
        return not self.errors

    def write_reports(self, directory: Path) -> Dict[str, Path]:
        """Write the normalized reports (as scored) to directory; return suite -> path."""
        directory.mkdir(parents=True, exist_ok=True)
        out = {}
        for name, report in self.reports.items():
            p = directory / f"{name}.json"
            p.write_text(json.dumps(report))
            out[name] = p
        return out


def _schema_errors(root: Path, schema_path: Path, data, label: str) -> List[str]:
    import jsonschema
    schema = json.loads((root / schema_path).read_text())
    validator = jsonschema.Draft202012Validator(schema)
    out = []
    for e in sorted(validator.iter_errors(data), key=lambda e: list(e.absolute_path)):
        where = "/".join(str(p) for p in e.absolute_path) or "(top level)"
        msg = e.message if len(e.message) <= 300 else \
            f"value does not satisfy {e.validator} {str(e.validator_value)[:80]}"
        out.append(f"{label}: {where}: {msg}")
    return out


def _reject_constant(name):
    raise ValueError(f"{name} is not valid JSON")


def _load_json(path: Path, label: str, errors: List[str]):
    if path.stat().st_size > MAX_FILE_BYTES:
        errors.append(f"{label}: larger than {MAX_FILE_BYTES // (1024 * 1024)} MB")
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"), parse_constant=_reject_constant)
        json.dumps(data, ensure_ascii=False).encode("utf-8")  # rejects unpaired surrogates
        return data
    except UnicodeEncodeError:
        errors.append(f"{label}: contains invalid Unicode (unpaired surrogates)")
    except (ValueError, UnicodeDecodeError) as e:
        errors.append(f"{label}: not valid JSON ({str(e)[:200]})")
    return None


def check_report(root: Path, name: str, spec: dict, report: dict, manifest: dict,
                 case_ids_cache: Dict[str, List[str]], check_cases: bool = True) -> List[str]:
    """Consistency of one suite report with the suite and the manifest. check_cases=False
    skips the checks against the current suite's cases (for a run on an older revision)."""
    errs = []
    label = f"reports/{name}.json"
    engine = manifest["engine"]
    if report.get("engineName") != engine["name"]:
        errs.append(f"{label}: engineName {report.get('engineName')!r} is not the manifest's {engine['name']!r}")
    if str(report.get("engineVersion")) != str(engine["version"]):
        errs.append(f"{label}: engineVersion {report.get('engineVersion')!r} is not the manifest's {engine['version']!r}")
    if spec.get("mode") and report.get("mode", "sql") != spec["mode"]:
        errs.append(f"{label}: run in mode {report.get('mode', 'sql')!r}, but {name} must be run with --mode {spec['mode']}")
    declared = next((s["suiteHash"] for s in manifest["suites"] if s["name"] == name), None)
    if report.get("suiteHash") and report["suiteHash"] != declared:
        errs.append(f"{label}: the runner recorded suite hash {report['suiteHash']}, "
                    f"but manifest.json declares {declared}")

    p, f, s, total = (int(report.get(k, 0)) for k in ("passed", "failed", "skipped", "totalTests"))
    if p + f + s != total:
        errs.append(f"{label}: passed + failed + skipped = {p + f + s}, but totalTests = {total}")
    if p + f == 0:
        errs.append(f"{label}: no test was executed (everything skipped)")

    if not check_cases:
        return errs
    key = spec["path"] + "|" + spec["kind"]
    if key not in case_ids_cache:
        case_ids_cache[key] = suite_case_ids(root, spec)
    ids = case_ids_cache[key]
    if total != len(ids):
        errs.append(f"{label}: totalTests = {total}, but the suite has {len(ids)} cases")

    known = set(ids)
    if "testResults" in report and "nonPassingResults" in report:
        errs.append(f"{label}: give either testResults or nonPassingResults, not both")
    elif "testResults" in report:  # every case listed
        listed = [case_id_from_result(spec, r) for r in report["testResults"]]
        missing, unknown = known - set(listed), [i for i in listed if i not in known]
        if unknown:
            errs.append(f"{label}: results for cases the suite does not have: {', '.join(map(str, unknown[:5]))}")
        if missing:
            errs.append(f"{label}: no result for {len(missing)} case(s), e.g. {', '.join(sorted(missing)[:5])}")
        if len(listed) != len(set(listed)):
            errs.append(f"{label}: some cases are listed more than once")
        status = [str(r.get("status", "")).lower() for r in report["testResults"]]
        got = (status.count("passed"), status.count("failed") + status.count("error"), status.count("skipped"))
        if got != (p, f, s):
            errs.append(f"{label}: per-case results give {got[0]} passed / {got[1]} failed / {got[2]} skipped, "
                        f"the totals say {p} / {f} / {s}")
    elif "nonPassingResults" in report:  # every failed or skipped case listed
        rows = report["nonPassingResults"]
        listed = [case_id_from_result(spec, r) for r in rows]
        unknown = [i for i in listed if i not in known]
        if unknown:
            errs.append(f"{label}: results for cases the suite does not have: {', '.join(map(str, unknown[:5]))}")
        if len(listed) != len(set(listed)):
            errs.append(f"{label}: some cases are listed more than once")
        status = [str(r.get("status", "")).lower() for r in rows]
        if (status.count("failed"), status.count("skipped")) != (f, s):
            errs.append(f"{label}: nonPassingResults lists {status.count('failed')} failed / "
                        f"{status.count('skipped')} skipped, the totals say {f} / {s}")
        if len(rows) != f + s:
            errs.append(f"{label}: nonPassingResults has {len(rows)} entries, expected failed + skipped = {f + s}")
    else:
        errs.append(f"{label}: per-case results are required (testResults or nonPassingResults)")
    return errs


def normalized_report(report: dict, spec: dict, ids: Optional[List[str]]) -> dict:
    """The report as the leaderboard uses it: submitted per-extension figures are dropped
    and, for function suites checked against the current cases, recomputed."""
    out = copy.deepcopy(report)
    out.pop("categories", None)
    out.pop("passRate", None)
    if spec["kind"] == "cases" and ids is not None:
        out["categories"] = derived_categories(ids, spec, report)
    return out


def check_submission(root: Path, folder: Path, registry: Optional[Dict[str, dict]] = None,
                     current_hashes: Optional[Dict[str, str]] = None,
                     case_ids_cache: Optional[Dict[str, List[str]]] = None,
                     data_root: Optional[Path] = None, allow_older_suites: bool = False,
                     now: Optional[datetime] = None) -> Check:
    """Check submissions/<engine>/<version>/ (``folder``, under ``data_root``) against the
    suites, registry and schemas in ``root``. data_root defaults to root; the PR check
    reads the submission from the pull request but everything else from main.

    A suite hash that differs from the current suite is an error, unless
    allow_older_suites (used when publishing submissions already merged, after the
    suites have changed): then the suite is recorded in older_suites and its report is
    checked for consistency but not against the current cases; suites no longer in the
    registry are ignored and newly required ones are not demanded."""
    data_root = data_root or root
    registry = registry if registry is not None else load_registry(root)
    current_hashes = current_hashes if current_hashes is not None else {}
    case_ids_cache = case_ids_cache if case_ids_cache is not None else {}
    c = Check(folder)
    full = data_root / folder
    parts = folder.as_posix().split("/")
    if len(parts) != 3 or parts[0] != SUBMISSIONS.as_posix():
        c.errors.append(f"{folder}: a submission lives in submissions/<engine>/<version>/")
        return c
    base = (data_root / SUBMISSIONS).resolve()
    if any(p.is_symlink() for p in (data_root / SUBMISSIONS, data_root / SUBMISSIONS / parts[1], full)) \
            or not str(full.resolve()).startswith(str(base) + os.sep):
        c.errors.append(f"{folder}: symbolic links are not accepted")
        return c
    if not (full / "manifest.json").is_file():
        c.errors.append(f"{folder}: manifest.json is missing")
        return c

    allowed = {"manifest.json"}
    entries = sorted(full.rglob("*"))
    links = [p for p in entries if p.is_symlink()]
    if links:  # never read through a link
        c.errors += [f"{folder}/{p.relative_to(full).as_posix()}: symbolic links are not accepted" for p in links]
        return c
    for f in entries:
        rel = f.relative_to(full).as_posix()
        if f.is_file() and rel not in allowed and not re.fullmatch(r"reports/[A-Za-z0-9._-]+\.json", rel):
            c.errors.append(f"{folder}/{rel}: unexpected file (only manifest.json and reports/<suite>.json)")
        elif f.is_dir() and rel != "reports":
            c.errors.append(f"{folder}/{rel}: unexpected folder (only reports/)")

    manifest = _load_json(full / "manifest.json", "manifest.json", c.errors)
    if manifest is None:
        return c
    schema_errs = _schema_errors(root, MANIFEST_SCHEMA, manifest, "manifest.json")
    if schema_errs:
        c.errors += schema_errs
        return c
    c.manifest = manifest
    engine = manifest["engine"]
    if slugify(engine["name"]) != parts[1]:
        c.errors.append(f"{folder}: engine {engine['name']!r} belongs in submissions/{slugify(engine['name'])}/")
    if version_dir(engine["version"]) != parts[2]:
        c.errors.append(f"{folder}: version {engine['version']!r} belongs in submissions/{parts[1]}/"
                        f"{version_dir(engine['version'])}/")
    if slugify(engine["name"]) in {slugify(n) for n in reserved_engine_names(root)}:
        c.errors.append(f"{folder}: {engine['name']!r} is run by this repository's own CI and cannot be "
                        "submitted; choose a distinct engine name")
    try:
        t = run_time(manifest)
        if t > (now or datetime.now(timezone.utc)) + MAX_CLOCK_SKEW:
            c.errors.append(f"manifest.json: run date {manifest['run']['date']} is in the future")
    except ValueError as e:
        c.errors.append(f"manifest.json: run date: {str(e)[:200]}")

    names = [s["name"] for s in manifest["suites"]]
    for dup in sorted({n for n in names if names.count(n) > 1}):
        c.errors.append(f"manifest.json: suite {dup!r} is listed more than once")
    for req in sorted(n for n, spec in registry.items() if spec.get("required") and n not in names):
        if allow_older_suites:
            c.warnings.append(f"{folder}: has no results for {req}, which is now required")
        else:
            c.errors.append(f"manifest.json: the scored suite {req!r} is required")

    listed_reports = set()
    for s in manifest["suites"]:
        name = s["name"]
        rel = f"reports/{name}.json"
        listed_reports.add(rel)
        spec = registry.get(name)
        if spec is None:
            if allow_older_suites:
                c.warnings.append(f"{folder}: {name} is no longer an accepted suite; left out")
            else:
                c.errors.append(f"manifest.json: {name!r} is not an accepted suite (see test-suites/registry.json)")
            continue
        if name not in current_hashes:
            current_hashes[name] = suite_hash(root, spec)
        current = s["suiteHash"] == current_hashes[name]
        if not current and allow_older_suites:
            c.older_suites.append(name)
            c.warnings.append(f"{folder}: {name} was run on an earlier revision of the suite")
        elif not current:
            c.errors.append(f"manifest.json: {name} was run on different suite files ({s['suiteHash']}); "
                            f"the current suite is {current_hashes[name]}. Run the current suite from main.")
        path = full / rel
        if not path.is_file():
            c.errors.append(f"{folder}/{rel}: missing (listed in manifest.json)")
            continue
        report = _load_json(path, rel, c.errors)
        if report is None:
            continue
        errs = _schema_errors(root, REPORT_SCHEMA, report, rel)
        check_cases = name not in c.older_suites
        if not errs:
            errs = check_report(root, name, spec, report, manifest, case_ids_cache, check_cases=check_cases)
        c.errors += errs
        if not errs:
            ids = case_ids_cache.get(spec["path"] + "|" + spec["kind"]) if check_cases else None
            c.reports[name] = normalized_report(report, spec, ids)
    for f in sorted(full.glob("reports/*.json")):
        rel = f.relative_to(full).as_posix()
        if rel not in listed_reports:
            c.errors.append(f"{folder}/{rel}: not listed in manifest.json")
    return c


def submission_folders(data_root: Path) -> List[Path]:
    """Every submissions/<engine>/<version>/ folder (relative to data_root)."""
    base = data_root / SUBMISSIONS
    if not base.is_dir():
        return []
    return sorted(v.relative_to(data_root) for e in base.iterdir() if e.is_dir()
                  for v in e.iterdir() if v.is_dir())


def combine_args(registry: Dict[str, dict], reports: Dict[str, Path]) -> List[str]:
    """combine_engine_reports.py arguments for the given suite reports (paths)."""
    flag = {"functions": "--functions", "queries": "--queries", "reference": "--reference"}
    args = []
    for name in sorted(reports, key=lambda n: (ROLES.index(registry[n]["role"]), n)):
        args += [flag[registry[name]["role"]], f"{name}={reports[name]}"]
    return args
