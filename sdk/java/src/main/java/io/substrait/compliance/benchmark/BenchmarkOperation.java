package io.substrait.compliance.benchmark;

import java.util.function.Supplier;

/**
 * Represents a single operation to be benchmarked
 */
public class BenchmarkOperation {
    private final String name;
    private final Supplier<Void> operation;

    public BenchmarkOperation(String name, Supplier<Void> operation) {
        this.name = name;
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public Supplier<Void> getOperation() {
        return operation;
    }

    /**
     * Create a benchmark operation from a Runnable
     */
    public static BenchmarkOperation of(String name, Runnable runnable) {
        return new BenchmarkOperation(name, () -> {
            runnable.run();
            return null;
        });
    }
}

// Made with Bob
