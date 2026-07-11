"""
Full Compliance Test Runner

This module implements the complete ComplianceRunner that loads test suites
from YAML files and executes them against the DuckDB engine.
"""

import csv
import os
import time
import yaml
from pathlib import Path
from typing import Any, List, Dict, Optional
from duckdb_engine import (
    DuckDBComplianceEngine, 
    ComplianceResult, 
    TestStatus,
    TableData
)


class TestCase:
    """Represents a single test case"""
    def __init__(self, test_id: str, name: str, plan_path: str, 
                 input_data: Optional[Dict] = None, expected_output: Optional[Dict] = None,
                 tags: Optional[List[str]] = None):
        self.test_id = test_id
        self.name = name
        self.plan_path = plan_path
        self.input_data = input_data or {}
        self.expected_output = expected_output
        self.tags = tags or []


class TestSuite:
    """Represents a test suite with multiple test cases"""
    def __init__(self, name: str, version: str = "1.0", description: str = ""):
        self.name = name
        self.version = version
        self.description = description
        self.test_cases: List[TestCase] = []
    
    def add_test_case(self, test_case: TestCase):
        self.test_cases.append(test_case)
    
    def get_test_count(self):
        return len(self.test_cases)


class ComplianceReport:
    """Aggregated report for test suite execution"""
    def __init__(self, suite_name: str):
        self.suite_name = suite_name
        self.results: List[ComplianceResult] = []
        self.start_time = time.time()
        self.end_time = None
    
    def add_result(self, result: ComplianceResult):
        self.results.append(result)
    
    def finalize(self):
        self.end_time = time.time()
    
    def get_total_count(self):
        return len(self.results)
    
    def get_passed_count(self):
        return sum(1 for r in self.results if r.status == TestStatus.PASSED)
    
    def get_failed_count(self):
        return sum(1 for r in self.results if r.status == TestStatus.FAILED)
    
    def get_error_count(self):
        return sum(1 for r in self.results if r.status == TestStatus.ERROR)
    
    def get_skipped_count(self):
        return sum(1 for r in self.results if r.status == TestStatus.SKIPPED)
    
    def get_pass_rate(self):
        total = self.get_total_count()
        if total == 0:
            return 0.0
        return (self.get_passed_count() / total) * 100
    
    def get_total_execution_time_ms(self):
        if self.end_time:
            return int((self.end_time - self.start_time) * 1000)
        return 0
    
    def all_passed(self):
        return self.get_passed_count() == self.get_total_count()


