package io.substrait.compliance.comparison;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IntegerComparator.
 */
class IntegerComparatorTest {
    
    private IntegerComparator comparator;
    
    @BeforeEach
    void setUp() {
        comparator = new IntegerComparator();
    }
    
    @Test
    void testSupportsIntegerTypes() {
        assertTrue(comparator.supports("i32"));
        assertTrue(comparator.supports("i64"));
        assertTrue(comparator.supports("int"));
        assertTrue(comparator.supports("long"));
        assertFalse(comparator.supports("fp64"));
        assertFalse(comparator.supports("string"));
    }
    
    @Test
    void testExactMatch() {
        ComparisonResult result = comparator.compare(42, "i32", 42, "i32");
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.EXACT_MATCH, result.getComparisonType());
    }
    
    @Test
    void testMismatch() {
        ComparisonResult result = comparator.compare(42, "i32", 43, "i32");
        assertFalse(result.matches());
        assertEquals(ComparisonResult.ComparisonType.NO_MATCH, result.getComparisonType());
        assertTrue(result.getDetailedMessage().contains("42"));
        assertTrue(result.getDetailedMessage().contains("43"));
    }
    
    @Test
    void testNegativeNumbers() {
        ComparisonResult result = comparator.compare(-42, "i32", -42, "i32");
        assertTrue(result.matches());
    }
    
    @Test
    void testZero() {
        ComparisonResult result = comparator.compare(0, "i32", 0, "i32");
        assertTrue(result.matches());
    }
    
    @Test
    void testLargeNumbers() {
        ComparisonResult result = comparator.compare(
            Long.MAX_VALUE, "i64", 
            Long.MAX_VALUE, "i64"
        );
        assertTrue(result.matches());
    }
    
    @Test
    void testStringToInteger() {
        ComparisonResult result = comparator.compare("42", "i32", 42, "i32");
        assertTrue(result.matches());
    }
    
    @Test
    void testNumberObjects() {
        ComparisonResult result = comparator.compare(
            Integer.valueOf(42), "i32",
            Long.valueOf(42), "i64"
        );
        assertTrue(result.matches());
    }
}

// Made with Bob
