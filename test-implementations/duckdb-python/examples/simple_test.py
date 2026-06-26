"""
Simple example of using the DuckDB Compliance Engine

This demonstrates basic usage of the ComplianceEngine interface.
"""

import sys
import os

# Add parent directory to path to import duckdb_engine
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from duckdb_engine import DuckDBComplianceEngine, TableData


def main():
    """Simple example demonstrating engine usage"""
    
    print("Simple DuckDB Compliance Engine Example")
    print("=" * 50)
    
    # 1. Create engine instance
    engine = DuckDBComplianceEngine()
    
    # 2. Get engine information
    info = engine.get_info()
    print(f"\nEngine: {info.name}")
    print(f"Version: {info.version}")
    
    # 3. Check capabilities
    caps = engine.get_capabilities()
    print(f"\nCapabilities:")
    print(f"  Relations: {len(caps.supported_relations)}")
    print(f"  Functions: {len(caps.supported_functions)}")
    print(f"  Types: {len(caps.supported_types)}")
    
    # 4. Validate a plan
    print("\n" + "-" * 50)
    print("Validating a sample plan...")
    
    sample_plan = b"sample_substrait_plan_bytes"
    result = engine.validate_plan(sample_plan)
    
    print(f"Validation result: {result.status}")
    if result.is_passed():
        print("✓ Plan validation passed")
    else:
        print(f"✗ Plan validation failed: {result.error_message}")
    
    # 5. Execute a plan (with empty input for demo)
    print("\n" + "-" * 50)
    print("Executing a sample plan...")
    
    input_data = {}  # Empty input for demonstration
    result = engine.execute_plan(sample_plan, input_data)
    
    print(f"Execution result: {result.status}")
    print(f"Execution time: {result.execution_time_ms}ms")
    
    if result.output_data:
        print(f"Output rows: {result.output_data.get_row_count()}")
        print(f"Output columns: {result.output_data.get_column_count()}")
    
    # 6. Cleanup
    engine.close()
    
    print("\n" + "=" * 50)
    print("Example complete!")
    print("\nNote: This is a demonstration implementation.")
    print("Full Substrait plan execution requires proper setup.")


if __name__ == "__main__":
    main()

# Made with Bob
