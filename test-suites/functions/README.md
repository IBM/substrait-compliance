# Substrait Function Test Suites

This directory contains comprehensive test suites for Substrait function implementations. These tests are designed to verify compliance with the Substrait specification across various query engines and implementations.

## Directory Structure

```
functions/
├── aggregate/       # Aggregate functions (avg, count, sum, etc.)
├── arithmetic/      # Arithmetic operations (add, subtract, multiply, etc.)
├── array/          # Array manipulation functions
├── cast/           # Type casting functions
├── comparison/     # Comparison operators (equal, greater than, etc.)
├── conditional/    # Conditional expressions (case, if-then-else)
├── datetime/       # Date and time functions
├── geospatial/     # Geospatial operations (st_area, st_distance, etc.)
├── json/           # JSON processing functions
├── map/            # Map/dictionary operations
├── set/            # Set operations (union, intersect, except)
├── string/         # String manipulation functions
├── struct/         # Struct/record operations
└── window/         # Window functions (rank, row_number, etc.)
```

## Test File Format

Each test file follows the Substrait test format specification:

### Header Section
```
### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: '/extensions/functions_<category>.yaml'
```

- `SUBSTRAIT_SCALAR_TEST` or `SUBSTRAIT_AGGREGATE_TEST`: Indicates the test type
- `SUBSTRAIT_INCLUDE`: References the Substrait extension file defining the function

### Test Organization

Tests are organized into semantic groups using comment headers:

#### Original Test Groups
- `basic`: Basic functionality without edge cases
- `null_handling`: Null value behavior
- `overflow`: Overflow and error handling
- `types`: Different data type variations
- `precision`: Floating point precision
- And other function-specific categories

#### Enhanced Test Groups
Additional comprehensive test coverage organized by:

- `edge_cases`: Boundary values and extreme inputs
- `overflow_behavior`: Overflow and saturation behavior
- `special_values`: Special floating point values (NaN, Infinity)
- `null_handling_extended`: Extended null handling scenarios
- `zero_handling`: Zero and negative zero handling
- `precision`: Floating point precision and small values
- `single_value`: Single value inputs
- `empty_values`: Empty strings and collections
- `negative_values`: Negative number handling
- `type_coverage`: Various data type coverage
- `unicode_handling`: Unicode and special characters
- `whitespace_handling`: Whitespace and control characters
- `mixed_magnitude`: Mixed magnitude values
- `large_datasets`: Large input datasets
- `additional_coverage`: Additional test coverage

### Test Syntax

```
function_name(input_args) = expected_output
function_name(input_args) [option:VALUE] = expected_output
```

**Examples:**
```
abs(-5::i32) = 5::i32
abs(-128::i8) [overflow:ERROR] = <!ERROR>
concat('hello'::str, 'world'::str) = 'helloworld'::str
avg((1, 2, 3)::i32) = 2.0::fp64
```

## Data Types

Common data types used in tests:

- **Integers**: `i8`, `i16`, `i32`, `i64` (signed), `u8`, `u16`, `u32`, `u64` (unsigned)
- **Floating Point**: `fp32`, `fp64`
- **String**: `str`
- **Boolean**: `bool`
- **Date/Time**: `date`, `time`, `ts` (timestamp), `tstz` (timestamp with timezone)
- **Complex**: `list<T>`, `struct<...>`, `map<K,V>`
- **Special Values**: `null`, `nan`, `inf`, `-inf`

## Test Options

Some tests include options to specify behavior:

- `[overflow:ERROR]`: Should raise an error on overflow
- `[overflow:SATURATE]`: Should saturate to max/min value
- `[overflow:SILENT]`: Undefined behavior on overflow
- `[null_handling:ACCEPT_NULLS]`: Propagate nulls
- `[null_handling:IGNORE_NULLS]`: Skip null values

## Running Tests

These test files are designed to be consumed by Substrait compliance testing frameworks. Refer to the main project documentation for instructions on running tests against specific query engines.

## Test Enhancement

The test suites have been enhanced with comprehensive coverage including:

- **3,381 additional test cases** across 136 function test files
- Systematic coverage of edge cases, boundary conditions, and error scenarios
- Unicode and internationalization testing
- Precision and numerical stability testing
- Comprehensive null handling scenarios

## Contributing

When adding new tests:

1. Follow the existing test file format and naming conventions
2. Group tests by semantic categories using comment headers
3. Include clear, descriptive test cases
4. Test both success and failure scenarios
5. Document any special behavior or edge cases
6. Ensure tests are deterministic and reproducible

### Test Categories Guidelines

- **basic**: Core functionality that should work in all implementations
- **edge_cases**: Boundary values (min/max integers, very large/small floats)
- **null_handling**: How functions behave with null inputs
- **overflow**: Behavior when operations exceed type limits
- **precision**: Floating point accuracy and rounding
- **types**: Behavior across different data types
- **special_values**: NaN, Infinity, and other special cases

## Metadata

Test enhancement metadata is tracked in `enhancement_metadata.json`, which includes:
- Files processed
- Number of tests added per category
- Test categorization statistics

## License

These test suites are part of the Substrait project and follow the project's licensing terms.

## References

- [Substrait Specification](https://substrait.io/)
- [Substrait Extensions](https://github.com/substrait-io/substrait/tree/main/extensions)
- [Test Format Specification](https://substrait.io/extensions/#testing-extensions)