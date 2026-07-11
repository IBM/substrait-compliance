package io.substrait.compliance.benchmark;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunnerTest {

    private MockEngine mockEngine;
    private BenchmarkConfig defaultConfig;

    static class MockEngine implements ComplianceEngine {
        private final String name;
        private final long delayMs;

        public MockEngine(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            TableData emptyTable = new TableData(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
            return ComplianceResult.success(emptyTable, delayMs);
        }

        @Override
        public EngineCapabilities getCapabilities() {
            return EngineCapabilities.builder()
                    .supportsExtensions(true)
                    .build();
        }

        @Override
        public PlanValidationResult validatePlan(Plan plan) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs / 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return PlanValidationResult.supported();
        }

        @Override
        public EngineInfo getEngineInfo() {
            return new EngineInfo(name, "1.0.0", "0.20.0");
        }
    }

    @BeforeEach
    void setUp() {
        mockEngine = new MockEngine("TestEngine", 0);
        defaultConfig = BenchmarkConfig.builder()
                .warmupRuns(2)
                .measurementRuns(10)
                .verbose(false)
                .build();
    }

    @Test
    void testBenchmarkConfigDefault() {
        BenchmarkConfig config = BenchmarkConfig.builder().build();
        assertEquals(5, config.getWarmupRuns());
        assertEquals(100, config.getMeasurementRuns());
        assertEquals(1, config.getParallelism());
        assertTrue(config.isCollectMemoryStats());
        assertFalse(config.isVerbose());
    }

    @Test
    void testBenchmarkConfigCustom() {
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(10)
                .measurementRuns(50)
                .parallelism(4)
                .collectMemoryStats(false)
                .verbose(true)
                .build();

        assertEquals(10, config.getWarmupRuns());
        assertEquals(50, config.getMeasurementRuns());
        assertEquals(4, config.getParallelism());
        assertFalse(config.isCollectMemoryStats());
        assertTrue(config.isVerbose());
    }

    @Test
    void testBenchmarkSingleOperation() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        BenchmarkStats result = runner.benchmarkOperation("test_op", () -> {
            int x = 1 + 1;
            return null;
        });

        assertNotNull(result);
        assertEquals("test_op", result.getOperationName());
        assertEquals(10, result.getTotalRuns());
        assertTrue(result.getMinTime().compareTo(result.getMaxTime()) <= 0);
        assertTrue(result.getMinTime().compareTo(result.getAvgTime()) <= 0);
        assertTrue(result.getAvgTime().compareTo(result.getMaxTime()) <= 0);
        assertTrue(result.getThroughput() > 0.0);
    }

    @Test
    void testBenchmarkMultipleOperations() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        List<BenchmarkOperation> operations = Arrays.asList(
                BenchmarkOperation.of("op1", () -> {
                    int x = 1 + 1;
                }),
                BenchmarkOperation.of("op2", () -> {
                    String s = "hello" + "world";
                })
        );

        BenchmarkResult result = runner.runBenchmark("multi_op_test", operations);

        assertNotNull(result);
        assertEquals("multi_op_test", result.getBenchmarkName());
        assertEquals("TestEngine", result.getEngineName());
        assertEquals(2, result.getStats().size());
        assertTrue(result.getTotalDuration().toMillis() > 0);
    }

    @Test
    void testBenchmarkStatsOrdering() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        BenchmarkStats result = runner.benchmarkOperation("ordering_test", () -> {
            return null;
        });

        // Verify statistical ordering
        assertTrue(result.getMinTime().compareTo(result.getMedianTime()) <= 0);
        assertTrue(result.getMedianTime().compareTo(result.getP95Time()) <= 0);
        assertTrue(result.getP95Time().compareTo(result.getP99Time()) <= 0);
        assertTrue(result.getP99Time().compareTo(result.getMaxTime()) <= 0);
    }

    @Test
    void testBenchmarkResultSummary() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        List<BenchmarkOperation> operations = Collections.singletonList(
                BenchmarkOperation.of("summary_test", () -> {})
        );

        BenchmarkResult result = runner.runBenchmark("summary_test", operations);
        String summary = result.summary();

        assertNotNull(summary);
        assertTrue(summary.contains("Benchmark: summary_test"));
        assertTrue(summary.contains("Engine: TestEngine"));
        assertTrue(summary.contains("Total Duration:"));
        assertTrue(summary.contains("Operation: summary_test"));
    }

    @Test
    void testBenchmarkResultCSV() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        List<BenchmarkOperation> operations = Collections.singletonList(
                BenchmarkOperation.of("csv_test", () -> {})
        );

        BenchmarkResult result = runner.runBenchmark("csv_test", operations);
        String csv = result.toCSV();

        assertNotNull(csv);
        assertTrue(csv.contains("Engine,Benchmark,Operation,TotalRuns"));
        assertTrue(csv.contains("TestEngine,csv_test,csv_test,10"));
    }

    @Test
    void testQuickBenchmark() throws Exception {
        BenchmarkStats result = BenchmarkRunner.quickBenchmark(
                mockEngine,
                "quick_test",
                () -> {
                    return null;
                },
                20
        );

        assertNotNull(result);
        assertEquals("quick_test", result.getOperationName());
        assertEquals(20, result.getTotalRuns());
        assertTrue(result.getThroughput() > 0.0);
    }

    @Test
    void testBenchmarkWithDelay() throws Exception {
        MockEngine delayEngine = new MockEngine("DelayEngine", 5);
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(1)
                .measurementRuns(3)
                .verbose(false)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(delayEngine, config);

        BenchmarkStats result = runner.benchmarkOperation("delay_test", () -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        // With 5ms delay, average should be at least 4ms (allowing variance)
        assertTrue(result.getAvgTime().toMillis() >= 4);
        assertEquals(3, result.getTotalRuns());
    }

    @Test
    void testBenchmarkThroughputCalculation() throws Exception {
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(0)
                .measurementRuns(100)
                .verbose(false)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, config);

        BenchmarkStats result = runner.benchmarkOperation("throughput_test", () -> {
            return null;
        });

        // Throughput should be positive and reasonable
        assertTrue(result.getThroughput() > 0.0);
        assertTrue(result.getThroughput() < 1_000_000.0); // Sanity check
    }

    @Test
    void testParallelBenchmark() throws Exception {
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(1)
                .measurementRuns(5)
                .parallelism(2)
                .verbose(false)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, config);

        List<BenchmarkOperation> operations = Arrays.asList(
                BenchmarkOperation.of("parallel_op1", () -> {
                    int sum = 0;
                    for (int i = 0; i < 100; i++) sum += i;
                }),
                BenchmarkOperation.of("parallel_op2", () -> {
                    String s = "";
                    for (int i = 0; i < 10; i++) s += "x";
                })
        );

        BenchmarkResult result = runner.runParallelBenchmark("parallel_test", operations, 2);

        assertNotNull(result);
        assertEquals("parallel_test", result.getBenchmarkName());
        assertEquals(2, result.getStats().size());
    }

    @Test
    void testBenchmarkOperationWrapper() {
        BenchmarkOperation op = BenchmarkOperation.of("test", () -> {
            int x = 1 + 1;
        });

        assertEquals("test", op.getName());
        assertNotNull(op.getOperation());
        assertNull(op.getOperation().get());
    }

    @Test
    void testOperationMetrics() {
        java.time.Duration duration = java.time.Duration.ofMillis(100);
        java.time.Instant timestamp = java.time.Instant.now();

        OperationMetrics metrics = new OperationMetrics("test_op", duration, 1024, timestamp);

        assertEquals("test_op", metrics.getOperationName());
        assertEquals(duration, metrics.getExecutionTime());
        assertEquals(1024, metrics.getMemoryUsed());
        assertEquals(timestamp, metrics.getTimestamp());
    }

    @Test
    void testBenchmarkStatsFormatting() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        BenchmarkStats result = runner.benchmarkOperation("format_test", () -> {
            return null;
        });

        String summary = result.summary();
        assertNotNull(summary);
        assertTrue(summary.contains("Operation: format_test"));
        assertTrue(summary.contains("Total Runs: 10"));
        assertTrue(summary.contains("Throughput:"));
    }

    @Test
    void testBenchmarkWithZeroWarmup() throws Exception {
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(0)
                .measurementRuns(5)
                .verbose(false)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, config);

        BenchmarkStats result = runner.benchmarkOperation("no_warmup", () -> {
            return null;
        });

        assertEquals(5, result.getTotalRuns());
        assertNotNull(result.getAvgTime());
    }

    @Test
    void testBenchmarkStdDevCalculation() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(mockEngine, defaultConfig);

        BenchmarkStats result = runner.benchmarkOperation("stddev_test", () -> {
            return null;
        });

        // Standard deviation should be non-negative
        assertTrue(result.getStdDevMs() >= 0.0);
    }
}

