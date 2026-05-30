# Substrait Compliance SDK - Rust

Rust SDK for decentralized Substrait compliance testing.

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
substrait-compliance = "1.0"
```

## Quick Start

```rust
use substrait_compliance::*;
use std::collections::HashMap;

// 1. Implement the ComplianceEngine trait
struct MyEngine;

impl ComplianceEngine for MyEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("MyEngine", "1.0.0", "MyCompany")
            .with_description("My Substrait engine")
    }
    
    fn get_capabilities(&self) -> EngineCapabilities {
        let mut caps = EngineCapabilities::new();
        caps.supported_relations = vec![
            "read".to_string(),
            "filter".to_string(),
            "project".to_string(),
        ];
        caps.supported_functions = vec![
            "add".to_string(),
            "subtract".to_string(),
        ];
        caps
    }
    
    fn execute_plan(
        &self,
        plan_bytes: &[u8],
        input_data: &HashMap<String, TableData>,
    ) -> error::Result<ComplianceResult> {
        // Execute Substrait plan
        let output = self.execute_internal(plan_bytes, input_data)?;
        
        Ok(ComplianceResult::new("test", TestStatus::Passed)
            .with_output(output))
    }
    
    fn validate_plan(&self, plan_bytes: &[u8]) -> error::Result<ComplianceResult> {
        // Validate plan structure
        let is_valid = self.validate_internal(plan_bytes)?;
        
        let status = if is_valid {
            TestStatus::Passed
        } else {
            TestStatus::Failed
        };
        
        Ok(ComplianceResult::new("validation", status))
    }
}

// 2. Load a test suite
let loader = YamlTestSuiteLoader::new();
let suite = loader.load(Path::new("test-suites/tpch/metadata.yaml"))?;

// 3. Run compliance tests
let engine = MyEngine;
let runner = ComplianceRunner::new(&engine);
let report = runner.run_test_suite(suite.as_ref());

// 4. Check results
println!("Passed: {}/{}", report.get_passed_count(), report.get_total_count());
println!("Pass Rate: {:.1}%", report.get_pass_rate());

for result in &report.results {
    if result.status != TestStatus::Passed {
        println!("Failed: {} - {:?}", result.test_id, result.error_message);
    }
}
```

## Architecture

```
src/
├── lib.rs          # Public API
├── engine.rs       # ComplianceEngine trait
├── runner.rs       # Test execution
├── result.rs       # Result types
├── test_suite.rs   # Test suite types
├── table_data.rs   # Data structures
├── loader.rs       # Test suite loaders
├── error.rs        # Error types
└── benchmark/      # Performance benchmarking
    └── mod.rs      # Benchmarking engine
```

## Features

- **Zero-cost abstractions** - Trait-based design with no runtime overhead
- **Type safety** - Strong typing prevents common errors
- **Memory safety** - Rust's ownership system ensures correctness
- **Performance** - Compiled to native code for maximum speed
- **Async support** - Ready for async/await patterns
- **Performance Benchmarking** - Built-in benchmarking framework

## API Reference

### ComplianceEngine

Main trait that engines must implement:

- `get_info()` - Return engine metadata
- `get_capabilities()` - Return supported features
- `execute_plan(plan_bytes, input_data)` - Execute a plan
- `validate_plan(plan_bytes)` - Validate a plan

### ComplianceRunner

Executes test suites:

- `run_test_suite(suite)` - Run all tests
- `run_test_case(test_case)` - Run single test

### YamlTestSuiteLoader

Load test suites from YAML:

- `load(path)` - Load from file
- `supports(path)` - Check format support

## Performance Benchmarking

The Rust SDK includes a comprehensive benchmarking framework for measuring engine performance.

### Quick Example

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

### Features

- **Statistical Analysis**: Min, Max, Avg, Median, P95, P99 latencies
- **Throughput Measurement**: Operations per second
- **Standard Deviation**: Measure of variance
- **CSV Export**: Export results for analysis
- **Async Support**: Full tokio async/await support
- **Configurable**: Warmup runs, measurement runs, verbosity

### Running the Example

```bash
cargo run --example benchmark_example
```

### Running Benchmark Tests

```bash
cargo test --test benchmark_test
```

## Development

```bash
# Build
cargo build

# Run tests
cargo test

# Format code
cargo fmt

# Lint
cargo clippy

# Documentation
cargo doc --open
```

## License

Apache License 2.0
