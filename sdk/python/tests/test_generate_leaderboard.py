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


def test_ranks_by_overall_and_lays_out_every_section(tmp_path):
    # A: functions 60%, queries 80% -> 70. B: functions 90%, queries 10% -> 50.
    entry(tmp_path, "A", fn=(60, 40), sql=(95, 5), q=(8, 2), q_old=(1, 9), ext=(9, 1))
    entry(tmp_path, "B", fn=(90, 10), sql=(99, 1), q=(1, 9), q_old=(1, 9))
    site = generate(tmp_path)
    md = (site / "leaderboard.md").read_text()

    ranking = md.split("## Rankings")[1].split("##")[0]
    assert ranking.index("| 🥇 | A 1.0 |") < ranking.index("| 🥈 | B 1.0 |")
    assert "| **70.0%** | 60.0% | 80.0% | *95.0%* |" in ranking
    assert "| **50.0%** | 90.0% | 10.0% | *99.0%* |" in ranking

    functions = md.split("## Functions\n")[1].split("##")[0]
    assert "| A 1.0 | **60.0% (60/100)** | 95.0% | −35.0 pts | 90.0% | — |" in functions
    assert "| B 1.0 | **90.0% (90/100)** | 99.0% | −9.0 pts | — | — |" in functions

    queries = md.split("## Queries")[1].split("##")[0]
    assert "| Engine | TPC-H (scored) | TPC-H, 0.81 legacy encoding |" in queries
    assert "| A 1.0 | **8/10 (80.0%)** | 1/10 (10.0%) |" in queries

    by_ext = md.split("## Functions by extension")[1].split("##")[0]
    assert "| window | 0.0% / 100.0% | 0.0% / 100.0% |" in by_ext

    other = md.split("## Other results (not scored)")[1].split("##")[0]
    assert "`legacy`" in other and "fsuite-sql" not in other and "tpch-substrait-0.81" not in other

    data = json.loads((site / "leaderboard.json").read_text())
    assert [e["engineName"] for e in data["engines"]] == ["A", "B"]
    assert data["engines"][0]["categories"]["queries"]["passRate"] == 80.0
    assert data["engines"][0]["schemaVersion"] == 2  # a published entry works as a regression baseline
    page = (site / "leaderboard.html").read_text()
    assert page.startswith("<!DOCTYPE html>") and "<strong>70.0%</strong>" in page
    assert "prefers-color-scheme: dark" in page


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
