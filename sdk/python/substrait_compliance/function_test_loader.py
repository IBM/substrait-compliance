"""Loader for function test suites."""

from pathlib import Path
from typing import List, Dict
from .test_suite import TestSuite, TestCase, TestSuiteMetadata
from .table_data import TableData
from .function_test_parser import FunctionTestParser, FunctionTestSuite, TestType
from .function_plan_generator import (
    SubstraitPlanGenerator,
    AggregatePlanGenerator,
    WindowPlanGenerator
)


class FunctionTestSuiteAdapter(TestSuite):
    """Adapts FunctionTestSuite to TestSuite interface."""
    
    def __init__(self, function_suite: FunctionTestSuite, test_cases: List[TestCase]):
        self._function_suite = function_suite
        self._test_cases = test_cases
        self._metadata = TestSuiteMetadata(
            name=function_suite.name,
            version="1.0.0",
            description=f"{function_suite.test_type.value} tests for {function_suite.name}"
        )
    
    def get_name(self) -> str:
        return self._function_suite.name
    
    def get_test_cases(self) -> List[TestCase]:
        return self._test_cases.copy()
    
    def get_metadata(self) -> TestSuiteMetadata:
        return self._metadata


class FunctionTestSuiteLoader:
    """
    Loads function test suites from .test files.
    
    Converts .test files into executable TestSuite objects by:
    1. Parsing the .test file format
    2. Generating Substrait plans for each test case
    3. Creating TestCase objects compatible with ComplianceRunner
    """
    
    def __init__(self):
        self.parser = FunctionTestParser()
        self.generators = {
            TestType.SCALAR: SubstraitPlanGenerator(),
            TestType.AGGREGATE: AggregatePlanGenerator(),
            TestType.WINDOW: WindowPlanGenerator()
        }
    
    def load_file(self, file_path: Path) -> TestSuite:
        """
        Load a single .test file.
        
        Args:
            file_path: Path to the .test file
            
        Returns:
            TestSuite object ready for execution
        """
        # Parse the .test file
        function_suite = self.parser.parse_file(file_path)
        
        # Get appropriate generator
        generator = self.generators.get(function_suite.test_type)
        if not generator:
            raise ValueError(f"No generator for test type: {function_suite.test_type}")
        
        # Convert function test cases to TestCase objects
        test_cases = []
        for func_test in function_suite.test_cases:
            # Generate Substrait plan
            plan_bytes = generator.generate_plan(func_test)
            
            # Create TestCase
            test_case = TestCase(
                id=func_test.test_id,
                description=f"{func_test.category}: {func_test.function_call}",
                plan_bytes=plan_bytes,
                input_data={},  # Function tests don't need external input data
                expected_output=self._create_expected_output(func_test)
            )
            test_cases.append(test_case)
        
        return FunctionTestSuiteAdapter(function_suite, test_cases)
    
    def load_directory(self, directory: Path, pattern: str = "*.test") -> List[TestSuite]:
        """
        Load all .test files from a directory.
        
        Args:
            directory: Directory containing .test files
            pattern: File pattern to match (default: *.test)
            
        Returns:
            List of TestSuite objects
        """
        test_suites = []
        
        for test_file in directory.glob(pattern):
            try:
                suite = self.load_file(test_file)
                test_suites.append(suite)
            except Exception as e:
                print(f"Warning: Failed to load {test_file}: {e}")
        
        return test_suites
    
    def load_category(self, base_dir: Path, category: str) -> List[TestSuite]:
        """
        Load all test files from a specific category.
        
        Args:
            base_dir: Base test-suites directory
            category: Category name (e.g., 'arithmetic', 'aggregate', 'window')
            
        Returns:
            List of TestSuite objects for that category
        """
        category_dir = base_dir / "functions" / category
        if not category_dir.exists():
            raise ValueError(f"Category directory not found: {category_dir}")
        
        return self.load_directory(category_dir)
    
    def _create_expected_output(self, func_test):
        """
        Create expected output TableData from function test case.
        
        For now, this creates a simple single-row, single-column table
        with the expected result.
        """
        from .function_test_parser import parse_expected_result
        
        expected = parse_expected_result(func_test.expected_result)
        
        # Create a simple TableData with one column and one row
        # In a real implementation, this would be more sophisticated
        class SimpleTableData(TableData):
            def __init__(self, value, type_str):
                self._value = value
                self._type = type_str
            
            def row_count(self) -> int:
                return 1
            
            def column_count(self) -> int:
                return 1
            
            def get_value(self, row: int, col: int):
                if row == 0 and col == 0:
                    return self._value
                raise IndexError("Invalid row/column")
            
            def get_column_name(self, col: int) -> str:
                return "result"
            
            def get_column_type(self, col: int) -> str:
                return self._type
        
        # Return expected output (None for error cases will be handled by runner)
        return SimpleTableData(
            expected.get('value'),
            expected.get('type', 'unknown')
        )


class CategoryTestSuiteLoader:
    """
    Loads all tests from a category and combines them into a single suite.
    """
    
    def __init__(self):
        self.function_loader = FunctionTestSuiteLoader()
    
    def load_category_combined(self, base_dir: Path, category: str) -> TestSuite:
        """
        Load all tests from a category into a single combined suite.
        
        Args:
            base_dir: Base test-suites directory
            category: Category name
            
        Returns:
            Single TestSuite containing all tests from the category
        """
        suites = self.function_loader.load_category(base_dir, category)
        
        # Combine all test cases
        all_test_cases = []
        for suite in suites:
            all_test_cases.extend(suite.get_test_cases())
        
        # Create combined metadata
        metadata = TestSuiteMetadata(
            name=f"{category}_functions",
            version="1.0.0",
            description=f"All {category} function tests ({len(all_test_cases)} tests)"
        )
        
        # Create a simple combined suite
        class CombinedTestSuite(TestSuite):
            def __init__(self, name: str, test_cases: List[TestCase], metadata: TestSuiteMetadata):
                self._name = name
                self._test_cases = test_cases
                self._metadata = metadata
            
            def get_name(self) -> str:
                return self._name
            
            def get_test_cases(self) -> List[TestCase]:
                return self._test_cases.copy()
            
            def get_metadata(self) -> TestSuiteMetadata:
                return self._metadata
        
        return CombinedTestSuite(
            name=f"{category}_functions",
            test_cases=all_test_cases,
            metadata=metadata
        )

