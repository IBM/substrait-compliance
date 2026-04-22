package io.substrait.compliance.analysis;

import io.substrait.compliance.EnhancedComplianceResult;
import io.substrait.compliance.FailureCategory;
import io.substrait.compliance.comparison.ComparisonResult;
import io.substrait.compliance.validator.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes test failures to provide insights and recommendations.
 * 
 * <p>This analyzer:
 * <ul>
 *   <li>Categorizes failures systematically</li>
 *   <li>Identifies root causes</li>
 *   <li>Suggests fixes</li>
 *   <li>Detects known issues</li>
 * </ul>
 */
public class FailureAnalyzer {
    
    /**
     * Analyzes a test failure and generates a detailed report.
     * 
     * @param result the enhanced compliance result to analyze
     * @return failure report with analysis
     */
    public FailureReport analyze(EnhancedComplianceResult result) {
        if (result.isSuccess()) {
            return FailureReport.success();
        }
        
        FailureReport.Builder reportBuilder = FailureReport.builder();
        
        // Determine failure category if not already set
        FailureCategory category = result.getFailureCategory();
        if (category == null) {
            category = determineCategory(result);
        }
        reportBuilder.category(category);
        
        // Extract root cause
        String rootCause = extractRootCause(result, category);
        reportBuilder.rootCause(rootCause);
        
        // Generate suggestions
        List<String> suggestions = generateSuggestions(result, category);
        reportBuilder.suggestions(suggestions);
        
        // Check for known issues
        boolean isKnown = checkKnownIssues(result, category);
        reportBuilder.isKnownIssue(isKnown);
        
        // Add diagnostic context
        reportBuilder.errorMessage(result.getErrorMessage());
        reportBuilder.executionTimeMs(result.getExecutionTimeMs());
        
        if (result.getPlanValidation() != null) {
            reportBuilder.validationErrors(result.getPlanValidation().getErrors().size());
        }
        
        if (result.getComparisonResult() != null) {
            reportBuilder.comparisonDetails(result.getComparisonResult().getDetailedMessage());
        }
        
        return reportBuilder.build();
    }
    
    /**
     * Determines the failure category based on the result.
     */
    private FailureCategory determineCategory(EnhancedComplianceResult result) {
        // Check validation errors first
        ValidationResult validation = result.getPlanValidation();
        if (validation != null && !validation.isValid()) {
            return FailureCategory.VALIDATION_ERROR;
        }
        
        // Check for type mismatch by comparing result types
        String expectedType = result.getExpectedResultType();
        String actualType = result.getActualResultType();
        if (expectedType != null && actualType != null && !expectedType.equals(actualType)) {
            return FailureCategory.TYPE_MISMATCH;
        }
        
        // Check comparison result
        ComparisonResult comparison = result.getComparisonResult();
        if (comparison != null && !comparison.matches()) {
            String message = comparison.getDetailedMessage().toLowerCase();
            if (message.contains("type")
                || (message.contains("expected") && message.contains("got")
                    && expectedType == null && actualType == null
                    && looksLikeTypeName(comparison.getDetailedMessage()))) {
                return FailureCategory.TYPE_MISMATCH;
            }
            return FailureCategory.VALUE_MISMATCH;
        }
        
        // Check error message patterns
        String errorMsg = result.getErrorMessage();
        if (errorMsg != null) {
            String lowerMsg = errorMsg.toLowerCase();
            
            if (lowerMsg.contains("parse") || lowerMsg.contains("syntax")) {
                return FailureCategory.PARSING_ERROR;
            }
            if (lowerMsg.contains("unsupported") || lowerMsg.contains("not supported")) {
                return FailureCategory.UNSUPPORTED_FEATURE;
            }
            if (lowerMsg.contains("timeout") || lowerMsg.contains("timed out")) {
                return FailureCategory.TIMEOUT;
            }
            if (lowerMsg.contains("memory") || lowerMsg.contains("out of")) {
                return FailureCategory.RESOURCE_EXHAUSTION;
            }
        }
        
        // Check exception type
        Throwable exception = result.getException();
        if (exception != null) {
            String exceptionName = exception.getClass().getSimpleName().toLowerCase();
            if (exceptionName.contains("parse")) {
                return FailureCategory.PARSING_ERROR;
            }
            if (exceptionName.contains("unsupported")) {
                return FailureCategory.UNSUPPORTED_FEATURE;
            }
            if (exceptionName.contains("timeout")) {
                return FailureCategory.TIMEOUT;
            }
        }
        
        // Default to runtime error
        return FailureCategory.RUNTIME_ERROR;
    }
    