class YamlTestSuiteLoader:
    """Loads test suites from YAML files"""
    
    def _extract_schema_map(self, data: Dict) -> Dict[str, List[Dict]]:
        """Extract table schemas from suite metadata."""
        schema_map = {}
        schema = data.get('schema', {})
        for table in schema.get('tables', []):
            table_name = table.get('name')
            columns = table.get('columns', [])
            if table_name and columns:
                schema_map[table_name] = columns
        return schema_map
    
    def _build_tpcds_input_tables(self, schema_map: Dict[str, List[Dict]]) -> List[Dict]:
        """Build default TPC-DS input table specs from available CSV files."""
        if not schema_map:
            return []
        
        input_tables = []
        for table_name in sorted(schema_map.keys()):
            input_tables.append({
                'name': table_name,
                'file': f'data/{table_name}.csv',
                'rows': 0
            })
        return input_tables
    
    def load_from_file(self, yaml_path: str) -> TestSuite:
        """Load a test suite from a YAML file"""
        with open(yaml_path, 'r') as f:
            data = yaml.safe_load(f)
        
        suite = TestSuite(
            name=data.get('name', 'Unknown'),
            version=data.get('version', '1.0'),
            description=data.get('description', '')
        )
        
        # Load test cases - support both formats
        test_cases = data.get('testCases', data.get('test_cases', []))
        base_dir = os.path.dirname(yaml_path)
        schema_map = self._extract_schema_map(data)
        
        # Handle testFiles format (references to other test files)
        test_files = data.get('testFiles', [])
        if test_files and not test_cases:
            # This metadata file references other test files
            # For now, we'll skip loading them and just note it
            print(f"  Note: {suite.name} references {len(test_files)} test file(s)")
            for tf in test_files:
                file_path = os.path.join(base_dir, tf.get('file', ''))
                if os.path.exists(file_path):
                    # Could recursively load these, but for demo we'll skip
                    pass
        
        # Special handling for TPC-DS: auto-discover all plan files if only a few are defined
        if suite.name.lower() in ['tpcds', 'tpc-ds'] and len(test_cases) < 20:
            plans_dir = os.path.join(base_dir, 'plans')
            if os.path.exists(plans_dir):
                discovered_tests = self._discover_tpcds_tests(plans_dir, base_dir)
                if len(discovered_tests) > len(test_cases):
                    print(f"  Auto-discovered {len(discovered_tests)} TPC-DS test cases from plan files")
                    test_cases = discovered_tests
        
        for idx, tc_data in enumerate(test_cases):
            test_id = tc_data.get('id', f'test_{idx}')
            name = tc_data.get('name', tc_data.get('description', test_id))
            
            # Support both planBinary and plan_path
            plan_path = tc_data.get('planBinary', tc_data.get('plan_path', ''))
            
            # Make plan path absolute
            if plan_path and not os.path.isabs(plan_path):
                plan_path = os.path.join(base_dir, plan_path)
            
            test_case = TestCase(
                test_id=test_id,
                name=name,
                plan_path=plan_path,
                input_data=tc_data.get('inputTables', tc_data.get('input_data')),
                expected_output=tc_data.get('expectedOutput', tc_data.get('expected_output')),
                tags=tc_data.get('tags', [])
            )
            suite.add_test_case(test_case)
        
        return suite
    
    def _discover_tpcds_tests(self, plans_dir: str, base_dir: str) -> List[Dict]:
        """Discover all TPC-DS test cases from plan files"""
        test_cases = []
        
        # Find all .bin files
        if not os.path.exists(plans_dir):
            return test_cases
        
        plan_files = sorted([f for f in os.listdir(plans_dir) if f.endswith('.bin')])
        
        for plan_file in plan_files:
            # Extract query ID (e.g., "q01" from "q01.bin")
            query_id = plan_file.replace('.bin', '')
            
            # Create test case definition
            test_case_data = {
                'id': query_id,
                'description': f'TPC-DS Query {query_id}',
                'complexity': 'MEDIUM',
                'planBinary': f'plans/{plan_file}',
                'planJson': f'plans/{query_id}.json',
                'inputTables': self._build_tpcds_input_tables(self._load_tpcds_schema_map(base_dir)),
                'expectedOutput': f'expected/{query_id}.csv'
            }
            test_cases.append(test_case_data)
        
        return test_cases
    
    def _load_tpcds_schema_map(self, base_dir: str) -> Dict[str, List[Dict]]:
        """Load TPC-DS schema metadata from metadata.yaml when auto-discovering tests."""
        metadata_path = os.path.join(base_dir, 'metadata.yaml')
        if not os.path.exists(metadata_path):
            return {}
        
        with open(metadata_path, 'r') as f:
            metadata = yaml.safe_load(f) or {}
        
        return self._extract_schema_map(metadata)
    
    def discover_test_suites(self, directory: str) -> List[str]:
        """Discover all test suite YAML files in a directory"""
        test_files = []
        for root, dirs, files in os.walk(directory):
            for file in files:
                if file.endswith('.yaml') or file.endswith('.yml'):
                    if file == 'metadata.yaml' or file.endswith('.test'):
                        test_files.append(os.path.join(root, file))
        return test_files


