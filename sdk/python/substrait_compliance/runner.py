"""Test runner for executing compliance tests."""

from datetime import datetime
from typing import List
from .engine import ComplianceEngine
from .test_suite import TestSuite, TestCase
from .result import ComplianceResult, ComplianceReport, TestStatus
from .exceptions import ComplianceException


class ComplianceRunner:
    """
    Executes compliance tests against an engine.
    
    The runner takes a test suite and executes each test case
    against the provided engine implementation.
    """
    
    def __init__(self, engine: ComplianceEngine):
        """
        Initialize runner with an engine.
        
        Args:
            engine: Engine implementation to test
        """
        self.engine = engine
    
    def run_test_suite(self, suite: TestSuite) -> ComplianceReport:
        """
        Run all tests in a suite.
        
        Args:
            suite: Test suite to execute
            
        Returns:
            ComplianceReport with aggregated results
        """
        engine_info = self.engine.get_info()
        report = ComplianceReport(
            suite_name=suite.get_name(),
            engine_name=engine_info.name,
            start_time=datetime.now()
        )
        
        test_cases = suite.get_test_cases()
        for test_case in test_cases:
            result = self.run_test_case(test_case)
            report.results.append(result)
        
        report.end_time = datetime.now()
        return report
    
    def run_test_case(self, test_case: TestCase) -> ComplianceResult:
        """
        Run a single test case.
        
        Args:
            test_case: Test case to execute
            
        Returns:
            ComplianceResult with execution outcome
        """
        start_time = datetime.now()
        
        try:
            # Execute the plan
            result = self.engine.execute_plan(
                test_case.plan_bytes,
                test_case.input_data
            )
            
            # If expected output provided, compare
            if test_case.expected_output and result.output_data:
                if not self._compare_results(
                    result.output_data,
                    test_case.expected_output
                ):
                    result.status = TestStatus.FAILED
                    result.error_message = "Output mismatch"
            
            # Calculate execution time
            end_time = datetime.now()
            result.execution_time_ms = int(
                (end_time - start_time).total_seconds() * 1000
            )
            
            return result
            
        except Exception as e:
            end_time = datetime.now()
            execution_time = int(
                (end_time - start_time).total_seconds() * 1000
            )
            
            return ComplianceResult(
                test_id=test_case.id,
                status=TestStatus.ERROR,
                error_message=str(e),
                execution_time_ms=execution_time
            )
    
    def _compare_results(self, actual, expected) -> bool:
        """Compare actual vs expected results."""
        # Simplified comparison - would need more sophisticated logic
        if actual.row_count() != expected.row_count():
            return False
        if actual.column_count() != expected.column_count():
            return False
        return True
