# Language SDKs

Eight SDKs let an engine run the compliance suites in its own tests, in its own language:
Java, Python, Rust, C++, Go, TypeScript, C# and Scala. Each has the same parts: an engine
interface to implement, a suite loader, a runner, a result comparator and a report.

The SDKs are for **in-house testing**. Leaderboard results come from the Python runners in
[`scripts/`](../scripts/) (and, for TPC-H, the Java SDK runner); see
[Which route do I use?](../README.md#which-route-do-i-use) and
[submissions/README.md](../submissions/README.md). Every SDK's comparator is checked against
the leaderboard's shared comparison vectors ([`conformance/`](conformance/README.md)), so an
in-house result means the same as a leaderboard one.

## SDKs at a glance

### Java SDK
- **JDK**: 17+  |  **Build**: Gradle
- **Key Classes**: `ComplianceEngine`, `ComplianceRunner`, `YamlTestSuiteLoader`, `EngineCapabilities`
- **Docs**: [java/README.md](java/README.md)

### Python SDK
- **Python**: 3.8+  |  **Build**: pyproject.toml / install-dev.sh
- **Key Modules**: `engine.py`, `runner.py`, `loader.py`, `result.py`
- **Docs**: [python/README.md](python/README.md)

### C++ SDK
- **Standard**: C++17  |  **Build**: CMake 3.15+
- **Key Headers**: `engine.h`, `runner.h`, `loader.h`, `result.h`, `comparator.h`
- **Features**: Smart pointers, zero-copy operations, header-only option, cross-platform
- **Docs**: [cpp/README.md](cpp/README.md)

### Go SDK
- **Go**: 1.21+  |  **Build**: Go modules
- **Key Files**: `engine.go`, `runner.go`, `loader.go`, `result.go`, `table_data.go`
- **Docs**: [go/README.md](go/README.md)

### Rust SDK
- **Edition**: 2021  |  **Build**: Cargo
- **Key Modules**: `engine.rs`, `runner.rs`, `loader.rs`, `result.rs`
- **Docs**: [rust/README.md](rust/README.md)

### TypeScript SDK
- **Runtime**: Node.js / Browser  |  **Build**: npm
- **Docs**: [typescript/README.md](typescript/README.md)

### C# SDK
- **Runtime**: .NET 10+  |  **Build**: dotnet
- **Docs**: [csharp/README.md](csharp/README.md)

### Scala SDK
- **Scala**: 2.13  |  **Build**: sbt  |  **JVM**: 17+
- **Key Files**: [`ComplianceEngine.scala`](scala/src/main/scala/io/substrait/compliance/ComplianceEngine.scala), [`ComplianceRunner.scala`](scala/src/main/scala/io/substrait/compliance/ComplianceRunner.scala), [`TestSuiteLoader.scala`](scala/src/main/scala/io/substrait/compliance/TestSuiteLoader.scala), [`EngineResult.scala`](scala/src/main/scala/io/substrait/compliance/EngineResult.scala)
- **Key Type**: [`EngineResult`](scala/src/main/scala/io/substrait/compliance/EngineResult.scala) separates engine execution from test results
- **Docs**: [scala/README.md](scala/README.md)

## Set up an SDK

### Step 1: Build and test the SDK

<details>
<summary><b>Java SDK (Recommended — Most Complete)</b></summary>

The SDK is not yet published to Maven Central or GitHub Packages.
Build from source:

```bash
cd sdk/java
./gradlew build        # compile + run tests
./gradlew shadowJar    # produces build/libs/substrait-compliance-0.1.2-all.jar
```

**Verify build:**
```bash
ls -la sdk/java/build/libs/
# substrait-compliance-0.1.2-all.jar   (fat jar with all dependencies)
# substrait-compliance-0.1.2.jar       (library jar, no deps bundled)
```
</details>

<details>
<summary><b>Python SDK (Pythonic Interface)</b></summary>

```bash
# Install the SDK and dev dependencies
cd sdk/python
python3 -m venv venv && source venv/bin/activate
./install-dev.sh   # sets index to PyPI, seeds build tools, installs editable

# Run tests to verify
pytest
```

**Verify Installation:**
```bash
python3 -c "import substrait_compliance; print('SDK installed successfully!')"
```
</details>

<details>
<summary><b>C++ SDK (Native Performance)</b></summary>

```bash
# Build the SDK
cd sdk/cpp
cmake -S . -B build -DBUILD_TESTS=ON
cmake --build build --parallel

# Run tests to verify
ctest --test-dir build --output-on-failure
```

**Verify Installation:**
```bash
ls -la libsubstrait_compliance.*
# Should see: libsubstrait_compliance.so (or .dylib on macOS, .dll on Windows)
```
</details>

<details>
<summary><b>Go SDK (Cloud-Native & Concurrent)</b></summary>

```bash
# Get the SDK
cd sdk/go
go mod download

# Run tests to verify
go test ./...
```
</details>

<details>
<summary><b>TypeScript/JavaScript SDK (Modern Web & Node.js)</b></summary>

```bash
# Install the SDK
cd sdk/typescript
npm install

# Build the SDK
npm run build

# Run tests to verify
npm test
```

**Verify Installation:**
```bash
ls -la dist/
# Should see: index.js, index.d.ts, and other compiled files
```
</details>

<details>
<summary><b>C#/.NET SDK (Enterprise & Cross-Platform)</b></summary>

```bash
# Build the SDK
cd sdk/csharp
dotnet restore
dotnet build

# Run tests to verify
dotnet test
```

**Verify Installation:**
```bash
ls -la bin/Debug/net10.0/
# Should see: Substrait.Compliance.dll and related files
```
</details>

<details>
<summary><b>Scala SDK (Functional & Type-Safe)</b></summary>

```bash
# Build the SDK
cd sdk/scala
sbt compile

# Run tests to verify
sbt test
```

**Verify Installation:**
```bash
ls -la target/scala-2.13/
# Should see: substrait-compliance_2.13-0.1.2.jar
```
</details>

<details>
<summary><b>Rust SDK (High Performance)</b></summary>

```bash
# Build the SDK
cd sdk/rust
cargo build --release

# Run tests to verify
cargo test
```

**Verify Installation:**
```bash
ls -la target/release/
# Should see: libsubstrait_compliance.rlib
```
</details>

### Step 2: Implement the engine interface

#### Interface quick-reference

Every SDK exposes the same four required operations plus optional lifecycle hooks. The table below shows the exact names and signatures by language — use it as your checklist before writing any code.

| | Java | Python | TypeScript | Rust | Go | C# | C++ | Scala |
|---|---|---|---|---|---|---|---|---|
| **Plan arg type** | `Plan` (parsed protobuf) | `bytes` | `Uint8Array` | `&[u8]` | `[]byte` | `byte[]` | `vector<uint8_t>` | `Array[Byte]` |
| **Execution model** | Synchronous | Synchronous | `async`/`Promise` | Synchronous | `context.Context` | `async Task` | Synchronous | `Future` |
| **Get metadata** | `getEngineInfo()` | `get_info()` | `getInfo()` | `get_info()` | `GetInfo()` | `GetInfo()` | `get_info()` | `getInfo` |
| **Get capabilities** | `getCapabilities()` | `get_capabilities()` | `getCapabilities()` | `get_capabilities()` | `GetCapabilities()` | `GetCapabilities()` | `get_capabilities()` | `getCapabilities` |
| **Execute plan** | `executePlan(Plan, Map<String,TableData>)` | `execute_plan(bytes, Dict[str,TableData])` | `executePlan(Uint8Array, Map<string,TableData>)` | `execute_plan(&[u8], &HashMap<String,TableData>)` | `ExecutePlan(ctx, []byte, map[string]*TableData)` | `ExecutePlanAsync(byte[], IReadOnlyDictionary<string,TableData>)` | `execute_plan(vector<uint8_t>, TableCollection)` | `executePlan(Array[Byte], Map[String,TableData])` |
| **Validate plan** | `validatePlan(Plan)` | `validate_plan(bytes)` | `validatePlan(Uint8Array)` | `validate_plan(&[u8])` | `ValidatePlan(ctx, []byte)` | `ValidatePlanAsync(byte[])` | `validate_plan(vector<uint8_t>)` | `validatePlan(Array[Byte])` |
| **Lifecycle init** | `initialize()` *(default no-op)* | — | `initialize?()` *(optional)* | — | `Initialize(ctx)` | `InitializeAsync()` *(default no-op)* | `initialize()` *(default no-op)* | `initialize()` *(default no-op)* |
| **Lifecycle cleanup** | `cleanup()` *(default no-op)* | — | `shutdown?()` *(optional)* | — | `Shutdown(ctx)` | `ShutdownAsync()` *(default no-op)* | `shutdown()` *(default no-op)* | `shutdown()` *(default no-op)* |

**Key differences to be aware of:**

- **Java** receives a fully-parsed `io.substrait.proto.Plan` object — the SDK deserializes the protobuf before calling your engine. All other SDKs receive raw bytes and you deserialize inside `execute_plan` / `executePlan`.
- **TypeScript** and **Scala** use `Future`-based async execution; the TypeScript runner also supports optional parallel execution (`RunnerOptions.parallel`). All other SDK runners execute tests sequentially.
- **Go** passes a `context.Context` through every call — use it for cancellation and deadlines.
- **C++** uses `vector<uint8_t>` for plan bytes and a `TableCollection` typedef (`unordered_map<string, TableData>`) for input data.
- **Lifecycle**: `initialize` is called once before the first test in a suite; `cleanup`/`shutdown` once after the last. They are not called per-test. You may keep open connections across test cases within a suite, but the engine must be stateless between test case executions (input data is passed fresh each time).

<details>
<summary><b>Java Example</b></summary>

```java
public class MyEngine implements ComplianceEngine {
    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo("MyEngine", "1.0.0", "MyCompany");
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        // Load data into your engine
        loadInputData(inputData);

        // Execute the Substrait plan (Plan is a parsed protobuf object)
        TableData output = executeSubstraitPlan(plan);

        return ComplianceResult.success(output, 0);
    }
}
```
</details>

<details>
<summary><b>Python Example</b></summary>

```python
class MyEngine(ComplianceEngine):
    def get_info(self) -> EngineInfo:
        return EngineInfo("MyEngine", "1.0.0", "MyCompany")

    def execute_plan(self, plan_bytes: bytes, input_data: Dict[str, TableData]) -> ComplianceResult:
        # Load data into your engine
        self._load_input_data(input_data)

        # Execute Substrait plan
        output = self._execute_substrait_plan(plan_bytes)

        return ComplianceResult("test-id", TestStatus.PASSED, output_data=output)
```
</details>

<details>
<summary><b>Rust Example</b></summary>

```rust
impl ComplianceEngine for MyEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("MyEngine", "1.0.0", "MyCompany")
    }

    fn execute_plan(&self, plan_bytes: &[u8], input_data: &HashMap<String, TableData>)
        -> Result<ComplianceResult> {
        // Load data into your engine
        self.load_input_data(input_data)?;

        // Execute Substrait plan
        let output = self.execute_substrait_plan(plan_bytes)?;

        Ok(ComplianceResult::new("test-id", TestStatus::Passed).with_output(output))
    }
}
```
</details>

<details>
<summary><b>TypeScript Example</b></summary>

```typescript
import { ComplianceEngine, EngineInfo, EngineCapabilities, ComplianceResult, TestStatus, TableData } from '@substrait/compliance';

class MyEngine implements ComplianceEngine {
    getInfo(): EngineInfo {
        return {
            name: 'MyEngine',
            version: '1.0.0',
            vendor: 'MyCompany',
            description: 'A high-performance query engine'
        };
    }

    getCapabilities(): EngineCapabilities {
        return {
            supportedRelations: ['read', 'filter', 'project', 'aggregate'],
            supportedFunctions: ['add', 'subtract', 'equal'],
            supportedTypes: ['i32', 'i64', 'string', 'boolean']
        };
    }

    async executePlan(planBytes: Uint8Array, inputData: Map<string, TableData>): Promise<ComplianceResult> {
        await this.loadInputData(inputData);
        const output = await this.executeSubstraitPlan(planBytes);
        return new ComplianceResult('test-id', TestStatus.PASSED, output, undefined, undefined, executionTimeMs);
    }
}
```
</details>

<details>
<summary><b>C# Example</b></summary>

```csharp
using Substrait.Compliance;

public class MyEngine : IComplianceEngine
{
    public EngineInfo GetInfo()
    {
        return new EngineInfo(
            Name: "MyEngine",
            Version: "1.0.0",
            Vendor: "MyCompany",
            Description: "A high-performance query engine"
        );
    }

    public async Task<ComplianceResult> ExecutePlanAsync(
        byte[] planBytes,
        IReadOnlyDictionary<string, TableData> inputData)
    {
        await LoadInputDataAsync(inputData);
        var output = await ExecuteSubstraitPlanAsync(planBytes);
        return new ComplianceResult("test-id", TestStatus.Passed, output, null, null, executionTimeMs);
    }
}
```
</details>

### Step 3: Run the suites

**Java example (DuckDB):**
```bash
cd examples/duckdb-java
./compile.sh
java -cp "build:../../sdk/java/build/libs/substrait-compliance-0.1.2-all.jar" \
    io.substrait.example.DuckDBComplianceExample

```

**Python example (DataFusion):**
```bash
pip install datafusion datafusion-substrait pyarrow
cd examples/datafusion-python
python datafusion_compliance.py

```

**Programmatic usage (Java):**
```java
// Load and run a test suite
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load("test-suites/tpch/metadata.yaml");

ComplianceRunner runner = new ComplianceRunner(new MyEngine());
ComplianceReport report = runner.runTestSuite(suite);

System.out.println("Pass Rate: " + report.getPassRate() + "%");
System.out.println("Passed: " + report.getPassedCount() + "/" + report.getTotalCount());
```

**See [examples/](../examples/) for complete implementations.**

### Step 4: Check the report

```bash
# Check generated reports
ls -la output/

# View report contents (requires jq)
cat output/your-engine-report.json | jq .

```

### Step 5: Run it in CI (optional)

From the repository root, copy the template workflow to your engine's repository:

```bash
cp .github/workflows/engine-compliance-template.yml \
   your-engine/.github/workflows/substrait-compliance.yml
```

Customize the workflow for your engine's build process (engine name, build commands, compliance threshold — default 80%).

See [.github/workflows/README.md](../.github/workflows/README.md) for details.

## How the SDKs line up

Every SDK with ✅ in all four columns can produce honest compliance scores for in-house testing when given a test suite with expected output. Leaderboard results come from the Python runners, and for TPC-H also the Java SDK runner. Every SDK's comparator is checked against the shared comparison vectors in [`sdk/conformance/`](conformance/README.md) and compares decimal columns at the scale the plan declares (see [Which route do I use?](../README.md#which-route-do-i-use)).

| SDK | Runner compares output | Type-aware comparator | Loader carries expected output | Pass-through integration test |
|-----|---|---|---|---|
| **Java** | ✅ full, epsilon 1e-9 | ✅ `normalizeType` + `valuesMatch` | ✅ typed CSV headers | ✅ TPC-H 22/22 proven |
| **Python** | ✅ full, `isclose` 1e-9 | ✅ `_normalize_type` + `_values_match` | ✅ YAML `expected_output` field | ✅ 5 pass-through tests |
| **TypeScript** | ✅ `ResultComparator` in runner | ✅ shared rule (`describeMismatch`) | ✅ repo suites; inputs typed by the plan | ✅ 5 pass-through tests |
| **C#** | ✅ `ResultComparator` in runner | ✅ shared rule (`DescribeMismatch`) | ✅ repo suites; inputs typed by the plan | ✅ 5 pass-through tests |
| **Go** | ✅ `DescribeMismatch()` in runner | ✅ shared rule | ✅ repo suites; inputs typed by the plan | ✅ 5 pass-through tests |
| **Rust** | ✅ full value comparison | ✅ shared rule (`describe_mismatch`) | ✅ repo suites; inputs typed by the plan, read per case | ✅ 4 pass-through tests |
| **C++** | ✅ full value comparison | ✅ shared rule (`describe_mismatch`) | ✅ repo suites; inputs typed by the plan, read per case | ✅ 5 pass-through tests |
| **Scala** | ✅ full value comparison | ✅ shared rule (`ResultComparison`) | ✅ repo suites; inputs typed by the plan | ✅ 5 pass-through tests |

## Performance benchmarking

All SDKs include benchmarking components to measure engine performance:

- **Statistical Analysis**: Min, Max, Avg, Median, P95, P99 latencies
- **Throughput Metrics**: Operations per second
- **Warmup Support**: Configurable warmup runs before measurement
- **CSV Export**: Export results for external analysis

**Available in all SDKs:**
- Java — `sdk/java/src/main/java/io/substrait/compliance/benchmark/`
- Python — `sdk/python/substrait_compliance/benchmark/benchmark_runner.py`
- Rust — `sdk/rust/src/benchmark/mod.rs`
- Go — `sdk/go/benchmark/benchmark_runner.go`
- C++ — `sdk/cpp/include/substrait_compliance/benchmark_runner.hpp`
- TypeScript — `sdk/typescript/src/benchmark/BenchmarkRunner.ts`
- C# — `sdk/csharp/Substrait.Compliance/Benchmark/BenchmarkRunner.cs`
- Scala — `sdk/scala/src/main/scala/io/substrait/compliance/benchmark/`

See [docs/PERFORMANCE_BENCHMARKING.md](../docs/PERFORMANCE_BENCHMARKING.md) for detailed usage and output format specifications.
