"""Tests for leaderboard submissions: scripts/submissions.py, prepare_submission.py,
validate_submission.py, collect_submissions.py and the runners' --engine module:Class.

Submissions are checked against the real suites of this repository; the submitted
reports are synthetic, built from the suites' own case ids."""

import json
import re
import shutil
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))

pytest.importorskip("jsonschema")
yaml = pytest.importorskip("yaml")

import collect_submissions  # noqa: E402
import generate_leaderboard  # noqa: E402
import prepare_submission  # noqa: E402
import submissions as sub  # noqa: E402
import validate_submission  # noqa: E402

REGISTRY = sub.load_registry(REPO)
_IDS = {}


def case_ids(name):
    if name not in _IDS:
        _IDS[name] = sub.suite_case_ids(REPO, REGISTRY[name])
    return _IDS[name]


def valid_skips(name):
    """Cases of a suite that may be skipped (undefined result, option mode, plan not built)."""
    return sorted(sub.valid_skip_ids(REPO, REGISTRY[name]) or case_ids(name))


def function_report(name, engine="ExampleDB", version="2.0", failed=10, skipped=5, mode="plan"):
    ids = case_ids(name)
    skip = valid_skips(name)[:skipped]  # skips the submission check accepts
    fail = [i for i in ids if i not in set(skip)][:failed]
    rows = [{"file": f"test-suites/functions-substrait-0.107/upstream/{i.rsplit(':', 1)[0]}",
             "line": int(i.rsplit(":", 1)[1]), "status": status, "reason": "x"}
            for status, group in (("failed", fail), ("skipped", skip)) for i in group]
    return {"engineName": engine, "engineVersion": version, "mode": mode, "totalTests": len(ids),
            "passed": len(ids) - failed - skipped, "failed": failed, "skipped": skipped,
            "categories": {"arithmetic": {"total": 4, "passed": 3, "failed": 1, "skipped": 0}},
            "nonPassingResults": rows}


def plan_report(name="tpch", engine="ExampleDB", version="2.0", passed=11):
    ids = case_ids(name)
    return {"engineName": engine, "engineVersion": version, "totalTests": len(ids), "passed": passed,
            "failed": len(ids) - passed, "skipped": 0,
            "testResults": [{"testId": i, "status": "PASSED" if n < passed else "FAILED"}
                            for n, i in enumerate(ids)]}


def write_reports(d, **reports):
    d.mkdir(parents=True, exist_ok=True)
    for name, report in reports.items():
        (d / f"{name}.json").write_text(json.dumps(report))
    return d


def prepare(tmp_path, engine="ExampleDB", version="2.0", date="2026-10-01T00:00:00Z", reports=None):
    reports_dir = write_reports(tmp_path / f"in-{engine}-{version}", **(reports or {
        "functions-substrait-0.107": function_report("functions-substrait-0.107", engine, version),
        "tpch": plan_report("tpch", engine, version),
    }))
    prepare_submission.main(["--engine", engine, "--version", version, "--consumer", f"{engine} substrait",
                             "--run-url", f"https://ci.example.com/{engine}/{version}", "--run-date", date,
                             "--reports-dir", str(reports_dir), "--repo-root", str(REPO),
                             "--submissions-root", str(tmp_path), "--force"])
    return Path("submissions") / sub.slugify(engine) / sub.version_dir(version)


def check(tmp_path, folder, **kw):
    return sub.check_submission(REPO, folder, REGISTRY, data_root=tmp_path, **kw)


def edit_json(path, fn):
    data = json.loads(path.read_text())
    fn(data)
    path.write_text(json.dumps(data))


# --------------------------------------------------------------------------- prepare + check

def test_a_prepared_submission_passes(tmp_path):
    folder = prepare(tmp_path)
    assert folder == Path("submissions/exampledb/2.0")
    manifest = json.loads((tmp_path / folder / "manifest.json").read_text())
    assert manifest["engine"] == {"name": "ExampleDB", "version": "2.0", "consumer": "ExampleDB substrait"}
    assert {s["name"] for s in manifest["suites"]} == {"functions-substrait-0.107", "tpch"}
    assert all(re.fullmatch(r"sha256:[0-9a-f]{64}", s["suiteHash"]) for s in manifest["suites"])
    c = check(tmp_path, folder)
    assert c.ok, c.errors
    assert set(c.reports) == {"functions-substrait-0.107", "tpch"}


