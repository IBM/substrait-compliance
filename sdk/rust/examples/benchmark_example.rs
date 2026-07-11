use std::collections::HashMap;
use std::error::Error;

use substrait_compliance::{
    BenchmarkConfig, BenchmarkRunner, Column, ComplianceEngine, ComplianceResult, DataType,
    EngineCapabilities, EngineInfo, TableData, TestStatus,
};

/// Mock engine for demonstration
struct MockEngine {
    name: String,
}

impl MockEngine {
    fn new(name: &str) -> Self {
        Self {
            name: name.to_string(),
        }
    }
}

impl ComplianceEngine for MockEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new(self.name.clone(), "1.0.0", "Substrait")
            .with_description("Mock engine used for benchmark examples")
    }

    fn get_capabilities(&self) -> EngineCapabilities {
        let mut capabilities = EngineCapabilities::new();
        capabilities.supported_relations = vec!["read".to_string(), "project".to_string()];
        capabilities.supported_functions = vec!["add".to_string(), "concat".to_string()];
        capabilities.supported_types = vec!["INTEGER".to_string(), "VARCHAR".to_string()];
        capabilities
    }

    fn execute_plan(
        &self,
        _plan: &[u8],
        _input_data: &HashMap<String, TableData>,
    ) -> substrait_compliance::error::Result<ComplianceResult> {
        Ok(
            ComplianceResult::new("benchmark-execute", TestStatus::Passed).with_output(
                TableData::new(
                    vec![Column::new("result", DataType::Integer)],
                    vec![vec!["1".to_string()]],
                ),
            ),
        )
    }

    fn validate_plan(&self, _plan: &[u8]) -> substrait_compliance::error::Result<ComplianceResult> {
        Ok(ComplianceResult::new(
            "benchmark-validate",
            TestStatus::Passed,
        ))
    }
}

fn make_operations() -> Vec<(
    &'static str,
    Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
)> {
    vec![
        (
            "simple_addition",
            Box::new(|| {
                let _result = 1 + 1;
                Ok(())
            }),
        ),
        (
            "string_concat",
            Box::new(|| {
                let _result = format!("{}{}", "hello", "world");
                Ok(())
            }),
        ),
    ]
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    println!("Substrait Compliance Rust SDK - Benchmark Example\n");

    let engine = MockEngine::new("MockEngine");

    // Example 1: Basic operation benchmark
    println!("=== Example 1: Basic Operation Benchmark ===");
    let config = BenchmarkConfig {
        warmup_runs: 5,
        measurement_runs: 50,
        verbose: true,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .run_benchmark("basic_operations", make_operations())
        .await?;
    println!("{}", result.summary());

    // Example 2: Plan execution benchmark
    println!("\n=== Example 2: Plan Execution Benchmark ===");
    let plan_data = vec![0u8; 1024];
    let input_data: HashMap<String, TableData> = HashMap::new();

    let config = BenchmarkConfig {
        warmup_runs: 5,
        measurement_runs: 100,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);
    let stats = runner
        .benchmark_operation("execute_plan", &|| {
            engine.execute_plan(&plan_data, &input_data).map(|_| ()).map_err(|e| e.into())
        })
        .await?;

    println!("{}", stats.summary());

    // Example 3: Export to CSV
    println!("\n=== Example 3: Export Results to CSV ===");
    let csv_output = result.to_csv();
    println!("CSV Output:\n{}", csv_output);

    // Example 4: Comparison benchmark
    println!("\n=== Example 4: Engine Comparison ===");
    let fast_engine = MockEngine::new("FastEngine");
    let slow_engine = MockEngine::new("SlowEngine");

    let config = BenchmarkConfig {
        warmup_runs: 3,
        measurement_runs: 30,
        verbose: false,
        ..Default::default()
    };

    let fast_runner = BenchmarkRunner::new(&fast_engine, config.clone());
    let fast_result = fast_runner
        .run_benchmark("comparison", make_operations())
        .await?;

    let slow_runner = BenchmarkRunner::new(&slow_engine, config);
    let slow_result = slow_runner
        .run_benchmark("comparison", make_operations())
        .await?;

    println!("Fast Engine Results:");
    println!("{}", fast_result.summary());
    println!("\nSlow Engine Results:");
    println!("{}", slow_result.summary());

    // Example 5: Scalability test
    println!("\n=== Example 5: Scalability Test ===");
    for size in [10, 100, 1000] {
        let operation_name = format!("process_{}_items", size);
        let config = BenchmarkConfig {
            warmup_runs: 2,
            measurement_runs: 20,
            verbose: false,
            ..Default::default()
        };

        let runner = BenchmarkRunner::new(&engine, config);
        let result = runner
            .run_benchmark(
                &format!("scalability_{}", size),
                vec![(
                    "process_items",
                    Box::new(move || {
                        let _result: i32 = (0..size).sum();
                        Ok(())
                    }),
                )],
            )
            .await?;

        println!("\nSize: {} items ({})", size, operation_name);
        for stat in &result.stats {
            println!(
                "  Avg: {:?}, Throughput: {:.2} ops/sec",
                stat.avg_time, stat.throughput
            );
        }
    }

    println!("\n=== Benchmark Complete ===");
    Ok(())
}

