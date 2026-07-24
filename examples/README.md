# Substrait Compliance Examples

Structural reference implementations showing how to wire a query engine into
the compliance framework. Each example implements the full `ComplianceEngine`
interface — data loading, plan validation, and the `executePlan` / `execute_plan`
method — but the plan-execution body is currently a stub returning empty output
(see the `STUB` comment in each file). A run against TPC-H will therefore show
0% pass rate until Option A (real engine execution) is implemented.

The five working demo engines in [`demo/engines/`](../demo/engines/) demonstrate
the full round-trip (load → execute → compare → report) and are the current
proof that the framework works end-to-end.

## Reference Implementations

### 1. DuckDB (C++)
**Location:** `duckdb-cpp/`

DuckDB integration skeleton using native Substrait support:
- Native Substrait extension usage
- SQL-based data loading
- Type mapping between Substrait and DuckDB
- Base64 encoding for binary plans

**Features:**
- 40+ supported functions
- All Substrait primitive and complex types
- Direct plan execution via `from_substrait()`
- Comprehensive validation

**Build & Run:**
```bash
cd duckdb-cpp
mkdir build && cd build
cmake ..
make
./duckdb_compliance_example [plan.substrait]
```

### 2. DuckDB (Java)
**Location:** `duckdb-java/`

Java-based DuckDB integration example:
- `ComplianceEngine` implementation
- SQL-based data loading
- Substrait plan execution
- TPC-H test suite support

**Run:**
```bash
cd duckdb-java
./compile.sh
java -cp "../../sdk/java/build/libs/*:." io.substrait.example.DuckDBComplianceExample
```

### 3. DataFusion (Rust)
**Location:** `datafusion-rust/`

Apache DataFusion integration skeleton with async execution:
- Native Substrait consumer (stub — not yet called)
- Arrow-based processing structure
- Async/await with Tokio runtime
- Type-safe Rust implementation

**Build & Run:**
```bash
cd datafusion-rust
cargo build --release
cargo run --release -- [plan.substrait]
```

### 4. DataFusion (Python)
**Location:** `datafusion-python/`

Python-based Apache DataFusion integration:
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

### 5. Velox (C++)
**Location:** `velox-cpp/`

Velox integration skeleton with vectorized execution structure:
- Velox's SubstraitVeloxPlanConverter (stub — not yet called)
- Columnar processing structure
- Presto SQL function library wiring
- Memory management scaffolding

**Build & Run:**
```bash
cd velox-cpp
mkdir build && cd build
cmake ..
make -j$(nproc)
./velox_compliance_example [plan.substrait]
```
## Comparison Matrix

| Feature | DuckDB (C++) | DataFusion (Rust) | Velox (C++) |
|---------|--------------|-------------------|-------------|
| **Language** | C++17 | Rust 2021 | C++17 |
| **Execution** | SQL-based | Vectorized | Vectorized |
| **Memory** | In-memory | Arrow columnar | Custom pools |
| **Stub status** | executePlan stub | execute_plan stub | executePlan stub |
| **Build Time** | Fast | Medium | Slow |
| **Dependencies** | Minimal | Moderate | Many |

## Choosing an Implementation

### Use DuckDB if:
- You need fast development iteration
- SQL compatibility is important
- You want minimal dependencies
- In-memory analytics is your use case

### Use DataFusion if:
- You prefer Rust's type safety
- You need async execution
- Arrow integration is important
- You want modern async/await patterns

### Use Velox if:
- You need maximum performance
- You're building production systems
- You need Presto SQL compatibility
- You can handle complex dependencies


## Architecture

All implementations follow the same pattern:

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
Each engine implements the `ComplianceEngine` interface:
- **C++:** `class MyEngine : public ComplianceEngine`
- **Rust:** `impl ComplianceEngine for MyEngine`
- **Java:** `class MyEngine implements ComplianceEngine`
- **Python:** `class MyEngine(ComplianceEngine)`

### Data Loading
Engines load test data into their native format:
- **DuckDB:** SQL CREATE TABLE + INSERT statements
- **DataFusion:** Arrow RecordBatch registration
- **Velox:** RowVector with memory pools
- **Custom:** Any format the engine supports

### Plan Execution
Engines execute Substrait plans using their native support:
- Parse binary Substrait plan
- Convert to engine's internal representation
- Execute against loaded data
- Return results as TableData

### Result Reporting
Framework aggregates results across all tests:
- Pass/fail counts
- Error messages
- Execution times
- Pass rate percentage

## Adding Your Engine

1. **Choose SDK:** C++, Rust, Java, or Python
2. **Pick Template:** Start from the closest reference implementation
3. **Implement Interface:** ComplianceEngine with all methods
4. **Load Data:** Convert TableData to your engine's format
5. **Execute Plans:** Use your engine's Substrait support
6. **Run Tests:** Use ComplianceRunner
7. **Optimize:** Profile and improve performance
8. **Document:** Create README with build instructions
9. **Share:** Contribute back to the community!

## Benefits

- **Self-Service:** Test locally without framework dependency
- **Fast Iteration:** No waiting for central CI/CD
- **Comprehensive:** Full function coverage
- **Transparent:** See exactly what's being tested
- **Reproducible:** Deterministic test cases
- **Structural template:** Full interface wiring without the engine execution stub filled in

## Performance Tips

### DuckDB
- Use in-memory database for speed
- Batch INSERT statements
- Enable parallel execution
- Use appropriate data types

### DataFusion
- Configure batch size (default 8192)
- Set target partitions for parallelism
- Use memory-mapped files for large data
- Enable query optimization

### Velox
- Configure memory pool size
- Use appropriate batch size (1024)
- Enable vectorization
- Configure spilling for large queries

## Next Steps

1. Choose an implementation based on your needs
2. Follow the build instructions in the README
3. Run the example with sample data
4. Create your own test plans
5. Integrate with your CI/CD pipeline
6. Share your compliance results!

## Support

- **C++ SDK:** `/sdk/cpp/README.md`
- **Rust SDK:** `/sdk/rust/README.md`
- **Java SDK:** `/sdk/java/README.md`
- **Python SDK:** `/sdk/python/README.md`
- **Test Suites:** `/test-suites/README.md`
- **DuckDB:** https://duckdb.org/docs/
- **DataFusion:** https://arrow.apache.org/datafusion/
- **Velox:** https://facebookincubator.github.io/velox/

## Contributing

We welcome contributions of new reference implementations! Please:
1. Follow the existing patterns
2. Include comprehensive documentation
3. Add build/test instructions
4. Provide example usage
5. Submit a pull request

## License

All reference implementations are licensed under Apache License 2.0.
