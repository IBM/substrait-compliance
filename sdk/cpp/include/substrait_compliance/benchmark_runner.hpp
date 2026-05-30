#pragma once

#include "compliance_engine.hpp"
#include <chrono>
#include <vector>
#include <string>
#include <functional>
#include <algorithm>
#include <numeric>
#include <cmath>
#include <fstream>
#include <sstream>

namespace substrait::compliance {

/**
 * Performance metrics for a single operation
 */
struct OperationMetrics {
    std::string operation_name;
    std::chrono::nanoseconds execution_time;
    size_t memory_used;
    std::chrono::system_clock::time_point timestamp;
};

/**
 * Benchmark statistics
 */
struct BenchmarkStats {
    std::string operation_name;
    int total_runs;
    std::chrono::nanoseconds min_time;
    std::chrono::nanoseconds max_time;
    std::chrono::nanoseconds avg_time;
    std::chrono::nanoseconds median_time;
    std::chrono::nanoseconds p95_time;
    std::chrono::nanoseconds p99_time;
    double std_dev_ms;
    double throughput; // operations per second

    std::string summary() const {
        std::ostringstream oss;
        oss << "Operation: " << operation_name << "\n"
            << "Total Runs: " << total_runs << "\n"
            << "Min Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(min_time).count() << "ms\n"
            << "Max Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(max_time).count() << "ms\n"
            << "Avg Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(avg_time).count() << "ms\n"
            << "Median Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(median_time).count() << "ms\n"
            << "P95 Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(p95_time).count() << "ms\n"
            << "P99 Time: " << std::chrono::duration_cast<std::chrono::milliseconds>(p99_time).count() << "ms\n"
            << "Std Dev: " << std_dev_ms << "ms\n"
            << "Throughput: " << throughput << " ops/sec\n";
        return oss.str();
    }
};

/**
 * Complete benchmark result
 */
struct BenchmarkResult {
    std::string engine_name;
    std::string benchmark_name;
    std::vector<BenchmarkStats> stats;
    std::chrono::nanoseconds total_duration;
    std::chrono::system_clock::time_point timestamp;

    std::string summary() const {
        std::ostringstream oss;
        oss << "Benchmark: " << benchmark_name << "\n"
            << "Engine: " << engine_name << "\n"
            << "Total Duration: " << std::chrono::duration_cast<std::chrono::milliseconds>(total_duration).count() << "ms\n\n";
        
        for (const auto& stat : stats) {
            oss << stat.summary() << "\n";
        }
        return oss.str();
    }

    std::string to_csv() const {
        std::ostringstream oss;
        oss << "Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n";
        
        for (const auto& stat : stats) {
            oss << engine_name << ","
                << benchmark_name << ","
                << stat.operation_name << ","
                << stat.total_runs << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.min_time).count() << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.max_time).count() << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.avg_time).count() << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.median_time).count() << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.p95_time).count() << ","
                << std::chrono::duration_cast<std::chrono::milliseconds>(stat.p99_time).count() << ","
                << stat.std_dev_ms << ","
                << stat.throughput << "\n";
        }
        return oss.str();
    }
};

/**
 * Configuration for benchmark execution
 */
struct BenchmarkConfig {
    int warmup_runs = 5;
    int measurement_runs = 100;
    bool collect_memory_stats = true;
    bool verbose = false;
};

/**
 * Runs performance benchmarks on compliance engines
 */
class BenchmarkRunner {
public:
    explicit BenchmarkRunner(ComplianceEngine& engine, const BenchmarkConfig& config = BenchmarkConfig())
        : engine_(engine), config_(config) {}

    /**
     * Run a complete benchmark suite
     */
    BenchmarkResult run_benchmark(
        const std::string& benchmark_name,
        const std::vector<std::pair<std::string, std::function<void()>>>& operations
    ) {
        auto start_time = std::chrono::system_clock::now();
        
        if (config_.verbose) {
            std::cout << "Starting benchmark: " << benchmark_name << "\n";
            std::cout << "Warmup runs: " << config_.warmup_runs << "\n";
            std::cout << "Measurement runs: " << config_.measurement_runs << "\n";
        }

        std::vector<BenchmarkStats> stats;
        for (const auto& [name, op] : operations) {
            stats.push_back(benchmark_operation(name, op));
        }

        auto end_time = std::chrono::system_clock::now();
        auto total_duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end_time - start_time);

