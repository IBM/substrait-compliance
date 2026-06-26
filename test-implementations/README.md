# Test Implementations

This directory contains test implementations of the Substrait Compliance Framework using various query engines. These implementations are for **demonstration and testing purposes only** and can be safely deleted without affecting the core framework.

## Purpose

These test implementations serve to:
1. Demonstrate how to implement the `ComplianceEngine` interface
2. Validate that the SDK interfaces work correctly
3. Provide examples for engine developers
4. Test the framework with real query engines

## Available Implementations

### DuckDB (Python)
- **Location**: `duckdb-python/`
- **Language**: Python
- **Engine**: DuckDB in-process analytical database
- **Status**: Demonstration implementation
- **Features**: 
  - Complete ComplianceEngine interface implementation
  - Basic plan validation
  - Example test runner
  - Simple usage examples

## Important Notes

⚠️ **These implementations are NOT part of the core framework**

- They are isolated in this directory
- They can be deleted at any time
- They do not affect the core SDK functionality
- They are for testing and demonstration only

## Adding New Test Implementations

To add a new test implementation:

1. Create a new directory: `test-implementations/<engine-name>-<language>/`
2. Implement the ComplianceEngine interface for your chosen SDK
3. Add a README.md explaining the implementation
4. Include examples and test runners
5. Add a .gitignore file

## Cleanup

To remove all test implementations:

```bash
rm -rf test-implementations/
```

To remove a specific implementation:

```bash
rm -rf test-implementations/duckdb-python/
```

## Contributing

If you create a test implementation that might be useful to others:
1. Ensure it's well-documented
2. Include clear examples
3. Add it to this README
4. Submit a pull request

Remember: These are **examples only** and not production-ready code.