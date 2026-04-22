package io.substrait.compliance.comparison;

import java.util.Set;

/**
 * Handles comparison of special values like NaN, Infinity, ERROR, and UNDEFINED.
 * 
 * <p>Special values in Substrait test cases:
 * <ul>
 *   <li>{@code <!ERROR>} - Expected error/exception</li>
 *   <li>{@code <!UNDEFINED>} - Undefined behavior (matches any value)</li>
 *   <li>{@code <!NAN>} or {@code NaN} - Not a Number</li>
 *   <li>{@code inf}, {@code Infinity} - Positive infinity</li>
 *   <li>{@code -inf}, {@code -Infinity} - Negative infinity</li>
 * </ul>
 */
public class SpecialValueHandler {
    
    private static final Set<String> SPECIAL_VALUE_MARKERS = Set.of(
        "<!ERROR>", "<!UNDEFINED>", "<!NAN>", 
        "nan", "NaN", "NAN",
        "inf", "Inf", "INF", "infinity", "Infinity", "INFINITY",
        "-inf", "-Inf", "-INF", "-infinity", "-Infinity", "-INFINITY"
    );
    
    /**
     * Checks if a value is a special value marker.
     */
    public boolean isSpecialValue(Object value) {
        if (value == null) {
            return false;
        }
        
        String str = value.toString().trim();
        return SPECIAL_VALUE_MARKERS.contains(str);
    }
    
    /**
     * Compares two values where at least one is a special value.
     */
    public ComparisonResult compare(Object expected, Object actual) {
        String expectedStr = normalize(expected);
        String actualStr = normalize(actual);
        
        // ERROR handling
        if (isError(expectedStr)) {
            return isError(actualStr) ? 
                ComparisonResult.specialValueMatch("ERROR") :
                ComparisonResult.noMatch("Expected error but got value: " + actual);
        }
        
        // UNDEFINED handling (matches anything)
        if (isUndefined(expectedStr)) {
            return ComparisonResult.specialValueMatch("UNDEFINED (matches any value)");
        }
        
        // NaN handling
        if (isNaN(expectedStr) && isNaN(actualStr)) {
            return ComparisonResult.specialValueMatch("NaN");
        }
        if (isNaN(expectedStr) && !isNaN(actualStr)) {
            return ComparisonResult.noMatch("Expected NaN but got: " + actual);
        }
        if (!isNaN(expectedStr) && isNaN(actualStr)) {
            return ComparisonResult.noMatch("Expected " + expected + " but got NaN");
        }
        
        // Positive infinity handling
        if (isPositiveInfinity(expectedStr) && isPositiveInfinity(actualStr)) {
            return ComparisonResult.specialValueMatch("Positive Infinity");
        }
        if (isPositiveInfinity(expectedStr) && !isPositiveInfinity(actualStr)) {
            return ComparisonResult.noMatch("Expected +Infinity but got: " + actual);
        }
        if (!isPositiveInfinity(expectedStr) && isPositiveInfinity(actualStr)) {
            return ComparisonResult.noMatch("Expected " + expected + " but got +Infinity");
        }
        
        // Negative infinity handling
        if (isNegativeInfinity(expectedStr) && isNegativeInfinity(actualStr)) {
            return ComparisonResult.specialValueMatch("Negative Infinity");
        }
        if (isNegativeInfinity(expectedStr) && !isNegativeInfinity(actualStr)) {
            return ComparisonResult.noMatch("Expected -Infinity but got: " + actual);
        }
        if (!isNegativeInfinity(expectedStr) && isNegativeInfinity(actualStr)) {
            return ComparisonResult.noMatch("Expected " + expected + " but got -Infinity");
        }
        
        return ComparisonResult.noMatch("Special value mismatch");
    }
    
    private String normalize(Object value) {
        return value == null ? "null" : value.toString().trim();
    }
    
    private boolean isError(String value) {
        return value.equals("<!ERROR>");
    }
    
    private boolean isUndefined(String value) {
        return value.equals("<!UNDEFINED>");
    }
    
    private boolean isNaN(String value) {
        if (value.equals("<!NAN>")) {
            return true;
        }
        String lower = value.toLowerCase();
        if (lower.equals("nan")) {
            return true;
        }
        // Check if it's a numeric NaN
        try {
            double d = Double.parseDouble(value);
            return Double.isNaN(d);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isPositiveInfinity(String value) {
        String lower = value.toLowerCase();
        if (lower.equals("inf") || lower.equals("infinity") || lower.equals("+inf")) {
            return true;
        }
        // Check if it's a numeric infinity
        try {
            double d = Double.parseDouble(value);
            return Double.isInfinite(d) && d > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isNegativeInfinity(String value) {
        String lower = value.toLowerCase();
        if (lower.equals("-inf") || lower.equals("-infinity")) {
            return true;
        }
        // Check if it's a numeric negative infinity
        try {
            double d = Double.parseDouble(value);
            return Double.isInfinite(d) && d < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

// Made with Bob
