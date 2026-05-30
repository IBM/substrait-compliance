"""Test suite loaders for various formats."""

from abc import ABC, abstractmethod
import csv
from pathlib import Path
from typing import Any, Dict, List
import yaml
from .test_suite import TestSuite, TestCase, TestSuiteMetadata
from .table_data import Column, DataType, TableData
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
            
            input_data = self._load_input_tables(tc_def.get('inputTables', []), base_dir)
            expected_output = self._load_optional_table(
                tc_def.get('expectedOutput'),
                base_dir
            )
            
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

    def _load_input_tables(
        self,
        input_tables: List[dict],
        base_dir: Path
    ) -> Dict[str, TableData]:
        """Load input tables referenced by the YAML definition."""
        tables: Dict[str, TableData] = {}

        for table_def in input_tables:
            table_name = table_def.get('name')
            csv_path = table_def.get('csv')
            if not table_name or not csv_path:
                continue
            tables[table_name] = self._load_csv_table(base_dir / csv_path)

        return tables

    def _load_optional_table(
        self,
        relative_path: str | None,
        base_dir: Path
    ) -> TableData | None:
        """Load an optional CSV-backed table."""
        if not relative_path:
            return None
        return self._load_csv_table(base_dir / relative_path)

    def _load_csv_table(self, path: Path) -> TableData:
        """Load a table from CSV with optional name:type headers."""
        with open(path, 'r', newline='') as f:
            reader = csv.reader(f)
            rows = list(reader)

        if not rows:
            return TableData(columns=[], rows=[])

        header = rows[0]
        columns: List[Column] = []
        column_types: List[DataType] = []

        for raw_header in header:
            name, data_type = self._parse_header(raw_header)
            columns.append(Column(name=name, data_type=data_type, nullable=True))
            column_types.append(data_type)

        data_rows: List[List[Any]] = []
        for raw_row in rows[1:]:
            padded_row = raw_row + [''] * (len(columns) - len(raw_row))
            converted_row = [
                self._convert_value(padded_row[index], column_types[index])
                for index in range(len(columns))
            ]
            data_rows.append(converted_row)

        return TableData(columns=columns, rows=data_rows)

    def _parse_header(self, raw_header: str) -> tuple[str, DataType]:
        """Parse a CSV header in name:type format."""
        header = raw_header.strip()
        if ':' not in header:
            return header, DataType.VARCHAR

        name, raw_type = header.split(':', 1)
        return name.strip(), self._parse_data_type(raw_type)

    def _parse_data_type(self, raw_type: str) -> DataType:
        """Normalize a textual type into a supported DataType."""
        normalized = raw_type.strip().lower()
        aliases = {
            'int': DataType.INTEGER,
            'integer': DataType.INTEGER,
            'i8': DataType.INTEGER,
            'i16': DataType.INTEGER,
            'i32': DataType.INTEGER,
            'long': DataType.BIGINT,
            'bigint': DataType.BIGINT,
            'i64': DataType.BIGINT,
            'float': DataType.DOUBLE,
            'double': DataType.DOUBLE,
            'fp32': DataType.DOUBLE,
            'fp64': DataType.DOUBLE,
            'decimal': DataType.DECIMAL,
            'bool': DataType.BOOLEAN,
            'boolean': DataType.BOOLEAN,
            'date': DataType.DATE,
            'string': DataType.VARCHAR,
            'varchar': DataType.VARCHAR,
        }
        return aliases.get(normalized, DataType.VARCHAR)

    def _convert_value(self, raw_value: str, data_type: DataType) -> Any:
        """Convert a CSV string value into the declared column type."""
        value = raw_value.strip()
        if value == '' or value.lower() == 'null':
            return None

        if data_type == DataType.INTEGER:
            return int(value)
        if data_type == DataType.BIGINT:
            return int(value)
        if data_type in (DataType.DOUBLE, DataType.DECIMAL):
            return float(value)
        if data_type == DataType.BOOLEAN:
            return value.lower() == 'true'

        return value
