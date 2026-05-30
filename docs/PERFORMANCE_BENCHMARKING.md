# Performance Benchmarking Across All SDKs

This document provides a comprehensive guide to performance benchmarking capabilities across all Substrait Compliance Framework SDKs.

## Overview

Performance benchmarking is essential for:
- Measuring engine execution performance
- Comparing different engine implementations
- Identifying performance bottlenecks
- Tracking performance regressions
- Validating scalability characteristics

## Benchmarking Features

All SDK implementations should provide:

### Core Metrics
- **Execution Time**: Min, Max, Average, Median, P95, P99
- **Throughput**: Operations per second
- **Standard Deviation**: Measure of variance
- **Memory Usage**: Optional memory consumption tracking

### Benchmark Types
1. **Basic Operation Benchmarks** - Test individual operations
2. **Plan Execution Benchmarks** - Test query plan execution
3. **Engine Comparison** - Compare multiple engines
4. **Scalability Testing** - Test with varying data sizes
5. **Parallel Execution** - Test concurrent execution
6. **Memory Profiling** - Track memory usage patterns

### Configuration Options
- Warmup runs (default: 5-10)
- Measurement runs (default: 50-100)
- Parallelism level
- Memory tracking enable/disable
- Verbose output

## Implementation Status

### ✅ Scala SDK (Complete)
**Location**: `sdk/scala/src/main/scala/io/substrait/compliance/benchmark/`

**Files**:
- `BenchmarkRunner.scala` - Core benchmarking engine
- `BenchmarkExample.scala` - Example usage with 6 scenarios
- `BenchmarkRunnerSpec.scala` - Comprehensive tests

**Usage**:
```scala
val runner = BenchmarkRunner(engine, BenchmarkConfig(
  warmupRuns = 10,
  measurementRuns = 100
))
val result = runner.runBenchmark("My Benchmark", operations)
println(result.summary)
```

### ✅ C++ SDK (Implemented)
**Location**: `sdk/cpp/include/substrait_compliance/benchmark_runner.hpp`

**Features**:
- Header-only implementation
- RAII-based resource management
- High-resolution timing with `std::chrono`
- Statistical analysis with STL algorithms

**Usage**:
```cpp
BenchmarkRunner runner(engine, config);
auto result = runner.run_benchmark("My Benchmark", operations);
std::cout << result.summary();
```

### 🔄 Go SDK (To Be Implemented)
**Recommended Location**: `sdk/go/benchmark/`

**Suggested Implementation**:
```go
// benchmark_runner.go
type BenchmarkRunner struct {
    engine ComplianceEngine
    config BenchmarkConfig
}

func (r *BenchmarkRunner) RunBenchmark(name string, ops []Operation) BenchmarkResult {
    // Use time.Now() for timing
    // Use runtime.MemStats for memory tracking
    // Use goroutines for parallel execution
}
```

**Key Go Features to Use**:
- `time.Duration` for timing
- `runtime.MemStats` for memory profiling
- Goroutines for parallel benchmarks
- `testing.B` integration for standard Go benchmarks

### 🔄 TypeScript SDK (To Be Implemented)
**Recommended Location**: `sdk/typescript/src/benchmark/`

**Suggested Implementation**:
```typescript
// BenchmarkRunner.ts
export class BenchmarkRunner {
  constructor(
    private engine: ComplianceEngine,
    private config: BenchmarkConfig
  ) {}

  async runBenchmark(
    name: string,
    operations: Array<[string, () => Promise<any>]>
  ): Promise<BenchmarkResult> {
    // Use performance.now() for high-resolution timing
    // Use process.memoryUsage() for memory tracking
  }
}
```

**Key TypeScript Features to Use**:
- `performance.now()` for high-resolution timing
- `process.memoryUsage()` for Node.js memory tracking
- `Promise.all()` for parallel execution
- Jest integration for testing

### 🔄 C#/.NET SDK (To Be Implemented)
**Recommended Location**: `sdk/csharp/Substrait.Compliance/Benchmark/`

