"""
DataFusion Python compliance example.

Demonstrates how to integrate Apache DataFusion with the Substrait
compliance framework using Python.
"""

import sys
from pathlib import Path
from typing import Dict, Optional

# Add SDK to path
sys.path.insert(0, str(Path(__file__).parent / "../../sdk/python"))

from substrait_compliance import (
    ComplianceEngine,
    EngineInfo,
    EngineCapabilities,
    ComplianceRunner,
    ComplianceResult,
    TestStatus,
    TableData,
    YamlTestSuiteLoader,
)


class DataFusionComplianceEngine(ComplianceEngine):
    """
    DataFusion engine implementation for Substrait compliance testing.
    
    This demonstrates how to integrate DataFusion with the compliance framework.
    """
    
    def __init__(self):
        """Initialize DataFusion context."""
        # In real implementation, would initialize DataFusion SessionContext
        # from datafusion import SessionContext
        # self.ctx = SessionContext()
        pass
    
    def get_info(self) -> EngineInfo:
        """Return DataFusion engine information."""
        return EngineInfo(
            name="DataFusion",
            version="35.0.0",
            vendor="Apache Software Foundation",
            description="Fast, extensible query engine with native Substrait support"
        )
    
    def get_capabilities(self) -> EngineCapabilities:
        """Return DataFusion capabilities."""
        return EngineCapabilities(
            supported_relations=[
                "read", "filter", "project", "aggregate",
                "join", "sort", "limit", "union"
            ],
            supported_functions=[
                "add", "subtract", "multiply", "divide",
                "sum", "count", "avg", "min", "max",
                "concat", "substring", "upper", "lower"
            ],
            supported_types=[
                "integer", "bigint", "double", "varchar",
                "date", "timestamp", "boolean"
            ],
            max_plan_depth=100,
            supports_extensions=True
        )
    
    def execute_plan(
        self,
        plan_bytes: bytes,
        input_data: Dict[str, TableData]
    ) -> ComplianceResult:
        """Execute a Substrait plan."""
        try:
            # 1. Register input tables
            self._register_tables(input_data)
            
            # 2. Execute Substrait plan
            # In real implementation:
            # from substrait import Plan
            # plan = Plan.deserialize(plan_bytes)
            # result = self.ctx.execute_substrait(plan)
            
            # For this example, simulate execution
            output = self._execute_substrait_plan(plan_bytes)
            
            return ComplianceResult(
                test_id="execution",
                status=TestStatus.PASSED,
                output_data=output
            )
            
        except Exception as e:
            return ComplianceResult(
                test_id="execution",
                status=TestStatus.ERROR,
                error_message=str(e)
            )
    
    def validate_plan(self, plan_bytes: bytes) -> ComplianceResult:
        """Validate a Substrait plan."""
        try:
            # Validate plan structure
            # In real implementation, would use DataFusion's validator
            is_valid = self._validate_substrait_plan(plan_bytes)
            
            return ComplianceResult(
                test_id="validation",
                status=TestStatus.PASSED if is_valid else TestStatus.FAILED,
                error_message=None if is_valid else "Plan validation failed"
            )
            
        except Exception as e:
            return ComplianceResult(
                test_id="validation",
                status=TestStatus.ERROR,
                error_message=str(e)
            )
    
    def _register_tables(self, input_data: Dict[str, TableData]):
        """Register input tables in DataFusion."""
        for table_name, data in input_data.items():
            # In real implementation:
            # df = self._table_data_to_dataframe(data)
            # self.ctx.register_table(table_name, df)
            pass
    
    def _execute_substrait_plan(self, plan_bytes: bytes) -> Optional[TableData]:
        """Execute Substrait plan and return results."""
        # In real implementation, would execute plan and convert results
        # For this example, return empty result
        return None
    
    def _validate_substrait_plan(self, plan_bytes: bytes) -> bool:
        """Validate Substrait plan structure."""
        # In real implementation, would validate plan
        return plan_bytes is not None and len(plan_bytes) > 0


def main():
    """Run DataFusion compliance tests."""
    print("DataFusion Substrait Compliance Testing")
    print("=" * 50)
    print()
    
    # 1. Create DataFusion engine
    engine = DataFusionComplianceEngine()
    
    # 2. Print engine info
    info = engine.get_info()
    print(f"Engine: {info}")
    print()
    
    # 3. Load TPC-H test suite
    loader = YamlTestSuiteLoader()
    suite_path = Path(__file__).parent / "../../test-suites/tpch/metadata.yaml"
    suite = loader.load(suite_path)
    
    print(f"Loaded test suite: {suite.get_name()}")
    print(f"Test cases: {len(suite.get_test_cases())}")
    print()
    
    # 4. Run compliance tests
    runner = ComplianceRunner(engine)
    report = runner.run_test_suite(suite)
    
    # 5. Print results
    print("Results:")
    print("-" * 50)
    print(f"Total:     {report.get_total_count()}")
    print(f"Passed:    {report.get_passed_count()}")
    print(f"Failed:    {report.get_failed_count()}")
    print(f"Errors:    {report.get_error_count()}")
    print(f"Pass Rate: {report.get_pass_rate():.1f}%")
    print()
    
    # 6. Show failed tests
    if report.get_failed_count() > 0 or report.get_error_count() > 0:
        print("Failed/Error Tests:")
        for result in report.results:
            if result.status != TestStatus.PASSED:
                print(f"  {result.test_id}: {result.status.value} - {result.error_message}")


if __name__ == "__main__":
    main()
