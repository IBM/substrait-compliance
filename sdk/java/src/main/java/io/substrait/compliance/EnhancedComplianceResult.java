package io.substrait.compliance;

import io.substrait.compliance.comparison.ComparisonResult;
import io.substrait.compliance.validator.ValidationResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced result of executing a Substrait plan in a compliance test.
 * 
 * <p>Extends the basic {@link ComplianceResult} with additional diagnostic information:
 * <ul>
 *   <li>Actual result value and type (not just success/failure)</li>
 *   <li>Expected result value and type for comparison</li>
 *   <li>Plan validation results</li>
 *   <li>Failure categorization</li>
 *   <li>Detailed comparison results</li>
 *   <li>Diagnostic data for debugging</li>
 * </ul>
 * 
 * <p>Use the {@link Builder} to construct instances.
 */
public class EnhancedComplianceResult extends ComplianceResult {
    
    private final Object actualResult;
    private final String actualResultType;
    private final Object expectedResult;
    private final String expectedResultType;
    private final ValidationResult planValidation;
    private final FailureCategory failureCategory;
    private final ComparisonResult comparisonResult;
    private final Map<String, Object> diagnosticData;
    
    private EnhancedComplianceResult(Builder builder) {
        super(builder.success, builder.outputData, builder.executionTimeMs, 
              builder.errorMessage, builder.exception);
        this.actualResult = builder.actualResult;
        this.actualResultType = builder.actualResultType;
        this.expectedResult = builder.expectedResult;
        this.expectedResultType = builder.expectedResultType;
        this.planValidation = builder.planValidation;
        this.failureCategory = builder.failureCategory;
        this.comparisonResult = builder.comparisonResult;
        this.diagnosticData = Collections.unmodifiableMap(new HashMap<>(builder.diagnosticData));
    }
    
    // Getters
    
    public Object getActualResult() {
        return actualResult;
    }
    
    public String getActualResultType() {
        return actualResultType;
    }
    
    public Object getExpectedResult() {
        return expectedResult;
    }
    
    public String getExpectedResultType() {
        return expectedResultType;
    }
    
    public ValidationResult getPlanValidation() {
        return planValidation;
    }
    
    public FailureCategory getFailureCategory() {
        return failureCategory;
    }
    
    public ComparisonResult getComparisonResult() {
        return comparisonResult;
    }
    
    public Map<String, Object> getDiagnosticData() {
        return diagnosticData;
    }
    
    /**
     * Creates a new builder for constructing EnhancedComplianceResult instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EnhancedComplianceResult{");
        sb.append("success=").append(isSuccess());
        
        if (failureCategory != null) {
            sb.append(", category=").append(failureCategory);
        }
        
        if (comparisonResult != null) {
            sb.append(", comparison=").append(comparisonResult.getComparisonType());
        }
        
        if (planValidation != null && !planValidation.isValid()) {
            sb.append(", validationErrors=").append(planValidation.getErrors().size());
        }
        
        sb.append(", time=").append(getExecutionTimeMs()).append("ms");
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Builder for creating EnhancedComplianceResult instances.
     */
    public static class Builder {
        private boolean success;
        private TableData outputData;
        private long executionTimeMs;
        private String errorMessage;
        private Throwable exception;
        private Object actualResult;
        private String actualResultType;
        private Object expectedResult;
        private String expectedResultType;
        private ValidationResult planValidation;
        private FailureCategory failureCategory;
        private ComparisonResult comparisonResult;
        private Map<String, Object> diagnosticData = new HashMap<>();
        
        private Builder() {}
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder outputData(TableData outputData) {
            this.outputData = outputData;
            return this;
        }
        
        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }
        
        public Builder actualResult(Object actualResult) {
            this.actualResult = actualResult;
            return this;
        }
        
        public Builder actualResultType(String actualResultType) {
            this.actualResultType = actualResultType;
            return this;
        }
        
        public Builder expectedResult(Object expectedResult) {
            this.expectedResult = expectedResult;
            return this;
        }
        
        public Builder expectedResultType(String expectedResultType) {
            this.expectedResultType = expectedResultType;
            return this;
        }
        
        public Builder planValidation(ValidationResult planValidation) {
            this.planValidation = planValidation;
            return this;
        }
        
        public Builder failureCategory(FailureCategory failureCategory) {
            this.failureCategory = failureCategory;
            return this;
        }
        
        public Builder comparisonResult(ComparisonResult comparisonResult) {
            this.comparisonResult = comparisonResult;
            return this;
        }
        
        public Builder addDiagnosticData(String key, Object value) {
            this.diagnosticData.put(key, value);
            return this;
        }
        
        public Builder diagnosticData(Map<String, Object> diagnosticData) {
            this.diagnosticData = new HashMap<>(diagnosticData);
            return this;
        }
        
        public EnhancedComplianceResult build() {
            return new EnhancedComplianceResult(this);
        }
    }
}

