# DuckDB Java Compliance Example

Example implementation showing how to integrate DuckDB with the Substrait compliance framework.

## Overview

This example demonstrates:
- Implementing the `ComplianceEngine` interface
- Loading and executing Substrait plans in DuckDB
- Running the TPC-H compliance test suite
- Reporting results

## Structure

```
src/main/java/io/substrait/example/
├── DuckDBComplianceEngine.java  # Engine implementation
└── DuckDBComplianceExample.java # Example runner
```

## Running

```bash
# Compile
javac -cp "../../sdk/java/build/libs/*:." \
  src/main/java/io/substrait/example/*.java

# Run
java -cp "../../sdk/java/build/libs/*:." \
  io.substrait.example.DuckDBComplianceExample
```

## Expected Output

```
DuckDB Substrait Compliance Testing
====================================

Engine: DuckDB 0.10.0 by DuckDB Labs

Loaded test suite: tpch
Test cases: 22

Results:
--------
Total:   22
Passed:  18
Failed:  3
Errors:  1
Pass Rate: 81.8%
```

## Integration Steps

1. **Implement ComplianceEngine**
   ```java
   public class MyEngine implements ComplianceEngine {
       public EngineInfo getInfo() { ... }
       public ComplianceResult executePlan(...) { ... }
   }
   ```

2. **Load Test Suite**
   ```java
   YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
   TestSuite suite = loader.load(Paths.get("metadata.yaml"));
   ```

3. **Run Tests**
   ```java
   ComplianceRunner runner = new ComplianceRunner(engine);
   ComplianceReport report = runner.runTestSuite(suite);
   ```

4. **Report Results**
   ```java
   System.out.println("Pass Rate: " + report.getPassRate() + "%");
   ```

## Notes

- This is a simplified example for demonstration
- Real implementation would use DuckDB's actual Substrait support
- Production code would include proper error handling
- Data loading would be optimized for performance
