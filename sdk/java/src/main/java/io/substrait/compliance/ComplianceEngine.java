package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.Map;

/**
 * Core interface for Substrait compliance testing.
 * 
 * <p>Database engines implement this interface to participate in Substrait compliance
 * testing. The framework provides test suites, and engines execute them using their
 * native Substrait execution capabilities.
 */
public interface ComplianceEngine {
    
    /**
     * Execute a Substrait plan with provided input data.
     */
    ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) 
        throws ComplianceException;
    
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
     */
    default void initialize() throws ComplianceException {
        // Default: no initialization needed
    }
    
    /**
     * Clean up engine resources (optional).
     */
    default void cleanup() throws ComplianceException {
        // Default: no cleanup needed
    }
}
