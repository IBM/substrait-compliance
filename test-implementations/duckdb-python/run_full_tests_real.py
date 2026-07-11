#!/usr/bin/env python3
"""
Full Compliance Test Suite Runner with REAL DuckDB Execution

This integrates the real DuckDB engine with the compliance runner
to execute ALL test suites and generate dashboard-ready reports.
"""

import sys
import os
import json
import time
from pathlib import Path
from typing import Dict, List

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from duckdb_engine_real import RealDuckDBComplianceEngine, TableData
from compliance_runner import YamlTestSuiteLoader, ComplianceRunner
import csv


def load_csv_to_table_data(csv_path: Path, table_name: str = "") -> TableData:
    """Load CSV file into TableData structure with proper schema"""
    table_data = TableData()
    
    # TPC-H and TPC-DS use pipe delimiter
    delimiter = '|'
    
    with open(csv_path, 'r') as f:
        reader = csv.reader(f, delimiter=delimiter)
        
        # Define schemas for known tables
        schemas = {
            'lineitem': [
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
            ],
            'orders': [
                {'name': 'o_orderkey', 'type': 'i64'},
                {'name': 'o_custkey', 'type': 'i64'},
                {'name': 'o_orderstatus', 'type': 'string'},
                {'name': 'o_totalprice', 'type': 'fp64'},
                {'name': 'o_orderdate', 'type': 'date'},
                {'name': 'o_orderpriority', 'type': 'string'},
                {'name': 'o_clerk', 'type': 'string'},
                {'name': 'o_shippriority', 'type': 'i32'},
                {'name': 'o_comment', 'type': 'string'}
            ],
            'customer': [
                {'name': 'c_custkey', 'type': 'i64'},
                {'name': 'c_name', 'type': 'string'},
                {'name': 'c_address', 'type': 'string'},
                {'name': 'c_nationkey', 'type': 'i64'},
                {'name': 'c_phone', 'type': 'string'},
                {'name': 'c_acctbal', 'type': 'fp64'},
                {'name': 'c_mktsegment', 'type': 'string'},
                {'name': 'c_comment', 'type': 'string'}
            ],
            'part': [
                {'name': 'p_partkey', 'type': 'i64'},
                {'name': 'p_name', 'type': 'string'},
                {'name': 'p_mfgr', 'type': 'string'},
                {'name': 'p_brand', 'type': 'string'},
                {'name': 'p_type', 'type': 'string'},
                {'name': 'p_size', 'type': 'i32'},
                {'name': 'p_container', 'type': 'string'},
                {'name': 'p_retailprice', 'type': 'fp64'},
                {'name': 'p_comment', 'type': 'string'}
            ],
            'supplier': [
                {'name': 's_suppkey', 'type': 'i64'},
                {'name': 's_name', 'type': 'string'},
                {'name': 's_address', 'type': 'string'},
                {'name': 's_nationkey', 'type': 'i64'},
                {'name': 's_phone', 'type': 'string'},
                {'name': 's_acctbal', 'type': 'fp64'},
                {'name': 's_comment', 'type': 'string'}
            ],
            'partsupp': [
                {'name': 'ps_partkey', 'type': 'i64'},
                {'name': 'ps_suppkey', 'type': 'i64'},
                {'name': 'ps_availqty', 'type': 'i32'},
                {'name': 'ps_supplycost', 'type': 'fp64'},
                {'name': 'ps_comment', 'type': 'string'}
            ],
            'nation': [
                {'name': 'n_nationkey', 'type': 'i64'},
                {'name': 'n_name', 'type': 'string'},
                {'name': 'n_regionkey', 'type': 'i64'},
                {'name': 'n_comment', 'type': 'string'}
            ],
            'region': [
                {'name': 'r_regionkey', 'type': 'i64'},
                {'name': 'r_name', 'type': 'string'},
                {'name': 'r_comment', 'type': 'string'}
            ]
        }
        
        # Use schema if available
        if table_name in schemas:
            table_data.columns = schemas[table_name]
        else:
            # Generic schema - read first row to determine column count
            first_row = next(reader)
            table_data.columns = [{'name': f'col{i}', 'type': 'VARCHAR'} for i in range(len(first_row))]
            table_data.add_row(first_row)
        
        # Load all rows
        for row in reader:
            table_data.add_row(row)
    
    return table_data


