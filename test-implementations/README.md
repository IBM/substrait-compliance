# Test Implementations

This directory contains reference implementations of the Substrait Compliance Framework using various query engines. They demonstrate how to integrate the framework with a real engine and serve as starting points for engine developers.

## Purpose

These implementations serve to:
1. Demonstrate how to implement the `ComplianceEngine` interface
2. Validate that the SDK interfaces work correctly
3. Provide concrete examples for engine developers
4. Test the framework with real query engines

## Available Implementations

### DuckDB (Python)
- **Location**: `duckdb-python/`
- **Language**: Python
- **Engine**: DuckDB in-process analytical database
- **Status**: Reference implementation
- **Features**:
  - Complete `ComplianceEngine` interface implementation
  - Basic plan validation
  - Example test runner
  - Simple usage examples

## Scope

These implementations live outside the core SDK and API modules. They are intentionally lightweight — focused on showing the integration pattern rather than covering every edge case. They are not endorsed as production-ready engine adapters.

## Adding New Implementations

To contribute a new engine implementation:

1. Create a new directory: `test-implementations/<engine-name>-<language>/`
2. Implement the `ComplianceEngine` interface using your chosen SDK
3. Add a `README.md` explaining the implementation and how to run it
4. Include examples and a test runner
5. Add a `.gitignore` for build artifacts
6. Submit a pull request

## Contributing

If you create an implementation that would be useful to others:
1. Ensure it is well-documented
2. Include clear usage examples
3. Add it to the **Available Implementations** table above
4. Submit a pull request against `IBM/substrait-compliance:main`

For general contribution guidelines see [`CONTRIBUTING.md`](../CONTRIBUTING.md).
