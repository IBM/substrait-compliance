# Substrait Compliance SDK - Java

Java SDK for decentralized Substrait compliance testing.

## Installation

### Maven

```xml
<dependency>
    <groupId>io.substrait</groupId>
    <artifactId>substrait-compliance</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.substrait:substrait-compliance:1.0.0'
}
```

## Quick Start

```java
import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.Map;

// 1. Implement the ComplianceEngine interface
public class MyEngine implements ComplianceEngine {
    
    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo("MyEngine", "1.0.0", "0.20.0");
    }
    
    @Override
    public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
                .addRelation("read")
                .addRelation("filter")
                .addRelation("project")
                .addFunction("add")
                .addFunction("subtract")
                .supportsExtensions(true)
                .build();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) 
            throws ComplianceException {
        // Execute Substrait plan
        TableData output = executeInternal(plan, inputData);
        return ComplianceResult.success(output, executionTimeMs);
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        // Validate plan structure
        boolean isValid = validateInternal(plan);
        return isValid ? 
            PlanValidationResult.supported() : 
            PlanValidationResult.unsupported(Arrays.asList("Unsupported feature"));
    }
}

// 2. Load a test suite
TestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load(Paths.get("test-suites/tpch/metadata.yaml"));

// 3. Run compliance tests
MyEngine engine = new MyEngine();
ComplianceRunner runner = new ComplianceRunner(engine);
ComplianceReport report = runner.runTestSuite(suite);

// 4. Check results
System.out.printf("Passed: %d/%d%n", 
    report.getPassedCount(), report.getTotalCount());
System.out.printf("Pass Rate: %.1f%%%n", report.getPassRate());

for (ComplianceResult result : report.getResults()) {
    if (!result.isSuccess()) {
        System.out.printf("Failed: %s - %s%n", 
            result.getTestId(), result.getErrorMessage());
    }
}
```

## Architecture

```
src/main/java/io/substrait/compliance/
├── ComplianceEngine.java       # Main engine interface
├── ComplianceRunner.java       # Test execution
├── ComplianceResult.java       # Result types
├── ComplianceReport.java       # Test reports
├── EngineInfo.java            # Engine metadata
├── EngineCapabilities.java    # Capability declaration
├── TableData.java             # Data structures
├── TestSuite.java             # Test suite types
├── TestCase.java              # Test case types
├── loader/                    # Test suite loaders
│   ├── TestSuiteLoader.java
│   └── YamlTestSuiteLoader.java
├── benchmark/                 # Performance benchmarking
│   ├── BenchmarkRunner.java
│   ├── BenchmarkConfig.java
│   ├── BenchmarkStats.java
│   ├── BenchmarkResult.java
│   ├── BenchmarkOperation.java
│   └── OperationMetrics.java
└── validator/                 # Plan validation
    └── SubstraitPlanValidator.java
```

## Features

- **Type Safety** - Strong typing with Java's type system
- **Builder Pattern** - Fluent APIs for configuration
- **Comprehensive Testing** - JUnit 5 test support
- **Performance** - Optimized for production use
- **Extensible** - Easy to extend and customize
- **Performance Benchmarking** - Built-in benchmarking framework

## API Reference

### ComplianceEngine

Main interface that engines must implement:

- `getEngineInfo()` - Return engine metadata
- `getCapabilities()` - Return supported features
- `executePlan(plan, inputData)` - Execute a Substrait plan
- `validatePlan(plan)` - Validate a plan before execution

### ComplianceRunner

Executes test suites:

- `runTestSuite(suite)` - Run all tests in a suite
- `runTestCase(testCase)` - Run a single test case

### YamlTestSuiteLoader

Load test suites from YAML files:

- `load(path)` - Load test suite from file
- `supports(path)` - Check if format is supported

## Performance Benchmarking

The Java SDK includes a comprehensive benchmarking framework for measuring engine performance.

### Quick Example

```java
import io.substrait.compliance.benchmark.*;
import java.util.Arrays;
import java.util.List;

BenchmarkConfig config = BenchmarkConfig.builder()
    .warmupRuns(5)
    .measurementRuns(100)
    .verbose(true)
    .build();

BenchmarkRunner runner = new BenchmarkRunner(engine, config);

List<BenchmarkOperation> operations = Arrays.asList(
    BenchmarkOperation.of("operation_name", () -> {
        // Your operation here
    })
);

BenchmarkResult result = runner.runBenchmark("benchmark_name", operations);
System.out.println(result.summary());
System.out.println(result.toCSV());
```

### Features

- **Statistical Analysis**: Min, Max, Avg, Median, P95, P99 latencies
- **Throughput Measurement**: Operations per second
- **Standard Deviation**: Measure of variance
- **CSV Export**: Export results for analysis
- **Parallel Execution**: Multi-threaded benchmarking support
- **Memory Tracking**: Optional memory usage monitoring
- **Builder Pattern**: Fluent configuration API

### Running the Example

```bash
./gradlew run --args="BenchmarkExample"
```

### Running Benchmark Tests

```bash
./gradlew test --tests "*BenchmarkRunnerTest"
```

### Advanced Usage

#### Parallel Benchmarking

```java
BenchmarkConfig config = BenchmarkConfig.builder()
    .warmupRuns(3)
    .measurementRuns(50)
    .parallelism(4)
    .build();

BenchmarkRunner runner = new BenchmarkRunner(engine, config);
BenchmarkResult result = runner.runParallelBenchmark(
    "parallel_test", 
    operations, 
    4  // thread count
);
```

#### Quick Benchmark

```java
BenchmarkStats stats = BenchmarkRunner.quickBenchmark(
    engine,
    "quick_test",
    () -> {
        // Your operation
        return null;
    },
    100  // number of runs
);
```

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests "ComplianceEngineTest"

# Generate documentation
./gradlew javadoc

# Run example
./gradlew run --args="BenchmarkExample"
```

## Testing

The SDK includes comprehensive test coverage:

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

## License

Apache License 2.0