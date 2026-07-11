package io.substrait.compliance.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationResult.
 */
class ValidationResultTest {
    
    private ValidationResult result;
    
    @BeforeEach
    void setUp() {
        result = new ValidationResult();
    }
    
    @Test
    void testInitiallyValid() {
        assertTrue(result.isValid());
        assertFalse(result.hasWarnings());
        assertFalse(result.hasInfo());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getInfo().isEmpty());
    }
    
    @Test
    void testAddError() {
        result.addError("Test error");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Test error", result.getErrors().get(0).getMessage());
        assertEquals(ValidationResult.ValidationLevel.ERROR, 
            result.getErrors().get(0).getLevel());
    }
    
    @Test
    void testAddWarning() {
        result.addWarning("Test warning");
        
        assertTrue(result.isValid()); // Warnings don't affect validity
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarnings().size());
        assertEquals("Test warning", result.getWarnings().get(0).getMessage());
        assertEquals(ValidationResult.ValidationLevel.WARNING,
            result.getWarnings().get(0).getLevel());
    }
    
    @Test
    void testAddInfo() {
        result.addInfo("Test info");
        
        assertTrue(result.isValid());
        assertTrue(result.hasInfo());
        assertEquals(1, result.getInfo().size());
        assertEquals("Test info", result.getInfo().get(0).getMessage());
        assertEquals(ValidationResult.ValidationLevel.INFO,
            result.getInfo().get(0).getLevel());
    }
    
    @Test
    void testMultipleIssues() {
        result.addError("Error 1");
        result.addError("Error 2");
        result.addWarning("Warning 1");
        result.addInfo("Info 1");
        
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
        assertEquals(1, result.getInfo().size());
    }
    
    @Test
    void testIssueWithLocation() {
        result.addError("Test error", "Relation 5", "Add missing input");
        
        ValidationResult.ValidationIssue issue = result.getErrors().get(0);
        assertEquals("Test error", issue.getMessage());
        assertEquals("Relation 5", issue.getLocation());
        assertEquals("Add missing input", issue.getSuggestion());
    }
    
    @Test
    void testGetAllIssues() {
        result.addError("Error");
        result.addWarning("Warning");
        result.addInfo("Info");
        
        assertEquals(3, result.getAllIssues().size());
    }
    
    @Test
    void testImmutability() {
        result.addError("Error");
        
        // Get the list and try to modify it
        var errors = result.getErrors();
        assertThrows(UnsupportedOperationException.class, () -> {
            errors.add(new ValidationResult.ValidationIssue(
                ValidationResult.ValidationLevel.ERROR, "New error"));
        });
    }
    
    @Test
    void testToString() {
        result.addError("Test error");
        result.addWarning("Test warning");
        
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("valid=false"));
        assertTrue(str.contains("Test error"));
        assertTrue(str.contains("Test warning"));
    }
    
    @Test
    void testValidationIssueToString() {
        ValidationResult.ValidationIssue issue = new ValidationResult.ValidationIssue(
            ValidationResult.ValidationLevel.ERROR,
            "Test message",
            "Location 1",
            "Fix suggestion"
        );
        
        String str = issue.toString();
        assertTrue(str.contains("ERROR"));
        assertTrue(str.contains("Test message"));
        assertTrue(str.contains("Location 1"));
        assertTrue(str.contains("Fix suggestion"));
    }
}

