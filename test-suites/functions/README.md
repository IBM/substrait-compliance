# Substrait Function-Level Compliance Tests

This directory contains comprehensive function-level test cases for Substrait compliance testing.

## 📁 Directory Structure

```
functions/
├── aggregate/          # Aggregate functions (COUNT, AVG, SUM, etc.)
│   ├── metadata.yaml
│   ├── count.test
│   ├── count_distinct.test
│   ├── avg.test
│   ├── stddev.test
│   ├── variance.test
│   └── string_agg.test
├── window/            # Window functions (ROW_NUMBER, RANK, LAG, etc.)
│   ├── metadata.yaml
│   ├── row_number.test
│   ├── rank.test
│   ├── dense_rank.test
│   ├── lag.test
│   ├── lead.test
│   ├── first_value.test
│   └── last_value.test
├── cast/              # Type casting functions
│   ├── metadata.yaml
│   ├── cast.test
│   └── try_cast.test
├── arithmetic/        # Arithmetic functions (existing)
├── boolean/           # Boolean functions (enhanced)
├── comparison/        # Comparison functions (existing)
├── datetime/          # DateTime functions (enhanced)
└── string/            # String functions (existing)
```

## 📊 Test Coverage Summary

| Category | Files | Tests | Status |
|----------|-------|-------|--------|
| **Aggregate** | 6 | ~166 | ✅ Complete |
| **Window** | 6 | ~229 | ✅ Complete |
| **Cast** | 2 | ~144 | ✅ Complete |
| **Boolean** | 4 | ~200 | ✅ Enhanced |
| **DateTime** | 12 | ~166 | ✅ Enhanced |
| **Arithmetic** | 33 | ~300 | ✅ Existing |
| **String** | 28 | ~250 | ✅ Existing |
| **Comparison** | 20 | ~180 | ✅ Existing |
| **TOTAL** | **111** | **~1,635** | ✅ |

## 🎯 Test File Format

Each `.test` file follows this format:

```
### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: '/extensions/functions_arithmetic.yaml'

# category_name: Description of test category
function_call(arg1::type, arg2::type) [option:value] = expected_result::type
```

### Examples

**Scalar Function:**
```
add(5::i32, 3::i32) = 8::i32
add(120::i8, 10::i8) [overflow:ERROR] = <!ERROR>
```

**Aggregate Function:**
```
count((1, 2, 3, 4, 5)::i32) = 5::i64
avg((1, 2, 3)::i32) = 2.0::fp64
```

**Window Function:**
```
row_number() OVER (ORDER BY value::i32) WITH ((1), (2), (3)) = (1, 2, 3)::i64
lag(value::i32, 1) OVER (ORDER BY id::i32) WITH ((1, 10), (2, 20)) = (Null, 10)::i32
```

**Type Casting:**
```
cast('42'::str, i32) = 42::i32
try_cast('invalid'::str, i32) = null::i32
```

## 🚀 Usage

### Using Python SDK

```python
from pathlib import Path
from substrait_compliance.function_test_loader import FunctionTestSuiteLoader
from substrait_compliance.runner import ComplianceRunner

# Load a category
loader = FunctionTestSuiteLoader()
suite = loader.load_category(Path("test-suites"), "aggregate")

# Run tests
runner = ComplianceRunner(your_engine)
report = runner.run_test_suite(suite)

print(f"Pass Rate: {report.get_pass_rate()}%")
```

### Load Specific Test File

```python
suite = loader.load_file(Path("test-suites/functions/aggregate/count.test"))
report = runner.run_test_suite(suite)
```

### Load All Function Tests

```python
categories = ["aggregate", "window", "cast", "boolean", "datetime"]
for category in categories:
    suite = loader.load_category(Path("test-suites"), category)
    report = runner.run_test_suite(suite)
    print(f"{category}: {report.get_pass_rate()}%")
```

## 📖 Test Categories

### 1. Aggregate Functions
Tests for SQL aggregate functions that operate on groups of rows:
- `count` - Count rows
- `count_distinct` - Count unique values
- `avg` - Calculate average
- `stddev` - Standard deviation
- `variance` - Variance
- `string_agg` - String aggregation

### 2. Window Functions
Tests for window/analytic functions:
- `row_number` - Sequential row numbering
- `rank` - Ranking with gaps
- `dense_rank` - Ranking without gaps
- `lag` - Access previous row
- `lead` - Access next row
- `first_value` - First value in window
- `last_value` - Last value in window

### 3. Type Casting
Tests for type conversion functions:
- `cast` - Type conversion with errors
- `try_cast` - Safe type conversion (returns NULL on failure)

### 4. Boolean Functions (Enhanced)
Enhanced tests for boolean logic:
- `and` - Logical AND (52 tests)
- `or` - Logical OR (52 tests)
- `not` - Logical NOT (45 tests)
- `xor` - Logical XOR (51 tests)

### 5. DateTime Functions (Enhanced)
Enhanced tests for date/time operations:
- `date_trunc` - Truncate dates (49 tests)
- `date_diff` - Date differences (52 tests)
- `current_date` - Current date (24 tests)
- `current_timestamp` - Current timestamp (33 tests)
- Plus existing: extract, add_datetime, subtract_datetime, etc.

## 🎨 Test Categories

Each test is categorized for better organization:

- `basic` - Basic functionality without edge cases
- `null_handling` - NULL value handling
- `overflow` - Overflow behavior
- `edge_cases` - Edge case scenarios
- `types` - Different data type handling
- `errors` - Error conditions
- `complex` - Complex scenarios

## 🔧 Options

Tests can specify options for behavior:

- `[overflow:ERROR]` - Should raise error on overflow
- `[overflow:SATURATE]` - Should saturate on overflow
- `[overflow:SILENT]` - Undefined behavior on overflow
- `[null_handling:ACCEPT_NULLS]` - Propagate NULLs
- `[null_handling:IGNORE_NULLS]` - Ignore NULLs
- `[rounding:TIE_TO_EVEN]` - Rounding mode

## 📝 Adding New Tests

To add new function tests:

1. **Create .test file** in appropriate category directory
2. **Follow the format** shown above
3. **Add metadata** to category's metadata.yaml
4. **Document** the function behavior
5. **Include edge cases** and null handling

Example:
```bash
# Create new test file
touch test-suites/functions/aggregate/median.test

# Edit the file with test cases
# Update metadata.yaml
```

## 🎯 Benefits

### For Engine Developers
- **Granular testing** - Test individual functions
- **Clear specifications** - Understand expected behavior
- **Fast debugging** - Identify specific function issues
- **Comprehensive coverage** - All edge cases included

### For Framework Users
- **Reference documentation** - Function behavior examples
- **Compliance validation** - Verify Substrait support
- **Gap analysis** - Identify missing functions
- **Regression testing** - Detect breaking changes

## 📚 Related Documentation

- [Function Tests Implementation Guide](../../docs/FUNCTION_TESTS_IMPLEMENTATION.md)
- [Python SDK Documentation](../../sdk/python/README.md)
- [Demo Usage Guide](../../demo/DEMO_USAGE.md)

## 🤝 Contributing

To contribute new function tests:

1. Review existing test format
2. Create comprehensive test cases
3. Include all edge cases
4. Document expected behavior
5. Update metadata files
6. Submit pull request

## 📊 Statistics

- **Total Test Files**: 111
- **Total Test Cases**: ~1,635
- **Categories**: 8
- **New Tests Added**: ~700
- **Enhanced Tests**: ~200
- **Coverage**: Comprehensive

---

**Made with ❤️ for the Substrait Community**