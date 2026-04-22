package io.substrait.compliance.comparison;

import java.util.Set;

/**
 * Comparator for floating-point types (fp32, fp64) with epsilon tolerance.
 * 
 * <p>Handles special floating-point values:
 * <ul>
 *   <li>NaN (Not a Number)</li>
 *   <li>Positive and negative infinity</li>
 *   <li>Epsilon-based comparison for regular values</li>
 * </ul>
 */
public class FloatingPointComparator implements TypeAwareComparator {
    
    private static final double DEFAULT_EPSILON = 1e-9;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "fp32", "fp64",
        "float", "float32", "float64",
        "double", "real", "decimal"
    );
    
    private final double epsilon;
    
    public FloatingPointComparator() {
        this(DEFAULT_EPSILON);
    }
    
    public FloatingPointComparator(double epsilon) {
        this.epsilon = epsilon;
    }
    
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        try {
            double expectedValue = toDouble(expected);
            double actualValue = toDouble(actual);
            
            // Handle infinity
            if (Double.isInfinite(expectedValue) || Double.isInfinite(actualValue)) {
                if (expectedValue == actualValue) {
                    String infType = expectedValue > 0 ? "Positive Infinity" : "Negative Infinity";
                    return ComparisonResult.specialValueMatch(infType);
                }
                return ComparisonResult.noMatch(
                    String.format("Infinity mismatch: expected %s, got %s", 
                        formatDouble(expectedValue), formatDouble(actualValue))
                );
            }
            
            // Handle NaN
            if (Double.isNaN(expectedValue) && Double.isNaN(actualValue)) {
                return ComparisonResult.specialValueMatch("NaN");
            }
            if (Double.isNaN(expectedValue) || Double.isNaN(actualValue)) {
                return ComparisonResult.noMatch(
                    String.format("NaN mismatch: expected %s, got %s",
                        formatDouble(expectedValue), formatDouble(actualValue))
                );
            }
            
            // Epsilon comparison for regular values
            double diff = Math.abs(expectedValue - actualValue);
            if (diff < epsilon) {
                if (diff == 0.0) {
                    return ComparisonResult.exactMatch();
                } else {
                    return ComparisonResult.epsilonMatch(diff);
                }
            }
            
            return ComparisonResult.noMatch(
                String.format("Expected %.10f but got %.10f (diff: %.10e, epsilon: %.10e)", 
                    expectedValue, actualValue, diff, epsilon)
            );
            
        } catch (Exception e) {
            return ComparisonResult.noMatch(
                "Failed to compare as floating-point: " + e.getMessage()
            );
        }
    }
    
    @Override
    public boolean supports(String type) {
        return type != null && SUPPORTED_TYPES.contains(type.toLowerCase());
    }
    
    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String str = value.toString().trim().toLowerCase();
        
        // Handle special string representations
        if (str.equals("nan") || str.equals("<!nan>")) {
            return Double.NaN;
        }
        if (str.equals("inf") || str.equals("infinity") || str.equals("+inf")) {
            return Double.POSITIVE_INFINITY;
        }
        if (str.equals("-inf") || str.equals("-infinity")) {
            return Double.NEGATIVE_INFINITY;
        }
        
        return Double.parseDouble(value.toString());
    }
    
    private String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "+Infinity" : "-Infinity";
        }
        return String.format("%.10f", value);
    }
}

// Made with Bob
