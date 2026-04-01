"""Result classes for compliance testing."""

from dataclasses import dataclass, field
from typing import Optional, List
from enum import Enum
from datetime import datetime
from .table_data import TableData


class TestStatus(Enum):
    """Test execution status."""
    PASSED = "passed"
    FAILED = "failed"
    SKIPPED = "skipped"
    ERROR = "error"


@dataclass
class ComplianceResult:
    """Result of executing a single test case."""
    
    test_id: str
    status: TestStatus
    output_data: Optional[TableData] = None
    error_message: Optional[str] = None
    execution_time_ms: int = 0
    
    def is_success(self) -> bool:
        """Check if test passed."""
        return self.status == TestStatus.PASSED
    
    def __str__(self) -> str:
        return f"ComplianceResult({self.test_id}: {self.status.value})"


@dataclass
class ComplianceReport:
    """Aggregated report of test suite execution."""
    
    suite_name: str
    engine_name: str
    results: List[ComplianceResult] = field(default_factory=list)
    start_time: datetime = field(default_factory=datetime.now)
    end_time: Optional[datetime] = None
    
    def get_passed_count(self) -> int:
        """Count passed tests."""
        return sum(1 for r in self.results if r.status == TestStatus.PASSED)
    
    def get_failed_count(self) -> int:
        """Count failed tests."""
        return sum(1 for r in self.results if r.status == TestStatus.FAILED)
    
    def get_skipped_count(self) -> int:
        """Count skipped tests."""
        return sum(1 for r in self.results if r.status == TestStatus.SKIPPED)
    
    def get_error_count(self) -> int:
        """Count errored tests."""
        return sum(1 for r in self.results if r.status == TestStatus.ERROR)
    
    def get_total_count(self) -> int:
        """Count total tests."""
        return len(self.results)
    
    def get_pass_rate(self) -> float:
        """Calculate pass rate as percentage."""
        total = self.get_total_count()
        if total == 0:
            return 0.0
        return (self.get_passed_count() / total) * 100.0
    
    def __str__(self) -> str:
        return (
            f"ComplianceReport({self.suite_name} on {self.engine_name}: "
            f"{self.get_passed_count()}/{self.get_total_count()} passed, "
            f"{self.get_pass_rate():.1f}%)"
        )
