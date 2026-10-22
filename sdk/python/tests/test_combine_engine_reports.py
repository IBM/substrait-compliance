"""Tests for scripts/combine_engine_reports.py."""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import combine_engine_reports  # noqa: E402


def write_report(path, passed, failed, skipped=0):
    path.write_text(json.dumps({
        "engineName": "DuckDB", "engineVersion": "1.5.6",
        "totalTests": passed + failed + skipped, "passed": passed, "failed": failed, "skipped": skipped,
    }))
    return path


def test_informational_suites_are_listed_but_not_in_the_headline(tmp_path):
    scored = write_report(tmp_path / "scored.json", passed=8, failed=2)
    info = write_report(tmp_path / "info.json", passed=1, failed=9, skipped=5)
    out = tmp_path / "out.json"
    combine_engine_reports.main([
        "--engine", "DuckDB", "--suite", f"functions={scored}",
        "--informational", f"functions-new={info}", "--output", str(out),
    ])
    combined = json.loads(out.read_text())
    assert (combined["passed"], combined["failed"], combined["skipped"]) == (8, 2, 0)
    assert combined["passRate"] == 80.0
    assert combined["suites"]["functions-new"]["informational"] is True
    assert combined["suites"]["functions-new"]["passRate"] == 10.0
    assert "informational" not in combined["suites"]["functions"]