def run_all_tests_with_real_engine():
    """Run all compliance tests with real DuckDB execution"""
    
    print("\n" + "=" * 70)
    print("DuckDB REAL Execution - Full Compliance Test Suite")
    print("=" * 70)
    
    # Initialize real engine
    engine = RealDuckDBComplianceEngine()
    info = engine.get_info()
    
    print(f"\n🔧 Engine: {info.name} v{info.version}")
    print(f"   Vendor: {info.vendor}")
    print(f"   Description: {info.description}")
    
    # Initialize test suite loader
    test_suites_dir = Path("../../test-suites")
    loader = YamlTestSuiteLoader()
    
    print(f"\n📁 Test suites directory: {test_suites_dir.absolute()}")
    
    # Discover test suites
    print("\n🔍 Discovering test suites...")
    suite_files = loader.discover_test_suites(str(test_suites_dir))
    print(f"   Found {len(suite_files)} test suite file(s)")
    
    # Load and run each suite
    all_results = []
    total_tests = 0
    total_passed = 0
    total_failed = 0
    total_errors = 0
    
    for suite_file in suite_files:
        print(f"\n{'=' * 70}")
        print(f"Loading test suite: {suite_file}")
        print('=' * 70)
        
        try:
            suite = loader.load_from_file(suite_file)
            
            if not suite or suite.get_test_count() == 0:
                print(f"⚠️  No test cases found in {suite_file}")
                continue
            
            suite_name = suite.name
            test_cases = suite.test_cases
            
            print(f"Suite: {suite_name}")
            print(f"Test cases: {len(test_cases)}")
            print("-" * 70)
            
            suite_passed = 0
            suite_failed = 0
            suite_errors = 0
            
            for i, test_case in enumerate(test_cases, 1):
                test_id = test_case.test_id
                description = test_case.name
                
                print(f"[{i}/{len(test_cases)}] {description}...", end=' ', flush=True)
                
                try:
                    # Load plan
                    if not test_case.plan_path:
                        print("⚠️ SKIPPED (no plan)")
                        continue
                    
                    plan_path = Path(test_case.plan_path)
                    if not plan_path.exists():
                        print(f"⚠️ SKIPPED (plan not found: {plan_path})")
                        continue
                    
                    with open(plan_path, 'rb') as f:
                        plan_bytes = f.read()
                    
                    # Load input data
                    input_data = {}
                    input_tables = test_case.input_data or []
                    
                    # Handle list format
                    if isinstance(input_tables, list):
                        for table_spec in input_tables:
                            table_name = table_spec.get('name', '')
                            table_file = table_spec.get('file', '')
                            
                            if table_name and table_file:
                                # Construct path relative to suite file
                                suite_dir = Path(suite_file).parent
                                table_path = suite_dir / table_file
                                if table_path.exists():
                                    input_data[table_name] = load_csv_to_table_data(table_path, table_name)
                    
                    # Execute plan
                    start_time = time.time()
                    result = engine.execute_plan(plan_bytes, input_data)
                    exec_time = int((time.time() - start_time) * 1000)
                    
                    # Update counters
                    total_tests += 1
                    if result.status == "PASSED":
                        suite_passed += 1
                        total_passed += 1
                        print(f"✓ PASSED ({exec_time}ms)")
                    elif result.status == "FAILED":
                        suite_failed += 1
                        total_failed += 1
                        print(f"✗ FAILED ({exec_time}ms)")
                        if result.error_message:
                            print(f"    Error: {result.error_message}")
                    else:
                        suite_errors += 1
                        total_errors += 1
                        print(f"⚠ {result.status} ({exec_time}ms)")
                        if result.error_message:
                            print(f"    Error: {result.error_message}")
                    
                except Exception as e:
                    total_tests += 1
                    suite_errors += 1
                    total_errors += 1
                    print(f"✗ ERROR: {str(e)}")
            
            # Suite summary
            print("\n" + "=" * 70)
            print(f"Test Suite: {suite_name}")
            print("=" * 70)
            print(f"Total Tests:     {len(test_cases)}")
            print(f"Passed:          {suite_passed}")
            print(f"Failed:          {suite_failed}")
            print(f"Errors:          {suite_errors}")
            if len(test_cases) > 0:
                pass_rate = (suite_passed / len(test_cases)) * 100
                print(f"Pass Rate:       {pass_rate:.1f}%")
            print("=" * 70)
            
        except Exception as e:
            print(f"✗ Error loading suite: {e}")
    
    # Overall summary
    print("\n" + "=" * 70)
    print("OVERALL SUMMARY - REAL EXECUTION")
    print("=" * 70)
    print(f"Total Tests:      {total_tests}")
    print(f"Total Passed:     {total_passed}")
    print(f"Total Failed:     {total_failed}")
    print(f"Total Errors:     {total_errors}")
    if total_tests > 0:
        overall_pass_rate = (total_passed / total_tests) * 100
        print(f"Overall Pass Rate: {overall_pass_rate:.1f}%")
    print("=" * 70)
    
    engine.close()


if __name__ == "__main__":
    run_all_tests_with_real_engine()

