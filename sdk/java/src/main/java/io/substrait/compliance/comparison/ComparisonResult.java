package io.substrait.compliance.comparison;

/**
 * Result of comparing expected and actual test results.
 * 
 * <p>Provides detailed information about whether results match and why they don't.
 */
public class ComparisonResult {
    
    private final boolean matches;
    private final String detailedMessage;
    private final ComparisonType comparisonType;
    private final double numericDifference;
    
    private ComparisonResult(boolean matches, String detailedMessage, 
                            ComparisonType comparisonType, double numericDifference) {
        this.matches = matches;
        this.detailedMessage = detailedMessage;
        this.comparisonType = comparisonType;
        this.numericDifference = numericDifference;
    }
    
    /**
     * Types of comparison matches.
     */
    public enum ComparisonType {
        /** Values match exactly */
        EXACT_MATCH,
        /** Floating-point values match within epsilon tolerance */
        EPSILON_MATCH,
        /** Values match after type coercion */
        TYPE_COERCION_MATCH,
        /** Special values match (NaN, Infinity, ERROR, UNDEFINED) */
        SPECIAL_VALUE_MATCH,
        /** Values do not match */
        NO_MATCH
    }
    
    // Factory methods for different match types
    
    public static ComparisonResult exactMatch() {
        return new ComparisonResult(true, "Exact match", ComparisonType.EXACT_MATCH, 0.0);
    }
    
    public static ComparisonResult epsilonMatch(double difference) {
        return new ComparisonResult(true, 
            String.format("Match within epsilon (diff: %.10e)", difference),
            ComparisonType.EPSILON_MATCH, difference);
    }
    
    public static ComparisonResult typeCoercionMatch(String message) {
        return new ComparisonResult(true, message, ComparisonType.TYPE_COERCION_MATCH, 0.0);
    }
    
    public static ComparisonResult specialValueMatch(String specialValue) {
        return new ComparisonResult(true, 
            "Special value match: " + specialValue,
            ComparisonType.SPECIAL_VALUE_MATCH, 0.0);
    }
    
    public static ComparisonResult noMatch(String reason) {
        return new ComparisonResult(false, reason, ComparisonType.NO_MATCH, Double.NaN);
    }
    
    // Getters
    
    public boolean matches() {
        return matches;
    }
    
    public String getDetailedMessage() {
        return detailedMessage;
    }
    
    public ComparisonType getComparisonType() {
        return comparisonType;
    }
    
    public double getNumericDifference() {
        return numericDifference;
    }
    
    @Override
    public String toString() {
        if (matches) {
            return "✓ " + detailedMessage;
        } else {
            return "✗ " + detailedMessage;
        }
    }
}

// Made with Bob
