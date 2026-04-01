"""Test suite loaders for various formats."""

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, List
import yaml
from .test_suite import TestSuite, TestCase, TestSuiteMetadata
from .table_data import TableData
from .exceptions import ComplianceException


class TestSuiteLoader(ABC):
    """Abstract loader interface."""
    
    @abstractmethod
    def load(self, path: Path) -> TestSuite:
        """Load test suite from file."""
        pass
    
    @abstractmethod
    def supports(self, path: Path) -> bool:
        """Check if loader supports this file."""
        pass


class SimpleTestSuite(TestSuite):
    """Simple in-memory test suite implementation."""
    
    def __init__(
        self,
        name: str,
        test_cases: List[TestCase],
        metadata: TestSuiteMetadata
    ):
        self._name = name
        self._test_cases = test_cases
        self._metadata = metadata
    
    def get_name(self) -> str:
        return self._name
    
    def get_test_cases(self) -> List[TestCase]:
        return self._test_cases.copy()
    
    def get_metadata(self) -> TestSuiteMetadata:
        return self._metadata


class YamlTestSuiteLoader(TestSuiteLoader):
    """Loads test suites from YAML files."""
    
    def load(self, path: Path) -> TestSuite:
        """Load test suite from YAML file."""
        try:
            with open(path, 'r') as f:
                data = yaml.safe_load(f)
            
            return self._create_test_suite(data, path.parent)
            
        except Exception as e:
            raise ComplianceException(
                f"Failed to load test suite from {path}",
                cause=e
            )
    
    def supports(self, path: Path) -> bool:
        """Check if file is YAML."""
        return path.suffix.lower() in ['.yaml', '.yml']
    
    def _create_test_suite(
        self,
        data: dict,
        base_dir: Path
    ) -> TestSuite:
        """Create test suite from parsed YAML."""
        test_cases = []
        
        for tc_def in data.get('testCases', []):
            # Load plan
            plan_path = base_dir / tc_def['planBinary']
            with open(plan_path, 'rb') as f:
                plan_bytes = f.read()
            
            # TODO: Load input data from CSV files
            input_data = {}
            
            # TODO: Load expected output
            expected_output = None
            
            test_case = TestCase(
                id=tc_def['id'],
                description=tc_def['description'],
                plan_bytes=plan_bytes,
                input_data=input_data,
                expected_output=expected_output
            )
            test_cases.append(test_case)
        
        metadata = TestSuiteMetadata(
            name=data['name'],
            version=data['version'],
            description=data['description']
        )
        
        return SimpleTestSuite(data['name'], test_cases, metadata)
