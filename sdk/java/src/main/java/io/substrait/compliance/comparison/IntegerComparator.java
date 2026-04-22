package io.substrait.compliance.comparison;

import java.util.Set;

/**
 * Comparator for integer types (i8, i16, i32, i64).
 */
public class IntegerComparator implements TypeAwareComparator {
    
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "i8", "i16", "i32", "i64",
        "int8", "int16", "int32", "int64",
        "integer", "int", "long", "bigint"
    );
    
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        try {
            long expectedValue = toLong(expected);
            long actualValue = toLong(actual);
            
            if (expectedValue == actualValue) {
                return ComparisonResult.exactMatch();
            }
            
            return ComparisonResult.noMatch(
                String.format("Expected %d but got %d", expectedValue, actualValue)
            );
        } catch (Exception e) {
            return ComparisonResult.noMatch(
                "Failed to compare as integers: " + e.getMessage()
            );
        }
    }
    
    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }
    
    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}

// Made with Bob
