"""Tests for scripts/engine_findings.py: what an engine gets wrong, grouped by cause."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import engine_findings as ef  # noqa: E402

REG = {"fs": {"path": "test-suites/fs"}, "fs-sql": {"path": "test-suites/fs"}}


def row(fn, line, status, reason, prefix="ci/test-suites/fs"):
    return {"file": f"{prefix}/arithmetic/{fn}.test", "line": line, "test": f"{fn}(1::i8)",
            "status": status, "reason": reason}


def test_signature_replaces_what_varies_between_cases():
    sig = ef.signature
    assert sig("CatalogException: Scalar Function with name negate does not exist!", "negate") == \
        sig("CatalogException: Scalar Function with name shift_left does not exist!", "shift_left")
    assert sig('DataFusion error: Unsupported function name: "regexp_split"', "regexp_split") == \
        'DataFusion error: Unsupported function name: "…"'
    assert sig("Aggregate function variance:req_fp64 is not supported: function anchor = 3", "variance") == \
        "Aggregate function … is not supported: function anchor = N"
    assert sig("got 1, expected 2 [SELECT regexp_count('a', 'b')]") == "Wrong result (got …, expected …)"
    assert sig("Not implemented Error: Unsupported expression type subquery\n\nLINE 1: SELECT *") == \
        "Not implemented Error: Unsupported expression type subquery"
    assert sig("Output mismatch: expected 4 rows, got 3") == "Output mismatch: wrong number of rows"


def test_plan_failures_are_the_consumer_unless_sql_fails_too():
    plan = {"mode": "plan", "nonPassingResults": [
        row("negate", 1, "failed", "Scalar Function with name negate does not exist!"),
        row("abs", 2, "failed", "got 2, expected 1"),
        row("add", 3, "skipped", "plan not built: user-defined type literals (u!...) are not built"),
        row("add", 4, "skipped", "unsupported option overflow:SATURATE (engine implements overflow:ERROR)"),
        row("div", 5, "skipped", "expected result is undefined")]}
    sql = {"mode": "sql", "nonPassingResults": [row("abs", 2, "failed", "got 2, expected 1", prefix="test-suites/fs")]}
    legacy = {"mode": "sql", "nonPassingResults": [row("old", 9, "failed", "boom")]}
    f = ef.compute_findings({"fs": {"report": plan, "category": "functions"},
                             "fs-sql": {"report": sql, "category": None},
                             "functions-legacy": {"report": legacy, "category": None}}, REG)
    by = {(g["section"], g["signature"]): g for g in f["groups"]}
    assert ("consumer", "Scalar Function with name … does not exist!") in by
    assert ("engine", "Wrong result (got …, expected …)") in by          # abs: fails as SQL too
    assert not any(k[0] == "consumer" and "Wrong" in k[1] for k in by)   # ... so not a consumer finding
    assert by[("untested", "plan not built: user-defined type literals (u!...) are not built")]["where"] == "plan builder"
    assert any(k[0] == "options" for k in by)
    assert not any("undefined" in k[1] for k in by)                      # nothing to report
    assert not any("boom" in k[1] for k in by)                           # legacy SQL suite is not a finding
    assert f["sections"]["consumer"] == {"groups": 1, "cases": 1}
    case = by[("consumer", "Scalar Function with name … does not exist!")]["list"][0]
    assert case["id"] == "arithmetic/negate.test:1" and case["suite"] == "fs"


def test_failing_queries_of_scored_query_suites():
    q = {"testResults": [{"testId": "q01", "status": "PASSED"},
                         {"testId": "q02", "status": "FAILED", "message": "Unsupported subquery"},
                         {"testId": "q04", "status": "FAILED", "message": "Unsupported subquery"}]}
    old = {"testResults": [{"testId": "q09", "status": "FAILED", "message": "x"}]}
    f = ef.compute_findings({"tpch": {"report": q, "category": "queries"},
                             "tpch-substrait-0.81": {"report": old, "category": None}}, {})
    assert [(g["section"], g["cases"], list(g["subjects"])) for g in f["groups"]] == [("queries", 2, ["q02", "q04"])]
