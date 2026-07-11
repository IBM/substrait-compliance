package io.substrait.compliance.benchmark;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;

import java.util.*;

/**
 * Example demonstrating the benchmarking capabilities of the Substrait Compliance SDK
 */
public class BenchmarkExample {

    /**
     * Mock engine for demonstration purposes
     */
    static class MockEngine implements ComplianceEngine {
        private final String name;

        public MockEngine(String name) {
            this.name = name;
        }

        @Override
        public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) {
            // Simulate work
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TableData emptyTable = new TableData(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
            );
            return ComplianceResult.success(emptyTable, 1);
        }

        @Override
        public EngineCapabilities getCapabilities() {
            return EngineCapabilities.builder()
                    .supportsExtensions(true)
                    .build();
        }

        @Override
        public PlanValidationResult validatePlan(Plan plan) {
            return PlanValidationResult.supported();
        }

        @Override
        public EngineInfo getEngineInfo() {
            return new EngineInfo(name, "1.0.0", "0.20.0");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Substrait Compliance Java SDK - Benchmark Example\n");

        MockEngine engine = new MockEngine("MockEngine");

        // Example 1: Basic operation benchmark
        System.out.println("=== Example 1: Basic Operation Benchmark ===");
        BenchmarkConfig config = BenchmarkConfig.builder()
                .warmupRuns(5)
                .measurementRuns(50)
                .verbose(true)
                .build();

        BenchmarkRunner runner = new BenchmarkRunner(engine, config);

        List<BenchmarkOperation> operations = Arrays.asList(
                BenchmarkOperation.of("simple_addition", () -> {
                    int result = 1 + 1;
                }),
                BenchmarkOperation.of("string_concat", () -> {
                    String result = "hello" + "world";
                })
        );

        BenchmarkResult result = runner.runBenchmark("basic_operations", operations);
        System.out.println(result.summary());

        // Example 2: Plan execution benchmark
        System.out.println("\n=== Example 2: Plan Execution Benchmark ===");
        Plan mockPlan = Plan.newBuilder().build();

        BenchmarkStats stats = BenchmarkRunner.quickBenchmark(
                engine,
                "execute_plan",
                () -> {
                    engine.executePlan(mockPlan, Collections.emptyMap());
                    return null;
                },
                100
        );

        System.out.println(stats.summary());

        // Example 3: Export to CSV
        System.out.println("\n=== Example 3: Export Results to CSV ===");
        String csvOutput = result.toCSV();
        System.out.println("CSV Output:");
        System.out.println(csvOutput);

        // Example 4: Comparison benchmark
        System.out.println("\n=== Example 4: Engine Comparison ===");
        MockEngine fastEngine = new MockEngine("FastEngine");
        MockEngine slowEngine = new MockEngine("SlowEngine");

        BenchmarkConfig comparisonConfig = BenchmarkConfig.builder()
                .warmupRuns(3)
                .measurementRuns(30)
                .verbose(false)
                .build();

        List<BenchmarkOperation> comparisonOps = Collections.singletonList(
                BenchmarkOperation.of("computation", () -> {
                    int sum = 0;
                    for (int i = 0; i < 1000; i++) {
                        sum += i;
                    }
                })
        );

        BenchmarkRunner fastRunner = new BenchmarkRunner(fastEngine, comparisonConfig);
        BenchmarkResult fastResult = fastRunner.runBenchmark("comparison", comparisonOps);

        BenchmarkRunner slowRunner = new BenchmarkRunner(slowEngine, comparisonConfig);
        BenchmarkResult slowResult = slowRunner.runBenchmark("comparison", comparisonOps);

        System.out.println("Fast Engine Results:");
        System.out.println(fastResult.summary());
        System.out.println("\nSlow Engine Results:");
        System.out.println(slowResult.summary());

        // Example 5: Scalability test
        System.out.println("\n=== Example 5: Scalability Test ===");
        int[] sizes = {10, 100, 1000};

        for (int size : sizes) {
            final int currentSize = size;
            List<BenchmarkOperation> scalabilityOps = Collections.singletonList(
                    BenchmarkOperation.of("process_" + size + "_items", () -> {
                        int sum = 0;
                        for (int i = 0; i < currentSize; i++) {
                            sum += i;
                        }
                    })
            );

            BenchmarkConfig scalabilityConfig = BenchmarkConfig.builder()
                    .warmupRuns(2)
                    .measurementRuns(20)
                    .verbose(false)
                    .build();

            BenchmarkRunner scalabilityRunner = new BenchmarkRunner(engine, scalabilityConfig);
            BenchmarkResult scalabilityResult = scalabilityRunner.runBenchmark(
                    "scalability_" + size,
                    scalabilityOps
            );

            System.out.println("\nSize: " + size + " items");
            for (BenchmarkStats stat : scalabilityResult.getStats()) {
                System.out.printf("  Avg: %s, Throughput: %.2f ops/sec%n",
                        formatDuration(stat.getAvgTime()),
                        stat.getThroughput());
            }
        }

        // Example 6: Parallel benchmark
        System.out.println("\n=== Example 6: Parallel Benchmark ===");
        BenchmarkConfig parallelConfig = BenchmarkConfig.builder()
                .warmupRuns(3)
                .measurementRuns(20)
                .parallelism(4)
                .verbose(false)
                .build();

        List<BenchmarkOperation> parallelOps = Arrays.asList(
                BenchmarkOperation.of("operation_1", () -> {
                    int sum = 0;
                    for (int i = 0; i < 100; i++) sum += i;
                }),
                BenchmarkOperation.of("operation_2", () -> {
                    String s = "";
                    for (int i = 0; i < 10; i++) s += "x";
                }),
                BenchmarkOperation.of("operation_3", () -> {
                    List<Integer> list = new ArrayList<>();
                    for (int i = 0; i < 50; i++) list.add(i);
                }),
                BenchmarkOperation.of("operation_4", () -> {
                    Map<String, Integer> map = new HashMap<>();
                    for (int i = 0; i < 20; i++) map.put("key" + i, i);
                })
        );

        BenchmarkRunner parallelRunner = new BenchmarkRunner(engine, parallelConfig);
        BenchmarkResult parallelResult = parallelRunner.runParallelBenchmark(
                "parallel_operations",
                parallelOps,
                4
        );

        System.out.println(parallelResult.summary());

        System.out.println("\n=== Benchmark Complete ===");
    }

    private static String formatDuration(java.time.Duration duration) {
        long nanos = duration.toNanos();
        if (nanos < 1000) {
            return nanos + "ns";
        } else if (nanos < 1_000_000) {
            return String.format("%.2fμs", nanos / 1000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2fms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2fs", nanos / 1_000_000_000.0);
        }
    }
}

