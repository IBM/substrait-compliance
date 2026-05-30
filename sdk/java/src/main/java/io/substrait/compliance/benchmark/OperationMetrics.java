package io.substrait.compliance.benchmark;

import java.time.Duration;
import java.time.Instant;

/**
 * Performance metrics for a single operation execution
 */
public class OperationMetrics {
    private final String operationName;
    private final Duration executionTime;
    private final long memoryUsed;
    private final Instant timestamp;

    public OperationMetrics(String operationName, Duration executionTime, long memoryUsed, Instant timestamp) {
        this.operationName = operationName;
        this.executionTime = executionTime;
        this.memoryUsed = memoryUsed;
        this.timestamp = timestamp;
    }

    public String getOperationName() {
        return operationName;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public long getMemoryUsed() {
        return memoryUsed;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

// Made with Bob