def test_long_messages_are_shortened(tmp_path):
    rep = function_report("functions-substrait-0.107")
    rep["nonPassingResults"][0]["reason"] = "y" * 9000
    folder = prepare(tmp_path, reports={"functions-substrait-0.107": rep, "tpch": plan_report()})
    saved = json.loads((tmp_path / folder / "reports/functions-substrait-0.107.json").read_text())
    assert len(saved["nonPassingResults"][0]["reason"]) == prepare_submission.MAX_MESSAGE


@pytest.mark.parametrize("tamper, message", [
    (lambda m, r: m["suites"][0].update(suiteHash="sha256:" + "0" * 64), "run on different suite files"),
    (lambda m, r: r["tpch"].update(totalTests=21, failed=10), "but the suite has 22 cases"),
    (lambda m, r: r["tpch"]["testResults"][0].update(testId="q99"), "cases the suite does not have"),
    (lambda m, r: r["tpch"]["testResults"][0].update(status="FAILED"), "per-case results give"),
    (lambda m, r: r["functions-substrait-0.107"].update(passed=r["functions-substrait-0.107"]["passed"] + 5,
                                                        skipped=0), "nonPassingResults lists"),
    (lambda m, r: r["functions-substrait-0.107"]["nonPassingResults"][0].update(line=99999),
     "cases the suite does not have"),
    (lambda m, r: r["functions-substrait-0.107"].update(mode="sql"), "must be run with --mode plan"),
    (lambda m, r: r["tpch"].update(engineName="OtherDB"), "is not the manifest's"),
    (lambda m, r: r["tpch"].pop("testResults"), "per-case results are required"),
    (lambda m, r: m["engine"].update(name="Example | [x](https://evil)"), "does not match"),
    (lambda m, r: m["run"].update(url="https://x.example/a)(b"), "does not match"),
    (lambda m, r: m["suites"].append({"name": "made-up", "suiteHash": "sha256:" + "1" * 64}),
     "not an accepted suite"),
    (lambda m, r: m.update(suites=[s for s in m["suites"] if s["name"] != "tpch"]), "'tpch' is required"),
])
def test_tampered_submissions_fail(tmp_path, tamper, message):
    folder = prepare(tmp_path)
    full = tmp_path / folder
    manifest = json.loads((full / "manifest.json").read_text())
    reports = {p.stem: json.loads(p.read_text()) for p in (full / "reports").glob("*.json")}
    tamper(manifest, reports)
    (full / "manifest.json").write_text(json.dumps(manifest))
    for name, r in reports.items():
        (full / "reports" / f"{name}.json").write_text(json.dumps(r))
    c = check(tmp_path, folder)
    assert not c.ok
    assert any(message in e for e in c.errors), c.errors


def test_wrong_folder_and_extra_files_fail(tmp_path):
    folder = prepare(tmp_path)
    moved = tmp_path / "submissions/otherdb/2.0"
    shutil.copytree(tmp_path / folder, moved)
    (moved / "run.sh").write_text("echo hi")
    c = check(tmp_path, Path("submissions/otherdb/2.0"))
    assert any("belongs in submissions/exampledb/" in e for e in c.errors)
    assert any("run.sh: unexpected file" in e for e in c.errors)


def test_prepare_refuses_reports_that_do_not_pass(tmp_path):
    bad = plan_report()
    bad["passed"] -= 1
    with pytest.raises(SystemExit):
        prepare(tmp_path, reports={"functions-substrait-0.107": function_report("functions-substrait-0.107"),
                                   "tpch": bad})


def test_suite_hash_ignores_line_endings(tmp_path):
    for rel in ("test-suites/registry.json", "test-suites/functions-substrait-0.107/upstream"):
        src = REPO / rel
        dst = tmp_path / rel
        if src.is_dir():
            shutil.copytree(src, dst)
        else:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(src, dst)
    spec = REGISTRY["functions-substrait-0.107"]
    before = sub.suite_hash(tmp_path, spec)
    f = next((tmp_path / spec["path"]).rglob("*.test"))
    f.write_bytes(f.read_bytes().replace(b"\n", b"\r\n"))
    assert sub.suite_hash(tmp_path, spec) == before
    f.write_bytes(f.read_bytes() + b"\n# changed\n")
    assert sub.suite_hash(tmp_path, spec) != before


