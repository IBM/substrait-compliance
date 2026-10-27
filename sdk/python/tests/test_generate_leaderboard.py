"""Tests for scripts/generate_leaderboard.py (schema-2 entries from combine_engine_reports.py)."""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import combine_engine_reports  # noqa: E402
import generate_leaderboard  # noqa: E402


def report(path, engine, passed, failed, skipped=0, **extra):
    path.write_text(json.dumps({"engineName": engine, "engineVersion": "1.0", "passed": passed,
                                "failed": failed, "skipped": skipped,
                                "totalTests": passed + failed + skipped, **extra}))
    return str(path)


def entry(tmp_path, engine, fn, sql, q, q_old, ext=None):
    d = tmp_path / engine
    d.mkdir()
    cats = lambda a, w: {"arithmetic": {"total": 10, "passed": a, "failed": 10 - a, "skipped": 0},  # noqa: E731
                         "window": {"total": 2, "passed": w, "failed": 2 - w, "skipped": 0}}
    args = ["--engine", engine, "--consumer", f"{engine} consumer",
            "--functions", f"fsuite={report(d / 'f.json', engine, *fn, categories=cats(fn[0] // 10, 0))}",
            "--queries", f"tpch={report(d / 'q.json', engine, *q)}",
            "--reference", f"fsuite-sql={report(d / 's.json', engine, *sql, categories=cats(9, 2))}",
            "--reference", f"tpch-substrait-0.81={report(d / 'q81.json', engine, *q_old)}",
            "--reference", f"legacy={report(d / 'l.json', engine, 1, 1)}"]
    if ext:
        args += ["--reference", f"fsuite-extended={report(d / 'e.json', engine, *ext)}"]
    out = tmp_path / "entries" / f"{engine}.json"
    combine_engine_reports.main(args + ["--output", str(out)])
    return out


def generate(tmp_path, fmt="all"):
    generate_leaderboard.main(["--input", str(tmp_path / "entries"), "--output",
                               str(tmp_path / "site" / "leaderboard.md"), "--format", fmt])
    return tmp_path / "site"


def test_ranks_by_overall_and_links_each_engine_page(tmp_path):
    # A: functions 60%, queries 80% -> 70. B: functions 90%, queries 10% -> 50.
    entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9), ext=(9, 1))
    entry(tmp_path, "B", fn=(90, 10), sql=(99, 1), q=(1, 9), q_old=(1, 9))
    site = generate(tmp_path)
    md = (site / "leaderboard.md").read_text()

    ranking = md.split("## Rankings")[1].split("##")[0]
    # Columns say what they measure: the scored suite's registry label (or name) and size.
    assert "| Rank | Engine | Overall | fsuite (100 cases) | TPC-H (10 queries) | Findings | Results |" in ranking
    page = "https://ibm.github.io/substrait-compliance/engines/a.html"
    assert ranking.index(f"| 🥇 | [A 1.0]({page}) |") < ranking.index("| 🥈 | [B 1.0]")
    # Each rate says how many of the category's cases ran (skipped cases are not counted).
    assert f"| **70.0%** | 60.0% (100 run) | 80.0% (10 run) | [0 findings]({page}) |" in ranking
    assert "| **50.0%** | 90.0% (100 run) | 10.0% (10 run) |" in ranking
    # The main page is the ranking and links; the detail moved to the engine pages.
    assert "## Functions" not in md and "## Queries" not in md and "How scoring works" in md

    data = json.loads((site / "leaderboard.json").read_text())
    assert [e["engineName"] for e in data["engines"]] == ["A", "B"]
    assert data["engines"][0]["categories"]["queries"]["passRate"] == 80.0
    assert data["engines"][0]["schemaVersion"] == 2  # a published entry works as a regression baseline
    assert data["engines"][0]["page"] == "engines/a.html"
    index = (site / "leaderboard.html").read_text()
    assert index.startswith("<!DOCTYPE html>") and "<strong>70.0%</strong>" in index
    assert 'href="engines/a.html"' in index and "prefers-color-scheme: dark" in index


def test_engine_pages_hold_the_detail(tmp_path):
    entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9), ext=(9, 1))
    site = generate(tmp_path)
    page = (site / "engines" / "a.html").read_text()
    assert "<h1>A 1.0</h1>" in page and "A consumer" in page
    assert "60.0% (60/100)" in page and "95.0% (95/100)" in page and "−35.0 pts" in page  # plans, SQL, gap
    assert "90.0% (9/10)" in page                                                        # extended cases
    assert "TPC-H (scored)" in page and "8/10" in page and "TPC-H, 0.81 encoding" in page
    assert "<td>window</td>" in page and "0.0%" in page                                   # by extension
    assert "<code>legacy</code>" in page                                                  # other results
    doc = json.loads((site / "engines" / "a.json").read_text())
    assert doc["engineName"] == "A" and doc["findings"]["groups"] == [] and "_tables" not in doc


