"""Parser for .test files containing function test cases."""

import re
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Dict, Any
from enum import Enum


class TestType(Enum):
    """Type of test."""
    SCALAR = "SUBSTRAIT_SCALAR_TEST"
    AGGREGATE = "SUBSTRAIT_AGGREGATE_TEST"
    WINDOW = "SUBSTRAIT_WINDOW_TEST"


@dataclass
class FunctionTestCase:
    """Represents a single function test case."""
    test_id: str
    category: str
    function_call: str
    expected_result: str
    options: Dict[str, str]
    line_number: int
    
    def __repr__(self):
        return f"FunctionTestCase({self.test_id}, {self.function_call} = {self.expected_result})"


@dataclass
class FunctionTestSuite:
    """Represents a collection of function tests from a .test file."""
    name: str
    test_type: TestType
    includes: List[str]
    test_cases: List[FunctionTestCase]
    file_path: Path
    
    def __repr__(self):
        return f"FunctionTestSuite({self.name}, {len(self.test_cases)} tests)"


class FunctionTestParser:
    """Parser for .test files."""
    
    # Regex patterns
    HEADER_PATTERN = re.compile(r'^### (SUBSTRAIT_\w+_TEST):\s*(.+)$')
    INCLUDE_PATTERN = re.compile(r'^### SUBSTRAIT_INCLUDE:\s*[\'"](.+)[\'"]$')
    CATEGORY_PATTERN = re.compile(r'^# (\w+):\s*(.+)$')
    TEST_CASE_PATTERN = re.compile(
        r'^([^=]+?)\s*(?:\[([^\]]+)\])?\s*=\s*(.+)$'
    )
    
    def parse_file(self, file_path: Path) -> FunctionTestSuite:
        """
        Parse a .test file into a FunctionTestSuite.
        
        Args:
            file_path: Path to the .test file
            
        Returns:
            FunctionTestSuite containing all parsed test cases
        """
        with open(file_path, 'r') as f:
            lines = f.readlines()
        
        test_type = None
        includes = []
        test_cases = []
        current_category = "uncategorized"
        
        for line_num, line in enumerate(lines, 1):
            line = line.strip()
            
            # Skip empty lines
            if not line:
                continue
            
            # Parse header
            header_match = self.HEADER_PATTERN.match(line)
            if header_match:
                test_type_str = header_match.group(1)
                test_type = TestType(test_type_str)
                continue
            
            # Parse includes
            include_match = self.INCLUDE_PATTERN.match(line)
            if include_match:
                includes.append(include_match.group(1))
                continue
            
            # Parse category comments
            category_match = self.CATEGORY_PATTERN.match(line)
            if category_match:
                current_category = category_match.group(1)
                continue
            
            # Skip other comments
            if line.startswith('#'):
                continue
            
            # Parse test case
            test_match = self.TEST_CASE_PATTERN.match(line)
            if test_match:
                function_call = test_match.group(1).strip()
                options_str = test_match.group(2)
                expected_result = test_match.group(3).strip()
                
                # Parse options
                options = {}
                if options_str:
                    for opt in options_str.split(','):
                        opt = opt.strip()
                        if ':' in opt:
                            key, value = opt.split(':', 1)
                            options[key.strip()] = value.strip()
                
                # Generate test ID
                test_id = f"{file_path.stem}_{current_category}_{len(test_cases) + 1}"
                
                test_case = FunctionTestCase(
                    test_id=test_id,
                    category=current_category,
                    function_call=function_call,
                    expected_result=expected_result,
                    options=options,
                    line_number=line_num
                )
                test_cases.append(test_case)
        
        if test_type is None:
            raise ValueError(f"No test type header found in {file_path}")
        
        return FunctionTestSuite(
            name=file_path.stem,
            test_type=test_type,
            includes=includes,
            test_cases=test_cases,
            file_path=file_path
        )
    
    def parse_directory(self, directory: Path) -> List[FunctionTestSuite]:
        """
        Parse all .test files in a directory.
        
        Args:
            directory: Directory containing .test files
            
        Returns:
            List of FunctionTestSuite objects
        """
        test_suites = []
        
        for test_file in directory.glob('*.test'):
            try:
                suite = self.parse_file(test_file)
                test_suites.append(suite)
            except Exception as e:
                print(f"Warning: Failed to parse {test_file}: {e}")
        
        return test_suites


def parse_function_call(function_call: str) -> Dict[str, Any]:
    """
    Parse a function call string into components.
    
    Example: "add(5::i32, 3::i32)" -> 
    {
        'function': 'add',
        'args': [
            {'value': '5', 'type': 'i32'},
            {'value': '3', 'type': 'i32'}
        ]
    }
    """
    # Extract function name
    func_match = re.match(r'^(\w+)\((.*)\)$', function_call.strip())
    if not func_match:
        raise ValueError(f"Invalid function call: {function_call}")
    
    func_name = func_match.group(1)
    args_str = func_match.group(2)
    
    # Parse arguments
    args = []
    if args_str.strip():
        # Simple split by comma (doesn't handle nested calls yet)
        for arg in args_str.split(','):
            arg = arg.strip()
            
            # Parse value::type format
            if '::' in arg:
                value, type_str = arg.rsplit('::', 1)
                args.append({
                    'value': value.strip(),
                    'type': type_str.strip()
                })
            else:
                args.append({
                    'value': arg,
                    'type': 'unknown'
                })
    
    return {
        'function': func_name,
        'args': args
    }


def parse_expected_result(result_str: str) -> Dict[str, Any]:
    """
    Parse expected result string.
    
    Example: "8::i32" -> {'value': '8', 'type': 'i32'}
    Example: "<!ERROR>" -> {'error': True}
    Example: "null::i32" -> {'value': None, 'type': 'i32'}
    """
    result_str = result_str.strip()
    
    # Check for error
    if result_str == '<!ERROR>':
        return {'error': True}
    
    # Check for undefined
    if result_str == '<!UNDEFINED>':
        return {'undefined': True}
    
    # Parse value::type format
    if '::' in result_str:
        value, type_str = result_str.rsplit('::', 1)
        value = value.strip()
        
        # Handle null
        if value.lower() in ('null', 'null'):
            return {'value': None, 'type': type_str.strip()}
        
        # Handle string literals
        if value.startswith("'") and value.endswith("'"):
            value = value[1:-1]
        
        return {
            'value': value,
            'type': type_str.strip()
        }
    
    return {'value': result_str, 'type': 'unknown'}

