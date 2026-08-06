# DuckDB Java Compliance Example

Example implementation showing how to integrate DuckDB with the Substrait
compliance framework using Java.

## Prerequisites

- Java 11 or higher
- The SDK fat jar (built from `sdk/java`)
- DuckDB JDBC driver (downloaded automatically by `download-deps.sh`)

## Quick Start

```bash
# 1. Download the DuckDB JDBC driver into lib/
./download-deps.sh

# 2. Build the SDK fat jar (skip if already built)
cd ../../sdk/java && ./gradlew shadowJar && cd -

# 3. Compile the example
./compile.sh

# 4. Run the example
FAT_JAR=../../sdk/java/build/libs/substrait-compliance-0.1.1-all.jar
DUCKDB_JAR=lib/duckdb_jdbc-1.3.1.0.jar
java -cp "build:$FAT_JAR:$DUCKDB_JAR" io.substrait.example.DuckDBComplianceExample
```

`compile.sh` calls `download-deps.sh` automatically if the JDBC jar is
missing, so running just `./compile.sh` is the normal workflow.

## Structure

```
duckdb-java/
├── compile.sh                                  # Build script
├── download-deps.sh                            # Fetches DuckDB JDBC jar
├── lib/                                        # Runtime JARs (git-ignored)
│   └── duckdb_jdbc-1.3.1.0.jar
└── src/main/java/io/substrait/example/
    ├── DuckDBComplianceEngine.java             # ComplianceEngine implementation
    └── DuckDBComplianceExample.java            # Entry point
```

## Implementing the `ComplianceEngine` Interface

The [`ComplianceEngine`](../../sdk/java/src/main/java/io/substrait/compliance/ComplianceEngine.java)
interface has six methods — four required and two optional defaults:

```java
public interface ComplianceEngine {

    // Required ──────────────────────────────────────────────────────────────

    /** Execute a Substrait plan with the provided input tables. */
    ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
        throws ComplianceException;

    /** Validate whether a plan is supported before execution. */
    PlanValidationResult validatePlan(Plan plan);

    /** Return engine identification and version metadata. */
    EngineInfo getEngineInfo();

    /** Declare which Substrait relations and functions are supported. */
    EngineCapabilities getCapabilities();

    // Optional defaults ─────────────────────────────────────────────────────

    /** Called once before the test suite runs. */
    default void initialize() throws ComplianceException {}

    /** Called once after the test suite completes. */
    default void cleanup() throws ComplianceException {}
}
```

### Execution model

- `initialize()` is called **once** before any test case runs.
- `executePlan()` is called **once per test case**, sequentially.  
  The runner is single-threaded; engines may keep connection state across calls.
- `cleanup()` is called **once** after all test cases finish (including on error).
- The runner is synchronous — there is no async/await contract in the Java SDK
  (contrast with the TypeScript SDK, whose interface is `async`).

### `DuckDBComplianceEngine` — how it works

[`DuckDBComplianceEngine`](src/main/java/io/substrait/example/DuckDBComplianceEngine.java)
holds a single `java.sql.Connection` to an in-memory DuckDB database and
implements the full interface:

| Method | What it does |
|--------|-------------|
| `getEngineInfo()` | Returns name, version, Substrait version |
| `getCapabilities()` | Declares supported relations and functions |
| `executePlan(plan, inputData)` | Loads `inputData` into DuckDB tables, then calls `SELECT * FROM substrait('<base64-plan>')` |
| `validatePlan(plan)` | Returns `supported()` if the plan is non-null and non-empty |

The DuckDB `substrait` extension is installed and loaded in the constructor:

```java
stmt.execute("INSTALL substrait");
stmt.execute("LOAD substrait");
```

If the extension cannot be installed (offline CI, restricted network), a
warning is printed and `executePlan` will fail at query time rather than at
construction time.

## Dependencies

| Artifact | Version | Notes |
|----------|---------|-------|
| `substrait-compliance SDK | 0.1.1 | Fat jar from `sdk/java` |
| `org.duckdb:duckdb_jdbc` | 1.3.1.0 | Fetched by `download-deps.sh` |

The JDBC jar is downloaded from Maven Central. No Maven or Gradle installation
is required on the developer machine.

## License

Apache License 2.0