def test_findings_are_grouped_explained_and_compared_with_the_previous_run(tmp_path, monkeypatch):
    d = tmp_path / "A"
    d.mkdir()
    case = lambda fn, line, status, reason: {"file": f"x/test-suites/fs/arithmetic/{fn}.test", "line": line,  # noqa: E731
                                             "test": f"{fn}(1::i8)", "status": status, "reason": reason}
    plan = report(d / "f.json", "A", 1, 3, mode="plan", nonPassingResults=[
        case("negate", 1, "failed", "Scalar Function with name negate does not exist!"),
        case("shift", 2, "failed", "Scalar Function with name shift does not exist!"),
        case("abs", 3, "failed", "got 2, expected 1")])
    sql = report(d / "s.json", "A", 3, 1, mode="sql", nonPassingResults=[case("abs", 3, "failed", "got 2, expected 1")])
    q = report(d / "q.json", "A", 1, 1, testResults=[{"testId": "q01", "status": "PASSED"},
                                                     {"testId": "q02", "status": "FAILED", "message": "Unsupported subquery"}])
    combine_engine_reports.main(["--engine", "A", "--functions", f"fs={plan}", "--queries", f"tpch={q}",
                                 "--reference", f"fs-sql={sql}", "--output", str(tmp_path / "entries" / "a.json")])
    rules = tmp_path / "rules.yaml"
    rules.write_text('findings:\n  - engine: a\n    match: "Scalar Function with name … does not exist"\n'
                     '    title: "Unmapped functions"\n    upstream: {url: "https://example.org/1", status: open}\n')
    monkeypatch.setattr(generate_leaderboard, "CURATED", rules)

    prev = tmp_path / "prev" / "engines"
    prev.mkdir(parents=True)
    prev.joinpath("a.json").write_text(json.dumps({"timestamp": "2000-01-01T00:00:00Z", "findings": {"groups": [
        {"section": "queries", "signature": "Unsupported subquery", "list": [{"suite": "tpch", "id": "q03"}]}]}}))
    generate_leaderboard.main(["--input", str(tmp_path / "entries"), "--output", str(tmp_path / "site" / "leaderboard.md"),
                               "--format", "all", "--previous", str(tmp_path / "prev")])
    doc = json.loads((tmp_path / "site" / "engines" / "a.json").read_text())
    groups = {g["signature"]: g for g in doc["findings"]["groups"]}
    unmapped = groups["Scalar Function with name … does not exist!"]
    assert unmapped["section"] == "consumer" and unmapped["cases"] == 2
    assert unmapped["subjects"] == {"negate": 1, "shift": 1} and unmapped["title"] == "Unmapped functions"
    # abs fails as SQL too: it is the engine's, listed once, from the SQL run.
    assert groups["Wrong result (got …, expected …)"]["section"] == "engine"
    assert groups["Wrong result (got …, expected …)"]["suites"] == {"fs-sql": 1}
    assert groups["Unsupported subquery"]["section"] == "queries"
    # Against the previous run: q02 newly failing; q03 no longer failing.
    # (fs is not in the registry, so its case ids keep the folder under test-suites/)
    assert {c["id"] for c in doc["changes"]["new"]} == {"fs/arithmetic/negate.test:1", "fs/arithmetic/shift.test:2",
                                                        "fs/arithmetic/abs.test:3", "q02"}
    assert [c["id"] for c in doc["changes"]["fixed"]] == ["q03"]
    page = (tmp_path / "site" / "engines" / "a.html").read_text()
    assert "Unmapped functions" in page and "https://example.org/1" in page and "Not yet reported upstream" in page
    assert "Functions:</strong> negate, shift" in page and "4</strong> newly failing" in page


def test_entries_in_the_old_format_are_listed_but_not_ranked(tmp_path):
    entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    (tmp_path / "entries" / "old.json").write_text(json.dumps(
        {"engineName": "Old", "engineVersion": "0.1", "passRate": 99.0, "passed": 99, "failed": 1}))
    site = generate(tmp_path, "both")
    md = (site / "leaderboard.md").read_text()
    assert "## Not ranked" in md and "- Old 0.1: 99.0%" in md
    assert "Old" not in md.split("## Rankings")[1].split("##")[0]
    data = json.loads((site / "leaderboard.json").read_text())
    assert data["totalEngines"] == 1 and data["unranked"][0]["engineName"] == "Old"


