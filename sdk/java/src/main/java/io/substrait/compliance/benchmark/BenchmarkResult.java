package io.substrait.compliance.benchmark;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete benchmark result containing all statistics
 */
public class BenchmarkResult {
    private final String engineName;
    private final String benchmarkName;
    private final List<BenchmarkStats> stats;
    private final Duration totalDuration;
    private final Instant timestamp;

    public BenchmarkResult(
            String engineName,
            String benchmarkName,
            List<BenchmarkStats> stats,
            Duration totalDuration,
            Instant timestamp) {
        this.engineName = engineName;
        this.benchmarkName = benchmarkName;
        this.stats = stats;
        this.totalDuration = totalDuration;
        this.timestamp = timestamp;
    }

    public String getEngineName() {
        return engineName;
    }

    public String getBenchmarkName() {
        return benchmarkName;
    }

    public List<BenchmarkStats> getStats() {
        return stats;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Benchmark: %s\n", benchmarkName));
        sb.append(String.format("Engine: %s\n", engineName));
        sb.append(String.format("Total Duration: %s\n\n", formatDuration(totalDuration)));

        for (BenchmarkStats stat : stats) {
            sb.append(stat.summary());
            sb.append("\n");
        }

        return sb.toString();
    }

    public String toCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n");

        for (BenchmarkStats stat : stats) {
            csv.append(String.format("%s,%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                    engineName,
                    benchmarkName,
                    stat.getOperationName(),
                    stat.getTotalRuns(),
                    stat.getMinTime().toNanos() / 1_000_000.0,
                    stat.getMaxTime().toNanos() / 1_000_000.0,
                    stat.getAvgTime().toNanos() / 1_000_000.0,
                    stat.getMedianTime().toNanos() / 1_000_000.0,
                    stat.getP95Time().toNanos() / 1_000_000.0,
                    stat.getP99Time().toNanos() / 1_000_000.0,
                    stat.getStdDevMs(),
                    stat.getThroughput()
            ));
        }

        return csv.toString();
    }

    private String formatDuration(Duration duration) {
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

// Made with Bob
