# Substrait Compliance Examples

Example implementations demonstrating how to integrate engines with the Substrait compliance framework.

## Examples

### 1. DuckDB (Java)
**Location:** `duckdb-java/`

Demonstrates DuckDB integration using the Java SDK:
- Full `ComplianceEngine` implementation
- SQL-based data loading
- Substrait plan execution
- TPC-H test suite execution

**Run:**
```bash
cd duckdb-java
javac -cp "../../sdk/java/build/libs/*:." src/main/java/io/substrait/example/*.java
java -cp "../../sdk/java/build/libs/*:." io.substrait.example.DuckDBComplianceExample
```

### 2. DataFusion (Python)
**Location:** `datafusion-python/`

Demonstrates Apache DataFusion integration using the Python SDK:
- Pythonic `ComplianceEngine` implementation
- DataFrame-based data handling
- Native Substrait support
- TPC-H test suite execution

**Run:**
```bash
cd datafusion-python
pip install -e ../../sdk/python
python datafusion_compliance.py
```

## Architecture

Both examples follow the same pattern:

```
1. Implement ComplianceEngine interface
   ├── get_info() - Return engine metadata
   ├── get_capabilities() - Declare supported features
   ├── execute_plan() - Execute Substrait plans
   └── validate_plan() - Validate plan structure

2. Load test suite
   └── YamlTestSuiteLoader.load("metadata.yaml")

3. Run tests
   └── ComplianceRunner.run_test_suite(suite)

4. Report results
   └── ComplianceReport with pass/fail statistics
```

## Key Concepts

### Engine Implementation
Each engine implements the `ComplianceEngine` interface/trait:
- **Java:** `implements ComplianceEngine`
- **Python:** `class MyEngine(ComplianceEngine)`
- **Rust:** `impl ComplianceEngine for MyEngine`

### Data Loading
Engines load test data into their native format:
- **DuckDB:** SQL CREATE TABLE + INSERT
- **DataFusion:** Register CSV/Parquet files
- **Custom:** Any format the engine supports

### Plan Execution
Engines execute Substrait plans using their native support:
- Parse binary Substrait plan
- Execute against loaded data
- Return results as TableData

### Result Reporting
Framework aggregates results across all tests:
- Pass/fail counts
- Error messages
- Execution times
- Pass rate percentage

## Adding Your Engine

1. **Choose SDK:** Java, Python, or Rust
2. **Implement Interface:** ComplianceEngine
3. **Load Data:** Convert TableData to your format
4. **Execute Plans:** Use your Substrait support
5. **Run Tests:** Use ComplianceRunner
6. **Report:** Share your compliance results!

## Benefits

- **Self-Service:** Test locally without framework dependency
- **Fast Iteration:** No waiting for central CI/CD
- **Comprehensive:** Full TPC-H coverage (22 queries)
- **Transparent:** See exactly what's being tested
- **Reproducible:** Deterministic test cases

## Next Steps

1. Copy an example as a starting point
2. Replace mock implementation with real engine calls
3. Run against TPC-H test suite
4. Iterate until tests pass
5. Share your compliance results!

## Support

- **Java SDK:** `/sdk/java/README.md`
- **Python SDK:** `/sdk/python/README.md`
- **Rust SDK:** `/sdk/rust/README.md`
- **Test Suites:** `/test-suites/tpch/README.md`