def test_html_escapes_engine_names(tmp_path):
    entry(tmp_path, "A<b>", fn=(1, 1), sql=(1, 1), q=(1, 1), q_old=(1, 1))
    page = (generate(tmp_path, "html") / "leaderboard.html").read_text()
    assert "A&lt;b&gt;" in page and "A<b>" not in page


# History: engines/<engine>.history.json carries every published run forward from the last publish.

def stamp(path, timestamp, version=None):
    data = json.loads(path.read_text())
    data["timestamp"] = timestamp
    if version:
        data["engineVersion"] = version
    path.write_text(json.dumps(data))


def publish(tmp_path, site, previous=None, seed=None):
    args = ["--input", str(tmp_path / "entries"), "--output", str(site / "leaderboard.md"), "--format", "all"]
    if previous:
        args += ["--previous", str(previous)]
    if seed:
        args += ["--history-seed", str(seed)]
    generate_leaderboard.main(args)
    return json.loads((site / "engines" / "a.history.json").read_text())["runs"]


def test_history_starts_with_the_first_run_and_grows_with_each_publish(tmp_path):
    a = entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    stamp(a, "2026-10-19T02:00:00Z")
    runs = publish(tmp_path, tmp_path / "site1")
    assert [(r["timestamp"], r["passRate"]) for r in runs] == [("2026-10-19T02:00:00Z", 70.0)]
    assert runs[0]["categories"]["functions"]["passRate"] == 60.0
    assert "first one recorded" in (tmp_path / "site1" / "engines" / "a.html").read_text()

    a.unlink()
    (tmp_path / "A").rename(tmp_path / "A-old")
    a = entry(tmp_path, "A", fn=(70, 30), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    stamp(a, "2026-10-26T02:00:00Z", version="1.1")
    runs = publish(tmp_path, tmp_path / "site2", previous=tmp_path / "site1")
    assert [(r["engineVersion"], r["passRate"]) for r in runs] == [("1.0", 70.0), ("1.1", 75.0)]
    page = (tmp_path / "site2" / "engines" / "a.html").read_text()
    assert "<h2>History</h2>" in page and '<svg class="trend"' in page and "(+5.0)" in page
    assert page.index("2026-10-26 02:00") < page.index("2026-10-19 02:00")  # newest first

    # Publishing the same run again (e.g. after a docs change) does not add a record.
    assert len(publish(tmp_path, tmp_path / "site3", previous=tmp_path / "site2")) == 2


def test_the_first_history_includes_the_run_on_the_previous_page(tmp_path):
    a = entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    stamp(a, "2026-10-26T02:00:00Z")
    prev = tmp_path / "prev" / "engines"
    prev.mkdir(parents=True)
    prev.joinpath("a.json").write_text(json.dumps({"timestamp": "2026-10-19T02:00:00Z", "engineVersion": "0.9",
                                                   "passRate": 65.0, "categories": {}}))
    runs = publish(tmp_path, tmp_path / "site", previous=tmp_path / "prev")
    assert [(r["timestamp"], r["passRate"]) for r in runs] == [("2026-10-19T02:00:00Z", 65.0),
                                                               ("2026-10-26T02:00:00Z", 70.0)]


def test_an_unreadable_history_stops_the_publish(tmp_path):
    entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    prev = tmp_path / "prev" / "engines"
    prev.mkdir(parents=True)
    prev.joinpath("a.history.json").write_text("{not json")
    try:
        publish(tmp_path, tmp_path / "site", previous=tmp_path / "prev")
    except SystemExit as err:
        assert "not publishing" in str(err)
    else:
        raise AssertionError("a publish over an unreadable history must stop")


def test_backfill_adds_earlier_runs_and_marks_a_change_of_scored_suites(tmp_path):
    a = entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9))
    stamp(a, "2026-10-26T02:00:00Z")
    seed = tmp_path / "seed" / "run-1"
    seed.mkdir(parents=True)
    old = json.loads(a.read_text())
    old.update(timestamp="2026-10-12T02:00:00Z", passRate=50.0)
    old["categories"]["functions"]["suites"] = ["an-older-suite"]
    (seed / "a.json").write_text(json.dumps(old))
    (seed / "raw-report.json").write_text(json.dumps({"engineName": "A", "passed": 1, "failed": 0}))  # not an entry
    runs = publish(tmp_path, tmp_path / "site", seed=tmp_path / "seed")
    assert [r["timestamp"] for r in runs] == ["2026-10-12T02:00:00Z", "2026-10-26T02:00:00Z"]
    page = (tmp_path / "site" / "engines" / "a.html").read_text()
    assert "2026-10-26 02:00 UTC †" in page and "scored suites changed" in page
