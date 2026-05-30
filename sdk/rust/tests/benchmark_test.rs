use std::collections::HashMap;
use std::error::Error;
use std::time::Duration;

use substrait_compliance::{
    BenchmarkConfig, BenchmarkRunner, Column, ComplianceEngine, ComplianceResult, DataType,
    EngineCapabilities, EngineInfo, TableData, TestStatus,
};

/// Mock engine for testing
struct MockEngine {
    name: String,
    delay_ms: u64,
}

impl MockEngine {
    fn new(name: &str, delay_ms: u64) -> Self {
        Self {
            name: name.to_string(),
            delay_ms,
        }
    }
}

impl ComplianceEngine for MockEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new(self.name.clone(), "1.0.0", "Substrait")
            .with_description("Mock engine used for benchmark tests")
    }

    fn get_capabilities(&self) -> EngineCapabilities {
        let mut capabilities = EngineCapabilities::new();
        capabilities.supported_relations = vec!["read".to_string()];
        capabilities.supported_functions = vec!["add".to_string()];
        capabilities.supported_types = vec!["INTEGER".to_string()];
        capabilities
    }

    fn execute_plan(
        &self,
        _plan: &[u8],
        _input_data: &HashMap<String, TableData>,
    ) -> substrait_compliance::error::Result<ComplianceResult> {
        if self.delay_ms > 0 {
            std::thread::sleep(Duration::from_millis(self.delay_ms));
        }

        Ok(
            ComplianceResult::new("execute-plan", TestStatus::Passed).with_output(TableData::new(
                vec![Column::new("result", DataType::Integer)],
                vec![vec!["1".to_string()]],
            )),
        )
    }

    fn validate_plan(&self, _plan: &[u8]) -> substrait_compliance::error::Result<ComplianceResult> {
        if self.delay_ms > 0 {
            std::thread::sleep(Duration::from_millis(self.delay_ms / 2));
        }

        Ok(ComplianceResult::new("validate-plan", TestStatus::Passed))
    }
}

fn single_operation(
) -> Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync> {
    Box::new(|| Ok(()))
}

#[tokio::test]
async fn test_benchmark_config_default() {
    let config = BenchmarkConfig::default();
    assert_eq!(config.warmup_runs, 5);
    assert_eq!(config.measurement_runs, 100);
    assert_eq!(config.parallelism, 1);
    assert!(config.collect_memory_stats);
    assert!(!config.verbose);
}

#[tokio::test]
async fn test_benchmark_config_custom() {
    let config = BenchmarkConfig {
        warmup_runs: 10,
        measurement_runs: 50,
        parallelism: 4,
        collect_memory_stats: false,
        verbose: true,
    };
    assert_eq!(config.warmup_runs, 10);
    assert_eq!(config.measurement_runs, 50);
    assert_eq!(config.parallelism, 4);
    assert!(!config.collect_memory_stats);
    assert!(config.verbose);
}

