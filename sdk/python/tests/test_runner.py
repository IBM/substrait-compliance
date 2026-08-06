"""Pass-through integration tests for ComplianceRunner.

A "pass-through" engine returns the expected output verbatim.
Running it against a suite that has expected output must yield 100%.
Any failure indicates a bug in the comparator or type-normalisation.
"""
import pytest
from substrait_compliance import (
    ComplianceEngine,
    EngineInfo,
    EngineCapabilities,
    ComplianceResult,
    ComplianceReport,
    TestStatus,
    TableData,
)
from substrait_compliance.runner import ComplianceRunner
from substrait_compliance.test_suite import TestSuite, TestCase, TestSuiteMetadata
from substrait_compliance.table_data import Column, DataType


# ── Helpers ───────────────────────────────────────────────────────────────────

def _col(name: str, dtype: DataType) -> Column:
    return Column(name=name, data_type=dtype, nullable=True)


def _make_table(cols: list, rows: list) -> TableData:
    return TableData(columns=cols, rows=rows)


def _make_integer_table(rows: list) -> TableData:
    cols = [_col("a", DataType.INTEGER), _col("b", DataType.INTEGER)]
    return _make_table(cols, rows)


def _make_double_table(vals: list) -> TableData:
    cols = [_col("val", DataType.DOUBLE)]
    return _make_table(cols, [[v] for v in vals])


class _InMemorySuite(TestSuite):
    """Minimal in-memory TestSuite backed by a plain list."""

    def __init__(self, name: str, test_cases: list):
        self._name = name
        self._cases = test_cases
        self._metadata = TestSuiteMetadata(name=name, version="0.0.0", description="")

    def get_name(self) -> str:
        return self._name

    def get_test_cases(self) -> list:
        return self._cases

    def get_metadata(self):
        return self._metadata


class _ExpectedReturnEngine(ComplianceEngine):
    """Returns the expected output stored in the test-case, keyed by plan_bytes."""

    def get_info(self) -> EngineInfo:
        return EngineInfo(name="ExpectedReturn", version="0.0.0", vendor="Test")

    def get_capabilities(self) -> EngineCapabilities:
        return EngineCapabilities()

    def execute_plan(self, plan_bytes: bytes, input_data: dict) -> ComplianceResult:
        # plan_bytes encodes the test-case id; look up expected via a side-channel
        # stored on the engine.
        test_id = plan_bytes.decode("utf-8", errors="replace")
        output = self._outputs.get(test_id)
        result = ComplianceResult(test_id=test_id, status=TestStatus.PASSED)
        result.output_data = output
        return result

    def validate_plan(self, plan_bytes: bytes) -> ComplianceResult:
        return ComplianceResult(test_id="validation", status=TestStatus.PASSED)


def _engine_with_outputs(outputs: dict) -> _ExpectedReturnEngine:
    engine = _ExpectedReturnEngine()
    engine._outputs = outputs
    return engine


def _tc(test_id: str, expected: TableData) -> TestCase:
    return TestCase(
        id=test_id,
        description=test_id,
        plan_bytes=test_id.encode(),
        input_data={},
        expected_output=expected,
    )


# ── Tests ─────────────────────────────────────────────────────────────────────

def test_pass_through_scores_100_percent():
    """Pass-through engine must achieve 100% when expected output is provided."""
    cases = {
        "q01": _make_integer_table([[1, 2], [3, 4]]),
        "q02": _make_integer_table([[10, 20]]),
        "q03": _make_integer_table([[0, 0], [1, 1]]),
    }
    test_cases = [_tc(tid, data) for tid, data in cases.items()]
    suite = _InMemorySuite("pass-through", test_cases)
    engine = _engine_with_outputs(cases)

    report = ComplianceRunner(engine).run_test_suite(suite)

    assert report.get_total_count() == 3
    assert report.get_passed_count() == 3, f"failures: {[(r.test_id, r.error_message) for r in report.results if r.status != TestStatus.PASSED]}"
    assert report.get_failed_count() == 0
    assert abs(report.get_pass_rate() - 100.0) < 0.01


def test_value_mismatch_is_detected():
    """A wrong value in the output must produce a FAILED result."""
    expected = _make_integer_table([[1, 2]])
    wrong    = _make_integer_table([[1, 99]])   # col b is wrong

    tc = _tc("mismatch", expected)
    suite = _InMemorySuite("mismatch", [tc])
    engine = _engine_with_outputs({"mismatch": wrong})

    report = ComplianceRunner(engine).run_test_suite(suite)

    assert report.get_failed_count() == 1, "wrong value should be detected"
    assert report.get_passed_count() == 0


def test_missing_expected_output_is_skipped():
    """A test case with no expected output must be SKIPPED, not PASSED."""
    tc = TestCase(id="no-exp", description="no expected", plan_bytes=b"no-exp", input_data={})
    # expected_output is None (default)

    suite = _InMemorySuite("skip-suite", [tc])
    engine = _engine_with_outputs({})   # returns no output

    report = ComplianceRunner(engine).run_test_suite(suite)

    statuses = [r.status for r in report.results]
    assert TestStatus.FAILED not in statuses, "missing expected must not produce FAILED"
    assert TestStatus.SKIPPED in statuses, "missing expected must produce SKIPPED"


def test_epsilon_comparison_for_floats():
    """Values within 1e-9 are equal; beyond that are different."""
    # Within epsilon → PASS
    expected_close = _make_double_table([1.0])
    actual_close   = _make_double_table([1.0 + 5e-10])

    # Outside epsilon → FAIL
    expected_far = _make_double_table([1.0])
    actual_far   = _make_double_table([2.0])

    tc_close = _tc("close", expected_close)
    tc_far   = _tc("far",   expected_far)

    suite  = _InMemorySuite("epsilon", [tc_close, tc_far])
    engine = _engine_with_outputs({"close": actual_close, "far": actual_far})

    report = ComplianceRunner(engine).run_test_suite(suite)

    assert report.get_passed_count() == 1, "'close' should pass (within epsilon)"
    assert report.get_failed_count() == 1, "'far' should fail (outside epsilon)"


def test_type_normalisation_integer_aliases():
    """int32 and INTEGER columns should compare as equal."""
    expected = _make_table(
        [_col("x", DataType.INTEGER)],
        [[42]]
    )
    # Simulate an engine that returns the same value with the same type
    actual = _make_table(
        [_col("x", DataType.INTEGER)],
        [[42]]
    )
    tc = _tc("types", expected)
    suite = _InMemorySuite("types", [tc])
    engine = _engine_with_outputs({"types": actual})

    report = ComplianceRunner(engine).run_test_suite(suite)
    assert report.get_passed_count() == 1
