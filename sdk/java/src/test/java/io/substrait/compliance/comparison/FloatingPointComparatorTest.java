package io.substrait.compliance.comparison;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FloatingPointComparator.
 */
class FloatingPointComparatorTest {
    
    private FloatingPointComparator comparator;
    private static final double EPSILON = 1e-9;
    
    @BeforeEach
    void setUp() {
        comparator = new FloatingPointComparator(EPSILON);
    }
    
    @Test
    void testSupportsFloatingPointTypes() {
        assertTrue(comparator.supports("fp32"));
        assertTrue(comparator.supports("fp64"));
        assertTrue(comparator.supports("float"));
        assertTrue(comparator.supports("double"));
        assertFalse(comparator.supports("i32"));
        assertFalse(comparator.supports("string"));
    }
    
    @Test
    void testExactMatch() {
        ComparisonResult result = comparator.compare(3.14, "fp64", 3.14, "fp64");
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.EXACT_MATCH, result.getComparisonType());
    }
    
    @Test
    void testEpsilonMatch() {
        ComparisonResult result = comparator.compare(
            3.14159265358979, "fp64",
            3.14159265358980, "fp64"
        );
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.EPSILON_MATCH, result.getComparisonType());
        assertTrue(result.getNumericDifference() < EPSILON);
    }
    
    @Test
    void testMismatchBeyondEpsilon() {
        ComparisonResult result = comparator.compare(3.14, "fp64", 3.15, "fp64");
        assertFalse(result.matches());
        assertEquals(ComparisonResult.ComparisonType.NO_MATCH, result.getComparisonType());
    }
    
    @Test
    void testNaNComparison() {
        ComparisonResult result = comparator.compare(
            Double.NaN, "fp64",
            Double.NaN, "fp64"
        );
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testNaNStringComparison() {
        ComparisonResult result = comparator.compare("NaN", "fp64", "<!NAN>", "fp64");
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testPositiveInfinity() {
        ComparisonResult result = comparator.compare(
            Double.POSITIVE_INFINITY, "fp64",
            Double.POSITIVE_INFINITY, "fp64"
        );
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testNegativeInfinity() {
        ComparisonResult result = comparator.compare(
            Double.NEGATIVE_INFINITY, "fp64",
            Double.NEGATIVE_INFINITY, "fp64"
        );
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testInfinityStringComparison() {
        ComparisonResult result = comparator.compare("inf", "fp64", "Infinity", "fp64");
        assertTrue(result.matches());
    }
    
    @Test
    void testNegativeInfinityStringComparison() {
        ComparisonResult result = comparator.compare("-inf", "fp64", "-Infinity", "fp64");
        assertTrue(result.matches());
    }
    
    @Test
    void testInfinityMismatch() {
        ComparisonResult result = comparator.compare(
            Double.POSITIVE_INFINITY, "fp64",
            Double.NEGATIVE_INFINITY, "fp64"
        );
        assertFalse(result.matches());
    }
    
    @Test
    void testNaNVsNumber() {
        ComparisonResult result = comparator.compare(Double.NaN, "fp64", 3.14, "fp64");
        assertFalse(result.matches());
    }
    
    @Test
    void testZero() {
        ComparisonResult result = comparator.compare(0.0, "fp64", 0.0, "fp64");
        assertTrue(result.matches());
    }
    
    @Test
    void testNegativeNumbers() {
        ComparisonResult result = comparator.compare(-3.14, "fp64", -3.14, "fp64");
        assertTrue(result.matches());
    }
    
    @Test
    void testVerySmallDifference() {
        ComparisonResult result = comparator.compare(
            1.0, "fp64",
            1.0 + 1e-10, "fp64"
        );
        assertTrue(result.matches());
    }
}