# --------------------------------------------------------------------------- pull-request mode

def run_pr_check(tmp_path, changed, capsys):
    lst = tmp_path / "changed.txt"
    lst.write_text("\n".join(changed) + "\n")
    try:
        validate_submission.main(["--repo-root", str(REPO), "--data-root", str(tmp_path),
                                  "--changed-files", str(lst)])
        code = 0
    except SystemExit as e:
        code = e.code
    return code, capsys.readouterr().out


def test_pr_check_passes_and_previews_the_ranking(tmp_path, capsys):
    folder = prepare(tmp_path)
    capsys.readouterr()
    changed = [f"{folder}/manifest.json", f"{folder}/reports/tpch.json"]
    code, out = run_pr_check(tmp_path, changed, capsys)
    assert code == 0, out
    assert "### Preview" in out and "| 🥇 | ExampleDB 2.0 |" in out
    assert "[Submitted](https://ci.example.com/ExampleDB/2.0)" in out


def test_pr_check_rejects_other_files_and_two_engines(tmp_path, capsys):
    a, b = prepare(tmp_path), prepare(tmp_path, engine="OtherDB")
    capsys.readouterr()
    code, out = run_pr_check(tmp_path, [f"{a}/manifest.json", "scripts/submissions.py"], capsys)
    assert code == 1 and "may only change files in submissions/<engine>/<version>/" in out
    code, out = run_pr_check(tmp_path, [f"{a}/manifest.json", f"{b}/manifest.json"], capsys)
    assert code == 1 and "one engine per pull request" in out


def test_pr_check_allows_the_guide(tmp_path, capsys):
    code, out = run_pr_check(tmp_path, ["submissions/README.md"], capsys)
    assert code == 0, out


# --------------------------------------------------------------------------- collecting

def test_collect_takes_each_engines_latest_and_marks_the_source(tmp_path):
    prepare(tmp_path, version="1.0", date="2026-09-01T00:00:00Z",
            reports={"functions-substrait-0.107": function_report("functions-substrait-0.107", version="1.0"),
                     "tpch": plan_report(version="1.0", passed=2)})
    prepare(tmp_path, version="2.0", date="2026-10-01T00:00:00Z")
    latest = collect_submissions.latest_checks(REPO, tmp_path, REGISTRY, warn=lambda *_: None)
    assert [c.folder.as_posix() for c in latest] == ["submissions/exampledb/2.0"]

    out = tmp_path / "entries"
    collect_submissions.main(["--output", str(out), "--repo-root", str(REPO), "--submissions-root", str(tmp_path)])
    entry = json.loads((out / "submitted-exampledb.json").read_text())
    assert entry["engineVersion"] == "2.0"
    assert entry["provenance"] == {"source": "submitted", "runUrl": "https://ci.example.com/ExampleDB/2.0",
                                   "submission": "submissions/exampledb/2.0"}
    assert entry["categories"]["queries"]["passRate"] == 50.0


def test_a_submission_on_an_older_suite_stays_and_is_marked(tmp_path):
    folder = prepare(tmp_path)
    edit_json(tmp_path / folder / "manifest.json",
              lambda m: m["suites"][1].update(suiteHash="sha256:" + "a" * 64))  # tpch
    assert not check(tmp_path, folder).ok
    c = check(tmp_path, folder, allow_older_suites=True)
    assert c.ok, c.errors
    assert c.older_suites == ["tpch"]


def test_the_leaderboard_prefers_a_ci_run_and_shows_sources(tmp_path):
    def entry(source, rate, url, older=None):
        return {"schemaVersion": 2, "engineName": "ExampleDB", "engineVersion": "2.0", "passRate": rate,
                "timestamp": "2026-10-01T00:00:00Z",
                "categories": {"functions": {"suites": ["f"], "passRate": rate},
                               "queries": {"suites": ["q"], "passRate": rate}},
                "suites": {}, "provenance": {"source": source, "runUrl": url,
                                             **({"olderSuites": older} if older else {})}}
    m = generate_leaderboard.build_model([entry("submitted", 90.0, "https://a.example/1"),
                                          entry("ci", 50.0, "https://b.example/2")])
    assert len(m["entries"]) == 1 and m["entries"][0]["passRate"] == 50.0
    row = m["rankings"]["rows"][0]
    assert row[-1] == ("CI run", "link", "https://b.example/2")

    other = entry("submitted", 70.0, "https://c.example/3", older=["tpch"])
    other["engineName"] = "OtherDB"
    m = generate_leaderboard.build_model([other])
    assert m["rankings"]["rows"][0][1][0].endswith(" †")
    assert "`tpch` (OtherDB 2.0)" in generate_leaderboard.older_note(m["older"])
    assert "[Submitted](https://c.example/3)" in "\n".join(generate_leaderboard.md_table(m["rankings"]))