    /**
     * Extracts the root cause of the failure.
     */
    private String extractRootCause(EnhancedComplianceResult result, FailureCategory category) {
        switch (category) {
            case VALIDATION_ERROR:
                ValidationResult validation = result.getPlanValidation();
                if (validation != null && !validation.getErrors().isEmpty()) {
                    return validation.getErrors().get(0).getMessage();
                }
                return "Plan validation failed";
                
            case TYPE_MISMATCH:
            case VALUE_MISMATCH:
                ComparisonResult comparison = result.getComparisonResult();
                if (comparison != null) {
                    return comparison.getDetailedMessage();
                }
                return "Result mismatch";
                
            case PARSING_ERROR:
                return "Failed to parse Substrait plan";
                
            case UNSUPPORTED_FEATURE:
                return "Engine does not support required feature";
                
            case RUNTIME_ERROR:
                if (result.getErrorMessage() != null) {
                    return result.getErrorMessage();
                }
                if (result.getException() != null) {
                    return result.getException().getMessage();
                }
                return "Runtime execution error";
                
            case TIMEOUT:
                return "Execution exceeded time limit";
                
            case RESOURCE_EXHAUSTION:
                return "Resource limits exceeded";
                
            default:
                return "Unknown failure";
        }
    }
    
    /**
     * Generates suggestions for fixing the failure.
     */
    private List<String> generateSuggestions(EnhancedComplianceResult result, FailureCategory category) {
        List<String> suggestions = new ArrayList<>();
        
        switch (category) {
            case VALIDATION_ERROR:
                suggestions.add("Review plan structure and ensure all required fields are present");
                suggestions.add("Check that all references (functions, types) are properly declared");
                ValidationResult validation = result.getPlanValidation();
                if (validation != null) {
                    validation.getErrors().forEach(error -> {
                        if (error.getSuggestion() != null) {
                            suggestions.add(error.getSuggestion());
                        }
                    });
                }
                break;
                
            case TYPE_MISMATCH:
                suggestions.add("Verify expected and actual result types match");
                suggestions.add("Check if type coercion is needed");
                suggestions.add("Review function signature and return type");
                break;
                
            case VALUE_MISMATCH:
                suggestions.add("Check if floating-point epsilon tolerance needs adjustment");
                suggestions.add("Verify test case expected value is correct");
                suggestions.add("Review function implementation for correctness");
                break;
                
            case PARSING_ERROR:
                suggestions.add("Validate Substrait plan syntax");
                suggestions.add("Ensure plan uses supported Substrait version");
                suggestions.add("Check for malformed protobuf messages");
                break;
                
            case UNSUPPORTED_FEATURE:
                suggestions.add("Check engine capability descriptor");
                suggestions.add("Consider skipping test for this engine");
                suggestions.add("Implement missing feature in engine");
                break;
                
            case RUNTIME_ERROR:
                suggestions.add("Review error message and stack trace");
                suggestions.add("Check input data validity");
                suggestions.add("Verify engine configuration");
                break;
                
            case TIMEOUT:
                suggestions.add("Increase timeout limit");
                suggestions.add("Optimize query plan");
                suggestions.add("Check for infinite loops or deadlocks");
                break;
                
            case RESOURCE_EXHAUSTION:
                suggestions.add("Increase memory limits");
                suggestions.add("Reduce data size");
                suggestions.add("Optimize resource usage");
                break;
                
            default:
                suggestions.add("Review test case and engine logs");
                break;
        }
        
        return suggestions;
    }
    
    /**
     * Checks if this is a known issue.
     * 
     * <p>This is a placeholder for future integration with issue tracking systems.
     */
    private boolean checkKnownIssues(EnhancedComplianceResult result, FailureCategory category) {
        // TODO: Integrate with issue tracking system
        // For now, return false
        return false;
    }

    private boolean looksLikeTypeName(String message) {
        String[] tokens = message.split("[^A-Za-z0-9_]+");
        int typeLikeTokenCount = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            String normalized = token.toLowerCase();
            if (normalized.matches("^(bool|boolean|i\\d+|u\\d+|fp\\d+|int\\d+|float\\d+|decimal\\d*|string|str|varchar|char|binary|date|time|timestamp|interval|uuid)$")) {
                typeLikeTokenCount++;
                if (typeLikeTokenCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }
}

// Made with Bob
