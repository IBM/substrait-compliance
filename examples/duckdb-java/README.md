# DuckDB Compliance Example

This example demonstrates how to integrate DuckDB with the Substrait Compliance Framework.

## Overview

The example includes:
- `DuckDBComplianceEngine.java` - Implementation of the `ComplianceEngine` interface for DuckDB
- `DuckDBComplianceExample.java` - Example usage showing how to run compliance tests

## Building

### Prerequisites
- Java 11 or higher
- Gradle (included via wrapper in SDK)

### Compile the Example

Use the provided compile script:

```bash
./compile.sh
```

Or manually with the full command:

```bash
# Build the SDK first
cd ../../sdk/java
./gradlew build
cd ../../examples/duckdb-java

# Compile with dependencies
javac -cp "../../sdk/java/build/libs/*:$HOME/.gradle/caches/modules-2/files-2.1/io.substrait/core/0.80.0/*/core-0.80.0.jar:$HOME/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-java/3.*/protobuf-java-3.*.jar:." \
    src/main/java/io/substrait/example/*.java
```

### Using Gradle (Alternative)

You can also build using Gradle from the root:

```bash
cd ../..
./sdk/java/gradlew :sdk:java:build
```

## Running

To run the example, you'll need:
1. The compiled classes
2. DuckDB JDBC driver on the classpath
3. Test suite files

```bash
java -cp "../../sdk/java/build/libs/*:$SUBSTRAIT_CORE:$PROTOBUF:$DUCKDB_JDBC:src/main/java" \
    io.substrait.example.DuckDBComplianceExample
```

## Implementation Notes

### Key Changes from Original API

The example has been updated to match the current SDK API:

1. **EngineInfo Constructor**: Now takes 3 parameters (name, version, substraitVersion) instead of 4
2. **EngineCapabilities Builder**: Uses `.addRelation()` and `.addFunction()` instead of `.addSupportedRelation()`
3. **ComplianceResult**: Uses static factory methods `success()` and `failure()`
4. **TableData Constructor**: Requires 3 parameters (columnNames, columnTypes, rows)
5. **ComplianceReport Methods**: Uses `getTotalTests()`, `getComplianceScore()`, `getTestResults()` instead of old method names
6. **TestResult**: Uses `TestResult.Status` enum instead of `TestStatus`
7. **Plan Type**: Uses `io.substrait.proto.Plan` from Substrait core library

### Architecture

The `DuckDBComplianceEngine` implements the `ComplianceEngine` interface with these key methods:

- `getEngineInfo()` - Returns engine identification
- `getCapabilities()` - Declares supported Substrait features
- `executePlan()` - Executes a Substrait plan and returns results
- `validatePlan()` - Validates if a plan is supported before execution

## Dependencies

The example requires:
- `substrait-compliance` SDK (from `../../sdk/java`)
- `io.substrait:core:0.80.0` (Substrait core library)
- `com.google.protobuf:protobuf-java:3.25.8` (Protocol Buffers)
- DuckDB JDBC driver (for actual execution)

## Next Steps

To make this a fully functional example:

1. Add DuckDB JDBC driver dependency
2. Implement actual Substrait plan execution using DuckDB's Substrait support
3. Add proper error handling and logging
4. Create example test suites
5. Add integration tests

## License

Apache License 2.0
