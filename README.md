# Substrait Compliance Framework

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?logo=github-actions&logoColor=white)](https://github.com/IBM/substrait-compliance/actions)
[![Release](https://img.shields.io/github/v/release/IBM/substrait-compliance?include_prereleases)](https://github.com/IBM/substrait-compliance/releases)
[![Contributors](https://img.shields.io/github/contributors/IBM/substrait-compliance)](https://github.com/IBM/substrait-compliance/graphs/contributors)

> **A decentralized compliance testing framework for Substrait implementations**

Enable database engines to self-certify their Substrait compliance through standardized interfaces, pre-packaged test suites, and automated reporting.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Prerequisites](#-prerequisites)
- [5-Minute Quick Start](#-5-minute-quick-start)
- [Test Your Own Engine](#-test-your-own-engine)
- [Test Suites](#-test-suites)
- [Repository Structure](#-repository-structure)
- [Architecture](#️-architecture)
- [SDK Features](#-sdk-features)
- [CI/CD Integration](#-cicd-integration)
- [REST API](#-rest-api)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [Key Documentation](#-key-documentation)

---

## 🎯 Overview

The Substrait Compliance Framework transforms how query engines validate their Substrait support. Instead of centralized testing, engines **self-certify** by:

1. **Implementing standard interfaces** — `ComplianceEngine` trait/interface
2. **Running available test suites** — TPC-H (22 queries), TPC-DS (99 queries), and function test suites
3. **Generating compliance reports** — JSON format with pass/fail results
4. **Publishing results** — Optional leaderboard/report publication

### Key Features

- 🔄 **Decentralized Testing** — Engines test themselves; no central bottleneck
- 🌐 **Multi-Language SDKs** — Java, Python, Rust, C++, Go, TypeScript/JavaScript, C#/.NET, and Scala at varying maturity levels
- 📦 **Pre-packaged Test Suites** — TPC-H, TPC-DS, and function test suites included in-repo
- ⚡ **Performance Benchmarking** — Benchmarking components included across several SDKs
- 🤖 **Automated CI/CD** — GitHub Actions workflows for validation, release, and automation
- 🌐 **REST API** — Spring Boot API implementation for programmatic access (pre-release)
- 📊 **Interactive Demo** — Live dashboard with mock engines, query drill-down, and complexity filtering
- 🏆 **Leaderboard Support** — Report aggregation and leaderboard generation

---

## 🔧 Prerequisites

Before you begin, ensure you have:

- ✅ **Java 17+** — Check with `java -version`
- ✅ **Python 3.8+** — Check with `python3 --version` (optional, for Python SDK/demo)
- ✅ **Rust 1.70+** — Check with `rustc --version` (optional, for Rust SDK)
- ✅ **Git** — For cloning the repository
- ✅ **Web Browser** — For viewing the interactive dashboard

**Quick Verification:**
```bash
java -version     # Should show 17 or higher
python3 --version # Should show 3.8 or higher
git --version     # Any recent version
```

---

## 🔧 SDK Notes

This repository includes SDKs and examples across multiple languages. Support level, feature parity, and verification status may vary by SDK and release.

Recent documentation and compatibility updates include:

- **Rust SDK**: Updated [`benchmark_example.rs`](sdk/rust/examples/benchmark_example.rs) to use the current instance-method benchmark pattern
- **Go SDK**: Cleaned up [`go.mod`](sdk/go/go.mod) and refreshed module metadata
- **Scala SDK**: Introduced [`EngineResult`](sdk/scala/src/main/scala/io/substrait/compliance/EngineResult.scala) to separate engine execution from test results
- **C++ SDK**: Expanded setup guidance in [`README.md`](sdk/cpp/README.md)
- **TypeScript SDK**: Improved package configuration and troubleshooting guidance in [`README.md`](sdk/typescript/README.md)

For current build and test status, rely on repository CI results and per-SDK documentation.

---

## 🚀 5-Minute Quick Start

Experience the framework before integrating your own engine:

```bash
# 1. Clone the repository
git clone https://github.com/IBM/substrait-compliance.git
cd substrait-compliance

# 2. Run the demo (generates mock compliance reports)
cd demo
./runner/run-simple-demo.sh

# Or run the enhanced demo with 10-phase framework
./runner/run-enhanced-demo.sh

# 3. View the interactive dashboard
cd dashboard
python3 -m http.server 8080

# 4. Open in your browser: http://localhost:8080
```

### Cold-start validation

Use this sequence to verify the public repo from a fresh clone:

```bash
# From repository root
cd demo
./runner/run-simple-demo.sh
./runner/run-enhanced-demo.sh
cd ..
```

For full SDK build verification, use the ordered checklist in [`docs/SDK_VERIFICATION.md`](docs/SDK_VERIFICATION.md).

**Expected output (abridged):**
```text
📦 Loading TPC-H test suite...
✅ Loaded test suite: tpch
   Total test cases: 22

🔧 Testing: MockDB v1.0.0
   ✅ Passed: 22  ❌ Failed: 0   📊 Pass Rate: 100.0%

🔧 Testing: FastDB v2.5.0
   ✅ Passed: 21  ❌ Failed: 1   📊 Pass Rate: 95.5%

🔧 Testing: CloudDB v3.1.0
   ✅ Passed: 17  ❌ Failed: 5   📊 Pass Rate: 77.3%

🔧 Testing: DuckDB v0.10.0
   ✅ Passed: 14  ⏭️  Skipped: 8  📊 Pass Rate: 63.6%

🔧 Testing: PostgreSQL v16.0
   ✅ Passed: 14  ⏭️  Skipped: 8  📊 Pass Rate: 63.6%

🥇 MockDB       1.0.0   100.0%  🟢 VERIFIED
🥈 FastDB       2.5.0    95.5%  🟢 VERIFIED
🥉 CloudDB      3.1.0    77.3%  🔵 EDGE
🥉 DuckDB       0.10.0   63.6%  🟡 BASIC
🥉 PostgreSQL   16.0     63.6%  🟡 BASIC

✅ Demo completed successfully!
```

**What You'll See in the Dashboard:**
- 🥇 **Leaderboard** — Rankings with medals and tier badges (VERIFIED / EDGE / BASIC)
- 📊 **Visual Charts** — Bar chart and doughnut chart showing pass rates
- 📈 **Detailed Statistics** — Per-engine breakdowns and test case results
- 🔍 **Query Drill-Down** — Click any engine to see detailed query-level results
- 🏷️ **Complexity Filtering** — Filter tests by SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX

**Troubleshooting:**
- If port 8080 is in use: `python3 -m http.server 8081`
- If permission denied: `chmod +x demo/runner/run-simple-demo.sh`
- Dashboard shows "Failed to load": ensure the demo ran successfully first

---

## 🔧 Test Your Own Engine

### Step 1: Choose Your SDK

<details>
<summary><b>Java SDK (Recommended — Most Complete)</b></summary>

**Option A — Depend on the published artifact (recommended)**

Add to your `build.gradle`:
```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/IBM/substrait-compliance")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key")  ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'io.substrait:substrait-compliance:0.1.0'
}
```

The package is published to [GitHub Packages](https://github.com/IBM/substrait-compliance/packages) on every push to `main`. A read-only GitHub token (personal or `GITHUB_TOKEN` in Actions) is sufficient to consume it.

**Option B — Build from source**

```bash
cd sdk/java
./gradlew build

# Run tests to verify
./gradlew test

# Expected output:
# BUILD SUCCESSFUL
# 12 tests passed ✅
```

**Verify installation:**
```bash
ls -la build/libs/
# substrait-compliance-0.1.0.jar       (library jar)
# substrait-compliance-0.1.0-all.jar   (fat jar with all dependencies)
# substrait-compliance-0.1.0-sources.jar
```
</details>

<details>
<summary><b>Python SDK (Pythonic Interface)</b></summary>

```bash
# Install the SDK
cd sdk/python
pip install -e .

# Run tests to verify
pytest

# Expected output:
# 8 passed ✅
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
mkdir build && cd build
cmake ..
make

# Run tests to verify
ctest --output-on-failure

# Expected output:
# 100% tests passed ✅
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

# Expected output:
# PASS
# ok      github.com/IBM/substrait-compliance/sdk/go
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

# Expected output:
# PASS  tests/table-data.test.ts
# Test Suites: 1 passed, 1 total
# Tests:       12 passed, 12 total
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

# Expected output:
# Passed!  - Failed:     0, Passed:    12, Skipped:     0, Total:    12
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

# Expected output:
# [info] ComplianceEngineSpec:
# [info] - should return engine information
# [info] - should return engine capabilities
# [info] All tests passed ✅
```

**Verify Installation:**
```bash
ls -la target/scala-2.13/
# Should see: substrait-compliance_2.13-1.0.0.jar
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

# Expected output:
# test result: ok. 6 passed ✅
```

**Verify Installation:**
```bash
ls -la target/release/
# Should see: libsubstrait_compliance.rlib
```
</details>

### Step 2: Implement ComplianceEngine

#### Interface quick-reference

Every SDK exposes the same four required operations plus optional lifecycle hooks. The table below shows the exact names and signatures by language — use it as your checklist before writing any code.

| | Java | Python | TypeScript | Rust | Go | C# |
|---|---|---|---|---|---|---|
| **Plan arg type** | `Plan` (parsed protobuf) | `bytes` | `Uint8Array` | `&[u8]` | `[]byte` | `byte[]` |
| **Execution model** | Synchronous | Synchronous | `async`/`Promise` | Synchronous | `context.Context` | `async Task` |
| **Get metadata** | `getEngineInfo()` | `get_info()` | `getInfo()` | `get_info()` | `GetInfo()` | `GetInfo()` |
| **Get capabilities** | `getCapabilities()` | `get_capabilities()` | `getCapabilities()` | `get_capabilities()` | `GetCapabilities()` | `GetCapabilities()` |
| **Execute plan** | `executePlan(Plan, Map<String,TableData>)` | `execute_plan(bytes, Dict[str,TableData])` | `executePlan(Uint8Array, Map<string,TableData>)` | `execute_plan(&[u8], &HashMap<String,TableData>)` | `ExecutePlan(ctx, []byte, map[string]*TableData)` | `ExecutePlanAsync(byte[], IReadOnlyDictionary<string,TableData>)` |
| **Validate plan** | `validatePlan(Plan)` | `validate_plan(bytes)` | `validatePlan(Uint8Array)` | `validate_plan(&[u8])` | `ValidatePlan(ctx, []byte)` | `ValidatePlanAsync(byte[])` |
| **Lifecycle init** | `initialize()` *(default no-op)* | — | `initialize?()` *(optional)* | — | `Initialize(ctx)` | `InitializeAsync()` *(default no-op)* |
| **Lifecycle cleanup** | `cleanup()` *(default no-op)* | — | `shutdown?()` *(optional)* | — | `Shutdown(ctx)` | `ShutdownAsync()` *(default no-op)* |

**Key differences to be aware of:**

- **Java** receives a fully-parsed `io.substrait.proto.Plan` object — the SDK deserializes the protobuf before calling your engine. All other SDKs receive raw bytes and you deserialize inside `execute_plan` / `executePlan`.
- **TypeScript** runner supports optional parallel execution (`RunnerOptions.parallel`). All other SDK runners execute tests sequentially.
- **Go** passes a `context.Context` through every call — use it for cancellation and deadlines.
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

### Step 3: Run Against Test Suites

**Java example (DuckDB integration):**
```bash
cd examples/duckdb-java
./gradlew run

# Expected output:
# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 20/22 (90.9%)
# Report saved: output/duckdb-report.json
```

**Python example (DataFusion integration):**
```bash
cd examples/datafusion-python
python datafusion_compliance.py

# Expected output:
# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 21/22 (95.5%)
# Report saved: output/datafusion-report.json
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

**See [examples/](examples/) for complete implementations.**

### Step 4: Verify Results

```bash
# Check generated reports
ls -la output/

# View report contents (requires jq)
cat output/your-engine-report.json | jq .

# Key fields in report:
# - engineInfo: { name, version, vendor }
# - testResults: [ { testId, status, executionTime } ]
# - summary: { total, passed, failed, skipped, passRate }
# - timestamp: ISO 8601 format
```

### Step 5: Integrate with CI/CD (Optional)

Copy the template workflow to your repository:

```bash
cp .github/workflows/engine-compliance-template.yml \
   your-engine/.github/workflows/substrait-compliance.yml
```

Customize the workflow for your engine's build process (engine name, build commands, compliance threshold — default 80%).

See [.github/workflows/README.md](.github/workflows/README.md) for details.

---

## 🧪 Test Suites

> **📚 Detailed Documentation:**
> - [test-suites/functions/README.md](test-suites/functions/README.md) — Function tests (140 files, 5,041 test assertions, 14 categories)
> - [test-suites/tpch/README.md](test-suites/tpch/README.md) — TPC-H benchmark (22 queries)
> - [test-suites/tpcds/README.md](test-suites/tpcds/README.md) — TPC-DS benchmark (99 queries)

### TPC-H Benchmark (22 Queries)

Complete TPC-H benchmark at scale factor 0.01. **Result correctness is fully verifiable** — all 22 expected output files are committed and compared on every test run.

| Component | Details |
|-----------|---------|
| **Queries** | 22 (Q1–Q22) |
| **Data Files** | 8 CSV files |
| **Total Rows** | 86,805 |
| **Data Size** | ~10.6 MB |
| **Plan Formats** | Binary (.bin) + JSON (.json) |
| **Complexity Levels** | SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX |
| **Expected Outputs** | ✅ All 22 present (`expected/q01.csv` – `q22.csv`) — [typed-header format](test-suites/tpch/README.md#expected-output-file-format) |

**Query Complexity Breakdown:**
- **SIMPLE** (3 queries): Q1, Q6, Q14 — Single table, basic aggregations
- **MEDIUM** (7 queries): Q3, Q4, Q10, Q12, Q13, Q16, Q19 — Joins, grouping
- **COMPLEX** (8 queries): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22 — Multiple joins, subqueries
- **VERY_COMPLEX** (4 queries): Q2, Q8, Q20, Q21 — Nested subqueries, complex joins

```bash
# Navigate to TPC-H test suite
cd test-suites/tpch

# View test suite metadata
cat metadata.yaml

# Inspect test data (8 CSV files: customer, lineitem, nation, orders,
# part, partsupp, region, supplier)
ls -la data/

# View Substrait plans (44 files: q01.bin, q01.json … q22.bin, q22.json)
ls -la plans/

# View reference expected outputs (all 22 queries)
ls -la expected/
```

**Running TPC-H tests (Java):**
```java
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite tpchSuite = loader.load("test-suites/tpch/metadata.yaml");

ComplianceRunner runner = new ComplianceRunner(myEngine);
ComplianceReport report = runner.runTestSuite(tpchSuite);

System.out.println("TPC-H Results:");
System.out.println("  Total Queries: " + report.getTotalCount());
System.out.println("  Passed: " + report.getPassedCount());
System.out.println("  Pass Rate: " + report.getPassRate() + "%");
```

### TPC-DS Benchmark (99 Queries)

> ⚠️ **Structural validation only — result correctness not yet verifiable.**
> The `expected/` directory contains no output files. Running TPC-DS tests
> currently verifies that your engine can parse and execute the Substrait plans
> without crashing; it does **not** verify that the results are correct. Tests
> without expected output are reported as `SKIPPED` by the runner. Expected
> outputs will be added in a future release (see [item 7 in ROADMAP.md](ROADMAP.md)).

TPC-DS (Decision Support) benchmark plans and data for complex analytical workloads:

| Component | Details |
|-----------|---------|
| **Queries** | 99 (query01.sql – query99.sql) |
| **Substrait Plans** | 194 (97 JSON + 97 binary) |
| **Data Tables** | 24 CSV files (multi-channel retail schema) |
| **Plan Formats** | Binary (.bin) + JSON (.json) |
| **Expected Outputs** | ❌ Not yet available — see [typed-header format](test-suites/tpch/README.md#expected-output-file-format) when contributing |

**Key Query Categories:**
- Customer behavior and profitability analysis
- Multi-channel retail analytics (store, catalog, web)
- Sales and returns analysis
- Inventory management and marketing effectiveness

```bash
cd test-suites/tpcds
cat metadata.yaml
ls -la data/      # 24 CSV files
ls -la plans/     # 194 Substrait plan files
ls -la expected/  # empty — no expected outputs yet
```

> **📚 See [test-suites/tpcds/README.md](test-suites/tpcds/README.md) for complete TPC-DS documentation**

### Function Tests (140 Files, 5,041 Assertions)

The function test suite covers 14 semantic categories with 5,041 individual test assertions:

```
aggregate/     (6 files)  — count, avg, stddev, variance, etc.
arithmetic/    (44 files) — add, multiply, sqrt, trigonometry, etc.
array/         (4 files)  — array operations
cast/          (1 file)   — cast, try_cast
comparison/    (19 files) — equal, gt, lt, between, coalesce, etc.
conditional/   (2 files)  — case, if
datetime/      (12 files) — date_trunc, date_diff, extract, etc.
geospatial/    (4 files)  — st_area, st_distance, st_contains, st_intersects
json/          (2 files)  — json_extract, json_parse
map/           (3 files)  — map operations
set/           (3 files)  — union, intersect, except
string/        (27 files) — concat, substring, regexp, etc.
struct/        (2 files)  — struct operations
window/        (7 files)  — row_number, rank, lag, lead, etc.
```

**Running Function Tests (Python):**
```python
from substrait_compliance import YamlTestSuiteLoader, ComplianceRunner

loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/functions/arithmetic/metadata.yaml")
runner = ComplianceRunner(my_engine)
report = runner.run_test_suite(suite)
print(f"Arithmetic Functions: {report.passed_count}/{report.total_count} passed")
```

### Running Specific Tests

**Single TPC-H query (Java):**
```java
TestSuite suite = loader.load("test-suites/tpch/metadata.yaml");

TestCase q1 = suite.getTestCases().stream()
    .filter(tc -> tc.getId().equals("q01"))
    .findFirst()
    .orElseThrow();

ComplianceResult result = myEngine.executePlan(q1.getPlan(), q1.getInputData());
System.out.println("Q1 Status: " + result.getStatus());
```

**Filter by complexity (Java):**
```java
List<TestCase> simpleTests = suite.getTestCases().stream()
    .filter(tc -> tc.getComplexity() == Complexity.SIMPLE)
    .collect(Collectors.toList());
```

---

## 📦 Repository Structure

```
substrait-compliance/
├── 🎯 demo/                       # ⭐ START HERE — Interactive demo
│   ├── runner/
│   │   ├── run-simple-demo.sh     # ⭐ Run this first!
│   │   ├── run-demo.sh            # Full demo with dashboard
│   │   ├── run-enhanced-demo.sh   # Enhanced demo runner
│   │   └── run-function-tests.sh  # Function test runner
│   ├── dashboard/                 # Visual results dashboard (HTML/JS)
│   ├── engines/                   # Example mock engines (Java)
│   │   ├── MockDBEngine.java
│   │   ├── FastDBEngine.java
│   │   ├── CloudDBEngine.java
│   │   ├── DuckDBEngine.java
│   │   └── PostgreSQLEngine.java
│   └── output/                    # Generated reports
├── 📚 sdk/                        # Multi-language SDKs (8 languages)
│   ├── java/                      # ⭐ Most complete — JDK 17+, Gradle
│   ├── python/                    # Python 3.8+, pip/setuptools
│   ├── rust/                      # Rust 2021 edition, Cargo
│   ├── go/                        # Go 1.21+, modules
│   ├── cpp/                       # C++17, CMake 3.15+
│   ├── typescript/                # TypeScript/Node.js, npm
│   ├── csharp/                    # C#/.NET 10+, dotnet
│   └── scala/                     # Scala 2.13, sbt
├── 🧪 test-suites/                # Test suites
│   ├── functions/                 # 140 function test files, 5,041 assertions (14 categories)
│   ├── tpch/                      # TPC-H (22 queries, 8 data files, 44 plans)
│   └── tpcds/                     # TPC-DS (99 queries, 24 data files, 198 plans)
├── 💡 examples/                   # Real-world integration examples
│   ├── datafusion-python/         # DataFusion integration (Python)
│   ├── datafusion-rust/           # DataFusion integration (Rust)
│   ├── duckdb-cpp/                # DuckDB integration (C++)
│   ├── duckdb-java/               # DuckDB integration (Java)
│   └── velox-cpp/                 # Velox integration (C++)
├── 🌐 api/                        # REST API (Spring Boot)
│   ├── src/                       # API source code
│   ├── docker-compose.yml         # Container orchestration
│   └── build.gradle               # Gradle build
├── 🤖 .github/workflows/          # CI/CD automation
│   ├── engine-compliance-template.yml  # ⭐ Template for your engine
│   ├── sdk-build-test.yml         # Tests all SDKs on every commit
│   ├── test-suite-validation.yml  # Validates test suite integrity
│   └── ...                        # API build, deploy, release workflows
├── 🔧 scripts/                    # Automation and quality tools
│   ├── generate_leaderboard.py    # Leaderboard generator
│   ├── quality_checker.py         # Test quality checker
│   ├── test_enhancer.py           # Test enhancement tool
│   └── verify_sdk_builds.sh       # SDK build verification
└── 📖 docs/                       # Documentation
    ├── REST_API_GUIDE.md
    ├── REST_API_ARCHITECTURE.md
    ├── DEPLOYMENT_GUIDE.md
    ├── PERFORMANCE_BENCHMARKING.md
    ├── SDK_VERIFICATION.md
    └── SUBSTRAIT_COMPLIANCE_FRAMEWORK_GUIDE.md
```

**Key Directories for Developers:**
- 🎯 **`demo/`** — Run the interactive demo first to see the framework in action
- 📚 **`sdk/`** — 8 SDKs: C++, C#, Go, Java, Python, Rust, Scala, TypeScript
- 🧪 **`test-suites/`** — 140 function test files (5,041 assertions) + TPC-H + TPC-DS benchmarks
- 💡 **`examples/`** — Real integration examples with DuckDB, DataFusion, and Velox

---

## 🏗️ Architecture

### Decentralized Compliance Model

```
┌─────────────────────────────────────────────────────────────┐
│         Compliance Framework (Standards Authority)           │
│  • SDKs (8 languages)                                        │
│  • Test Suites (TPC-H, TPC-DS, Functions)                    │
│  • Documentation & CI/CD Templates                           │
└─────────────────────────────────────────────────────────────┘
                          ↓ downloads & implements
┌─────────────────────────────────────────────────────────────┐
│              Query Engines (Self-Certify)                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐             │
│  │  DuckDB    │  │ DataFusion │  │   Spark    │  ...        │
│  │  Java SDK  │  │ Python SDK │  │  Java SDK  │             │
│  └────────────┘  └────────────┘  └────────────┘             │
└─────────────────────────────────────────────────────────────┘
                          ↓ reports results (optional)
┌─────────────────────────────────────────────────────────────┐
│         Public Compliance Leaderboard (GitHub Pages)         │
│  • Rankings by pass rate                                     │
│  • Detailed results per engine                               │
│  • Historical trends                                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 SDK Features

### Java SDK
- **JDK**: 17+  |  **Build**: Gradle  |  **Tests**: 12 unit tests
- **Key Classes**: `ComplianceEngine`, `ComplianceRunner`, `YamlTestSuiteLoader`, `EngineCapabilities`
- **Docs**: [sdk/java/README.md](sdk/java/README.md)

### Python SDK
- **Python**: 3.8+  |  **Build**: setuptools  |  **Tests**: 8 integration tests
- **Key Modules**: `engine.py`, `runner.py`, `loader.py`, `result.py`
- **Docs**: [sdk/python/README.md](sdk/python/README.md)

### C++ SDK
- **Standard**: C++17  |  **Build**: CMake 3.15+  |  **Tests**: Google Test
- **Key Headers**: `engine.h`, `runner.h`, `loader.h`, `result.h`, `comparator.h`
- **Features**: Smart pointers, zero-copy operations, header-only option, cross-platform
- **Docs**: [sdk/cpp/README.md](sdk/cpp/README.md)

### Go SDK
- **Go**: 1.21+  |  **Build**: Go modules  |  **Features**: goroutines, context support, zero external dependencies
- **Key Files**: `engine.go`, `runner.go`, `loader.go`, `result.go`, `table_data.go`

### Rust SDK
- **Edition**: 2021  |  **Build**: Cargo  |  **Tests**: 6 integration tests
- **Key Modules**: `engine.rs`, `runner.rs`, `loader.rs`, `result.rs`

### TypeScript SDK
- **Runtime**: Node.js / Browser  |  **Build**: npm  |  **Tests**: Jest
- **Docs**: [sdk/typescript/README.md](sdk/typescript/README.md)

### C# SDK
- **Runtime**: .NET 10+  |  **Build**: dotnet  |  **Tests**: 12 unit tests
- **Docs**: [sdk/csharp/README.md](sdk/csharp/README.md)

### Scala SDK
- **Scala**: 2.13  |  **Build**: sbt  |  **JVM**: 17+
- **Key Type**: [`EngineResult`](sdk/scala/src/main/scala/io/substrait/compliance/EngineResult.scala) separates engine execution from test results

---

## ⚡ Performance Benchmarking

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

See [docs/PERFORMANCE_BENCHMARKING.md](docs/PERFORMANCE_BENCHMARKING.md) for detailed usage and output format specifications.

---

## 🤖 CI/CD Integration

### Automated Workflows

- **`sdk-build-test.yml`** — Tests all SDKs on every commit
- **`test-suite-validation.yml`** — Validates test suite integrity
- **`api-build-test.yml`** — API build and test
- **`api-container-build.yml`** — Multi-platform container images
- **`api-deploy-staging.yml`** / **`api-deploy-production.yml`** — Staged deployments
- **`release-publish.yml`** — Automated versioning and publishing

### For Engine Developers

Copy the template workflow to enable automated compliance testing in your own repository:

```bash
cp .github/workflows/engine-compliance-template.yml \
   .github/workflows/substrait-compliance.yml
```

Customize: engine name/version, build commands, test execution, compliance threshold (default 80%).

See [.github/workflows/README.md](.github/workflows/README.md) for details.

---

## 🔍 Troubleshooting

### Common Issues and Solutions

#### "Permission denied" on shell scripts
```bash
chmod +x demo/runner/run-simple-demo.sh
chmod +x demo/verify-setup.sh
./demo/runner/run-simple-demo.sh
```

#### "Java version not supported"
```bash
java -version   # Should show 17 or higher

# macOS (Homebrew)
brew install openjdk@17

# Ubuntu/Debian
sudo apt update && sudo apt install openjdk-17-jdk

# Windows — download from https://adoptium.net/
```

#### "Dashboard shows 'Failed to load data'"
```bash
# Run the demo first to generate data
cd demo
./runner/run-simple-demo.sh

# Verify files exist
ls -la demo/output/leaderboard.json
ls -la demo/dashboard/data/leaderboard.json

# Serve over HTTP (not file://)
cd demo/dashboard
python3 -m http.server 8080
# Open http://localhost:8080
```

#### "Port 8080 already in use"
```bash
python3 -m http.server 8081   # or 9000, 3000, etc.

# macOS/Linux: kill the blocking process
lsof -ti:8080 | xargs kill -9
```

#### "SDK build fails"
```bash
# Java: clean rebuild
cd sdk/java
./gradlew clean build --refresh-dependencies

# Python: reinstall
cd sdk/python
pip uninstall substrait-compliance -y
pip install --upgrade pip setuptools wheel
pip install -e .

# Rust: clean rebuild
cd sdk/rust
cargo clean && cargo build --release
```

#### "Test data not found"
```bash
ls -la test-suites/tpch/data/   # Should show 8 CSV files
ls -la test-suites/tpch/plans/  # Should show 44 plan files

# If files are missing
git pull origin main
```

#### "Module not found" errors (Python)
```bash
cd sdk/python
pip install -e ".[dev]"
python3 -c "import substrait_compliance; print('OK')"
```

#### "Gradle build fails with 'Could not resolve dependencies'"
```bash
rm -rf ~/.gradle/caches/
cd sdk/java
./gradlew clean build --refresh-dependencies
```

#### "Dashboard shows but no data appears"
```bash
# Check browser console (F12) for errors
# Most common causes:
# 1. Opened as file:// — use a web server instead
cd demo/dashboard && python3 -m http.server 8080

# 2. JSON file malformed
cat demo/dashboard/data/leaderboard.json | python3 -m json.tool
```

### Getting Help

1. **Check Documentation** — [demo/README.md](demo/README.md), [examples/README.md](examples/README.md)
2. **Review Examples** — Working implementations in `examples/` and `demo/engines/`
3. **Enable Debug Logging** (Java): `System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")`
4. **Report Issues** — [GitHub Issues](https://github.com/IBM/substrait-compliance/issues) · [GitHub Discussions](https://github.com/IBM/substrait-compliance/discussions)

---

## ✅ Verification Checklist

```bash
# Automated verification
cd demo && ./verify-setup.sh
```

**Manual Steps:**

```bash
# 1. Prerequisites
java -version    # 17+
python3 --version # 3.8+

# 2. Repository structure
ls -la demo/ sdk/ test-suites/ examples/
ls -la test-suites/tpch/data/*.csv    # 8 files
ls -la test-suites/tpch/plans/        # 44 files

# 3. Demo execution
cd demo && ./runner/run-simple-demo.sh
# Expected: 5 engines tested; summary shows 100%/95.5%/77.3%/63.6%/63.6%; "Demo completed successfully!"

# 4. Report generation
ls -la demo/output/   # mockdb/fastdb/clouddb/duckdb/postgresql-report.json + leaderboard.json
cat demo/output/leaderboard.json | python3 -m json.tool

# 5. Dashboard
cd demo/dashboard && python3 -m http.server 8080 &
curl -s http://localhost:8080 | grep -q "Substrait Compliance" && echo "OK"

# 6. SDK build (pick at least one)
cd sdk/java && ./gradlew build test    # BUILD SUCCESSFUL, 12 tests
cd sdk/python && pip install -e . && pytest  # 8 passed
cd sdk/rust && cargo build --release && cargo test  # 6 passed
```

**Success Criteria:**
- ✅ All prerequisites installed (Java 17+, Python 3.8+)
- ✅ Demo runs without errors; reports generated in `demo/output/`
- ✅ Dashboard accessible at `http://localhost:8080`; shows 5 engines
- ✅ At least one SDK builds and its tests pass

---

## 📈 Compliance Leaderboard

Run the demo to generate leaderboard data from the five demo engines, then view it in the interactive dashboard. Results include fidelity tier badges (VERIFIED ≥ 90% / EDGE ≥ 70% / BASIC ≥ 50%) assigned from TPC-H pass rates.

Generate and view the leaderboard:
```bash
cd demo && ./runner/run-simple-demo.sh
cd dashboard && python3 -m http.server 8080
# Open http://localhost:8080
```

To generate a standalone leaderboard JSON from existing reports:
```bash
python3 scripts/generate_leaderboard.py
```

---

## 🌐 REST API

A Spring Boot REST API provides programmatic access to compliance results.

**Features:** JWT authentication, report submission, data querying with filtering/pagination, webhooks, rate limiting, caching.

```bash
# Start with Docker Compose
cd api
docker-compose up -d postgres

# In a separate terminal, start the API application
./gradlew bootRun

# Access the API
curl http://localhost:8080/api/v1/leaderboard
```

**Documentation:**
- [docs/REST_API_GUIDE.md](docs/REST_API_GUIDE.md) — Complete API reference
- [docs/REST_API_ARCHITECTURE.md](docs/REST_API_ARCHITECTURE.md) — Architecture and design
- [docs/API_IMPLEMENTATION.md](docs/API_IMPLEMENTATION.md) — Implementation guide
- [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) — Deployment instructions
- [api/README.md](api/README.md) — API module readme

> **Note:** The REST API is pre-release. See [api/README.md](api/README.md) for current status.

---

## 📖 Key Documentation

### 🎯 Getting Started
- [README.md](README.md) — This file
- [demo/README.md](demo/README.md) — Interactive demo guide
- [demo/DASHBOARD_GUIDE.md](demo/DASHBOARD_GUIDE.md) — Dashboard features and navigation
- [demo/TROUBLESHOOTING.md](demo/TROUBLESHOOTING.md) — Demo troubleshooting
- [examples/README.md](examples/README.md) — Integration examples

### 🧪 Test Suites
- [test-suites/functions/README.md](test-suites/functions/README.md) — Function tests
- [test-suites/tpch/README.md](test-suites/tpch/README.md) — TPC-H benchmark
- [test-suites/tpcds/README.md](test-suites/tpcds/README.md) — TPC-DS benchmark

### 📚 SDK Documentation
- [sdk/java/README.md](sdk/java/README.md) — Java SDK
- [sdk/python/README.md](sdk/python/README.md) — Python SDK
- [sdk/rust/README.md](sdk/rust/README.md) — Rust SDK
- [sdk/cpp/README.md](sdk/cpp/README.md) — C++ SDK
- [sdk/typescript/README.md](sdk/typescript/README.md) — TypeScript SDK
- [sdk/csharp/README.md](sdk/csharp/README.md) — C# SDK

### 🔧 Development & Quality
- [docs/PERFORMANCE_BENCHMARKING.md](docs/PERFORMANCE_BENCHMARKING.md) — Benchmarking guide
- [docs/SDK_VERIFICATION.md](docs/SDK_VERIFICATION.md) — SDK verification guide
- [docs/SUBSTRAIT_COMPLIANCE_FRAMEWORK_GUIDE.md](docs/SUBSTRAIT_COMPLIANCE_FRAMEWORK_GUIDE.md) — Comprehensive technical guide
- [scripts/README.md](scripts/README.md) — Scripts documentation
- [scripts/TEST_ENHANCEMENT_GUIDE.md](scripts/TEST_ENHANCEMENT_GUIDE.md) — Test enhancement workflow
- [.github/workflows/README.md](.github/workflows/README.md) — CI/CD workflow documentation
- [CHANGELOG.md](CHANGELOG.md) — Version history

### 🤝 Contributing
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contribution guidelines
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — Community code of conduct
- [GOVERNANCE.md](GOVERNANCE.md) — Project governance

---

## 🛠️ Development

### Building from Source

**Java SDK:**
```bash
cd sdk/java && ./gradlew build test
```

**Python SDK:**
```bash
cd sdk/python && pip install -e .[dev] && pytest
```

**Rust SDK:**
```bash
cd sdk/rust && cargo build --release && cargo test
```

### Running Examples

**DuckDB (Java):**
```bash
cd examples/duckdb-java && ./gradlew run
```

**DataFusion (Python):**
```bash
cd examples/datafusion-python && python datafusion_compliance.py
```

**DuckDB (C++):**
```bash
cd examples/duckdb-cpp && mkdir build && cd build && cmake .. && make && ./example
```

**DataFusion (Rust):**
```bash
cd examples/datafusion-rust && cargo run
```

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Areas for Contribution

- Additional test suites and test cases
- New engine integration examples
- SDK improvements and new language SDKs
- Documentation enhancements
- Bug fixes and optimizations

---

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| **SDKs** | 8 (Java, Python, Rust, Go, C++, TypeScript, C#, Scala) |
| **Test Suites** | 3 (TPC-H, TPC-DS, Functions) |
| **Function Test Files** | 140 (14 categories) |
| **Function Test Assertions** | 5,041 individual test cases |
| **TPC-H Queries** | 22 |
| **TPC-DS Queries** | 99 |
| **TPC-H Data Rows** | 86,805 (scale factor 0.01) |
| **TPC-DS Data Tables** | 24 |
| **CI/CD Workflows** | 12 |
| **Example Implementations** | 5 (DuckDB Java/C++, DataFusion Python/Rust, Velox C++) |
| **REST API Endpoints** | 10+ |

---

## 📜 License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Substrait Community** — For the query plan specification
- **TPC Organization** — For the TPC-H and TPC-DS benchmark queries
- **Engine Developers** — For implementing Substrait support

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/IBM/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.com/IBM/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)
- **Mailing List**: substrait-dev@googlegroups.com
- **Slack**: #substrait-compliance (join at [substrait.io](https://substrait.io/))

---

## 🗺️ Roadmap

- [x] REST API infrastructure with Spring Boot
- [x] Comprehensive CI/CD workflows
- [x] Interactive demo system with dashboard
- [x] Multi-platform container builds
- [x] TPC-DS benchmark (99 queries)
- [ ] Performance benchmarking CI integration
- [ ] Compliance badges
- [ ] Historical trend analysis
- [ ] Multi-version testing

---

**Made with ❤️ for the Substrait Community**
