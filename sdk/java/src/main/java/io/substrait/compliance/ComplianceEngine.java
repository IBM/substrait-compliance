package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.Map;

/**
 * Core interface for Substrait compliance testing.
 *
 * <p>Database engines implement this interface to participate in Substrait compliance
 * testing. The framework provides test suites, and engines execute them using their
 * native Substrait execution capabilities.
 *
 * <h2>Required methods</h2>
 * <ul>
 *   <li>{@link #executePlan(Plan, Map)} — execute a deserialized Substrait plan</li>
 *   <li>{@link #validatePlan(Plan)} — validate a plan before execution</li>
 *   <li>{@link #getEngineInfo()} — return engine metadata</li>
 *   <li>{@link #getCapabilities()} — declare supported features</li>
 * </ul>
 *
 * <h2>Optional lifecycle hooks</h2>
 * <ul>
 *   <li>{@link #initialize()} — called once before the suite runs</li>
 *   <li>{@link #cleanup()} — called once after all tests finish</li>
 * </ul>
 *
 * <h2>Raw-bytes bridge</h2>
 * <p>All other language SDKs (Python, TypeScript, Rust, Go, Scala, C#, C++) pass
 * raw {@code byte[]} to {@code executePlan}.  Java's loader pre-deserializes the
 * plan for convenience, but engines that prefer to work with bytes can override
 * {@link #executePlan(byte[], Map)} instead — the default implementation
 * deserializes the bytes and delegates to {@link #executePlan(Plan, Map)}.
 */
public interface ComplianceEngine {

    /**
     * Execute a deserialized Substrait plan with provided input data.
     *
     * <p>This is the primary method engines implement.  The runner pre-deserializes
     * the protobuf bytes into a {@link Plan} before calling this method.
     */
    ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
        throws ComplianceException;

    /**
     * Execute a Substrait plan from raw protobuf bytes.
     *
     * <p>This bridge method aligns the Java interface with all other language SDKs,
     * which pass raw bytes.  The default implementation deserializes the bytes and
     * delegates to {@link #executePlan(Plan, Map)}.  Engines that prefer to handle
     * deserialization themselves can override this method directly.
     */
    default ComplianceResult executePlan(byte[] planBytes, Map<String, TableData> inputData)
        throws ComplianceException {
        try {
            Plan plan = Plan.parseFrom(planBytes);
            return executePlan(plan, inputData);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw new ComplianceException("Failed to deserialize Substrait plan", e);
        }
    }

    /**
     * Report engine capabilities and supported Substrait features.
     */
    EngineCapabilities getCapabilities();

    /**
     * Validate if a Substrait plan is supported before execution.
     */
    PlanValidationResult validatePlan(Plan plan);

    /**
     * Get engine identification and version information.
     */
    EngineInfo getEngineInfo();

    /**
     * Initialize the engine (optional).
     *
     * <p>Called once before the test suite runs.  Override to perform setup
     * such as opening connections or warming caches.
     */
    default void initialize() throws ComplianceException {
        // Default: no initialization needed
    }

    /**
     * Clean up engine resources (optional).
     *
     * <p>Called once after all tests finish (including on error).  Override to
     * release connections, flush logs, etc.
     */
    default void cleanup() throws ComplianceException {
        // Default: no cleanup needed
    }
}
