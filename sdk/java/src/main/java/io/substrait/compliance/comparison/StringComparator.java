package io.substrait.compliance.comparison;

import java.util.Set;

/**
 * Comparator for string types.
 */
public class StringComparator implements TypeAwareComparator {
    
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "string", "str", "varchar", "char", "text"
    );
    
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        try {
            String expectedValue = toString(expected);
            String actualValue = toString(actual);
            
            if (expectedValue.equals(actualValue)) {
                return ComparisonResult.exactMatch();
            }
            
            return ComparisonResult.noMatch(
                String.format("Expected '%s' but got '%s'", expectedValue, actualValue)
            );
        } catch (Exception e) {
            return ComparisonResult.noMatch(
                "Failed to compare as strings: " + e.getMessage()
            );
        }
    }
    
    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }
    
    private String toString(Object value) {
        return value == null ? "null" : value.toString();
    }
}

// Made with Bob
