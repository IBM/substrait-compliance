"""Generate Substrait plans for function test cases."""

import json
from typing import Dict, Any, List
from .function_test_parser import FunctionTestCase, parse_function_call, parse_expected_result


class SubstraitPlanGenerator:
    """
    Generates simplified Substrait plans for function tests.
    
    Note: This is a simplified generator for demonstration purposes.
    A production implementation would use the full Substrait protobuf definitions.
    """
    
    def generate_plan(self, test_case: FunctionTestCase) -> bytes:
        """
        Generate a Substrait plan for a function test case.
        
        Args:
            test_case: The function test case
            
        Returns:
            Serialized Substrait plan as bytes (JSON for demo)
        """
        # Parse the function call
        func_info = parse_function_call(test_case.function_call)
        expected = parse_expected_result(test_case.expected_result)
        
        # Create a simplified Substrait plan structure
        plan = {
            "version": {"major": 0, "minor": 1, "patch": 0},
            "extensionUris": [
                {
                    "extensionUriAnchor": 1,
                    "uri": "/extensions/functions_arithmetic.yaml"
                }
            ],
            "extensions": [
                {
                    "extensionFunction": {
                        "extensionUriReference": 1,
                        "functionAnchor": 1,
                        "name": func_info['function']
                    }
                }
            ],
            "relations": [
                {
                    "root": {
                        "input": {
                            "project": {
                                "input": {
                                    "read": {
                                        "baseSchema": {
                                            "names": ["dummy"],
                                            "struct": {
                                                "types": [{"i32": {}}]
                                            }
                                        },
                                        "namedTable": {
                                            "names": ["dummy_table"]
                                        }
                                    }
                                },
                                "expressions": [
                                    self._generate_expression(func_info)
                                ]
                            }
                        },
                        "names": ["result"]
                    }
                }
            ],
            # Store test metadata
            "metadata": {
                "test_id": test_case.test_id,
                "category": test_case.category,
                "function_call": test_case.function_call,
                "expected_result": test_case.expected_result,
                "options": test_case.options
            }
        }
        
        # Serialize to JSON (in production, would use protobuf)
        return json.dumps(plan, indent=2).encode('utf-8')
    
    def _generate_expression(self, func_info: Dict[str, Any]) -> Dict[str, Any]:
        """Generate expression node for function call."""
        args = []
        for arg in func_info['args']:
            args.append(self._generate_literal(arg))
        
        return {
            "scalarFunction": {
                "functionReference": 1,
                "arguments": args,
                "outputType": self._get_type_reference(func_info.get('return_type', 'i32'))
            }
        }
    
    def _generate_literal(self, arg: Dict[str, Any]) -> Dict[str, Any]:
        """Generate literal value node."""
        value = arg['value']
        type_str = arg['type']
        
        # Handle null
        if value is None or value.lower() == 'null':
            return {
                "literal": {
                    "null": self._get_type_reference(type_str)
                }
            }
        
        # Handle different types
        if type_str in ('i8', 'i16', 'i32', 'i64'):
            return {
                "literal": {
                    type_str: int(value)
                }
            }
        elif type_str in ('fp32', 'fp64'):
            # Handle special float values
            if value.lower() == 'inf':
                return {"literal": {type_str: float('inf')}}
            elif value.lower() == '-inf':
                return {"literal": {type_str: float('-inf')}}
            elif value.lower() == 'nan':
                return {"literal": {type_str: float('nan')}}
            else:
                return {"literal": {type_str: float(value)}}
        elif type_str == 'bool':
            return {
                "literal": {
                    "boolean": value.lower() == 'true'
                }
            }
        elif type_str == 'str':
            return {
                "literal": {
                    "string": value
                }
            }
        else:
            # Default to string
            return {
                "literal": {
                    "string": str(value)
                }
            }
    
    def _get_type_reference(self, type_str: str) -> Dict[str, Any]:
        """Get type reference for Substrait type."""
        type_map = {
            'i8': {'i8': {}},
            'i16': {'i16': {}},
            'i32': {'i32': {}},
            'i64': {'i64': {}},
            'fp32': {'fp32': {}},
            'fp64': {'fp64': {}},
            'bool': {'bool': {}},
            'str': {'string': {}},
            'date': {'date': {}},
            'time': {'time': {}},
            'ts': {'timestamp': {}},
        }
        return type_map.get(type_str, {'string': {}})


class AggregatePlanGenerator(SubstraitPlanGenerator):
    """Generator for aggregate function plans."""
    
    def generate_plan(self, test_case: FunctionTestCase) -> bytes:
        """Generate aggregate function plan."""
        func_info = parse_function_call(test_case.function_call)
        
        # For aggregates, the input is a list of values
        # Parse: sum((1, 2, 3)::i32)
        plan = {
            "version": {"major": 0, "minor": 1, "patch": 0},
            "relations": [
                {
                    "root": {
                        "input": {
                            "aggregate": {
                                "input": self._generate_values_input(func_info),
                                "measures": [
                                    {
                                        "measure": {
                                            "functionReference": 1,
                                            "arguments": [{"value": {"selection": {"directReference": {"structField": {"field": 0}}}}}],
                                            "outputType": self._get_type_reference('i64')
                                        }
                                    }
                                ]
                            }
                        },
                        "names": ["result"]
                    }
                }
            ],
            "metadata": {
                "test_id": test_case.test_id,
                "category": test_case.category,
                "function_call": test_case.function_call,
                "expected_result": test_case.expected_result
            }
        }
        
        return json.dumps(plan, indent=2).encode('utf-8')
    
    def _generate_values_input(self, func_info: Dict[str, Any]) -> Dict[str, Any]:
        """Generate input values for aggregate."""
        # Simplified - would parse the tuple of values
        return {
            "read": {
                "baseSchema": {
                    "names": ["value"],
                    "struct": {
                        "types": [{"i32": {}}]
                    }
                },
                "namedTable": {
                    "names": ["input_values"]
                }
            }
        }


class WindowPlanGenerator(SubstraitPlanGenerator):
    """Generator for window function plans."""
    
    def generate_plan(self, test_case: FunctionTestCase) -> bytes:
        """Generate window function plan."""
        func_info = parse_function_call(test_case.function_call)
        
        plan = {
            "version": {"major": 0, "minor": 1, "patch": 0},
            "relations": [
                {
                    "root": {
                        "input": {
                            "project": {
                                "input": {
                                    "read": {
                                        "baseSchema": {
                                            "names": ["id", "value"],
                                            "struct": {
                                                "types": [{"i32": {}}, {"i32": {}}]
                                            }
                                        },
                                        "namedTable": {
                                            "names": ["input_data"]
                                        }
                                    }
                                },
                                "expressions": [
                                    {
                                        "windowFunction": {
                                            "functionReference": 1,
                                            "arguments": [],
                                            "partitionBy": [],
                                            "sorts": [
                                                {
                                                    "expr": {"selection": {"directReference": {"structField": {"field": 0}}}},
                                                    "direction": "ASC"
                                                }
                                            ],
                                            "outputType": {"i64": {}}
                                        }
                                    }
                                ]
                            }
                        },
                        "names": ["result"]
                    }
                }
            ],
            "metadata": {
                "test_id": test_case.test_id,
                "category": test_case.category,
                "function_call": test_case.function_call,
                "expected_result": test_case.expected_result
            }
        }
        
        return json.dumps(plan, indent=2).encode('utf-8')

