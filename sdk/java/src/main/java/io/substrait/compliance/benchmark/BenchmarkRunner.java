package io.substrait.compliance.benchmark;

import io.substrait.compliance.ComplianceEngine;
import io.substrait.compliance.EngineInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Performance benchmarking framework for Substrait compliance engines.
 * Provides statistical analysis of operation performance including latency,
 * throughput, and memory usage metrics.
 */
public class BenchmarkRunner {
    private final ComplianceEngine engine;
    private final BenchmarkConfig config;

    public BenchmarkRunner(ComplianceEngine engine, BenchmarkConfig config) {
        this.engine = engine;
        this.config = config;
    }

    /**
     * Run a complete benchmark suite with multiple operations
     */
    public BenchmarkResult runBenchmark(String benchmarkName, List<BenchmarkOperation> operations)
            throws Exception {
        Instant startTime = Instant.now();

        if (config.isVerbose()) {
            System.out.println("Starting benchmark: " + benchmarkName);
            System.out.println("Warmup runs: " + config.getWarmupRuns());
            System.out.println("Measurement runs: " + config.getMeasurementRuns());
        }

        List<BenchmarkStats> stats = new ArrayList<>();
        for (BenchmarkOperation operation : operations) {
            BenchmarkStats stat = benchmarkOperation(operation.getName(), operation.getOperation());
            stats.add(stat);
        }

        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);

        EngineInfo info = engine.getEngineInfo();
        return new BenchmarkResult(
                info.getEngineName(),
                benchmarkName,
                stats,
                totalDuration,
                startTime
        );
    }

    /**
     * Benchmark a single operation
     */
    public BenchmarkStats benchmarkOperation(String operationName, Supplier<Void> operation)
            throws Exception {
        if (config.isVerbose()) {
            System.out.println("  Benchmarking: " + operationName);
        }

        // Warmup phase
        if (config.getWarmupRuns() > 0) {
            if (config.isVerbose()) {
                System.out.println("    Warmup: " + config.getWarmupRuns() + " runs");
            }
            for (int i = 0; i < config.getWarmupRuns(); i++) {
                operation.get();
            }
        }

        // Force garbage collection before measurement
        if (config.isCollectMemoryStats()) {
            System.gc();
            Thread.sleep(100);
        }

        // Measurement phase
        if (config.isVerbose()) {
            System.out.println("    Measuring: " + config.getMeasurementRuns() + " runs");
        }

        List<OperationMetrics> metrics = new ArrayList<>();
        for (int i = 0; i < config.getMeasurementRuns(); i++) {
            OperationMetrics metric = measureOperation(operationName, operation);
            metrics.add(metric);
        }

        return calculateStats(operationName, metrics);
    }

    /**
     * Measure a single operation execution
     */
    private OperationMetrics measureOperation(String operationName, Supplier<Void> operation) {
        long memoryBefore = 0;
        if (config.isCollectMemoryStats()) {
            Runtime runtime = Runtime.getRuntime();
            memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        }

        long startNanos = System.nanoTime();
        Instant timestamp = Instant.now();

        operation.get();

        long endNanos = System.nanoTime();
        Duration executionTime = Duration.ofNanos(endNanos - startNanos);

        long memoryUsed = 0;
        if (config.isCollectMemoryStats()) {
            Runtime runtime = Runtime.getRuntime();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            memoryUsed = Math.max(0, memoryAfter - memoryBefore);
        }

        return new OperationMetrics(operationName, executionTime, memoryUsed, timestamp);
    }

    /**
     * Calculate statistics from collected metrics
     */
    private BenchmarkStats calculateStats(String operationName, List<OperationMetrics> metrics) {
        List<Duration> times = metrics.stream()
                .map(OperationMetrics::getExecutionTime)
                .sorted()
                .collect(Collectors.toList());

        int totalRuns = times.size();
        Duration minTime = times.get(0);
        Duration maxTime = times.get(totalRuns - 1);

        // Calculate average
        long totalNanos = times.stream().mapToLong(Duration::toNanos).sum();
        Duration avgTime = Duration.ofNanos(totalNanos / totalRuns);

        // Calculate percentiles
        Duration medianTime = times.get(totalRuns / 2);
        Duration p95Time = times.get((int) (totalRuns * 0.95));
        Duration p99Time = times.get((int) (totalRuns * 0.99));

        // Calculate standard deviation
        double avgNanos = (double) totalNanos / totalRuns;
        double variance = times.stream()
                .mapToDouble(t -> {
                    double diff = t.toNanos() - avgNanos;
                    return diff * diff;
                })
                .sum() / totalRuns;
        double stdDevMs = Math.sqrt(variance) / 1_000_000.0;

        // Calculate throughput (ops/sec)
        double totalTimeSecs = totalNanos / 1_000_000_000.0;
        double throughput = totalRuns / totalTimeSecs;

        return new BenchmarkStats(
                operationName,
                totalRuns,
                minTime,
                maxTime,
                avgTime,
                medianTime,
                p95Time,
                p99Time,
                stdDevMs,
                throughput
        );
    }

    /**
     * Quick benchmark helper for simple use cases
     */
    public static BenchmarkStats quickBenchmark(
            ComplianceEngine engine,
            String operationName,
            Supplier<Void> operation,
            int runs) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(5)
                .measurementRuns(runs)
                .verbose(false)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(engine, config);
        return runner.benchmarkOperation(operationName, operation);
    }

    /**
     * Run parallel benchmark with multiple threads
     */
    public BenchmarkResult runParallelBenchmark(
            String benchmarkName,
            List<BenchmarkOperation> operations,
            int threadCount) throws Exception {
        Instant startTime = Instant.now();

        if (config.isVerbose()) {
            System.out.println("Starting parallel benchmark: " + benchmarkName);
            System.out.println("Thread count: " + threadCount);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<BenchmarkStats>> futures = new ArrayList<>();

        for (BenchmarkOperation operation : operations) {
            Future<BenchmarkStats> future = executor.submit(() ->
                    benchmarkOperation(operation.getName(), operation.getOperation())
            );
            futures.add(future);
        }

        List<BenchmarkStats> stats = new ArrayList<>();
        for (Future<BenchmarkStats> future : futures) {
            stats.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);

        EngineInfo info = engine.getEngineInfo();
        return new BenchmarkResult(
                info.getEngineName(),
                benchmarkName,
                stats,
                totalDuration,
                startTime
        );
    }
}

