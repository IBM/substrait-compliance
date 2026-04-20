# Function Tests Execution and Dashboard Guide

## Overview
This guide explains how to execute the new function-level compliance tests and view the results in the dashboard.

## Quick Start

### 1. Run Function Tests
```bash
cd demo/runner
./run-function-tests-python.sh
```

This will:
- Execute tests for all function categories using Python
- Generate JSON reports in `demo/output/`
- Create a summary report
- Display results in a formatted table

### 2. View Results in Dashboard
```bash
cd demo/dashboard
python3 -m http.server 8889
```

Then open your browser to: **http://localhost:8889**

## What Tests Are Included

The new test suite includes **33 test files** across **7 major categories**:

### 1. Advanced Math Functions (11 tests)
- `round.test` - Rounding with precision
- `ceil.test` - Ceiling function
- `floor.test` - Floor function
- `trunc.test` - Truncation
- `log.test` - Logarithm with custom base
- `log10.test` - Base-10 logarithm
- `ln.test` - Natural logarithm
- `sign.test` - Sign function
- `mod.test` - Modulo operation
- `radians.test` - Degree to radian conversion
- `degrees.test` - Radian to degree conversion

### 2. Array/List Functions (6 tests)
- `array_construct.test` - Array construction
- `array_element.test` - Element access
- `array_length.test` - Array length
- `array_concat.test` - Concatenation
- `array_contains.test` - Element search
- `array_position.test` - Find position

### 3. Struct/Map Functions (5 tests)
- `struct_construct.test` - Struct construction
- `struct_extract.test` - Field extraction
- `map_construct.test` - Map construction
- `map_extract.test` - Value extraction
- `map_keys.test` - Key extraction

### 4. JSON Functions (2 tests)
- `json_extract.test` - JSON path extraction
- `json_parse.test` - JSON parsing

### 5. Conditional Functions (2 tests)
- `case_when.test` - CASE WHEN expressions
- `if_then_else.test` - IF-THEN-ELSE

### 6. Set Operations (3 tests)
- `union.test` - Set union
- `intersect.test` - Set intersection
- `except.test` - Set difference

### 7. Geospatial Functions (4 tests)
- `st_distance.test` - Distance calculations
- `st_contains.test` - Containment checks
- `st_intersects.test` - Intersection checks
- `st_area.test` - Area calculations

## Test File Format

Each `.test` file follows the Substrait test format:

```
### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: '/extensions/functions_*.yaml'

# category: Description
function_name(arg1::type, arg2::type) = expected_result::type
```

Example from `round.test`:
```
# basic: Basic rounding examples
round(3.14159::fp64) = 3::fp64
round(2.5::fp64) = 3::fp64
round(-2.5::fp64) = -3::fp64
```

## Understanding the Results

### Output Files
After running tests, you'll find these files in `demo/output/`:

1. **Engine-specific reports**:
   - `MockDBEngine_function_tests.json`
   - `FastDBEngine_function_tests.json`
   - `CloudDBEngine_function_tests.json`

2. **Summary report**:
   - `function_tests_summary.json`

### JSON Structure
```json
{
  "engine": "FastDBEngine",
  "timestamp": 1234567890,
  "categories": {
    "arithmetic": {
      "total": 330,
      "passed": 317,
      "failed": 13,
      "passRate": 96.06
    },
    "array": {
      "total": 150,
      "passed": 128,
      "failed": 22,
      "passRate": 85.33
    }
    // ... more categories
  },
  "totalTests": 2500,
  "totalPassed": 2250,
  "overallPassRate": 90.0
}
```

## Dashboard Features

### Main Dashboard View
- **Leaderboard**: Shows all engines with overall pass rates
- **Charts**: Visual comparison of engine performance
- **Detailed Results**: Expandable cards for each engine

### Function Test View
The dashboard automatically displays function test results alongside TPC-H query results.

### Filtering and Analysis
- Filter by function category
- Compare engines side-by-side
- Drill down into specific test failures

## Expected Pass Rates by Engine

### MockDBEngine (Lower Maturity)
- Arithmetic: ~85%
- String: ~88%
- Array: ~60%
- JSON: ~50%
- Geospatial: ~45%

### FastDBEngine (Good Maturity)
- Arithmetic: ~96%
- String: ~94%
- Array: ~85%
- JSON: ~78%
- Geospatial: ~72%

### CloudDBEngine (High Maturity)
- Arithmetic: ~99%
- String: ~97%
- Array: ~92%
- JSON: ~88%
- Geospatial: ~85%

## Troubleshooting

### Tests Not Running
**Problem**: Script fails with compilation errors

**Solution**:
```bash
# Ensure you're in the right directory
cd demo/runner

# Check Java version (needs Java 11+)
java -version

# Verify test files exist
ls -la ../../test-suites/functions/
```

### Dashboard Not Showing Results
**Problem**: Dashboard loads but shows no data

**Solution**:
```bash
# Check if output files exist
ls -la demo/output/*.json

# Verify JSON is valid
cat demo/output/FastDBEngine_function_tests.json | python3 -m json.tool

# Re-run tests
cd demo/runner
./run-function-tests.sh
```

### Missing Categories
**Problem**: Some function categories don't appear

**Solution**:
The `FunctionTestDemo.java` automatically scans for all directories in `test-suites/functions/`. If categories are missing:

1. Verify directory structure:
```bash
ls -la test-suites/functions/
```

2. Check for `.test` files:
```bash
find test-suites/functions/ -name "*.test"
```

## Advanced Usage

### Running Specific Categories
To test only specific categories, modify `FunctionTestDemo.java`:

```java
// Instead of scanning all categories
String[] categories = {"arithmetic", "array", "json"};
```

### Custom Pass Rate Thresholds
Adjust pass rates in `getEnginePassRate()` method to simulate different engine capabilities.

### Adding New Test Files
1. Create `.test` file in appropriate category directory
2. Follow the Substrait test format
3. Re-run tests - new tests are automatically discovered

## Integration with CI/CD

### GitHub Actions Example
```yaml
- name: Run Function Tests
  run: |
    cd demo/runner
    ./run-function-tests.sh
    
- name: Upload Results
  uses: actions/upload-artifact@v2
  with:
    name: function-test-results
    path: demo/output/*.json
```

### Automated Dashboard Updates
The dashboard automatically refreshes when new JSON files are detected in the `output/` directory.

## Next Steps

1. **Review Results**: Check which functions have low pass rates
2. **Identify Gaps**: Compare across engines to find common issues
3. **Prioritize Fixes**: Focus on high-impact functions first
4. **Track Progress**: Re-run tests after implementing fixes

## Support

For issues or questions:
- Check existing test files for examples
- Review the Substrait specification
- Consult the main README.md for project overview

---
*Generated for Substrait Compliance Testing Framework*