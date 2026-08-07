# Substrait Compliance Examples

Reference implementations showing how to wire a query engine into the
compliance framework. Each example implements the full `ComplianceEngine`
interface — data loading, plan validation, and `executePlan` / `execute_plan`.

**Execution status by example:**

| Example | Language | `executePlan` status |
|---------|----------|----------------------|
| `duckdb-java` | Java | ✅ Real — JDBC + `substrait()` table function |
| `duckdb-cpp` | C++ | ✅ Real — native `from_substrait()` C++ API |
| `datafusion-python` | Python | ✅ Real if `datafusion-substrait` is installed; returns `None` (FAILED) otherwise |
| `datafusion-rust` | Rust | ✅ Real — registers Arrow MemTables, calls `from_substrait_plan` + `collect()` |
| `velox-cpp` | C++ | ✅ Wired — `SubstraitVeloxPlanConverter`, `Task::create/start/next`; requires a Velox build to link |

For the `duckdb-java` example, a typical run against the TPC-H suite with the
substrait extension loaded reports results per query — pass rate depends on
which TPC-H plans your DuckDB version can execute. Without the extension it
reports FAILED for each query with a clear `substrait extension not loaded`
message.

## Reference Implementations

### 1. DuckDB (Java)
**Location:** `duckdb-java/`

Real DuckDB integration via JDBC. The `executePlan` method:
1. Loads input `TableData` into DuckDB via `CREATE TABLE` + `INSERT`
2. Base64-encodes the plan bytes and calls `SELECT * FROM substrait('<base64>')`
3. Reads the `ResultSet` back into a `TableData` using JDBC metadata for types

**Run:**
```bash
cd duckdb-java
./compile.sh
java -cp "build:../../sdk/java/build/libs/substrait-compliance-0.1.1-all.jar" \
    io.substrait.example.DuckDBComplianceExample
```

Requires DuckDB JDBC with the substrait extension installable. The engine
attempts `INSTALL substrait; LOAD substrait;` on startup and prints a warning
if the extension is unavailable.

### 2. DuckDB (C++)
**Location:** `duckdb-cpp/`

Real DuckDB integration via the native C++ API. The `executePlan` method:
1. Loads input data via `DROP TABLE IF EXISTS` + `CREATE TABLE` + `INSERT`
2. Base64-encodes the plan bytes and calls `conn_->Query("SELECT * FROM from_substrait('<base64>')")`
3. Fetches chunks from the `QueryResult` and converts to `TableData`

**Build & Run:**
```bash
cd duckdb-cpp
cmake -S . -B build
cmake --build build --parallel
./build/duckdb_compliance_example [plan.substrait]
```

### 3. DataFusion (Python)
**Location:** `datafusion-python/`

Python DataFusion integration using `datafusion-substrait`. When the packages
are installed, `execute_plan` calls:

```python
plan = await serde.deserialize_bytes(plan_bytes)
df   = await consumer.from_substrait_plan(ctx, plan)
batches = df.collect()
```

If `datafusion` or `datafusion-substrait` is not installed the engine
constructor prints a warning and `execute_plan` returns `None`, causing
the runner to report FAILED with a clear message rather than raising at
import time.

**Run:**
```bash
cd datafusion-python
pip install datafusion datafusion-substrait pyarrow
pip install -e ../../sdk/python
python datafusion_compliance.py
```

### 4. DataFusion (Rust)
**Location:** `datafusion-rust/`

Real DataFusion integration using `datafusion-substrait`:
- Registers input `TableData` as in-memory Arrow `MemTable`s
- Serializes the plan and calls `from_substrait_plan` to get a `LogicalPlan`
- Executes via `SessionContext::execute_logical_plan` and collects results
- Requires `protoc` to compile the `substrait` proto crate (`apt install protobuf-compiler` or Homebrew)

**Build & Run:**
```bash
cd datafusion-rust
cargo build --release
cargo run --release -- [plan.substrait]
```

### 5. Velox (C++)
**Location:** `velox-cpp/`

Fully wired Velox integration with vectorized execution:
- Registers input data as `RowVector`s in a Velox memory pool
- Converts the Substrait plan via `SubstraitVeloxPlanConverter::toVeloxPlan`
- Executes via `exec::Task::create/start` and collects results with `task->next()`
- Source compiles against the Velox headers; linking requires a complete Velox build

**Build & Run:**
```bash
cd velox-cpp
cmake -S . -B build
cmake --build build --parallel
./build/velox_compliance_example [plan.substrait]
```

## Comparison Matrix

| Feature | DuckDB (Java) | DuckDB (C++) | DataFusion (Python) | DataFusion (Rust) | Velox (C++) |
|---------|--------------|--------------|---------------------|-------------------|-------------|
| **Language** | Java 17 | C++17 | Python 3.9+ | Rust 2021 | C++17 |
| **Execution model** | Synchronous | Synchronous | Synchronous | Async (Tokio) | Synchronous |
| **`executePlan` status** | ✅ Real | ✅ Real | ✅ Real (if installed) | ✅ Real | ✅ Wired (needs Velox build) |
| **Plan mechanism** | JDBC `substrait()` | `from_substrait()` | `consumer.from_substrait_plan()` | `from_substrait_plan` + `collect()` | `SubstraitVeloxPlanConverter` + `Task` |
| **Build time** | Fast (Gradle) | Fast | None (Python) | Medium | Slow |
| **Dependencies** | DuckDB JDBC | DuckDB C++ | datafusion, pyarrow | datafusion crate | Velox, Presto funcs |

## Architecture

All implementations follow the same pattern:

```
1. Implement ComplianceEngine interface
   ├── get_info() / getEngineInfo()  — Return engine metadata
   ├── get_capabilities()            — Declare supported features
   ├── execute_plan()                — Execute Substrait plans
   └── validate_plan()               — Validate plan structure

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
- **DuckDB:** SQL `CREATE TABLE` + `INSERT` statements
- **DataFusion:** Arrow `RecordBatch` registration
- **Velox:** `RowVector` with memory pools
- **Custom:** Any format the engine supports

### Plan Execution
Engines execute Substrait plans using their native support:
1. Receive plan bytes (binary protobuf) from the runner
2. Convert to engine's internal representation
3. Execute against pre-loaded input tables
4. Return results as `TableData`

### Result Reporting
Framework aggregates results across all tests:
- Pass/fail counts and error messages
- Execution times per query
- Pass rate percentage

## Adding Your Engine

1. **Choose SDK:** C++, Rust, Java, Python (or Go, TypeScript, C#, Scala)
2. **Pick Template:** Start from the closest reference implementation above
3. **Implement Interface:** `ComplianceEngine` with all four required methods
4. **Load Data:** Convert `TableData` to your engine's format in `execute_plan`
5. **Execute Plans:** Call your engine's Substrait consumer with the plan bytes
6. **Run Tests:** Use `ComplianceRunner`
7. **Optimize:** Profile and improve performance
8. **Document:** Create a README with build instructions
9. **Share:** Contribute back to the community!

## Performance Tips

### DuckDB
- Use in-memory database for speed
- Batch `INSERT` statements
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
2. Follow the build instructions above
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
