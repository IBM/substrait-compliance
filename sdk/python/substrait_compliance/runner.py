"""Test runner for executing compliance tests."""

from datetime import datetime
from math import isclose
from typing import Any, List
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
            
            # Calculate execution time
            end_time = datetime.now()
            result.execution_time_ms = int(
                (end_time - start_time).total_seconds() * 1000
            )

            # If no expected output is present we cannot verify correctness —
            # return SKIPPED rather than PASSED so the result is honest.
            if not test_case.expected_output:
                return ComplianceResult(
                    test_id=test_case.id,
                    status=TestStatus.SKIPPED,
                    error_message="No expected output — cannot verify correctness",
                    execution_time_ms=result.execution_time_ms,
                )

            # Expected output present — compare and report accordingly
            if result.output_data:
                if not self._compare_results(result.output_data, test_case.expected_output):
                    result.status = TestStatus.FAILED
                    result.error_message = "Output mismatch"

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
        if actual is None or expected is None:
            return actual is expected

        if actual.row_count() != expected.row_count():
            return False
        if actual.column_count() != expected.column_count():
            return False

        actual_columns = actual.columns
        expected_columns = expected.columns
        if len(actual_columns) != len(expected_columns):
            return False

        for actual_column, expected_column in zip(actual_columns, expected_columns):
            if actual_column.name.lower() != expected_column.name.lower():
                return False
            if actual_column.data_type and expected_column.data_type:
                if not self._types_compatible(actual_column.data_type.value, expected_column.data_type.value):
                    return False

        for actual_row, expected_row in zip(actual.rows, expected.rows):
            if len(actual_row) != len(expected_row):
                return False
            for actual_value, expected_value in zip(actual_row, expected_row):
                if not self._values_match(actual_value, expected_value):
                    return False

        return True

    def _types_compatible(self, type1: str | None, type2: str | None) -> bool:
        """Check if two data types are compatible for comparison."""
        if type1 is None or type2 is None:
            return True
        norm1 = self._normalize_type(type1)
        norm2 = self._normalize_type(type2)
        if norm1 == norm2:
            return True
        if {norm1, norm2} <= {'date', 'varchar'}:
            return True
        if {norm1, norm2} <= {'integer', 'double'}:
            return True
        return False

    def _normalize_type(self, data_type: str | None) -> str | None:
        """Normalize type aliases before comparison."""
        if data_type is None:
            return None

        normalized = data_type.strip().lower()
        aliases = {
            'int': 'integer',
            'integer': 'integer',
            'i8': 'integer',
            'i16': 'integer',
            'i32': 'integer',
            'long': 'integer',
            'bigint': 'integer',
            'i64': 'integer',
            'float': 'double',
            'double': 'double',
            'fp32': 'double',
            'fp64': 'double',
            'decimal': 'double',
            'bool': 'boolean',
            'boolean': 'boolean',
            'string': 'varchar',
            'varchar': 'varchar',
            'date': 'date',
            'timestamp': 'date',
        }
        return aliases.get(normalized, normalized)

    def _values_match(self, actual: Any, expected: Any) -> bool:
        """Compare individual cell values with basic normalization."""
        if actual is None or expected is None:
            return actual is expected

        try:
            act_num = float(actual)
            exp_num = float(expected)
            return isclose(act_num, exp_num, rel_tol=1e-4, abs_tol=1e-4)
        except (ValueError, TypeError):
            pass

        if isinstance(actual, bool) or isinstance(expected, bool):
            return self._to_bool(actual) == self._to_bool(expected)

        # Normalize date/datetime string representations
        act_str = str(actual).strip()
        exp_str = str(expected).strip()
        if act_str == exp_str:
            return True
        if hasattr(actual, 'strftime') and actual.strftime('%Y-%m-%d') == exp_str:
            return True
        if act_str.endswith(' 00:00:00') and act_str[:-9] == exp_str:
            return True

        return act_str == exp_str

    def _to_bool(self, value: Any) -> bool:
        """Normalize boolean-like values."""
        if isinstance(value, bool):
            return value
        return str(value).strip().lower() == 'true'
