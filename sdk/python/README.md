# Substrait Compliance SDK - Python

Python SDK for decentralized Substrait compliance testing.

## Installation

```bash
pip install substrait-compliance
```

## Quick Start

```python
from substrait_compliance import (
    ComplianceEngine,
    ComplianceRunner,
    YamlTestSuiteLoader,
    EngineInfo,
    EngineCapabilities,
    ComplianceResult,
    TestStatus
)

# 1. Implement the ComplianceEngine interface
class MyEngine(ComplianceEngine):
    def get_info(self):
        return EngineInfo(
            name="MyEngine",
            version="1.0.0",
            vendor="MyCompany"
        )
    
    def get_capabilities(self):
        return EngineCapabilities(
            supported_relations=["read", "filter", "project"],
            supported_functions=["add", "subtract", "multiply"]
        )
    
    def execute_plan(self, plan_bytes, input_data):
        # Execute Substrait plan
        output = self._execute_internal(plan_bytes, input_data)
        
        return ComplianceResult(
            test_id="test",
            status=TestStatus.PASSED,
            output_data=output
        )
    
    def validate_plan(self, plan_bytes):
        # Validate plan structure
        is_valid = self._validate_internal(plan_bytes)
        
        return ComplianceResult(
            test_id="validation",
            status=TestStatus.PASSED if is_valid else TestStatus.FAILED
        )

# 2. Load a test suite
loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/tpch/metadata.yaml")

# 3. Run compliance tests
engine = MyEngine()
runner = ComplianceRunner(engine)
report = runner.run_test_suite(suite)

# 4. Check results
print(f"Passed: {report.get_passed_count()}/{report.get_total_count()}")
print(f"Pass Rate: {report.get_pass_rate():.1f}%")

for result in report.results:
    if result.status != TestStatus.PASSED:
        print(f"Failed: {result.test_id} - {result.error_message}")
```

## Architecture

```
substrait_compliance/
├── engine.py          # ComplianceEngine interface
├── runner.py          # Test execution
├── result.py          # Result classes
├── test_suite.py      # Test suite classes
├── table_data.py      # Data structures
├── loader.py          # Test suite loaders
└── exceptions.py      # Exception classes
```

## API Reference

### ComplianceEngine

Main interface that engines must implement:

- `get_info()` - Return engine metadata
- `get_capabilities()` - Return supported features
- `execute_plan(plan_bytes, input_data)` - Execute a plan
- `validate_plan(plan_bytes)` - Validate a plan

### ComplianceRunner

Executes test suites:

- `run_test_suite(suite)` - Run all tests
- `run_test_case(test_case)` - Run single test

### YamlTestSuiteLoader

Load test suites from YAML:

- `load(path)` - Load from file
- `supports(path)` - Check format support

## Development

```bash
# Install dev dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Format code
black substrait_compliance/

# Type checking
mypy substrait_compliance/
```

## License

Apache License 2.0
