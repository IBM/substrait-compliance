package io.substrait.compliance.comparison;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpecialValueHandler.
 */
class SpecialValueHandlerTest {
    
    private SpecialValueHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new SpecialValueHandler();
    }
    
    @Test
    void testIsSpecialValue() {
        assertTrue(handler.isSpecialValue("<!ERROR>"));
        assertTrue(handler.isSpecialValue("<!UNDEFINED>"));
        assertTrue(handler.isSpecialValue("<!NAN>"));
        assertTrue(handler.isSpecialValue("NaN"));
        assertTrue(handler.isSpecialValue("inf"));
        assertTrue(handler.isSpecialValue("Infinity"));
        assertTrue(handler.isSpecialValue("-inf"));
        assertTrue(handler.isSpecialValue("-Infinity"));
        
        assertFalse(handler.isSpecialValue("42"));
        assertFalse(handler.isSpecialValue("hello"));
        assertFalse(handler.isSpecialValue(null));
    }
    
    @Test
    void testErrorComparison() {
        ComparisonResult result = handler.compare("<!ERROR>", "<!ERROR>");
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testErrorVsValue() {
        ComparisonResult result = handler.compare("<!ERROR>", "42");
        assertFalse(result.matches());
        assertTrue(result.getDetailedMessage().contains("error"));
    }
    
    @Test
    void testUndefinedMatchesAnything() {
        ComparisonResult result1 = handler.compare("<!UNDEFINED>", "42");
        assertTrue(result1.matches());
        
        ComparisonResult result2 = handler.compare("<!UNDEFINED>", "hello");
        assertTrue(result2.matches());
        
        ComparisonResult result3 = handler.compare("<!UNDEFINED>", "<!ERROR>");
        assertTrue(result3.matches());
    }
    
    @Test
    void testNaNComparison() {
        ComparisonResult result = handler.compare("<!NAN>", "NaN");
        assertTrue(result.matches());
        assertEquals(ComparisonResult.ComparisonType.SPECIAL_VALUE_MATCH, result.getComparisonType());
    }
    
    @Test
    void testNaNVsValue() {
        ComparisonResult result = handler.compare("<!NAN>", "42");
        assertFalse(result.matches());
    }
    
    @Test
    void testPositiveInfinity() {
        ComparisonResult result1 = handler.compare("inf", "Infinity");
        assertTrue(result1.matches());
        
        ComparisonResult result2 = handler.compare("inf", "+inf");
        assertTrue(result2.matches());
    }
    
    @Test
    void testNegativeInfinity() {
        ComparisonResult result = handler.compare("-inf", "-Infinity");
        assertTrue(result.matches());
    }
    
    @Test
    void testInfinityVsValue() {
        ComparisonResult result = handler.compare("inf", "42");
        assertFalse(result.matches());
    }
    
    @Test
    void testPositiveVsNegativeInfinity() {
        ComparisonResult result = handler.compare("inf", "-inf");
        assertFalse(result.matches());
    }
    
    @Test
    void testCaseInsensitivity() {
        assertTrue(handler.isSpecialValue("nan"));
        assertTrue(handler.isSpecialValue("NAN"));
        assertTrue(handler.isSpecialValue("NaN"));
        
        assertTrue(handler.isSpecialValue("inf"));
        assertTrue(handler.isSpecialValue("INF"));
        assertTrue(handler.isSpecialValue("Inf"));
    }
}

