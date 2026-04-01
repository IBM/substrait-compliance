# DataFusion Python Compliance Example

Example implementation showing how to integrate Apache DataFusion with the Substrait compliance framework using Python.

## Overview

This example demonstrates:
- Implementing the `ComplianceEngine` interface in Python
- Loading and executing Substrait plans in DataFusion
- Running the TPC-H compliance test suite
- Reporting results

## Structure

```
datafusion-python/
├── datafusion_compliance.py  # Engine implementation & runner
└── README.md                 # This file
```

## Installation

```bash
# Install dependencies
pip install substrait-compliance datafusion

# Or install from local SDK
cd ../../sdk/python
pip install -e .
```

## Running

```bash
python datafusion_compliance.py
```

## Expected Output

```
DataFusion Substrait Compliance Testing
==================================================

Engine: DataFusion 35.0.0 by Apache Software Foundation

Loaded test suite: tpch
Test cases: 22

Results:
--------------------------------------------------
Total:     22
Passed:    20
Failed:    1
Errors:    1
Pass Rate: 90.9%
```

## Integration Steps

1. **Implement ComplianceEngine**
   ```python
   class MyEngine(ComplianceEngine):
       def get_info(self):
           return EngineInfo("MyEngine", "1.0.0", "MyCompany")
       
       def execute_plan(self, plan_bytes, input_data):
           # Execute Substrait plan
           return ComplianceResult("test", TestStatus.PASSED)
   ```

2. **Load Test Suite**
   ```python
   loader = YamlTestSuiteLoader()
   suite = loader.load("metadata.yaml")
   ```

3. **Run Tests**
   ```python
   runner = ComplianceRunner(engine)
   report = runner.run_test_suite(suite)
   ```

4. **Report Results**
   ```python
   print(f"Pass Rate: {report.get_pass_rate():.1f}%")
   ```

## Notes

- This is a simplified example for demonstration
- Real implementation would use DataFusion's actual Substrait support
- Production code would include proper error handling
- Data loading would be optimized for performance

## DataFusion Integration

DataFusion has native Substrait support:

```python
from datafusion import SessionContext
from substrait import Plan

# Create context
ctx = SessionContext()

# Register tables
ctx.register_csv("lineitem", "data/lineitem.csv")

# Execute Substrait plan
plan = Plan.deserialize(plan_bytes)
result = ctx.execute_substrait(plan)

# Get results
df = result.to_pandas()
```