**Suggested Implementation**:
```csharp
// BenchmarkRunner.cs
public class BenchmarkRunner
{
    public async Task<BenchmarkResult> RunBenchmarkAsync(
        string benchmarkName,
        IEnumerable<(string, Func<Task>)> operations)
    {
        // Use Stopwatch for timing
        // Use GC.GetTotalMemory() for memory tracking
        // Use Task.WhenAll() for parallel execution
    }
}
```

**Key C# Features to Use**:
- `System.Diagnostics.Stopwatch` for timing
- `GC.GetTotalMemory()` for memory tracking
- `Task.WhenAll()` for parallel execution
- BenchmarkDotNet integration for advanced benchmarking

### 🔄 Python SDK (To Be Implemented)
**Recommended Location**: `sdk/python/substrait_compliance/benchmark/`

**Suggested Implementation**:
```python
# benchmark_runner.py
class BenchmarkRunner:
    def __init__(self, engine: ComplianceEngine, config: BenchmarkConfig):
        self.engine = engine
        self.config = config
    
    def run_benchmark(
        self,
        benchmark_name: str,
        operations: List[Tuple[str, Callable]]
    ) -> BenchmarkResult:
        # Use time.perf_counter() for timing
        # Use tracemalloc for memory tracking
        # Use asyncio for async operations
```

**Key Python Features to Use**:
- `time.perf_counter()` for high-resolution timing
- `tracemalloc` for memory profiling
- `asyncio` for async operations
- `multiprocessing` for parallel execution
- Integration with `pytest-benchmark`

### ✅ Rust SDK (Complete)
**Location**: `sdk/rust/src/benchmark/mod.rs`

**Files**:
- `mod.rs` - Core benchmarking engine with async support (318 lines)
- `examples/benchmark_example.rs` - Comprehensive example with 5 scenarios (181 lines)

**Usage**:
```rust
use substrait_compliance::{BenchmarkConfig, BenchmarkRunner};

let config = BenchmarkConfig {
    warmup_runs: 5,
    measurement_runs: 100,
    verbose: true,
    ..Default::default()
};

let runner = BenchmarkRunner::new(&engine, config);

let operations = vec![
    ("operation_name", Box::new(|| {
        // Your operation here
        Ok(())
    }) as Box<dyn Fn() -> Result<(), Box<dyn std::error::Error>> + Send + Sync>),
];

let result = runner.run_benchmark("benchmark_name", operations).await?;
println!("{}", result.summary());
println!("{}", result.to_csv());
```

**Key Features**:
- `std::time::Instant` for high-precision timing
- Async/await support with tokio
- Statistical analysis (min, max, avg, median, P95, P99)
- CSV export functionality
- Memory tracking support

### ✅ Java SDK (Complete)
**Location**: `sdk/java/src/main/java/io/substrait/compliance/benchmark/`

**Files**:
- `BenchmarkRunner.java` - Core benchmarking engine (227 lines)
- `BenchmarkConfig.java` - Configuration builder (81 lines)
- `BenchmarkStats.java` - Statistical results (125 lines)
- `BenchmarkResult.java` - Complete benchmark results (104 lines)
- `BenchmarkOperation.java` - Operation wrapper (33 lines)
- `OperationMetrics.java` - Individual metrics (37 lines)
- `BenchmarkExample.java` - Example with 6 scenarios (213 lines)

**Usage**:
```java
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

**Key Features**:
- `System.nanoTime()` for high-resolution timing
- `Runtime.getRuntime()` for memory tracking
- `ExecutorService` for parallel execution
- Builder pattern for configuration
- CSV export functionality
- Parallel benchmark support

## Standard Benchmark Suite

All implementations should support these standard benchmarks:

### 1. Basic Operations Benchmark
```
- getInfo() - 1000 iterations
- getCapabilities() - 1000 iterations
- validatePlan() - 100 iterations
```

### 2. Plan Execution Benchmark
```
- Small dataset (100 rows) - 100 iterations
- Medium dataset (10,000 rows) - 50 iterations
- Large dataset (1,000,000 rows) - 10 iterations
```

### 3. Scalability Benchmark
```
Test with: 100, 1K, 10K, 100K, 1M rows
Measure: Execution time, throughput, memory usage
```

### 4. Parallel Execution Benchmark
```
Test with: 1, 2, 4, 8, 16 threads
Measure: Throughput scaling, overhead
```

## Output Formats

All implementations should support:

### 1. Console Output
```
Benchmark: Plan Execution
Engine: MyEngine v1.0.0
Total Duration: 5432ms