# --------------------------------------------------------------------------- consistency

@pytest.mark.parametrize("workflow", ["duckdb-compliance.yml", "datafusion-compliance.yml"])
def test_ci_workflows_score_suites_as_the_registry_says(workflow):
    text = (REPO / ".github/workflows" / workflow).read_text()
    flags = re.findall(r"--(functions|queries|reference) ([\w.-]+)=", text)
    assert flags
    role = {"functions": "functions", "queries": "queries", "reference": "reference"}
    for flag, name in flags:
        if name in REGISTRY:  # functions-legacy is run here only, not accepted from teams
            assert REGISTRY[name]["role"] == role[flag], (workflow, name)


def test_runners_load_an_engine_class_from_outside_the_repository(tmp_path, monkeypatch):
    import run_function_compliance
    import run_plan_compliance
    (tmp_path / "my_engine.py").write_text("class Fn:\n    name = 'Mine'\nclass Plans:\n    name = 'Mine'\n")
    monkeypatch.syspath_prepend(str(tmp_path))
    assert run_function_compliance.resolve_engine("my_engine:Fn").name == "Mine"
    assert run_plan_compliance.resolve_engine("my_engine:Plans").name == "Mine"
    assert run_function_compliance.resolve_engine("datafusion") is run_function_compliance.DataFusionEngine
    for bad in ("nosuch", "my_engine:Missing", "nosuch_module:X"):
        with pytest.raises(SystemExit):
            run_function_compliance.resolve_engine(bad)


# --------------------------------------------------------------------------- review fixes

def test_per_extension_figures_are_recomputed_not_taken_from_the_report(tmp_path):
    real = json.loads((REPO.parent / "lb-reports" / "datafusion-functions-substrait-0.107.json").read_text()) \
        if (REPO.parent / "lb-reports").is_dir() else None
    rep = function_report("functions-substrait-0.107")
    rep["categories"] = {"arithmetic": {"total": 999, "passed": 999},
                         "x | <img src=https://evil> [l](https://e)": {"total": 1, "passed": 1}}
    folder = prepare(tmp_path, reports={"functions-substrait-0.107": rep, "tpch": plan_report()})
    c = check(tmp_path, folder)
    assert c.ok, c.errors
    cats = c.reports["functions-substrait-0.107"]["categories"]
    assert "x | <img" not in json.dumps(cats)
    assert sum(v["total"] for v in cats.values()) == len(case_ids("functions-substrait-0.107"))
    assert sum(v["failed"] for v in cats.values()) == 10 and sum(v["skipped"] for v in cats.values()) == 5
    if real:  # recomputed figures equal what the runner itself reported
        spec = REGISTRY["functions-substrait-0.107"]
        derived = sub.derived_categories(case_ids("functions-substrait-0.107"), spec, real)
        assert {k: {f: v[f] for f in ("total", "passed", "failed", "skipped")} for k, v in real["categories"].items()} \
            == derived


def test_invalid_unicode_and_huge_numbers_are_rejected_cleanly(tmp_path):
    folder = prepare(tmp_path)
    p = tmp_path / folder / "reports/tpch.json"
    p.write_text(p.read_text()[:-2] + ', "x": "\\ud800"}\n')
    assert any("invalid Unicode" in e for e in check(tmp_path, folder).errors)
    edit_json_text = json.loads((tmp_path / folder / "reports/functions-substrait-0.107.json").read_text())
    edit_json_text["passed"] = 10 ** 400
    (tmp_path / folder / "reports/functions-substrait-0.107.json").write_text(json.dumps(edit_json_text))
    errs = check(tmp_path, folder).errors
    assert any("passed: value does not satisfy maximum" in e for e in errs), errs


