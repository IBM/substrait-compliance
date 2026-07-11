package io.substrait.compliance.comparison;

import java.util.HashMap;
import java.util.Map;

/**
 * Main comparator that orchestrates type-aware comparison of test results.
 * 
 * <p>This comparator:
 * <ul>
 *   <li>Handles null values</li>
 *   <li>Detects and handles special values (NaN, Infinity, ERROR, UNDEFINED)</li>
 *   <li>Delegates to type-specific comparators</li>
 *   <li>Falls back to string comparison if no specific comparator is found</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * TestResultComparator comparator = new TestResultComparator();
 * ComparisonResult result = comparator.compare(
 *     expectedValue, "fp64",
 *     actualValue, "fp64"
 * );
 * 
 * if (!result.matches()) {
 *     System.err.println("Mismatch: " + result.getDetailedMessage());
 * }
 * }</pre>
 */
public class TestResultComparator {
    
    private final Map<String, TypeAwareComparator> comparators;
    private final SpecialValueHandler specialValueHandler;
    private final TypeAwareComparator defaultComparator;
    
    /**
     * Creates a new comparator with default epsilon for floating-point comparison.
     */
    public TestResultComparator() {
        this(1e-9);
    }
    
    /**
     * Creates a new comparator with custom epsilon for floating-point comparison.
     * 
     * @param epsilon tolerance for floating-point comparisons
     */
    public TestResultComparator(double epsilon) {
        this.comparators = new HashMap<>();
        this.specialValueHandler = new SpecialValueHandler();
        this.defaultComparator = new StringComparator();
        
        // Register type-specific comparators
        registerComparator(new IntegerComparator());
        registerComparator(new FloatingPointComparator(epsilon));
        registerComparator(new BooleanComparator());
        registerComparator(new StringComparator());
    }
    
    /**
     * Registers a type-specific comparator.
     */
    private void registerComparator(TypeAwareComparator comparator) {
        // Register with a key that the comparator supports
        // For now, we'll use a simple approach and check all comparators
        comparators.put(comparator.getClass().getSimpleName(), comparator);
    }
    
    /**
     * Compares expected and actual values with type awareness.
     * 
     * @param expected expected value
     * @param expectedType expected type (e.g., "i32", "fp64", "string")
     * @param actual actual value
     * @param actualType actual type
     * @return comparison result
     */
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        // Handle null cases
        if (expected == null && actual == null) {
            return ComparisonResult.exactMatch();
        }
        if (expected == null) {
            return ComparisonResult.noMatch("Expected null but got: " + actual);
        }
        if (actual == null) {
            return ComparisonResult.noMatch("Expected " + expected + " but got null");
        }
        
        // Handle special values (NaN, Infinity, ERROR, UNDEFINED)
        if (specialValueHandler.isSpecialValue(expected) || 
            specialValueHandler.isSpecialValue(actual)) {
            return specialValueHandler.compare(expected, actual);
        }
        
        // Find appropriate type-aware comparator
        TypeAwareComparator comparator = findComparator(expectedType, actualType);
        if (comparator != null) {
            return comparator.compare(expected, expectedType, actual, actualType);
        }
        
        // Fall back to default (string) comparison
        return defaultComparator.compare(expected, expectedType, actual, actualType);
    }
    
    /**
     * Finds the appropriate comparator for the given types.
     */
    private TypeAwareComparator findComparator(String expectedType, String actualType) {
        // Use expected type to find comparator
        String type = expectedType != null ? expectedType : actualType;
        if (type == null) {
            return null;
        }
        
        // Check each registered comparator
        for (TypeAwareComparator comparator : comparators.values()) {
            if (comparator.supports(type)) {
                return comparator;
            }
        }
        
        return null;
    }
    
    /**
     * Gets a detailed comparison message for debugging.
     * 
     * @param expected expected value
     * @param expectedType expected type
     * @param actual actual value
     * @param actualType actual type
     * @return detailed message
     */
    public String getDetailedMessage(Object expected, String expectedType,
                                    Object actual, String actualType) {
        ComparisonResult result = compare(expected, expectedType, actual, actualType);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: ").append(expected);
        if (expectedType != null) {
            sb.append(" (").append(expectedType).append(")");
        }
        sb.append("\n");
        
        sb.append("Actual: ").append(actual);
        if (actualType != null) {
            sb.append(" (").append(actualType).append(")");
        }
        sb.append("\n");
        
        sb.append("Result: ").append(result.getDetailedMessage());
        
        return sb.toString();
    }
}

