package io.substrait.compliance.benchmark;

import java.time.Duration;

/**
 * Statistical analysis of benchmark results
 */
public class BenchmarkStats {
    private final String operationName;
    private final int totalRuns;
    private final Duration minTime;
    private final Duration maxTime;
    private final Duration avgTime;
    private final Duration medianTime;
    private final Duration p95Time;
    private final Duration p99Time;
    private final double stdDevMs;
    private final double throughput;

    public BenchmarkStats(
            String operationName,
            int totalRuns,
            Duration minTime,
            Duration maxTime,
            Duration avgTime,
            Duration medianTime,
            Duration p95Time,
            Duration p99Time,
            double stdDevMs,
            double throughput) {
        this.operationName = operationName;
        this.totalRuns = totalRuns;
        this.minTime = minTime;
        this.maxTime = maxTime;
        this.avgTime = avgTime;
        this.medianTime = medianTime;
        this.p95Time = p95Time;
        this.p99Time = p99Time;
        this.stdDevMs = stdDevMs;
        this.throughput = throughput;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getTotalRuns() {
        return totalRuns;
    }

    public Duration getMinTime() {
        return minTime;
    }

    public Duration getMaxTime() {
        return maxTime;
    }

    public Duration getAvgTime() {
        return avgTime;
    }

    public Duration getMedianTime() {
        return medianTime;
    }

    public Duration getP95Time() {
        return p95Time;
    }

    public Duration getP99Time() {
        return p99Time;
    }

    public double getStdDevMs() {
        return stdDevMs;
    }

    public double getThroughput() {
        return throughput;
    }

    public String summary() {
        return String.format(
                "Operation: %s\n" +
                "Total Runs: %d\n" +
                "Min Time: %s\n" +
                "Max Time: %s\n" +
                "Avg Time: %s\n" +
                "Median Time: %s\n" +
                "P95 Time: %s\n" +
                "P99 Time: %s\n" +
                "Std Dev: %.2fms\n" +
                "Throughput: %.2f ops/sec\n",
                operationName,
                totalRuns,
                formatDuration(minTime),
                formatDuration(maxTime),
                formatDuration(avgTime),
                formatDuration(medianTime),
                formatDuration(p95Time),
                formatDuration(p99Time),
                stdDevMs,
                throughput
        );
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

