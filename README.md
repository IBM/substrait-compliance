# Substrait Compliance Framework

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?logo=github-actions&logoColor=white)](https://github.com/substrait-io/substrait-compliance/actions)
[![Release](https://img.shields.io/github/v/release/substrait-io/substrait-compliance)](https://github.com/substrait-io/substrait-compliance/releases)
[![Contributors](https://img.shields.io/github/contributors/substrait-io/substrait-compliance)](https://github.com/substrait-io/substrait-compliance/graphs/contributors)

> **A decentralized compliance testing framework for Substrait implementations**

Enable database engines to self-certify their Substrait compliance through standardized interfaces, pre-packaged test suites, and automated reporting.

---

## 📑 Table of Contents

- [Prerequisites](#-prerequisites)
- [Overview](#-overview)
- [5-Minute Quick Start](#-5-minute-quick-start)
- [Test Suites](#-test-suites)
  - [TPC-H Benchmark](#tpc-h-benchmark-22-queries)
  - [TPC-DS Benchmark](#tpc-ds-benchmark-99-queries)
  - [Function Tests](#function-tests-279-files-4000-test-cases)
- [Integration Guide](#-integration-guide)
- [Repository Structure](#-repository-structure)
- [CI/CD Integration](#-cicd-integration)
- [REST API](#-rest-api)
- [Troubleshooting](#-troubleshooting)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [Key Documentation](#-key-documentation)

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

---

## 🔧 Prerequisites

Before you begin, ensure you have:

- ✅ **Java 17+** - Check with `java -version`
- ✅ **Python 3.8+** - Check with `python3 --version` (optional, for Python SDK/demo)
- ✅ **Rust 1.70+** - Check with `rustc --version` (optional, for Rust SDK)
- ✅ **Git** - For cloning the repository
- ✅ **Web Browser** - For viewing the interactive dashboard

**Quick Verification:**
```bash
java -version     # Should show 17 or higher
python3 --version # Should show 3.8 or higher
git --version     # Any recent version
```

---

## 🎯 Overview

The Substrait Compliance Framework transforms how query engines validate their Substrait support. Instead of centralized testing, engines **self-certify** by:

1. **Implementing standard interfaces** - ComplianceEngine trait/interface
2. **Running available test suites** - TPC-H (22 queries) + function test suites, with additional suites and broader verification still in progress
3. **Generating compliance reports** - JSON format with pass/fail results
4. **Publishing results** - Optional leaderboard/report publication once repository and community infrastructure are fully launched

### Key Features

- 🔄 **Decentralized Testing** - Engines test themselves, no central bottleneck
- 🌐 **Multi-Language SDKs** - Repository contains Java, Python, Rust, C++, Go, TypeScript/JavaScript, C#/.NET, and Scala SDK implementations at varying maturity levels
- 📦 **Pre-packaged Test Suites** - TPC-H and function test suites are present in-repo; broader suite completion and verification remain in progress
- ⚡ **Performance Benchmarking** - Benchmarking components are included across several SDKs
- 🤖 **Automated CI/CD** - GitHub Actions workflows are included for validation, release, and automation, with full release verification still pending
- 🌐 **REST API** - Spring Boot API implementation is included in the repository and should be treated as pre-release until launch validation is complete
- 📊 **Interactive Demo** - Live dashboard with mock engines, query drill-down, and complexity filtering
- 🏆 **Leaderboard Support** - Report aggregation and leaderboard generation are included; public publication depends on launch setup
- ✨ **Quality Tooling** - Quality-checking and enhancement scripts are included for test-suite maintenance
- 🔟 **10-Phase Framework** - Repository includes validation, analysis, storage, analytics, and reproducibility components in varying stages of maturity

---

## 🚀 5-Minute Quick Start

### Try the Interactive Demo First! (Fastest Way to Get Started)

Experience the framework in action before integrating your own engine:

```bash
# 1. Clone the repository
git clone https://github.com/substrait-io/substrait-compliance.git
cd substrait-compliance

# 2. Run the demo (generates mock compliance reports)
cd demo
./runner/run-simple-demo.sh

# Or run the enhanced demo with 10-phase framework
./runner/run-enhanced-demo.sh

# 3. View the interactive dashboard
cd dashboard
python3 -m http.server 8080

# 4. Open in your browser
# Navigate to: http://localhost:8080
```

**Expected Output:**
```
================================================================================
Substrait Compliance Framework - Demo
================================================================================

🔧 Testing: MockDB v1.0.0
   ✅ Passed: 19/22 (86.4%)
   💾 Report saved: output/mockdb-report.json

🔧 Testing: FastDB v2.5.0
   ✅ Passed: 21/22 (95.5%)
   💾 Report saved: output/fastdb-report.json

🔧 Testing: CloudDB v3.1.0
   ✅ Passed: 17/22 (77.3%)
   💾 Report saved: output/clouddb-report.json

📈 Generating leaderboard...
   💾 Dashboard data updated: dashboard/data/leaderboard.json

✅ Demo completed successfully!
```

**What You'll See in the Dashboard:**
- 🥇 **Leaderboard** - Rankings with medals (FastDB 🥇, MockDB 🥈, CloudDB 🥉)
- 📊 **Visual Charts** - Bar chart and doughnut chart showing pass rates
- 📈 **Detailed Statistics** - Per-engine breakdowns and test case results
- 🎯 **Test Distribution** - Passed, failed, and skipped test counts
- 🔍 **Query Drill-Down** - Click any engine to see detailed query-level results
- 🏷️ **Complexity Filtering** - Filter tests by SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX
- 📋 **10-Phase Framework** - Validation, analysis, storage, analytics, and reproducibility (enhanced demo)

**Troubleshooting:**
- If port 8080 is in use: `python3 -m http.server 8081`
- If permission denied: `chmod +x runner/run-simple-demo.sh`
- Dashboard shows "Failed to load": Ensure demo ran successfully first

---

## 🔧 Test Your Own Engine

### Step 1: Choose Your SDK

<details>
<summary><b>Java SDK (Recommended - Most Complete)</b></summary>

```bash
# Build the SDK
cd sdk/java
./gradlew build

# Run tests to verify
./gradlew test

# Expected output:
# BUILD SUCCESSFUL
# 12 tests passed ✅
```

**Verify Installation:**
```bash
ls -la build/libs/
# Should see: substrait-compliance-sdk-*.jar
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
# ok      github.com/substrait-io/substrait-compliance/sdk/go
```

**Verify Installation:**
```bash
go list -m github.com/substrait-io/substrait-compliance/sdk/go
# Should see: github.com/substrait-io/substrait-compliance/sdk/go v1.0.0
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
ls -la bin/Debug/net6.0/
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

<details>
<summary><b>Java Example</b></summary>

```java
public class MyEngine implements ComplianceEngine {
    @Override
    public EngineInfo getInfo() {
        return new EngineInfo("MyEngine", "1.0.0", "MyCompany");
    }
    
    @Override
    public ComplianceResult executePlan(byte[] planBytes, Map<String, TableData> inputData) {
        // Load data into your engine
        loadInputData(inputData);
        
        // Execute Substrait plan
        TableData output = executeSubstraitPlan(planBytes);
        
        return new ComplianceResult("test-id", TestStatus.PASSED, output, null, 0);
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
        // Load data into your engine
        await this.loadInputData(inputData);
        
        // Execute Substrait plan
        const output = await this.executeSubstraitPlan(planBytes);
        
        return new ComplianceResult('test-id', TestStatus.PASSED, output, undefined, undefined, executionTimeMs);
    }
    
    async validatePlan(planBytes: Uint8Array): Promise<ComplianceResult> {
        const isValid = await this.validateSubstraitPlan(planBytes);
        return new ComplianceResult('validation', isValid ? TestStatus.PASSED : TestStatus.FAILED);
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
    
    public EngineCapabilities GetCapabilities()
    {
        return new EngineCapabilities(
            SupportedRelations: new[] { "read", "filter", "project", "aggregate" },
            SupportedFunctions: new[] { "add", "subtract", "equal" },
            SupportedTypes: new[] { "i32", "i64", "string", "boolean" }
        );
    }
    
    public async Task<ComplianceResult> ExecutePlanAsync(
        byte[] planBytes,
        IReadOnlyDictionary<string, TableData> inputData)
    {
        // Load data into your engine
        await LoadInputDataAsync(inputData);
        
        // Execute Substrait plan
        var output = await ExecuteSubstraitPlanAsync(planBytes);
        
        return new ComplianceResult("test-id", TestStatus.Passed, output, null, null, executionTimeMs);
    }
    
    public async Task<ComplianceResult> ValidatePlanAsync(byte[] planBytes)
    {
        var isValid = await ValidateSubstraitPlanAsync(planBytes);
        return new ComplianceResult("validation", isValid ? TestStatus.Passed : TestStatus.Failed);
    }
}
```
</details>

### Step 3: Run Against Test Suites

**Java Example:**
```bash
cd examples/duckdb-java
./gradlew run

# Expected output:

---

## ⚡ Performance Benchmarking

All SDKs include comprehensive performance benchmarking capabilities to measure and optimize engine performance.

### Features

- **Statistical Analysis**: Min, Max, Avg, Median, P95, P99 latencies
- **Throughput Metrics**: Operations per second calculations
- **Memory Profiling**: Track memory usage patterns (language-specific)
- **Warmup Support**: Configurable warmup runs before measurement
- **Parallel Execution**: Test concurrent execution capabilities
- **CSV Export**: Export results for external analysis

### Quick Example (Scala)

```scala
import io.substrait.compliance.benchmark._

val engine = new MyEngine()
val config = BenchmarkConfig(warmupRuns = 10, measurementRuns = 100)
val runner = BenchmarkRunner(engine, config)

val operations = Seq(
  ("executePlan", () => engine.executePlan(plan, inputs)),
  ("validatePlan", () => engine.validatePlan(plan))
)

val result = Await.result(runner.runBenchmark("My Benchmark", operations), 5.minutes)
println(result.summary)
// Output: Min: 45ms, Max: 123ms, Avg: 54ms, P95: 67ms, Throughput: 18.5 ops/sec
```

### Available in All SDKs

- ✅ **Scala** - `sdk/scala/src/main/scala/io/substrait/compliance/benchmark/`
- ✅ **C++** - `sdk/cpp/include/substrait_compliance/benchmark_runner.hpp`
- ✅ **Go** - `sdk/go/benchmark/benchmark_runner.go`
- ✅ **TypeScript** - `sdk/typescript/src/benchmark/BenchmarkRunner.ts`
- ✅ **C#** - `sdk/csharp/Substrait.Compliance/Benchmark/BenchmarkRunner.cs`
- ✅ **Python** - `sdk/python/substrait_compliance/benchmark/benchmark_runner.py`
- ✅ **Rust** - `sdk/rust/src/benchmark/mod.rs`
- ✅ **Java** - `sdk/java/src/main/java/io/substrait/compliance/benchmark/`

### Documentation

See [Performance Benchmarking Guide](docs/PERFORMANCE_BENCHMARKING.md) for:
- Detailed usage examples for each language
- Standard benchmark suite definitions
- Best practices and CI/CD integration
- Output format specifications

# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 20/22 (90.9%)
# Report saved: output/duckdb-report.json
```

**Python Example:**
```bash
cd examples/datafusion-python
python datafusion_compliance.py

# Expected output:
# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 21/22 (95.5%)
# Report saved: output/datafusion-report.json
```

**TypeScript Example:**
```bash
cd examples/my-engine-typescript
npm install
npm run test

# Or run directly with ts-node
npx ts-node src/compliance-test.ts

# Expected output:
# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 20/22 (90.9%)
# Report saved: output/my-engine-report.json
```

**C# Example:**
```bash
cd examples/my-engine-csharp
dotnet run

# Expected output:
# Loading TPC-H test suite...
# Running 22 test cases...
# ✅ Passed: 19/22 (86.4%)
# Report saved: output/my-engine-report.json
```

**Programmatic Usage:**
```java
// Java: Load and run test suite
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load("test-suites/tpch/metadata.yaml");

ComplianceRunner runner = new ComplianceRunner(new MyEngine());
ComplianceReport report = runner.runTestSuite(suite);

System.out.println("Pass Rate: " + report.getPassRate() + "%");
System.out.println("Passed: " + report.getPassedCount() + "/" + report.getTotalCount());
```

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

Customize the workflow for your engine's build process.

**See [examples/](examples/) for complete implementations.**

---

## 🧪 Test Suites

> **📚 Detailed Documentation**:
> - [test-suites/functions/README.md](test-suites/functions/README.md) - Function tests (279 files total: 143 standard + 136 enhanced, ~4,000+ test cases)
> - [test-suites/tpch/README.md](test-suites/tpch/README.md) - TPC-H benchmark (22 queries)
> - [test-suites/tpcds/README.md](test-suites/tpcds/README.md) - TPC-DS benchmark (99 queries)

### TPC-H Benchmark (22 Queries)

The framework includes a complete TPC-H benchmark at scale factor 0.01:

```bash
# Navigate to TPC-H test suite
cd test-suites/tpch

# View test suite metadata
cat metadata.yaml

# Inspect test data
ls -la data/
# Output: 8 CSV files (customer.csv, lineitem.csv, nation.csv, orders.csv, 
#         part.csv, partsupp.csv, region.csv, supplier.csv)
# Total: 86,805 rows, 10.6 MB

# View Substrait plans
ls -la plans/
# Output: 44 files (q01.bin, q01.json, q02.bin, q02.json, ... q22.bin, q22.json)
```

**Query Complexity Breakdown:**
- **SIMPLE** (3 queries): Q1, Q6, Q14 - Single table, basic aggregations
- **MEDIUM** (7 queries): Q3, Q4, Q10, Q12, Q13, Q16, Q19 - Joins, grouping
- **COMPLEX** (8 queries): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22 - Multiple joins, subqueries
- **VERY_COMPLEX** (4 queries): Q2, Q8, Q20, Q21 - Nested subqueries, complex joins

**Running TPC-H Tests:**
```java
// Java example
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

The framework includes the complete TPC-DS (Decision Support) benchmark for complex analytical workloads:

```bash
# Navigate to TPC-DS test suite
cd test-suites/tpcds

# View test suite metadata
cat metadata.yaml

# Inspect test data (24 CSV files)
ls -la data/
# Output: Multi-channel retail data (store, catalog, web sales and returns)

# View available plans (194 files: 97 JSON + 97 binary)
ls -la plans/
```

**Complete Coverage:**
- **99 SQL Queries**: All TPC-DS benchmark queries (query01.sql - query99.sql)
- **194 Substrait Plans**: 97 queries with both JSON and binary formats
- **24 Data Tables**: Complete TPC-DS schema at scale factor 0.01
- **Multi-channel Analytics**: Store, catalog, and web sales channels

**Key Query Categories:**
- Customer behavior and profitability analysis
- Multi-channel retail analytics
- Sales and returns analysis
- Inventory management
- Marketing effectiveness
- Complex business intelligence scenarios

> **📚 See [test-suites/tpcds/README.md](test-suites/tpcds/README.md) for complete TPC-DS documentation**

### Function Tests (279 Files Total)

The framework includes **two comprehensive test suite collections**:

#### Standard Test Suite (`test-suites/functions/` - 143 files)
Core function coverage across 15 categories:

```bash
# Navigate to standard function tests
cd test-suites/functions

# View available categories (15 total)
ls -la
# - aggregate/     (6 files: count, avg, stddev, variance, etc.)
# - arithmetic/    (44 files: add, multiply, sqrt, trigonometry, etc.)
# - array/         (6 files: array operations)
# - boolean/       (4 files: and, or, not, xor)
# - cast/          (2 files: cast, try_cast)
# - comparison/    (19 files: equal, gt, lt, between, coalesce, etc.)
# - conditional/   (2 files: case, if)
# - datetime/      (12 files: date_trunc, date_diff, extract, etc.)
# - geospatial/    (4 files: st_area, st_distance, st_contains, st_intersects)
# - json/          (2 files: json_extract, json_parse)
# - map/           (3 files: map operations)
# - set/           (3 files: union, intersect, except)
# - string/        (27 files: concat, substring, regexp, etc.)
# - struct/        (2 files: struct operations)
# - window/        (7 files: row_number, rank, lag, lead, etc.)
```

#### Enhanced Test Suite (`test-suites-enhanced/functions/` - 136 files)
Extended coverage with **AI-enhanced quality (95%+ score)** across 14 categories:

```bash
# Navigate to enhanced function tests
cd test-suites-enhanced/functions

# View available categories (14 total)
ls -la
# - aggregate/     (6 files: count, count_distinct, avg, sum, stddev, variance, string_agg)
# - arithmetic/    (44 files: comprehensive math operations)
# - array/         (4 files: array_construct, array_element, array_concat, array_contains)
# - cast/          (1 file: try_cast with error handling)
# - comparison/    (19 files: complete comparison operations)
# - conditional/   (2 files: case_when, if_then_else)
# - datetime/      (12 files: extensive date/time operations)
# - geospatial/    (4 files: spatial operations)
# - json/          (2 files: json_extract, json_parse)
# - map/           (3 files: map_construct, map_extract, map_keys)
# - set/           (3 files: union, intersect, except)
# - string/        (27 files: comprehensive string manipulation)
# - struct/        (2 files: struct_construct, struct_extract)
# - window/        (7 files: ranking and navigation functions)
```

**Quality Assurance:** Function-test quality tooling and enhancement artifacts are included in the repository. Treat generated quality metrics and enhancement outputs as project tooling results, not as a substitute for release verification.

**Running Function Tests:**
```python
# Python example - Standard suite
from substrait_compliance import YamlTestSuiteLoader, ComplianceRunner

# Load arithmetic function tests
loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/functions/arithmetic/metadata.yaml")

# Run tests
runner = ComplianceRunner(my_engine)
report = runner.run_test_suite(suite)

print(f"Arithmetic Functions: {report.passed_count}/{report.total_count} passed")

# Or use enhanced suite
enhanced_suite = loader.load("test-suites-enhanced/functions/arithmetic/metadata.yaml")
enhanced_report = runner.run_test_suite(enhanced_suite)
```

### Running Specific Tests

**Single Query:**
```java
// Load TPC-H suite
TestSuite suite = loader.load("test-suites/tpch/metadata.yaml");

// Filter to Q1 only
TestCase q1 = suite.getTestCases().stream()
    .filter(tc -> tc.getId().equals("q01"))
    .findFirst()
    .orElseThrow();

// Run single test
ComplianceResult result = myEngine.executePlan(
    q1.getPlanBytes(), 
    q1.getInputData()
);

System.out.println("Q1 Status: " + result.getStatus());
```

**Specific Function Category:**
```bash
# Run only string functions
cd test-suites/functions/string
# Implement loader to process *.test files in this directory
```

**Custom Test Selection:**
```java
// Filter by complexity
List<TestCase> simpleTests = suite.getTestCases().stream()
    .filter(tc -> tc.getComplexity() == Complexity.SIMPLE)
    .collect(Collectors.toList());

// Run filtered tests
for (TestCase test : simpleTests) {
    ComplianceResult result = myEngine.executePlan(
        test.getPlanBytes(), 
        test.getInputData()
    );
    // Process result...
}
```

---


## 📦 Repository Structure

```
substrait-compliance/
├── 🎯 demo/                       # ⭐ START HERE - Interactive demo
│   ├── runner/
│   │   ├── run-simple-demo.sh     # ⭐ Run this first!
│   │   ├── run-demo.sh            # Full demo with dashboard
│   │   ├── run-enhanced-demo.sh   # Enhanced demo runner
│   │   └── run-function-tests.sh  # Function test runner
│   ├── dashboard/                 # Visual results dashboard
│   ├── engines/                   # Example mock engines (Java)
│   │   ├── MockDBEngine.java      # Simple mock implementation
│   │   ├── FastDBEngine.java      # Fast mock engine
│   │   ├── CloudDBEngine.java     # Cloud mock engine
│   │   ├── DuckDBEngine.java      # DuckDB integration
│   │   └── PostgreSQLEngine.java  # PostgreSQL integration
│   └── output/                    # Generated reports
├── 📚 sdk/                        # Multi-language SDKs (8 languages)
│   ├── cpp/                       # ⭐ C++ SDK - High performance
│   │   └── examples/              # C++ examples
│   ├── csharp/                    # ⭐ C# SDK - .NET integration
│   │   ├── IComplianceEngine.cs   # Core interface
│   │   ├── ComplianceRunner.cs    # Test runner
│   │   ├── Examples/              # C# examples
│   │   └── Tests/                 # Unit tests
│   ├── go/                        # ⭐ Go SDK - Concurrent processing
│   │   ├── engine.go              # Core interface
│   │   ├── result.go              # Result types
│   │   └── table_data_test.go     # Unit tests
│   ├── java/                      # ⭐ Java SDK (JDK 11+) - Most complete
│   │   ├── src/main/java/         # Core interfaces
│   │   ├── src/test/java/         # Unit tests
│   │   └── build.gradle           # Gradle build
│   ├── python/                    # ⭐ Python SDK (3.8+) - Pythonic interface
│   │   ├── substrait_compliance/  # Package modules
│   │   ├── tests/                 # Unit tests
│   │   └── setup.py               # PyPI setup
│   ├── rust/                      # ⭐ Rust SDK (2021 edition) - High performance
│   │   ├── src/                   # Library modules
│   │   ├── tests/                 # Integration tests
│   │   └── Cargo.toml             # Cargo manifest
│   ├── scala/                     # ⭐ Scala SDK - JVM integration
│   └── typescript/                # ⭐ TypeScript SDK - Node.js/Browser
├── 🧪 test-suites/                # Standard test suites (143 files)
│   ├── functions/                 # ⭐ Function tests (143 files)
│   │   ├── aggregate/             # 7 files - COUNT, AVG, SUM, STDDEV, etc.
│   │   ├── arithmetic/            # 44 files - ADD, MULTIPLY, SQRT, etc.
│   │   ├── array/                 # 6 files - Array operations
│   │   ├── boolean/               # 4 files - AND, OR, NOT, XOR
│   │   ├── cast/                  # 2 files - CAST, TRY_CAST
│   │   ├── comparison/            # 19 files - =, <, >, BETWEEN, etc.
│   │   ├── conditional/           # 2 files - CASE, IF
│   │   ├── datetime/              # 12 files - Date/time operations
│   │   ├── geospatial/            # 4 files - ST_* functions
│   │   ├── json/                  # 2 files - JSON operations
│   │   ├── map/                   # 3 files - Map operations
│   │   ├── set/                   # 3 files - UNION, INTERSECT, EXCEPT
│   │   ├── string/                # 27 files - String manipulation
│   │   ├── struct/                # 2 files - Struct operations
│   │   └── window/                # 8 files - ROW_NUMBER, RANK, LAG, etc.
│   ├── tpch/                      # TPC-H benchmark (22 queries)
│   │   ├── metadata.yaml          # Test suite definition
│   │   ├── data/                  # 8 CSV files (86,805 rows)
│   │   └── plans/                 # 44 Substrait plans (bin + json)
│   └── tpcds/                     # TPC-DS benchmark (expanding to 99)
│       ├── metadata.yaml          # Test suite definition
│       ├── data/                  # Multi-channel retail data
│       └── plans/                 # Substrait plans for decision support
├── 🧪 test-suites-enhanced/       # ⭐ Enhanced test suites (136 files, AI-quality checked)
│   └── functions/                 # 136 files with comprehensive coverage (95%+ quality)
│       ├── aggregate/             # 6 files - Enhanced aggregate functions
│       ├── arithmetic/            # 44 files - Enhanced math operations
│       ├── array/                 # 4 files - Enhanced array operations
│       ├── cast/                  # 1 file - TRY_CAST with error handling
│       ├── comparison/            # 19 files - Enhanced comparisons
│       ├── conditional/           # 2 files - CASE_WHEN, IF_THEN_ELSE
│       ├── datetime/              # 12 files - Enhanced date/time ops
│       ├── geospatial/            # 4 files - Enhanced spatial functions
│       ├── json/                  # 2 files - Enhanced JSON operations
│       ├── map/                   # 3 files - Enhanced map operations
│       ├── set/                   # 3 files - Enhanced set operations
│       ├── string/                # 27 files - Enhanced string functions
│       ├── struct/                # 2 files - Enhanced struct operations
│       └── window/                # 7 files - Enhanced window functions
├── 💡 examples/                   # ⭐ Real-world integration examples
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
│   ├── api-build-test.yml         # API build and test
│   ├── api-container-build.yml    # Container image builds
│   ├── api-deploy-staging.yml     # Staging deployment
│   ├── api-deploy-production.yml  # Production deployment
│   └── engine-compliance-template.yml # ⭐ Template for your engine
├── 🔧 scripts/                    # Automation and quality tools
│   ├── generate_leaderboard.py    # Leaderboard generator
│   ├── generate_technical_report.py # Technical report generator
│   ├── quality_checker.py         # Test quality checker
│   ├── test_enhancer.py           # Test enhancement tool
│   ├── test_comparator.py         # Test comparison tool
│   ├── enhance_all_categories.sh  # Batch enhancement script
│   ├── verify_sdk_builds.sh       # SDK build verification
│   └── quality_config.yaml        # Quality check configuration
├── 📊 quality_reports/            # Quality assessment reports
│   ├── *_quality.json             # Individual function quality reports
│   └── *_enhancement.json         # Enhancement results
└── 📖 docs/                       # Comprehensive documentation
    ├── API_IMPLEMENTATION.md      # API implementation details
    ├── DEPLOYMENT_GUIDE.md        # Deployment instructions
    ├── IMPLEMENTATION_HISTORY.md  # Development history
    ├── PERFORMANCE_BENCHMARKING.md # Performance analysis
    ├── REST_API_ARCHITECTURE.md   # API architecture
    ├── REST_API_GUIDE.md          # API usage guide
    ├── SDK_VERIFICATION.md        # SDK verification guide
    ├── SECURITY_HARDENING_GUIDE.md # Security best practices
    └── TECHNICAL_REPORT.md        # Comprehensive technical report
```

**Key Directories for Developers:**
- 🎯 **Start with `demo/`** - Run the interactive demo first to see the framework in action
- 📚 **Use `sdk/`** - Choose from 8 SDKs: C++, C#, Go, Java, Python, Rust, Scala, or TypeScript
- 🧪 **Test with `test-suites/`** - 143 standard function tests plus TPC-H/TPC-DS benchmarks
- 🧪 **Enhanced tests in `test-suites-enhanced/`** - 136 AI-quality-checked tests (95%+ quality score)
- 💡 **Learn from `examples/`** - Real integration examples with DuckDB, DataFusion, and Velox
- 📊 **Review `quality_reports/`** - Quality metrics and enhancement results

---

## 🏗️ Architecture

### Decentralized Compliance Model

```
┌─────────────────────────────────────────────────────────────┐
│         Compliance Framework (Standards Authority)           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Publishes:                                           │  │
│  │  • SDKs (Java, Python, Rust)                          │  │
│  │  • Test Suites (TPC-H)                                │  │
│  │  • Documentation                                      │  │
│  │  • CI/CD Templates                                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          ↓
              Downloads & Implements
                          ↓
┌─────────────────────────────────────────────────────────────┐
│              Query Engines (Self-Certify)                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  DuckDB    │  │ DataFusion │  │   Spark    │            │
│  │  • Java SDK│  │ • Python   │  │  • Java SDK│            │
│  │  • 90.9%   │  │ • 95.5%    │  │  • 86.4%   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
                          ↓
              Reports Results (Optional)
                          ↓
┌─────────────────────────────────────────────────────────────┐
│         Public Compliance Leaderboard (GitHub Pages)         │
│  • Rankings by pass rate                                    │
│  • Detailed results per engine                              │
│  • Historical trends                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Test Suite

### TPC-H Benchmark

Complete TPC-H benchmark at scale factor 0.01:

| Component | Details |
|-----------|---------|
| **Queries** | 22 (Q1-Q22) |
| **Data Files** | 8 CSV files |
| **Total Rows** | 86,805 |
| **Data Size** | 10.6 MB |
| **Plan Formats** | Binary (.bin) + JSON (.json) |
| **Complexity Levels** | SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX |

**Query Breakdown:**
- **SIMPLE** (3): Q1, Q6, Q14
- **MEDIUM** (7): Q3, Q4, Q10, Q12, Q13, Q16, Q19
- **COMPLEX** (8): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22
- **VERY_COMPLEX** (4): Q2, Q8, Q20, Q21

---

## 🔧 SDK Features

### Java SDK

- **Version**: 1.0.0
- **JDK**: 11+
- **Build**: Gradle
- **Tests**: 12 unit tests (100% passing)
- **Coverage**: JaCoCo reports
- **Distribution**: Maven Central (planned)

**Key Classes:**
- `ComplianceEngine` - Main interface
- `ComplianceRunner` - Test executor
- `YamlTestSuiteLoader` - Test suite loader
- `EngineCapabilities` - Feature descriptor

### Python SDK

- **Version**: 1.0.0
- **Python**: 3.8+
- **Build**: setuptools
- **Tests**: 8 integration tests (100% passing)
- **Coverage**: pytest-cov
- **Distribution**: PyPI (planned)

**Key Modules:**
- `engine.py` - ComplianceEngine ABC
- `runner.py` - ComplianceRunner
- `loader.py` - YamlTestSuiteLoader
- `result.py` - Result classes

### C++ SDK

- **Version**: 1.0.0
- **Standard**: C++17
- **Build**: CMake 3.15+
- **Tests**: 4 unit test suites (Google Test)
- **Coverage**: gcov/lcov
- **Distribution**: vcpkg, Conan (planned)

**Key Headers:**
- `engine.h` - ComplianceEngine interface
- `runner.h` - ComplianceRunner
- `loader.h` - YamlTestSuiteLoader
- `result.h` - Result classes
- `comparator.h` - Result comparison

**Features:**
- Modern C++17 with smart pointers
- Zero-copy operations for performance
- Header-only option available
- Cross-platform (Linux, macOS, Windows)

### Go SDK

- **Version**: 1.0.0
- **Go**: 1.21+
- **Build**: Go modules
- **Tests**: 3 test suites (100% passing)
- **Coverage**: go test -cover
- **Distribution**: Go modules

**Key Packages:**
- `engine.go` - ComplianceEngine interface
- `runner.go` - ComplianceRunner with goroutines
- `loader.go` - YAMLTestSuiteLoader
- `result.go` - Result types
- `table_data.go` - Data structures

**Features:**
- Idiomatic Go with interfaces
- Built-in concurrency with goroutines
- Context support for cancellation
- Zero external dependencies (core)
- Cross-platform

### Rust SDK

- **Version**: 1.0.0
- **Edition**: 2021
- **Build**: Cargo
- **Tests**: 6 integration tests (100% passing)
- **Coverage**: tarpaulin
- **Distribution**: crates.io (planned)

**Key Modules:**
- `engine.rs` - ComplianceEngine trait
- `runner.rs` - ComplianceRunner
- `loader.rs` - YamlTestSuiteLoader
- `result.rs` - Result types

---

## 🎮 Interactive Demo

Try the compliance framework with our interactive demo system:

```bash
cd demo
./runner/run-demo.sh
```

**Features:**
- 🎯 **Mock Engines** - Three pre-configured database engines (MockDB, FastDB, CloudDB)
- 📊 **Live Dashboard** - Real-time visualization of test results
- 🚀 **Quick Start** - Run compliance tests in under 2 minutes
- 📈 **Visual Reports** - Interactive charts and statistics

See [demo/START_HERE.md](demo/START_HERE.md) for detailed instructions.

---

## 🤖 CI/CD Integration

### Automated Workflows

**API Workflows:**
1. **PR Validation** - Validate code changes before merge
2. **Build & Test** - Continuous validation on every push
3. **Container Build** - Multi-platform Docker images
4. **Deploy Staging** - Automatic staging deployments
5. **Deploy Production** - Production releases with approval
6. **Release** - Automated versioning and publishing

**SDK Workflows:**
1. **SDK Build & Test** - Tests all SDKs on every commit
2. **Release & Publish** - Automated releases to package registries
3. **Test Suite Validation** - Validates test suite integrity

### For Engine Developers

Copy the template workflow to enable automated compliance testing:

```bash
cp .github/workflows/engine-compliance-template.yml \
   .github/workflows/substrait-compliance.yml
```

Customize:
- Engine name and version
- Build commands
- Test execution
- Compliance threshold (default: 80%)

See [.github/workflows/README.md](.github/workflows/README.md) for details.

---

## 🔍 Troubleshooting

### Common Issues and Solutions

#### "Permission denied" on shell scripts
```bash
# Fix: Make scripts executable
chmod +x demo/runner/run-simple-demo.sh
chmod +x demo/verify-setup.sh

# Then run
./demo/runner/run-simple-demo.sh
```

#### "Java version not supported"
```bash
# Check your Java version
java -version

# Should show 11 or higher
# If not, install Java 11+:

# macOS (using Homebrew)
brew install openjdk@11

# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-11-jdk

# Windows
# Download from https://adoptium.net/
```

#### "Dashboard shows 'Failed to load data'"
```bash
# Cause: Demo wasn't run yet or data file missing
# Solution: Run the demo first

cd demo
./runner/run-simple-demo.sh

# Verify files were created
ls -la output/leaderboard.json
ls -la dashboard/data/leaderboard.json

# Both files should exist

# Important: Must use HTTP server (not file://)
cd dashboard
python3 -m http.server 8080
# Then open http://localhost:8080
```

#### "Port 8080 already in use"
```bash
# Try alternative ports
python3 -m http.server 8081
python3 -m http.server 9000
python3 -m http.server 3000

# Or find and kill the process using port 8080
# macOS/Linux:
lsof -ti:8080 | xargs kill -9

# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

#### "SDK build fails"
```bash
# Java: Clean and rebuild
cd sdk/java
./gradlew clean build --refresh-dependencies

# If Gradle wrapper fails
./gradlew wrapper --gradle-version 8.5
./gradlew build

# Python: Reinstall
cd sdk/python
pip uninstall substrait-compliance -y
pip install -e .

# If dependencies fail
pip install --upgrade pip setuptools wheel
pip install -e .

# Rust: Clean and rebuild
cd sdk/rust
cargo clean
cargo build --release

# If dependencies fail
cargo update
cargo build --release
```

#### "Test data not found"
```bash
# Verify test suite structure
ls -la test-suites/tpch/data/
ls -la test-suites/tpch/plans/

# Should see:
# - 8 CSV files in data/ (customer.csv, lineitem.csv, etc.)
# - 44 plan files in plans/ (q01.bin, q01.json, etc.)

# If files are missing, check git clone
git status
git pull origin main
```

#### "Module not found" errors (Python)
```bash
# Ensure SDK is installed
cd sdk/python
pip install -e .

# Verify installation
python3 -c "import substrait_compliance; print('OK')"

# If still fails, check Python path
python3 -c "import sys; print('\n'.join(sys.path))"

# Install in development mode with all dependencies
pip install -e ".[dev]"
```

#### "Gradle build fails with 'Could not resolve dependencies'"
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches/

# Rebuild
cd sdk/java
./gradlew clean build --refresh-dependencies

# If behind proxy, configure gradle.properties
# ~/.gradle/gradle.properties:
# systemProp.http.proxyHost=proxy.example.com
# systemProp.http.proxyPort=8080
```

#### "Dashboard shows but no data appears"
```bash
# Check browser console for errors (F12)
# Common causes:

# 1. Opened as file:// instead of http://
#    Solution: Use web server (python3 -m http.server)

# 2. CORS errors
#    Solution: Ensure serving from correct directory
cd demo/dashboard
python3 -m http.server 8080

# 3. JSON file is empty or malformed
cat dashboard/data/leaderboard.json
# Should show valid JSON with engines array

# 4. Path issues
#    Ensure you're in the dashboard directory when starting server
```

### Getting Help

If you're still experiencing issues:

1. **Check Documentation**
   - [demo/START_HERE.md](demo/START_HERE.md) - Demo instructions
   - [demo/QUICKSTART.md](demo/QUICKSTART.md) - Quick start guide
   - [examples/README.md](examples/README.md) - Integration examples
   - [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - Complete integration guide

2. **Review Examples**
   - Look at working implementations in `examples/`
   - Check demo code in `demo/engines/`

3. **Enable Debug Logging**
   ```java
   // Java: Enable debug logging
   System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
   ```

4. **Report Issues**
   - 🐛 [GitHub Issues](https://github.com/substrait-io/substrait-compliance/issues)
   - 💬 [GitHub Discussions](https://github.com/substrait-io/substrait-compliance/discussions)

---

## ✅ Verification Checklist

Use this checklist to verify your setup is complete and working:

### Quick Verification Script

```bash
# Run the automated verification script
cd demo
./verify-setup.sh

# This checks:
# - Java version
# - Python version
# - Required directories
# - Demo execution
# - Report generation
```

### Manual Verification Steps

**1. Prerequisites Check**
```bash
# ✅ Java 11+
java -version
# Expected: java version "11" or higher

# ✅ Python 3.8+
python3 --version
# Expected: Python 3.8.x or higher

# ✅ Git
git --version
# Expected: git version 2.x.x
```

**2. Repository Structure**
```bash
# ✅ Verify key directories exist
ls -la demo/ sdk/ test-suites/ examples/
# All should exist

# ✅ Verify test data
ls -la test-suites/tpch/data/*.csv
# Should show 8 CSV files

# ✅ Verify Substrait plans
ls -la test-suites/tpch/plans/*.{bin,json}
# Should show 44 files (22 .bin + 22 .json)
```

**3. Demo Execution**
```bash
# ✅ Run demo successfully
cd demo
./runner/run-simple-demo.sh

# Expected output:
# - "Testing: MockDB v1.0.0" ✅
# - "Testing: FastDB v2.5.0" ✅
# - "Testing: CloudDB v3.1.0" ✅
# - "Demo completed successfully!" ✅
```

**4. Report Generation**
```bash
# ✅ Verify reports were created
ls -la demo/output/
# Should show:
# - mockdb-report.json
# - fastdb-report.json
# - clouddb-report.json
# - leaderboard.json

# ✅ Verify dashboard data
ls -la demo/dashboard/data/
# Should show:
# - leaderboard.json

# ✅ Validate JSON format
cat demo/output/leaderboard.json | python3 -m json.tool
# Should display formatted JSON without errors
```

**5. Dashboard Access**
```bash
# ✅ Start web server
cd demo/dashboard
python3 -m http.server 8080 &
SERVER_PID=$!

# ✅ Test dashboard access
curl -s http://localhost:8080 | grep -q "Substrait Compliance"
echo "Dashboard accessible: $?"
# Expected: 0 (success)

# ✅ Test data endpoint
curl -s http://localhost:8080/data/leaderboard.json | python3 -m json.tool > /dev/null
echo "Data endpoint valid: $?"
# Expected: 0 (success)

# Cleanup
kill $SERVER_PID
```

**6. SDK Build Verification**
```bash
# ✅ Java SDK
cd sdk/java
./gradlew build
# Expected: BUILD SUCCESSFUL

./gradlew test
# Expected: 12 tests passed

# ✅ Python SDK
cd ../python
pip install -e .
# Expected: Successfully installed

pytest
# Expected: 8 passed

# ✅ Rust SDK
cd ../rust
cargo build --release
# Expected: Finished release

cargo test
# Expected: test result: ok. 6 passed
```

**7. Example Execution**
```bash
# ✅ Java example (if DuckDB available)
cd examples/duckdb-java
./gradlew run
# Expected: Compliance report generated

# ✅ Python example (if DataFusion available)
cd ../datafusion-python
python datafusion_compliance.py
# Expected: Compliance report generated
```

### Success Criteria

Your setup is complete when:

- ✅ All prerequisites are installed (Java 11+, Python 3.8+)
- ✅ Demo runs successfully without errors
- ✅ Reports are generated in `demo/output/`
- ✅ Dashboard is accessible at `http://localhost:8080`
- ✅ Dashboard displays 3 engines with rankings
- ✅ At least one SDK builds successfully
- ✅ SDK tests pass

### Next Steps After Verification

Once all checks pass:

1. **Explore the Demo** - Understand how the framework works
2. **Review Examples** - See real integration patterns
3. **Choose Your SDK** - Pick Java, Python, or Rust
4. **Implement Your Engine** - Follow the integration guide
5. **Run Tests** - Execute against TPC-H or function tests
6. **Share Results** - Contribute to the compliance leaderboard

**🎉 Ready to integrate your engine!**

---

## 📈 Compliance Leaderboard

Public leaderboard showing compliance status across all engines:

- **Rankings** by pass rate
- **Detailed results** per engine
- **Statistics** (average, highest, lowest)
- **Interactive dashboard** (GitHub Pages)

**Example:**

| Rank | Engine | Version | Pass Rate | Queries Passed |
|------|--------|---------|-----------|----------------|
| 🥇 | DataFusion | 35.0.0 | 95.5% | 21/22 |
| 🥈 | DuckDB | 0.10.0 | 90.9% | 20/22 |
| 🥉 | Spark | 3.5.0 | 86.4% | 19/22 |

---

## 📖 Key Documentation

### 🎯 Getting Started
- **[README.md](README.md)** - This file - comprehensive overview and quick start
- **[demo/README.md](demo/README.md)** - Complete interactive demo guide with setup and usage
- **[demo/DASHBOARD_GUIDE.md](demo/DASHBOARD_GUIDE.md)** - Dashboard features and navigation
- **[demo/TROUBLESHOOTING.md](demo/TROUBLESHOOTING.md)** - Demo troubleshooting and FAQ
- **[examples/README.md](examples/README.md)** - Example implementations (DuckDB, DataFusion)

### 🧪 Test Suites
- **[test-suites/functions/README.md](test-suites/functions/README.md)** - Function tests (279 files: 143 standard + 136 enhanced, ~4,000+ test cases)
- **[test-suites/tpch/README.md](test-suites/tpch/README.md)** - TPC-H benchmark (22 queries)
- **[test-suites/tpcds/README.md](test-suites/tpcds/README.md)** - TPC-DS benchmark (99 queries)

### 📚 SDK Documentation
- **[sdk/java/README.md](sdk/java/README.md)** - Java SDK (JDK 11+)
- **[sdk/python/README.md](sdk/python/README.md)** - Python SDK (3.8+)
- **[sdk/rust/README.md](sdk/rust/README.md)** - Rust SDK (2021 edition)
- **[sdk/csharp/README.md](sdk/csharp/README.md)** - C# SDK (.NET 6.0+)

### 🌐 REST API
- **[docs/REST_API_GUIDE.md](docs/REST_API_GUIDE.md)** - Complete REST API reference and usage guide
- **[docs/REST_API_ARCHITECTURE.md](docs/REST_API_ARCHITECTURE.md)** - System architecture and design
- **[docs/API_IMPLEMENTATION.md](docs/API_IMPLEMENTATION.md)** - Implementation guide with CI/CD integration
- **[docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)** - Deployment instructions

### 🔧 Implementation & History
- **[docs/IMPLEMENTATION_HISTORY.md](docs/IMPLEMENTATION_HISTORY.md)** - Complete implementation history and milestones
- **[docs/TECHNICAL_REPORT.md](docs/TECHNICAL_REPORT.md)** - Comprehensive technical report
- **[.github/workflows/README.md](.github/workflows/README.md)** - CI/CD workflow documentation

### 🎨 Quality & Automation
- **[scripts/README.md](scripts/README.md)** - Complete scripts documentation and usage guide
- **[scripts/TEST_ENHANCEMENT_GUIDE.md](scripts/TEST_ENHANCEMENT_GUIDE.md)** - Test enhancement workflow
- **[CHANGELOG.md](CHANGELOG.md)** - Version history and changes

### 🤝 Contributing
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - General contribution guidelines
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** - Community code of conduct
- **[GOVERNANCE.md](GOVERNANCE.md)** - Project governance model

---

## 🛠️ Development

### Building from Source

**Java SDK:**
```bash
cd sdk/java
./gradlew build test
```

**Python SDK:**
```bash
cd sdk/python
pip install -e .[dev]
pytest
```

**Rust SDK:**
```bash
cd sdk/rust
cargo build --release
cargo test
```

### Running Examples

**DuckDB (Java):**
```bash
cd examples/duckdb-java
./gradlew run
```

**DataFusion (Python):**
```bash
cd examples/datafusion-python
python datafusion_compliance.py
```

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Areas for Contribution

- Additional test suites (TPC-DS, SSB, etc.)
- More example implementations
- SDK improvements
- Documentation enhancements
- Bug fixes and optimizations

---

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| **Total Lines of Code** | ~33,000 |
| **SDKs** | 8 (Java, Python, Rust, Go, C++, TypeScript, C#, Scala) |
| **Test Suites** | 3 (TPC-H, TPC-DS, Functions) |
| **Function Test Files** | 279 (143 standard + 136 enhanced) |
| **Function Test Cases** | ~4,000+ individual test cases |
| **Function Categories** | 15 (Aggregate, Arithmetic, Array, Boolean, Cast, Comparison, Conditional, DateTime, Geospatial, JSON, Map, Set, String, Struct, Window) |
| **TPC-H Queries** | 22 queries |
| **TPC-DS Queries** | 99 queries (complete) |
| **Total Test Coverage** | 4,000+ test cases + 121 benchmark queries |
| **Test Data Rows** | 86,805 (TPC-H scale factor 0.01) |
| **CI/CD Workflows** | 12 |
| **Documentation Files** | 15+ |
| **Example Implementations** | 5 (Mock engines + integration examples) |
| **REST API Endpoints** | 10+ (Reports, leaderboard, webhooks) |

---

## 📜 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Substrait Community** - For the amazing query plan specification
- **TPC-H Benchmark** - For the comprehensive test queries
- **Engine Developers** - For implementing Substrait support

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/substrait-io/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)
- **Mailing List**: substrait-dev@googlegroups.com
- **Slack**: #substrait-compliance (join at [substrait.io](https://substrait.io/))

---

## 🌐 REST API

A comprehensive REST API is available for programmatic access to compliance results:

- **[REST API Overview](docs/REST_API_SUMMARY.md)** - Project summary and getting started
- **[API Specification](docs/REST_API_PLAN.md)** - Complete API endpoints and specifications
- **[Architecture](docs/REST_API_ARCHITECTURE.md)** - System architecture and diagrams
- **[Implementation Guide](docs/REST_API_IMPLEMENTATION_GUIDE.md)** - Step-by-step implementation
- **[Contributing to API](docs/API_CONTRIBUTING.md)** - Contribution guidelines

### Key Features

- 🔐 **JWT Authentication** - Secure API access with token-based auth
- 📊 **Report Submission** - Submit compliance reports programmatically
- 🔍 **Data Querying** - Query compliance data with filtering and pagination
- 🔔 **Webhooks** - Real-time notifications for compliance events
- ⚡ **Rate Limiting** - Fair usage with configurable limits
- 💾 **Caching** - Optimized performance for frequently accessed data

### Quick Start

```bash
# Run with Podman
cd api
podman-compose up -d

# Access API
curl http://localhost:8080/api/v1/leaderboard
```

See [REST API documentation](docs/REST_API_SUMMARY.md) for details.

---

## 🗺️ Roadmap

- [x] REST API infrastructure with Spring Boot
- [x] Comprehensive CI/CD workflows
- [x] Interactive demo system with dashboard
- [x] Multi-platform container builds
- [ ] Additional test suites (TPC-DS, SSB)
- [ ] Performance benchmarking
- [ ] Compliance badges
- [ ] Historical trend analysis
- [ ] Multi-version testing

---

**Made with ❤️ for the Substrait Community**
