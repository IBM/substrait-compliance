package io.substrait.compliance.comparison;

/**
 * Interface for type-aware value comparison.
 * 
 * <p>Implementations handle comparison for specific data types (integers, floats, strings, etc.)
 * with appropriate semantics for each type.
 */
public interface TypeAwareComparator {
    
    /**
     * Compares expected and actual values with type awareness.
     * 
     * @param expected expected value
     * @param expectedType expected type (e.g., "i32", "fp64", "string")
     * @param actual actual value
     * @param actualType actual type
     * @return comparison result
     */
    ComparisonResult compare(Object expected, String expectedType, 
                            Object actual, String actualType);
    
    /**
     * Returns true if this comparator supports the given type.
     * 
     * @param type type to check (e.g., "i32", "fp64", "string")
     * @return true if supported
     */
    boolean supports(String type);
}

