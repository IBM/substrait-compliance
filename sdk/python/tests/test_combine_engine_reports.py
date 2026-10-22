"""Tests for scripts/combine_engine_reports.py."""

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import combine_engine_reports  # noqa: E402


def write_report(path, passed, failed, skipped=0, **extra):
    path.write_text(json.dumps({
        "engineName": "DuckDB", "engineVersion": "1.5.6",
        "totalTests": passed + failed + skipped, "passed": passed, "failed": failed, "skipped": skipped,
        **extra,
    }))
    return path


def combine(tmp_path, *args):
    out = tmp_path / "out.json"
    combine_engine_reports.main(["--engine", "DuckDB", *args, "--output", str(out)])
    return json.loads(out.read_text())


def test_overall_is_the_average_of_the_categories_not_a_pooled_rate(tmp_path):
    # 1,000 function cases must not drown 10 queries: 90% and 50% average to 70%,
    # where pooling the counts would give 89.6%.
    fn = write_report(tmp_path / "fn.json", passed=900, failed=100)
    q = write_report(tmp_path / "q.json", passed=5, failed=5)
    c = combine(tmp_path, "--functions", f"functions={fn}", "--queries", f"tpch={q}")
    assert c["schemaVersion"] == 2
    assert c["categories"]["functions"]["passRate"] == 90.0
    assert c["categories"]["queries"]["passRate"] == 50.0
    assert c["passRate"] == 70.0
    assert c["suites"]["functions"]["category"] == "functions"
    assert c["suites"]["tpch"]["category"] == "queries"


def test_overall_averages_exact_rates(tmp_path):
    # 2/3 and 1/3 -> exactly 50.0, not (66.7 + 33.3) / 2 rounded twice
    fn = write_report(tmp_path / "fn.json", passed=2, failed=1)
    q = write_report(tmp_path / "q.json", passed=1, failed=2)
    assert combine(tmp_path, "--functions", f"f={fn}", "--queries", f"q={q}")["passRate"] == 50.0


def test_reference_suites_are_listed_but_not_scored(tmp_path):
    fn = write_report(tmp_path / "fn.json", passed=8, failed=2)
    q = write_report(tmp_path / "q.json", passed=1, failed=1)
    sql = write_report(tmp_path / "sql.json", passed=1, failed=9, skipped=5)
    c = combine(tmp_path, "--functions", f"functions={fn}", "--queries", f"tpch={q}",
                "--reference", f"functions-sql={sql}")
    assert c["suites"]["functions-sql"]["informational"] is True
    assert "category" not in c["suites"]["functions-sql"]
    assert c["suites"]["functions-sql"]["passRate"] == 10.0
    assert (c["passed"], c["failed"]) == (9, 3)  # scored suites only
    assert c["passRate"] == 65.0


def test_informational_is_an_alias_for_reference(tmp_path):
    fn = write_report(tmp_path / "fn.json", passed=1, failed=0)
    old = write_report(tmp_path / "old.json", passed=1, failed=1)
    c = combine(tmp_path, "--functions", f"f={fn}", "--informational", f"legacy={old}")
    assert c["suites"]["legacy"]["informational"] is True


def test_a_missing_category_counts_as_zero(tmp_path):
    fn = write_report(tmp_path / "fn.json", passed=10, failed=0)
    c = combine(tmp_path, "--functions", f"f={fn}")
    assert c["categories"]["queries"] == {"suites": [], "passed": 0, "failed": 0, "skipped": 0, "passRate": None}
    assert c["passRate"] == 50.0


def test_per_extension_figures_and_crashes_are_kept(tmp_path):
    fn = write_report(tmp_path / "fn.json", passed=3, failed=1, skipped=1, mode="plan", engineCrashes=2,
                      categories={"arithmetic": {"total": 3, "passed": 2, "failed": 1, "skipped": 0},
                                  "window": {"total": 2, "passed": 1, "failed": 0, "skipped": 1}})
    c = combine(tmp_path, "--functions", f"f={fn}")
    s = c["suites"]["f"]
    assert s["mode"] == "plan" and s["engineCrashes"] == 2
    assert s["extensions"]["arithmetic"]["passRate"] == 66.7
    assert s["extensions"]["window"] == {"totalTests": 2, "passed": 1, "failed": 0, "skipped": 1, "passRate": 100.0}


def test_rejects_reports_of_another_engine_and_empty_runs(tmp_path):
    other = tmp_path / "other.json"
    other.write_text(json.dumps({"engineName": "DataFusion", "passed": 1, "failed": 0}))
    with pytest.raises(SystemExit):
        combine(tmp_path, "--functions", f"f={other}")
    empty = write_report(tmp_path / "empty.json", passed=0, failed=0, skipped=4)
    with pytest.raises(SystemExit):
        combine(tmp_path, "--functions", f"f={empty}")


def test_needs_a_scored_suite(tmp_path):
    ref = write_report(tmp_path / "r.json", passed=1, failed=0)
    with pytest.raises(SystemExit):
        combine(tmp_path, "--reference", f"r={ref}")