        return BenchmarkResult{
            engine_.get_info().name,
            benchmark_name,
            stats,
            total_duration,
            start_time
        };
    }

    /**
     * Benchmark a single operation
     */
    BenchmarkStats benchmark_operation(
        const std::string& operation_name,
        const std::function<void()>& operation
    ) {
        if (config_.verbose) {
            std::cout << "  Benchmarking: " << operation_name << "\n";
        }

        // Warmup phase
        if (config_.warmup_runs > 0 && config_.verbose) {
            std::cout << "    Warmup: " << config_.warmup_runs << " runs\n";
        }
        for (int i = 0; i < config_.warmup_runs; ++i) {
            operation();
        }

        // Measurement phase
        if (config_.verbose) {
            std::cout << "    Measuring: " << config_.measurement_runs << " runs\n";
        }

        std::vector<OperationMetrics> metrics;
        metrics.reserve(config_.measurement_runs);

        for (int i = 0; i < config_.measurement_runs; ++i) {
            metrics.push_back(measure_operation(operation_name, operation));
        }

        return calculate_stats(operation_name, metrics);
    }

    /**
     * Quick benchmark helper
     */
    static BenchmarkStats quick_benchmark(
        ComplianceEngine& engine,
        const std::string& operation_name,
        const std::function<void()>& operation,
        int runs = 100
    ) {
        BenchmarkConfig config;
        config.warmup_runs = 5;
        config.measurement_runs = runs;
        config.verbose = false;
        
        BenchmarkRunner runner(engine, config);
        return runner.benchmark_operation(operation_name, operation);
    }

private:
    ComplianceEngine& engine_;
    BenchmarkConfig config_;

    OperationMetrics measure_operation(
        const std::string& operation_name,
        const std::function<void()>& operation
    ) {
        auto start_time = std::chrono::high_resolution_clock::now();
        auto timestamp = std::chrono::system_clock::now();
        
        operation();
        
        auto end_time = std::chrono::high_resolution_clock::now();
        auto execution_time = std::chrono::duration_cast<std::chrono::nanoseconds>(end_time - start_time);

        return OperationMetrics{
            operation_name,
            execution_time,
            0, // Memory tracking would require platform-specific code
            timestamp
        };
    }

    BenchmarkStats calculate_stats(
        const std::string& operation_name,
        const std::vector<OperationMetrics>& metrics
    ) {
        std::vector<int64_t> times;
        times.reserve(metrics.size());
        
        for (const auto& m : metrics) {
            times.push_back(m.execution_time.count());
        }
        
        std::sort(times.begin(), times.end());

        int total_runs = times.size();
        int64_t min_time = times.front();
        int64_t max_time = times.back();
        int64_t avg_time = std::accumulate(times.begin(), times.end(), 0LL) / total_runs;
        int64_t median_time = times[total_runs / 2];
        int64_t p95_time = times[static_cast<size_t>(total_runs * 0.95)];
        int64_t p99_time = times[static_cast<size_t>(total_runs * 0.99)];

        // Calculate standard deviation
        double variance = 0.0;
        for (int64_t t : times) {
            double diff = t - avg_time;
            variance += diff * diff;
        }
        variance /= total_runs;
        double std_dev_ms = std::sqrt(variance) / 1'000'000.0;

        // Calculate throughput
        double total_time_seconds = std::accumulate(times.begin(), times.end(), 0LL) / 1'000'000'000.0;
        double throughput = total_runs / total_time_seconds;

        return BenchmarkStats{
            operation_name,
            total_runs,
            std::chrono::nanoseconds(min_time),
            std::chrono::nanoseconds(max_time),
            std::chrono::nanoseconds(avg_time),
            std::chrono::nanoseconds(median_time),
            std::chrono::nanoseconds(p95_time),
            std::chrono::nanoseconds(p99_time),
            std_dev_ms,
            throughput
        };
    }
};

} // namespace substrait::compliance

// Made with Bob
