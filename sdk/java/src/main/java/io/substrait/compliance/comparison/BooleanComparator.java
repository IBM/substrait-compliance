package io.substrait.compliance.comparison;

import java.util.Set;

/**
 * Comparator for boolean types.
 */
public class BooleanComparator implements TypeAwareComparator {
    
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "bool", "boolean"
    );
    
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        try {
            boolean expectedValue = toBoolean(expected);
            boolean actualValue = toBoolean(actual);
            
            if (expectedValue == actualValue) {
                return ComparisonResult.exactMatch();
            }
            
            return ComparisonResult.noMatch(
                String.format("Expected %b but got %b", expectedValue, actualValue)
            );
        } catch (Exception e) {
            return ComparisonResult.noMatch(
                "Failed to compare as booleans: " + e.getMessage()
            );
        }
    }
    
    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }
    
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString().trim().toLowerCase();
        if (str.equals("true") || str.equals("1") || str.equals("t") || str.equals("yes")) {
            return true;
        }
        if (str.equals("false") || str.equals("0") || str.equals("f") || str.equals("no")) {
            return false;
        }
        return Boolean.parseBoolean(str);
    }
}

// Made with Bob
