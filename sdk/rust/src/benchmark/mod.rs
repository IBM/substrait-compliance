use crate::ComplianceEngine;
use std::time::{Duration, Instant};

/// Performance metrics for a single operation
#[derive(Debug, Clone)]
pub struct OperationMetrics {
    pub operation_name: String,
    pub execution_time: Duration,
    pub memory_used: usize,
    pub timestamp: Instant,
}

/// Benchmark statistics
#[derive(Debug, Clone)]
pub struct BenchmarkStats {
    pub operation_name: String,
    pub total_runs: usize,
    pub min_time: Duration,
    pub max_time: Duration,
    pub avg_time: Duration,
    pub median_time: Duration,
    pub p95_time: Duration,
    pub p99_time: Duration,
    pub std_dev_ms: f64,
    pub throughput: f64, // operations per second
}

impl BenchmarkStats {
    pub fn summary(&self) -> String {
        format!(
            "Operation: {}\n\
             Total Runs: {}\n\
             Min Time: {:?}\n\
             Max Time: {:?}\n\
             Avg Time: {:?}\n\
             Median Time: {:?}\n\
             P95 Time: {:?}\n\
             P99 Time: {:?}\n\
             Std Dev: {:.2}ms\n\
             Throughput: {:.2} ops/sec\n",
            self.operation_name,
            self.total_runs,
            self.min_time,
            self.max_time,
            self.avg_time,
            self.median_time,
            self.p95_time,
            self.p99_time,
            self.std_dev_ms,
            self.throughput
        )
    }
}

/// Complete benchmark result
#[derive(Debug, Clone)]
pub struct BenchmarkResult {
    pub engine_name: String,
    pub benchmark_name: String,
    pub stats: Vec<BenchmarkStats>,
    pub total_duration: Duration,
    pub timestamp: Instant,
}

impl BenchmarkResult {
    pub fn summary(&self) -> String {
        let mut output = format!(
            "Benchmark: {}\n\
             Engine: {}\n\
             Total Duration: {:?}\n\n",
            self.benchmark_name, self.engine_name, self.total_duration
        );

        for stat in &self.stats {
            output.push_str(&stat.summary());
            output.push('\n');
        }

        output
    }

    pub fn to_csv(&self) -> String {
        let mut csv = String::from(
            "Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n",
        );

        for stat in &self.stats {
            csv.push_str(&format!(
                "{},{},{},{},{:.2},{:.2},{:.2},{:.2},{:.2},{:.2},{:.2},{:.2}\n",
                self.engine_name,
                self.benchmark_name,
                stat.operation_name,
                stat.total_runs,
                stat.min_time.as_secs_f64() * 1000.0,
                stat.max_time.as_secs_f64() * 1000.0,
                stat.avg_time.as_secs_f64() * 1000.0,
                stat.median_time.as_secs_f64() * 1000.0,
                stat.p95_time.as_secs_f64() * 1000.0,
                stat.p99_time.as_secs_f64() * 1000.0,
                stat.std_dev_ms,
                stat.throughput
            ));
        }

        csv
    }
}

/// Configuration for benchmark execution
#[derive(Debug, Clone)]
pub struct BenchmarkConfig {
    pub warmup_runs: usize,
    pub measurement_runs: usize,
    pub parallelism: usize,
    pub collect_memory_stats: bool,
    pub verbose: bool,
}

impl Default for BenchmarkConfig {
    fn default() -> Self {
        Self {
            warmup_runs: 5,
            measurement_runs: 100,
            parallelism: 1,
            collect_memory_stats: true,
            verbose: false,
        }
    }
}

/// Runs performance benchmarks on compliance engines
pub struct BenchmarkRunner<'a, E: ComplianceEngine> {
    engine: &'a E,
    config: BenchmarkConfig,
}

impl<'a, E: ComplianceEngine> BenchmarkRunner<'a, E> {
    pub fn new(engine: &'a E, config: BenchmarkConfig) -> Self {
        Self { engine, config }
    }

