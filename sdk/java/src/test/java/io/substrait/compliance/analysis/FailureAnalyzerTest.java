package io.substrait.compliance.analysis;

import io.substrait.compliance.EnhancedComplianceResult;
import io.substrait.compliance.FailureCategory;
import io.substrait.compliance.comparison.ComparisonResult;
import io.substrait.compliance.validator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FailureAnalyzer.
 */
class FailureAnalyzerTest {
    
    private FailureAnalyzer analyzer;
    
    @BeforeEach
    void setUp() {
        analyzer = new FailureAnalyzer();
    }
    
    @Test
    void testSuccessResult() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(true)
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertTrue(report.isSuccess());
    }
    
    @Test
    void testValidationError() {
        ValidationResult validation = new ValidationResult();
        validation.addError("Plan has no relations");
        
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .planValidation(validation)
            .executionTimeMs(50)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.VALIDATION_ERROR, report.getCategory());
        assertTrue(report.getRootCause().contains("relations"));
        assertFalse(report.getSuggestions().isEmpty());
    }
    
    @Test
    void testTypeMismatch() {
        ComparisonResult comparison = ComparisonResult.noMatch("Expected i32 but got fp64");
        
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .comparisonResult(comparison)
            .expectedResultType("i32")
            .actualResultType("fp64")
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.TYPE_MISMATCH, report.getCategory());
        assertFalse(report.getSuggestions().isEmpty());
        assertTrue(report.getSuggestions().stream()
            .anyMatch(s -> s.toLowerCase().contains("type")));
    }
    
    @Test
    void testValueMismatch() {
        ComparisonResult comparison = ComparisonResult.noMatch("Expected 42 but got 43");
        
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .comparisonResult(comparison)
            .expectedResult(42)
            .actualResult(43)
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.VALUE_MISMATCH, report.getCategory());
        assertFalse(report.getSuggestions().isEmpty());
    }
    
    @Test
    void testParsingError() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .errorMessage("Failed to parse Substrait plan: invalid syntax")
            .executionTimeMs(10)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.PARSING_ERROR, report.getCategory());
        assertTrue(report.getRootCause().toLowerCase().contains("parse"));
    }
    
    @Test
    void testUnsupportedFeature() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .errorMessage("Unsupported operation: WINDOW function")
            .executionTimeMs(20)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.UNSUPPORTED_FEATURE, report.getCategory());
        assertTrue(report.getSuggestions().stream()
            .anyMatch(s -> s.toLowerCase().contains("capability")));
    }
    
    @Test
    void testTimeout() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .errorMessage("Query execution timed out after 30 seconds")
            .executionTimeMs(30000)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.TIMEOUT, report.getCategory());
        assertTrue(report.getSuggestions().stream()
            .anyMatch(s -> s.toLowerCase().contains("timeout")));
    }
    
    @Test
    void testResourceExhaustion() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .errorMessage("Out of memory: heap space exhausted")
            .executionTimeMs(5000)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.RESOURCE_EXHAUSTION, report.getCategory());
        assertTrue(report.getSuggestions().stream()
            .anyMatch(s -> s.toLowerCase().contains("memory")));
    }
    
    @Test
    void testRuntimeError() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .errorMessage("Division by zero")
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertFalse(report.isSuccess());
        assertEquals(FailureCategory.RUNTIME_ERROR, report.getCategory());
        assertEquals("Division by zero", report.getRootCause());
    }
    
    @Test
    void testExplicitCategory() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .failureCategory(FailureCategory.COMPARISON_ERROR)
            .errorMessage("Some error")
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        assertEquals(FailureCategory.COMPARISON_ERROR, report.getCategory());
    }
    
    @Test
    void testReportToString() {
        EnhancedComplianceResult result = EnhancedComplianceResult.builder()
            .success(false)
            .failureCategory(FailureCategory.VALUE_MISMATCH)
            .errorMessage("Mismatch")
            .executionTimeMs(100)
            .build();
        
        FailureReport report = analyzer.analyze(result);
        String str = report.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("VALUE_MISMATCH"));
        assertTrue(str.contains("rootCause"));
        assertTrue(str.contains("suggestions"));
    }
}

// Made with Bob
