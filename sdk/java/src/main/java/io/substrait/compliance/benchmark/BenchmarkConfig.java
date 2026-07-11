package io.substrait.compliance.benchmark;

/**
 * Configuration for benchmark execution
 */
public class BenchmarkConfig {
    private final int warmupRuns;
    private final int measurementRuns;
    private final int parallelism;
    private final boolean collectMemoryStats;
    private final boolean verbose;

    private BenchmarkConfig(Builder builder) {
        this.warmupRuns = builder.warmupRuns;
        this.measurementRuns = builder.measurementRuns;
        this.parallelism = builder.parallelism;
        this.collectMemoryStats = builder.collectMemoryStats;
        this.verbose = builder.verbose;
    }

    public int getWarmupRuns() {
        return warmupRuns;
    }

    public int getMeasurementRuns() {
        return measurementRuns;
    }

    public int getParallelism() {
        return parallelism;
    }

    public boolean isCollectMemoryStats() {
        return collectMemoryStats;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int warmupRuns = 5;
        private int measurementRuns = 100;
        private int parallelism = 1;
        private boolean collectMemoryStats = true;
        private boolean verbose = false;

        public Builder warmupRuns(int warmupRuns) {
            this.warmupRuns = warmupRuns;
            return this;
        }

        public Builder measurementRuns(int measurementRuns) {
            this.measurementRuns = measurementRuns;
            return this;
        }

        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder collectMemoryStats(boolean collectMemoryStats) {
            this.collectMemoryStats = collectMemoryStats;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public BenchmarkConfig build() {
            return new BenchmarkConfig(this);
        }
    }
}