#[tokio::test]
async fn test_benchmark_single_operation() {
    let engine = MockEngine::new("TestEngine", 1);
    let config = BenchmarkConfig {
        warmup_runs: 2,
        measurement_runs: 10,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .benchmark_operation("test_op", &|| Ok(()))
        .await
        .expect("Benchmark should succeed");

    assert_eq!(result.operation_name, "test_op");
    assert_eq!(result.total_runs, 10);
    assert!(result.min_time <= result.max_time);
    assert!(result.min_time <= result.avg_time);
    assert!(result.avg_time <= result.max_time);
    assert!(result.throughput > 0.0);
}

#[tokio::test]
async fn test_benchmark_multiple_operations() {
    let engine = MockEngine::new("TestEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 1,
        measurement_runs: 5,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let operations: Vec<(
        &str,
        Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
    )> = vec![
        (
            "op1",
            Box::new(|| {
                let _x = 1 + 1;
                Ok(())
            }),
        ),
        (
            "op2",
            Box::new(|| {
                let _s = format!("{}{}", "hello", "world");
                Ok(())
            }),
        ),
    ];

    let result = runner
        .run_benchmark("multi_op_test", operations)
        .await
        .expect("Benchmark should succeed");

    assert_eq!(result.benchmark_name, "multi_op_test");
    assert_eq!(result.engine_name, "TestEngine");
    assert_eq!(result.stats.len(), 2);
    assert!(result.total_duration.as_nanos() > 0);
}

#[tokio::test]
async fn test_benchmark_stats_ordering() {
    let engine = MockEngine::new("TestEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 0,
        measurement_runs: 10,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .benchmark_operation("ordering_test", &|| Ok(()))
        .await
        .expect("Benchmark should succeed");

    assert!(result.min_time <= result.median_time);
    assert!(result.median_time <= result.p95_time);
    assert!(result.p95_time <= result.p99_time);
    assert!(result.p99_time <= result.max_time);
}

#[tokio::test]
async fn test_benchmark_result_summary() {
    let engine = MockEngine::new("TestEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 1,
        measurement_runs: 5,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let operations = vec![(
        "summary_test",
        Box::new(|| Ok(())) as Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
    )];

    let result = runner
        .run_benchmark("summary_test", operations)
        .await
        .expect("Benchmark should succeed");

    let summary = result.summary();
    assert!(summary.contains("Benchmark: summary_test"));
    assert!(summary.contains("Engine: TestEngine"));
    assert!(summary.contains("Total Duration:"));
    assert!(summary.contains("Operation: summary_test"));
}

#[tokio::test]
async fn test_benchmark_result_csv() {
    let engine = MockEngine::new("TestEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 1,
        measurement_runs: 5,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let operations = vec![(
        "csv_test",
        Box::new(|| Ok(())) as Box<dyn Fn() -> Result<(), Box<dyn Error>> + Send + Sync>,
    )];

    let result = runner
        .run_benchmark("csv_test", operations)
        .await
        .expect("Benchmark should succeed");

    let csv = result.to_csv();
    assert!(csv.contains("Engine,Benchmark,Operation,TotalRuns"));
    assert!(csv.contains("TestEngine,csv_test,csv_test,5"));
}

#[tokio::test]
async fn test_quick_benchmark_equivalent() {
    let engine = MockEngine::new("QuickEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 0,
        measurement_runs: 20,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);
    let result = runner
        .benchmark_operation("quick_test", &*single_operation())
        .await
        .expect("Quick benchmark should succeed");

    assert_eq!(result.operation_name, "quick_test");
    assert_eq!(result.total_runs, 20);
    assert!(result.throughput > 0.0);
}

#[tokio::test]
async fn test_benchmark_with_delay() {
    let engine = MockEngine::new("DelayEngine", 5);
    let config = BenchmarkConfig {
        warmup_runs: 1,
        measurement_runs: 3,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .benchmark_operation("delay_test", &|| {
            std::thread::sleep(Duration::from_millis(5));
            Ok(())
        })
        .await
        .expect("Benchmark should succeed");

    assert!(result.avg_time.as_millis() >= 4);
    assert_eq!(result.total_runs, 3);
}

#[tokio::test]
async fn test_benchmark_error_handling() {
    let engine = MockEngine::new("ErrorEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 0,
        measurement_runs: 5,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .benchmark_operation("error_test", &|| Err("Test error".into()))
        .await;

    assert!(result.is_err());
}

#[tokio::test]
async fn test_benchmark_throughput_calculation() {
    let engine = MockEngine::new("ThroughputEngine", 0);
    let config = BenchmarkConfig {
        warmup_runs: 0,
        measurement_runs: 100,
        verbose: false,
        ..Default::default()
    };

    let runner = BenchmarkRunner::new(&engine, config);

    let result = runner
        .benchmark_operation("throughput_test", &|| Ok(()))
        .await
        .expect("Benchmark should succeed");

    assert!(result.throughput.is_finite());
    assert!(result.throughput > 0.0);
}

// Made with Bob