def test_latest_submission_compares_dates_as_times(tmp_path):
    prepare(tmp_path, version="1.0", date="2026-10-01T09:00:00+05:00",  # 04:00Z
            reports={"functions-substrait-0.107": function_report("functions-substrait-0.107", version="1.0"),
                     "tpch": plan_report(version="1.0")})
    prepare(tmp_path, version="2.0", date="2026-10-01T05:00:00Z")
    latest = collect_submissions.latest_checks(REPO, tmp_path, REGISTRY, warn=lambda *_: None)
    assert [c.folder.parts[2] for c in latest] == ["2.0"]


@pytest.mark.parametrize("date, message", [("2999-01-01T00:00:00Z", "is in the future"),
                                           ("2026-99-01T00:00:00Z", "run date")])
def test_bad_run_dates_fail(tmp_path, date, message):
    folder = prepare(tmp_path)
    edit_json(tmp_path / folder / "manifest.json", lambda m: m["run"].update(date=date))
    assert any(message in e for e in check(tmp_path, folder).errors)


def test_engines_run_here_cannot_be_submitted(tmp_path):
    with pytest.raises(SystemExit):
        prepare(tmp_path, engine="DuckDB")
    assert not (tmp_path / "submissions/duckdb").exists()  # removed again on failure


def test_symlinks_are_rejected_without_being_read(tmp_path):
    folder = prepare(tmp_path)
    secret = tmp_path / "secret.json"
    secret.write_text(json.dumps({"TOP_SECRET_KEY": 1}))
    target = tmp_path / folder / "reports/tpch.json"
    target.unlink()
    target.symlink_to(secret)
    c = check(tmp_path, folder)
    assert any("symbolic links are not accepted" in e for e in c.errors)
    assert "TOP_SECRET_KEY" not in " ".join(c.errors)


def test_the_hash_a_runner_recorded_must_match_the_manifest(tmp_path):
    folder = prepare(tmp_path)
    edit_json(tmp_path / folder / "reports/tpch.json", lambda r: r.update(suiteHash="sha256:" + "b" * 64))
    assert any("the runner recorded suite hash" in e for e in check(tmp_path, folder).errors)


def test_runner_hash_does_not_depend_on_where_the_suite_is(tmp_path):
    spec = REGISTRY["functions-substrait-0.107"]
    copy = tmp_path / "compliance-assets/test-suites/upstream"
    shutil.copytree(REPO / spec["path"], copy)
    assert sub.hash_suite_path(copy, "cases") == sub.suite_hash(REPO, spec)


def test_older_submissions_survive_registry_changes(tmp_path):
    folder = prepare(tmp_path)
    registry = json.loads(json.dumps(REGISTRY))
    registry["new-required"] = {**registry["tpch"], "required": True}
    registry.pop("functions-substrait-0.107-sql", None)
    edit_json(tmp_path / folder / "manifest.json",
              lambda m: m["suites"].append({"name": "retired-suite", "suiteHash": "sha256:" + "c" * 64}))
    (tmp_path / folder / "reports/retired-suite.json").write_text(json.dumps(plan_report()))
    strict = sub.check_submission(REPO, folder, registry, data_root=tmp_path)
    assert not strict.ok
    lenient = sub.check_submission(REPO, folder, registry, data_root=tmp_path, allow_older_suites=True)
    assert lenient.ok, lenient.errors
    assert any("no longer an accepted suite" in w for w in lenient.warnings)
    assert any("now required" in w for w in lenient.warnings)


def test_markdown_cells_cannot_inject():
    cell = generate_leaderboard.md_cell("x | <img src=a> [l](https://e)\n# heading")
    assert "|" not in cell.replace("\\|", "") and "<img" not in cell and "\n" not in cell and "\\[l\\]" in cell
    assert generate_leaderboard.md_cell(("Run", "link", "https://a)(b")) == "Run"


def test_check_output_cannot_issue_workflow_commands(tmp_path, capsys, monkeypatch):
    folder = prepare(tmp_path)
    edit_json(tmp_path / folder / "reports/functions-substrait-0.107.json",
              lambda r: r["nonPassingResults"][0].update(file="x\n::error title=Approved::ok\n.test"))
    capsys.readouterr()
    monkeypatch.setenv("GITHUB_ACTIONS", "true")
    monkeypatch.delenv("GITHUB_STEP_SUMMARY", raising=False)
    code, out = run_pr_check(tmp_path, [f"{folder}/manifest.json"], capsys)
    assert code == 1
    lines = out.splitlines()
    assert lines[0].startswith("::stop-commands::") and lines[-1] == f"::{lines[0].split('::')[2]}::"
    assert not any(ln.startswith("::error") for ln in lines)