class ComplianceRunner:
    """Runs compliance tests against an engine"""
    
    def __init__(self, engine: Any, verbose: bool = True):
        self.engine = engine
        self.verbose = verbose
        self._csv_cache: Dict[str, TableData] = {}
    
    def run_test_suite(self, suite: TestSuite) -> ComplianceReport:
        """Run all tests in a suite"""
        report = ComplianceReport(suite.name)
        
        if self.verbose:
            print(f"\nRunning test suite: {suite.name}")
            print(f"Description: {suite.description}")
            print(f"Test cases: {suite.get_test_count()}")
            print("-" * 60)
        
        for idx, test_case in enumerate(suite.test_cases, 1):
            if self.verbose:
                print(f"[{idx}/{suite.get_test_count()}] {test_case.name}...", end=" ")
            
            result = self.run_test_case(test_case)
            report.add_result(result)
            
            if self.verbose:
                status_symbol = "✓" if result.is_passed() else "✗"
                print(f"{status_symbol} {result.status} ({result.execution_time_ms}ms)")
                if result.error_message and not result.is_passed():
                    print(f"    Error: {result.error_message}")
        
        report.finalize()
        return report
    
    def run_test_case(self, test_case: TestCase) -> ComplianceResult:
        """Run a single test case"""
        try:
            # Load plan bytes
            if not test_case.plan_path or not os.path.exists(test_case.plan_path):
                return ComplianceResult(
                    test_id=test_case.test_id,
                    status=TestStatus.SKIPPED,
                    error_message=f"Plan file not found: {test_case.plan_path}"
                )
            
            with open(test_case.plan_path, 'rb') as f:
                plan_bytes = f.read()
            
            # Prepare input data
            input_tables = self._prepare_input_data(
                test_case.input_data,
                os.path.dirname(os.path.dirname(test_case.plan_path))
            )
            
            # Execute plan
            result = self.engine.execute_plan(plan_bytes, input_tables)
            result.test_id = test_case.test_id
            
            return result
            
        except Exception as e:
            return ComplianceResult(
                test_id=test_case.test_id,
                status=TestStatus.ERROR,
                error_message=str(e),
                error_details=repr(e)
            )
    
    def _prepare_input_data(self, input_data: Optional[Dict], base_dir: Optional[str] = None) -> Dict[str, TableData]:
        """Convert input data definitions to TableData objects, loading CSV files when specified."""
        if not input_data:
            return {}
        
        # Handle list format (inputTables from YAML)
        if isinstance(input_data, list):
            tables = {}
            for table_spec in input_data:
                table_name = table_spec.get('name', 'unknown')
                table_file = table_spec.get('file')
                if table_file and base_dir:
                    file_path = os.path.normpath(os.path.join(base_dir, table_file))
                    tables[table_name] = self._load_csv_to_table_data(file_path, table_name, table_spec.get('columns'))
                else:
                    table = TableData(columns=table_spec.get('columns', []))
                    for row in table_spec.get('rows', []):
                        table.add_row(row)
                    tables[table_name] = table
            return tables
        
        # Handle dict format
        tables = {}
        for table_name, table_spec in input_data.items():
            table_file = table_spec.get('file') if isinstance(table_spec, dict) else None
            if table_file and base_dir:
                file_path = os.path.normpath(os.path.join(base_dir, table_file))
                tables[table_name] = self._load_csv_to_table_data(file_path, table_name, table_spec.get('columns'))
                continue
            
            table = TableData(columns=table_spec.get('columns', []))
            for row in table_spec.get('rows', []):
                table.add_row(row)
            tables[table_name] = table
        
        return tables
    
    def _infer_columns_from_rows(self, rows: List[List[str]]) -> List[Dict]:
        """Create a fallback schema when metadata columns are unavailable."""
        if not rows:
            return [{'name': 'placeholder', 'type': 'i32'}]
        return [{'name': f'col{i}', 'type': 'string'} for i in range(len(rows[0]))]
    
    def _load_csv_to_table_data(self, csv_path: str, table_name: str = "", columns: Optional[List[Dict]] = None) -> TableData:
        """Load a delimited data file into TableData with optional schema metadata."""
        cache_key = f"{table_name}:{csv_path}"
        if cache_key in self._csv_cache:
            return self._csv_cache[cache_key]
        
        if not os.path.exists(csv_path):
            raise FileNotFoundError(f"Input data file not found: {csv_path}")
        
        rows: List[List[str]] = []
        with open(csv_path, 'r', newline='') as f:
            reader = csv.reader(f, delimiter='|')
            for row in reader:
                if row and row[-1] == '':
                    row = row[:-1]
                if not row:
                    continue
                rows.append(row)
        
        table_data = TableData(columns=columns or self._infer_columns_from_rows(rows))
        for row in rows:
            table_data.add_row(row)
        
        self._csv_cache[cache_key] = table_data
        return table_data
    
    def print_report(self, report: ComplianceReport):
        """Print a formatted test report"""
        print("\n" + "=" * 60)
        print(f"Test Suite: {report.suite_name}")
        print("=" * 60)
        print(f"Total Tests:     {report.get_total_count()}")
        print(f"Passed:          {report.get_passed_count()}")
        print(f"Failed:          {report.get_failed_count()}")
        print(f"Errors:          {report.get_error_count()}")
        print(f"Skipped:         {report.get_skipped_count()}")
        print(f"Pass Rate:       {report.get_pass_rate():.1f}%")
        print(f"Execution Time:  {report.get_total_execution_time_ms()}ms")
        print("=" * 60)
        
        if not report.all_passed():
            print("\nFailed/Error Tests:")
            for result in report.results:
                if not result.is_passed():
                    print(f"  ✗ {result.test_id}: {result.status}")
                    if result.error_message:
                        print(f"    {result.error_message}")

