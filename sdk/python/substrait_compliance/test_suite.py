"""Test suite and test case classes."""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Dict, List, Optional
from .table_data import TableData


@dataclass
class TestSuiteMetadata:
    """Metadata about a test suite."""
    
    name: str
    version: str
    description: str
    
    def __str__(self) -> str:
        return f"{self.name} v{self.version}"


@dataclass
class TestCase:
    """A single test case with plan and data."""
    
    id: str
    description: str
    plan_bytes: bytes
    input_data: Dict[str, TableData]
    expected_output: Optional[TableData] = None
    
    def __str__(self) -> str:
        return f"TestCase({self.id}: {self.description})"


class TestSuite(ABC):
    """
    Abstract test suite interface.
    
    Test suites contain multiple test cases and metadata.
    """
    
    @abstractmethod
    def get_name(self) -> str:
        """Return suite name."""
        pass
    
    @abstractmethod
    def get_test_cases(self) -> List[TestCase]:
        """Return all test cases."""
        pass
    
    @abstractmethod
    def get_metadata(self) -> TestSuiteMetadata:
        """Return suite metadata."""
        pass