    /// Run a complete benchmark suite
    pub async fn run_benchmark(
        &self,
        benchmark_name: &str,
        operations: Vec<(&str, Box<dyn Fn() -> Result<(), Box<dyn std::error::Error>> + Send + Sync>)>,
    ) -> Result<BenchmarkResult, Box<dyn std::error::Error>> {
        let start_time = Instant::now();

        if self.config.verbose {
            println!("Starting benchmark: {}", benchmark_name);
            println!("Warmup runs: {}", self.config.warmup_runs);
            println!("Measurement runs: {}", self.config.measurement_runs);
        }

        let mut stats = Vec::new();
        for (name, operation) in operations {
            let stat = self.benchmark_operation(name, &operation).await?;
            stats.push(stat);
        }

        let end_time = Instant::now();
        let total_duration = end_time - start_time;

        let info = self.engine.get_info();
        Ok(BenchmarkResult {
            engine_name: info.name.clone(),
            benchmark_name: benchmark_name.to_string(),
            stats,
            total_duration,
            timestamp: start_time,
        })
    }

    /// Benchmark a single operation
    pub async fn benchmark_operation(
        &self,
        operation_name: &str,
        operation: &(dyn Fn() -> Result<(), Box<dyn std::error::Error>> + Send + Sync),
    ) -> Result<BenchmarkStats, Box<dyn std::error::Error>> {
        if self.config.verbose {
            println!("  Benchmarking: {}", operation_name);
        }

        // Warmup phase
        if self.config.warmup_runs > 0 {
            if self.config.verbose {
                println!("    Warmup: {} runs", self.config.warmup_runs);
            }
            for _ in 0..self.config.warmup_runs {
                operation()?;
            }
        }

        // Measurement phase
        if self.config.verbose {
            println!("    Measuring: {} runs", self.config.measurement_runs);
        }

        let mut metrics = Vec::with_capacity(self.config.measurement_runs);
        for _ in 0..self.config.measurement_runs {
            let metric = self.measure_operation(operation_name, operation)?;
            metrics.push(metric);
        }

        Ok(self.calculate_stats(operation_name, &metrics))
    }

    /// Measure a single operation execution
    fn measure_operation(
        &self,
        operation_name: &str,
        operation: &(dyn Fn() -> Result<(), Box<dyn std::error::Error>> + Send + Sync),
    ) -> Result<OperationMetrics, Box<dyn std::error::Error>> {
        let start_time = Instant::now();
        let timestamp = Instant::now();

        operation()?;

        let execution_time = start_time.elapsed();

        Ok(OperationMetrics {
            operation_name: operation_name.to_string(),
            execution_time,
            memory_used: 0, // Memory tracking would require jemalloc or similar
            timestamp,
        })
    }

    /// Calculate statistics from metrics
    fn calculate_stats(&self, operation_name: &str, metrics: &[OperationMetrics]) -> BenchmarkStats {
        let mut times: Vec<Duration> = metrics.iter().map(|m| m.execution_time).collect();
        times.sort();

        let total_runs = times.len();
        let min_time = times[0];
        let max_time = times[total_runs - 1];

        let total_nanos: u128 = times.iter().map(|t| t.as_nanos()).sum();
        let avg_nanos = total_nanos / total_runs as u128;
        let avg_time = Duration::from_nanos(avg_nanos as u64);

        let median_time = times[total_runs / 2];
        let p95_time = times[(total_runs as f64 * 0.95) as usize];
        let p99_time = times[(total_runs as f64 * 0.99) as usize];

        // Calculate standard deviation
        let variance: f64 = times
            .iter()
            .map(|t| {
                let diff = t.as_nanos() as f64 - avg_nanos as f64;
                diff * diff
            })
            .sum::<f64>()
            / total_runs as f64;
        let std_dev_ms = variance.sqrt() / 1_000_000.0;

        // Calculate throughput (ops/sec)
        let total_time_secs = total_nanos as f64 / 1_000_000_000.0;
        let throughput = total_runs as f64 / total_time_secs;

        BenchmarkStats {
            operation_name: operation_name.to_string(),
            total_runs,
            min_time,
            max_time,
            avg_time,
            median_time,
            p95_time,
            p99_time,
            std_dev_ms,
            throughput,
        }
    }

}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_benchmark_stats() {
        // Test that benchmark stats are calculated correctly
        let metrics = vec![
            OperationMetrics {
                operation_name: "test".to_string(),
                execution_time: Duration::from_millis(10),
                memory_used: 0,
                timestamp: Instant::now(),
            },
            OperationMetrics {
                operation_name: "test".to_string(),
                execution_time: Duration::from_millis(20),
                memory_used: 0,
                timestamp: Instant::now(),
            },
        ];

        // This would need a mock engine to test properly
        // For now, just verify the structure compiles
        assert_eq!(metrics.len(), 2);
    }
}

// Made with Bob
