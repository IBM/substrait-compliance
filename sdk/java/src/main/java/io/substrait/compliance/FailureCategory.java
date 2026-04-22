package io.substrait.compliance;

/**
 * Categories of test failures for systematic classification and analysis.
 * 
 * <p>This enum helps categorize why a test failed, enabling:
 * <ul>
 *   <li>Better debugging by identifying failure patterns</li>
 *   <li>Metrics collection on failure types</li>
 *   <li>Prioritization of fixes based on failure category</li>
 * </ul>
 */
public enum FailureCategory {
    /**
     * Plan parsing failed - the Substrait plan could not be parsed.
     */
    PARSING_ERROR("Plan parsing failed"),
    
    /**
     * Plan validation failed - the plan structure or semantics are invalid.
     */
    VALIDATION_ERROR("Plan validation failed"),
    
    /**
     * Feature not supported by the engine.
     */
    UNSUPPORTED_FEATURE("Feature not supported by engine"),
    
    /**
     * Result type mismatch - expected and actual result types differ.
     */
    TYPE_MISMATCH("Result type mismatch"),
    
    /**
     * Result value mismatch - types match but values differ.
     */
    VALUE_MISMATCH("Result value mismatch"),
    
    /**
     * Runtime execution error - error occurred during plan execution.
     */
    RUNTIME_ERROR("Runtime execution error"),
    
    /**
     * Execution timeout - plan took too long to execute.
     */
    TIMEOUT("Execution timeout"),
    
    /**
     * Resource limits exceeded (memory, CPU, etc.).
     */
    RESOURCE_EXHAUSTION("Resource limits exceeded"),
    
    /**
     * Result comparison failed - unable to compare results.
     */
    COMPARISON_ERROR("Result comparison failed"),
    
    /**
     * Unknown or unclassified failure.
     */
    UNKNOWN("Unknown failure");
    
    private final String description;
    
    FailureCategory(String description) {
        this.description = description;
    }
    
    /**
     * Gets the human-readable description of this failure category.
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return name() + ": " + description;
    }
}

// Made with Bob
