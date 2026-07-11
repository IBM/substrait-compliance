#!/usr/bin/env python3
"""
Test script to demonstrate REAL Substrait plan execution with DuckDB.

This script:
1. Loads actual CSV data from test suites
2. Parses real Substrait plans
3. Executes queries in DuckDB
4. Shows actual execution times and results
"""

import sys
import os
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from duckdb_engine_real import RealDuckDBComplianceEngine, TableData
import csv


def load_csv_to_table_data(csv_path: Path, delimiter='|') -> TableData:
    """Load CSV file into TableData structure"""
    table_data = TableData()
    
    with open(csv_path, 'r') as f:
        # TPC-H data uses pipe delimiter and has no headers
        reader = csv.reader(f, delimiter=delimiter)
        
        # For TPC-H lineitem, define the schema
        if 'lineitem' in str(csv_path):
            table_data.columns = [
                {'name': 'l_orderkey', 'type': 'i64'},
                {'name': 'l_partkey', 'type': 'i64'},
                {'name': 'l_suppkey', 'type': 'i64'},
                {'name': 'l_linenumber', 'type': 'i32'},
                {'name': 'l_quantity', 'type': 'fp64'},
                {'name': 'l_extendedprice', 'type': 'fp64'},
                {'name': 'l_discount', 'type': 'fp64'},
                {'name': 'l_tax', 'type': 'fp64'},
                {'name': 'l_returnflag', 'type': 'string'},
                {'name': 'l_linestatus', 'type': 'string'},
                {'name': 'l_shipdate', 'type': 'date'},
                {'name': 'l_commitdate', 'type': 'date'},
                {'name': 'l_receiptdate', 'type': 'date'},
                {'name': 'l_shipinstruct', 'type': 'string'},
                {'name': 'l_shipmode', 'type': 'string'},
                {'name': 'l_comment', 'type': 'string'}
            ]
        else:
            # Generic schema
            first_row = next(reader)
            table_data.columns = [{'name': f'col{i}', 'type': 'VARCHAR'} for i in range(len(first_row))]
            table_data.add_row(first_row)
        
        # Load rows
        for row in reader:
            table_data.add_row(row)
    
    return table_data


def test_tpch_q01():
    """Test TPC-H Query 1 with real execution"""
    print("=" * 70)
    print("Testing TPC-H Query 1 - Pricing Summary Report")
    print("=" * 70)
    
    # Initialize engine
    engine = RealDuckDBComplianceEngine()
    info = engine.get_info()
    print(f"\n🔧 Engine: {info.name} v{info.version}")
    print(f"   Vendor: {info.vendor}")
    
    # Load plan
    plan_path = Path("../../test-suites/tpch/plans/q01.bin")
    print(f"\n📄 Loading plan: {plan_path}")
    
    with open(plan_path, 'rb') as f:
        plan_bytes = f.read()
    
    print(f"   Plan size: {len(plan_bytes)} bytes")
    
    # Validate plan
    print("\n🔍 Validating plan...")
    validation_result = engine.validate_plan(plan_bytes)
    print(f"   Status: {validation_result.status}")
    if validation_result.error_message:
        print(f"   Error: {validation_result.error_message}")
    
    # Load input data
    print("\n📊 Loading input data...")
    lineitem_path = Path("../../test-suites/tpch/data/lineitem.csv")
    
    if not lineitem_path.exists():
        print(f"   ❌ Data file not found: {lineitem_path}")
        return
    
    print(f"   Loading: {lineitem_path}")
    lineitem_data = load_csv_to_table_data(lineitem_path)
    print(f"   Loaded {lineitem_data.get_row_count()} rows, {lineitem_data.get_column_count()} columns")
    
    # Execute plan
    print("\n⚡ Executing plan...")
    input_data = {"lineitem": lineitem_data}
    result = engine.execute_plan(plan_bytes, input_data)
    
    print(f"   Status: {result.status}")
    print(f"   Execution time: {result.execution_time_ms}ms")
    
    if result.output_data:
        print(f"   Output rows: {result.output_data.get_row_count()}")
        print(f"   Output columns: {result.output_data.get_column_count()}")
        
        # Show first few rows
        if result.output_data.rows:
            print("\n   📋 Sample output (first 3 rows):")
            for i, row in enumerate(result.output_data.rows[:3]):
                print(f"      Row {i+1}: {row}")
    
    if result.error_message:
        print(f"   ❌ Error: {result.error_message}")
    
    engine.close()
    print("\n✅ Test complete!")


def test_simple_query():
    """Test with a simple custom query to verify DuckDB is working"""
    print("\n" + "=" * 70)
    print("Testing Simple Query - Verify DuckDB Execution")
    print("=" * 70)
    
    engine = RealDuckDBComplianceEngine()
    
    # Create simple test data
    print("\n📊 Creating test data...")
    test_data = TableData()
    test_data.columns = [
        {'name': 'id', 'type': 'i32'},
        {'name': 'name', 'type': 'string'},
        {'name': 'value', 'type': 'fp64'}
    ]
    test_data.add_row([1, 'Alice', 100.5])
    test_data.add_row([2, 'Bob', 200.75])
    test_data.add_row([3, 'Charlie', 150.25])
    
    print(f"   Created {test_data.get_row_count()} rows")
    
    # Register table
    print("\n🔧 Registering table with DuckDB...")
    engine._register_table("test_table", test_data)
    
    # Execute a simple query
    print("\n⚡ Executing query: SELECT * FROM test_table WHERE value > 150")
    try:
        result = engine.conn.execute("SELECT * FROM test_table WHERE value > 150").fetchall()
        print(f"   ✅ Query executed successfully!")
        print(f"   Results: {result}")
        print(f"   Found {len(result)} matching rows")
    except Exception as e:
        print(f"   ❌ Query failed: {e}")
    
    engine.close()
    print("\n✅ Test complete!")


def main():
    """Run all tests"""
    print("\n" + "=" * 70)
    print("REAL DuckDB Substrait Compliance Engine Test")
    print("=" * 70)
    
    # Test 1: Simple query to verify DuckDB works
    test_simple_query()
    
    # Test 2: Real TPC-H query
    test_tpch_q01()
    
    print("\n" + "=" * 70)
    print("All tests complete!")
    print("=" * 70)


if __name__ == "__main__":
    main()

