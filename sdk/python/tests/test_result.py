"""Tests for result module."""

import pytest
from substrait_compliance import (
    ComplianceResult,
    ComplianceReport,
    TestStatus
)


def test_compliance_result():
    """Test ComplianceResult."""
    result = ComplianceResult(
        test_id="q01",
        status=TestStatus.PASSED,
        execution_time_ms=100
    )
    
    assert result.test_id == "q01"
    assert result.is_success()
    assert result.execution_time_ms == 100


def test_compliance_report():
    """Test ComplianceReport."""
    report = ComplianceReport(
        suite_name="tpch",
        engine_name="TestEngine"
    )
    
    # Add results
    report.results.append(
        ComplianceResult("q01", TestStatus.PASSED)
    )
    report.results.append(
        ComplianceResult("q02", TestStatus.FAILED)
    )
    report.results.append(
        ComplianceResult("q03", TestStatus.PASSED)
    )
    
    assert report.get_total_count() == 3
    assert report.get_passed_count() == 2
    assert report.get_failed_count() == 1
    assert report.get_pass_rate() == pytest.approx(66.67, rel=0.01)