Operation: executePlan_1k_rows
Total Runs: 100
Min Time: 45ms
Max Time: 123ms
Avg Time: 54ms
Median Time: 52ms
P95 Time: 67ms
P99 Time: 89ms
Std Dev: 12.3ms
Throughput: 18.5 ops/sec
```

### 2. CSV Export
```csv
Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput
MyEngine,PlanExecution,executePlan_1k,100,45,123,54,52,67,89,12.3,18.5
```

### 3. JSON Export
```json
{
  "engine_name": "MyEngine",
  "benchmark_name": "Plan Execution",
  "timestamp": "2024-01-15T10:30:00Z",
  "stats": [
    {
      "operation_name": "executePlan_1k_rows",
      "total_runs": 100,
      "min_time_ms": 45,
      "max_time_ms": 123,
      "avg_time_ms": 54,
      "median_time_ms": 52,
      "p95_time_ms": 67,
      "p99_time_ms": 89,
      "std_dev_ms": 12.3,
      "throughput": 18.5
    }
  ]
}
```

## Best Practices

### 1. Warmup Phase
- Always include warmup runs (5-10 iterations)
- Discard warmup results from statistics
- Allows JIT compilation, cache warming, etc.

### 2. Statistical Significance
- Run enough iterations (50-100 minimum)
- Calculate confidence intervals
- Report standard deviation

### 3. Isolation
- Run benchmarks in isolated environment
- Minimize background processes
- Use consistent hardware

### 4. Reproducibility
- Document system configuration
- Use fixed random seeds
- Version all dependencies

### 5. Comparison
- Use same hardware for comparisons
- Run multiple times and average
- Report variance

## Integration with CI/CD

Benchmarks should be integrated into CI/CD pipelines:

```yaml
# .github/workflows/benchmark.yml
name: Performance Benchmarks

on:
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 0 * * 0'  # Weekly

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Benchmarks
        run: |
          # Run benchmarks for each SDK
          cd sdk/scala && sbt "runMain benchmark.BenchmarkExample"
          cd sdk/cpp && ./build/benchmark_example
          # ... other SDKs
      - name: Upload Results
        uses: actions/upload-artifact@v2
        with:
          name: benchmark-results
          path: benchmark_results.csv
```

## Future Enhancements

1. **Automated Performance Regression Detection**
   - Compare against baseline
   - Alert on significant regressions
   - Track performance over time

2. **Distributed Benchmarking**
   - Run benchmarks across multiple machines
   - Aggregate results
   - Test network performance

3. **Real-world Workload Simulation**
   - TPC-H/TPC-DS query benchmarks
   - Mixed workload scenarios
   - Concurrent user simulation

4. **Advanced Profiling**
   - CPU profiling integration
   - Memory leak detection
   - Cache miss analysis

## Contributing

When implementing benchmarking for a new SDK:

1. Follow the patterns established in Scala/C++ implementations
2. Provide comprehensive examples
3. Include unit tests
4. Update this documentation
5. Add CI/CD integration

## References

- [Scala Benchmark Implementation](../sdk/scala/src/main/scala/io/substrait/compliance/benchmark/)
- [C++ Benchmark Implementation](../sdk/cpp/include/substrait_compliance/benchmark_runner.hpp)
- [JMH (Java)](https://github.com/openjdk/jmh)
- [BenchmarkDotNet (C#)](https://benchmarkdotnet.org/)
- [Criterion (Rust)](https://github.com/bheisler/criterion.rs)
- [pytest-benchmark (Python)](https://pytest-benchmark.readthedocs.io/)