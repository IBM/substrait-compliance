"""Tests for scripts/check_compliance_regression.py."""

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import check_compliance_regression as crc  # noqa: E402


def entry(overall, functions, queries, fsuites=("f",), qsuites=("tpch",)):
    return {"schemaVersion": 2, "engineName": "DuckDB", "passRate": overall,
            "categories": {"functions": {"suites": list(fsuites), "passRate": functions},
                           "queries": {"suites": list(qsuites), "passRate": queries}}}


def run(tmp_path, current, baseline, *extra):
    cur = tmp_path / "cur.json"
    cur.write_text(json.dumps(current))
    args = ["--current", str(cur), "--baseline", str(tmp_path / "base.json"), *extra]
    if baseline is not None:
        (tmp_path / "base.json").write_text(json.dumps(baseline))
    crc.main(args)


def test_passes_within_the_allowed_drop_and_on_improvements(tmp_path, capsys):
    run(tmp_path, entry(61.5, 69.0, 54.0), entry(62.0, 69.5, 54.5))
    run(tmp_path, entry(70.0, 80.0, 60.0), entry(62.0, 69.5, 54.5))
    assert "improved" in capsys.readouterr().out


def test_fails_when_a_category_drops_more_than_allowed(tmp_path, capsys):
    with pytest.raises(SystemExit) as e:
        run(tmp_path, entry(60.0, 65.5, 54.5), entry(62.0, 69.5, 54.5))
    assert e.value.code == 1
    out = capsys.readouterr().out
    assert "functions: 69.5% → 65.5% (-4.0 pts)" in out and "overall: 62.0% → 60.0%" in out


def test_allow_regression_reports_without_failing(tmp_path, capsys):
    run(tmp_path, entry(50.0, 45.5, 54.5), entry(62.0, 69.5, 54.5), "--allow-regression")
    assert "Regression accepted" in capsys.readouterr().out


def test_skips_without_a_comparable_baseline(tmp_path, capsys):
    run(tmp_path, entry(10.0, 10.0, 10.0), None)  # first run: no published entry
    run(tmp_path, entry(10.0, 10.0, 10.0), {"engineName": "DuckDB", "passRate": 87.0})  # old format
    out = capsys.readouterr().out
    assert "No published result" in out and "older report format" in out


def test_a_category_with_different_suites_is_not_compared(tmp_path, capsys):
    # Adding TPC-DS to queries changes what the score means; that is not a regression.
    run(tmp_path, entry(40.0, 69.5, 10.0, qsuites=("tpch", "tpcds")), entry(62.0, 69.5, 54.5))
    out = capsys.readouterr().out
    assert "queries: scored suites changed" in out


def test_writes_the_job_summary_only_when_asked(tmp_path, monkeypatch, capsys):
    summary = tmp_path / "summary.md"
    run(tmp_path, entry(62.0, 69.5, 54.5), entry(62.0, 69.5, 54.5))
    assert not summary.exists()  # conftest clears GITHUB_STEP_SUMMARY for every test
    monkeypatch.setenv("GITHUB_STEP_SUMMARY", str(summary))
    run(tmp_path, entry(62.0, 69.5, 54.5), entry(62.0, 69.5, 54.5))
    assert "Compliance regression check: DuckDB" in summary.read_text()
