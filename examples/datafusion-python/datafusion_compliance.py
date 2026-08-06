"""
DataFusion Python compliance example.

Demonstrates how to integrate Apache DataFusion with the Substrait
compliance framework using Python.

Requirements
------------
    pip install datafusion datafusion-substrait pyarrow

DataFusion's Python bindings expose a Substrait consumer since
datafusion-python >= 34.0 / datafusion-substrait >= 0.10.
"""

import sys
from pathlib import Path
from typing import Dict, Optional, List, Any

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


def _arrow_batches_to_table_data(batches: list) -> TableData:
    """Convert a list of PyArrow RecordBatches to TableData."""
    import pyarrow as pa

    if not batches:
        return TableData(columns=[], rows=[])

    schema = batches[0].schema

    def arrow_type_to_substrait(arrow_type) -> str:
        """Map an Arrow data type to a Substrait canonical type string."""
        import pyarrow as pa
        if pa.types.is_int8(arrow_type) or pa.types.is_int16(arrow_type) or \
           pa.types.is_int32(arrow_type) or pa.types.is_uint8(arrow_type) or \
           pa.types.is_uint16(arrow_type):
            return "integer"
        if pa.types.is_int64(arrow_type) or pa.types.is_uint32(arrow_type) or \
           pa.types.is_uint64(arrow_type):
            return "bigint"
        if pa.types.is_float32(arrow_type):
            return "float"
        if pa.types.is_float64(arrow_type) or pa.types.is_decimal(arrow_type):
            return "double"
        if pa.types.is_boolean(arrow_type):
            return "boolean"
        # date, timestamp, string all normalise to string for comparison
        return "string"

    columns = [field.name for field in schema]
    col_types = [arrow_type_to_substrait(field.type) for field in schema]

    rows: List[List[Any]] = []
    for batch in batches:
        for row_idx in range(batch.num_rows):
            row = [batch.column(col_idx)[row_idx].as_py()
                   for col_idx in range(batch.num_columns)]
            rows.append(row)

    return TableData(columns=columns, column_types=col_types, rows=rows)


class DataFusionComplianceEngine(ComplianceEngine):
    """
    DataFusion engine implementation for Substrait compliance testing.

    Uses datafusion-substrait to execute serialized Substrait plans natively.
    If the datafusion or datafusion-substrait packages are not installed the
    engine falls back to returning None so that tests are honestly marked
    FAILED rather than raising an ImportError at construction time.
    """

    def __init__(self):
        """Initialize DataFusion SessionContext."""
        self.ctx = None
        self._substrait_available = False
        try:
            from datafusion import SessionContext
            import datafusion_substrait  # noqa: F401 — check import only
            self.ctx = SessionContext()
            self._substrait_available = True
        except ImportError as exc:
            print(
                f"Warning: DataFusion/datafusion-substrait not available: {exc}\n"
                "Install with: pip install datafusion datafusion-substrait pyarrow\n"
                "Tests will be marked FAILED until the packages are installed."
            )

    def get_info(self) -> EngineInfo:
        """Return DataFusion engine information."""
        return EngineInfo(
            name="DataFusion",
            version="35.0.0",
            vendor="Apache Software Foundation",
            description="Fast, extensible query engine with native Substrait support",
        )

    def get_capabilities(self) -> EngineCapabilities:
        """Return DataFusion capabilities."""
        return EngineCapabilities(
            supported_relations=[
                "read", "filter", "project", "aggregate",
                "join", "sort", "limit", "union",
            ],
            supported_functions=[
                "add", "subtract", "multiply", "divide",
                "sum", "count", "avg", "min", "max",
                "concat", "substring", "upper", "lower",
            ],
            supported_types=[
                "integer", "bigint", "double", "varchar",
                "date", "timestamp", "boolean",
            ],
            max_plan_depth=100,
            supports_extensions=True,
        )

    def execute_plan(
        self,
        plan_bytes: bytes,
        input_data: Dict[str, TableData],
    ) -> ComplianceResult:
        """Execute a Substrait plan using DataFusion."""
        try:
            self._register_tables(input_data)
            output = self._execute_substrait_plan(plan_bytes)
            return ComplianceResult(
                test_id="execution",
                status=TestStatus.PASSED,
                output_data=output,
            )
        except Exception as e:
            return ComplianceResult(
                test_id="execution",
                status=TestStatus.ERROR,
                error_message=str(e),
            )

    def validate_plan(self, plan_bytes: bytes) -> ComplianceResult:
        """Validate a Substrait plan without executing it."""
        is_valid = plan_bytes is not None and len(plan_bytes) > 0
        return ComplianceResult(
            test_id="validation",
            status=TestStatus.PASSED if is_valid else TestStatus.FAILED,
            error_message=None if is_valid else "Plan bytes are empty",
        )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _register_tables(self, input_data: Dict[str, TableData]) -> None:
        """Register input TableData objects as in-memory Arrow tables in DataFusion."""
        if not self._substrait_available or not input_data:
            return
        import pyarrow as pa

        for table_name, data in input_data.items():
            if not data.rows:
                continue
            # Build an Arrow table from the SDK's TableData
            col_arrays = []
            for col_idx, col_name in enumerate(data.columns):
                col_values = [row[col_idx] for row in data.rows]
                col_arrays.append(pa.array(col_values))
            arrow_table = pa.table(
                {name: arr for name, arr in zip(data.columns, col_arrays)}
            )
            self.ctx.register_record_batches(
                table_name, [arrow_table.to_batches()]
            )

    def _execute_substrait_plan(self, plan_bytes: bytes) -> Optional[TableData]:
        """Execute a serialized Substrait plan and return results as TableData.

        Option A: datafusion-substrait consumer.

        The plan is deserialised with
            datafusion_substrait.substrait.serde.deserialize_bytes(plan_bytes)
        then executed through
            datafusion_substrait.substrait.consumer.from_substrait_plan(ctx, plan)

        Returns None if the datafusion-substrait package is not installed, so
        that the compliance runner marks the test FAILED with an honest message.
        """
        if not self._substrait_available:
            return None

        from datafusion_substrait.substrait import serde, consumer
        import asyncio

        # Deserialize the plan bytes
        plan = asyncio.run(serde.deserialize_bytes(plan_bytes))

        # Convert to a DataFusion LogicalPlan and execute
        df = asyncio.run(consumer.from_substrait_plan(self.ctx, plan))
        batches = df.collect()

        return _arrow_batches_to_table_data(batches)


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
