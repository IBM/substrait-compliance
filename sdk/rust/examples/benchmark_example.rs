use substrait_compliance::{
    BenchmarkConfig, BenchmarkRunner, ComplianceEngine, EngineCapabilities, EngineInfo,
};
use std::error::Error;

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

#[async_trait::async_trait]
impl ComplianceEngine for MockEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo {
            name: self.name.clone(),
            version: "1.0.0".to_string(),
            substrait_version: "0.20.0".to_string(),
        }
    }

    fn get_capabilities(&self) -> EngineCapabilities {
        EngineCapabilities {
            supports_json: true,
            supports_binary: true,
            max_plan_size: 1024 * 1024,
        }
    }

    async fn execute_plan(
        &self,
        _plan: &[u8],
        _input_data: Vec<substrait_compliance::TableData>,
    ) -> Result<substrait_compliance::TableData, Box<dyn Error + Send + Sync>> {
        // Simulate work
        tokio::time::sleep(tokio::time::Duration::from_micros(100)).await;
        Ok(substrait_compliance::TableData {
            columns: vec![],
            rows: vec![],
        })
    }

    async fn validate_plan(&self, _plan: &[u8]) -> Result<bool, Box<dyn Error + Send + Sync>> {
        // Simulate validation
        tokio::time::sleep(tokio::time::Duration::from_micros(50)).await;
        Ok(true)
    }
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

    let operations: Vec<(
        &str,
        Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
    )> = vec![
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
    ];

    let result = runner.run_benchmark("basic_operations", operations).await?;
    println!("{}", result.summary());

    // Example 2: Plan execution benchmark
    println!("\n=== Example 2: Plan Execution Benchmark ===");
    let plan_data = vec![0u8; 1024]; // Mock plan data

    let stats = BenchmarkRunner::quick_benchmark(
        &engine,
        "execute_plan",
        Box::new(move || {
            let rt = tokio::runtime::Runtime::new().unwrap();
            rt.block_on(async {
                engine
                    .execute_plan(&plan_data, vec![])
                    .await
                    .map(|_| ())
                    .map_err(|e| e.into())
            })
        }),
        100,
    )
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

    let operations: Vec<(
        &str,
        Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
    )> = vec![(
        "computation",
        Box::new(|| {
            let _result: i32 = (0..1000).sum();
            Ok(())
        }),
    )];

    let fast_runner = BenchmarkRunner::new(&fast_engine, config.clone());
    let fast_result = fast_runner
        .run_benchmark("comparison", operations.clone())
        .await?;

    let slow_runner = BenchmarkRunner::new(&slow_engine, config);
    let slow_result = slow_runner.run_benchmark("comparison", operations).await?;

    println!("Fast Engine Results:");
    println!("{}", fast_result.summary());
    println!("\nSlow Engine Results:");
    println!("{}", slow_result.summary());

    // Example 5: Scalability test
    println!("\n=== Example 5: Scalability Test ===");
    for size in [10, 100, 1000] {
        let operations: Vec<(
            &str,
            Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
        )> = vec![(
            &format!("process_{}_items", size),
            Box::new(move || {
                let _result: i32 = (0..size).sum();
                Ok(())
            }),
        )];

        let config = BenchmarkConfig {
            warmup_runs: 2,
            measurement_runs: 20,
            verbose: false,
            ..Default::default()
        };

        let runner = BenchmarkRunner::new(&engine, config);
        let result = runner
            .run_benchmark(&format!("scalability_{}", size), operations)
            .await?;

        println!("\nSize: {} items", size);
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

// Made with Bob