# --------------------------------------------------------------------------- valid skips
#
# A skipped case is left out of the pass rate, so for the scored suites the check accepts a
# skip only for a reason that says nothing about the engine, judged from the case itself.
# The cases below are picked from the real suite each time the tests run.

FUNCTIONS = "functions-substrait-0.107"


def _suite_cases():
    """(case id, parsed case) for every case of the scored function suite."""
    from substrait_compliance.function_cases import parse_case_file
    root = REPO / REGISTRY[FUNCTIONS]["path"]
    for f in sorted(root.rglob("*.test")):
        for tc in parse_case_file(str(f)).testcases:
            yield f"{f.relative_to(root).as_posix()}:{tc.line}", tc


def _case(kind, exclude=()):
    """A case id of one kind (option mode, plan not built, or ordinary) not in exclude. The
    suite's undefined results are all on option-pinned cases, so they are covered by "option"."""
    valid = sub.valid_skip_ids(REPO, REGISTRY[FUNCTIONS])
    for cid, tc in _suite_cases():
        if cid in exclude:
            continue
        undefined = getattr(tc.result, "error", None) == "undefined"
        if kind == "option" and tc.options and not undefined:
            return cid
        if kind == "not built" and cid in valid and not tc.options and not undefined:
            return cid
        if kind == "ordinary" and cid not in valid:
            return cid
    raise AssertionError(f"the suite has no {kind} case")


def _skip(tmp_path, folder, case_id, reason="not supported"):
    """Turn one failed case of the prepared submission's function report into a skip of
    case_id (the totals stay consistent, so only the skip rule can reject it)."""
    def edit(r):
        failed = next(x for x in r["nonPassingResults"] if x["status"] == "failed")
        failed.update(file=f"{REGISTRY[FUNCTIONS]['path']}/{case_id.rsplit(':', 1)[0]}",
                      line=int(case_id.rsplit(":", 1)[1]), status="skipped", reason=reason)
        r["failed"] -= 1
        r["skipped"] += 1
    edit_json(tmp_path / folder / "reports" / f"{FUNCTIONS}.json", edit)


def test_honest_skips_are_accepted(tmp_path):
    folder = prepare(tmp_path)  # function_report skips only cases that may be skipped
    assert check(tmp_path, folder, check_skips=True).ok


@pytest.mark.parametrize("kind", ["option", "not built"])
def test_each_valid_reason_to_skip_is_accepted(tmp_path, kind):
    folder = prepare(tmp_path)
    already = set(valid_skips(FUNCTIONS)[:5])  # the prepared report's own skips
    _skip(tmp_path, folder, _case(kind, exclude=already))
    c = check(tmp_path, folder, check_skips=True)
    assert c.ok, c.errors


def test_skipping_an_ordinary_case_is_rejected(tmp_path):
    folder = prepare(tmp_path)
    case = _case("ordinary")
    _skip(tmp_path, folder, case)
    c = check(tmp_path, folder, check_skips=True)
    assert not c.ok
    assert any("skipped without a valid reason" in e and case in e for e in c.errors), c.errors


def test_the_reason_a_report_gives_is_not_trusted(tmp_path):
    folder = prepare(tmp_path)
    _skip(tmp_path, folder, _case("ordinary"), reason="plan not built: user-defined type literals")
    assert not check(tmp_path, folder, check_skips=True).ok


def test_skipping_a_tpch_query_is_rejected(tmp_path):
    folder = prepare(tmp_path)
    def edit(r):
        q = next(x for x in r["testResults"] if x["status"] == "FAILED")
        q["status"] = "SKIPPED"
        r["failed"] -= 1
        r["skipped"] += 1
    edit_json(tmp_path / folder / "reports" / "tpch.json", edit)
    c = check(tmp_path, folder, check_skips=True)
    assert any("tpch.json" in e and "skipped without a valid reason" in e for e in c.errors), c.errors


def test_publishing_does_not_rejudge_skips(tmp_path):
    # The skip rule is applied when a submission is checked for merging, not when merged
    # submissions are published, so a later plan builder cannot change an accepted result.
    folder = prepare(tmp_path)
    _skip(tmp_path, folder, _case("ordinary"))
    assert check(tmp_path, folder).ok
    assert not check(tmp_path, folder, check_skips=True).ok
