package io.substrait.compliance.analysis;

import io.substrait.compliance.FailureCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report generated from analyzing a test failure.
 * 
 * <p>Contains:
 * <ul>
 *   <li>Failure category</li>
 *   <li>Root cause analysis</li>
 *   <li>Suggestions for fixing</li>
 *   <li>Known issue status</li>
 *   <li>Diagnostic context</li>
 * </ul>
 */
public class FailureReport {
    
    private final boolean success;
    private final FailureCategory category;
    private final String rootCause;
    private final List<String> suggestions;
    private final boolean isKnownIssue;
    private final String errorMessage;
    private final long executionTimeMs;
    private final int validationErrors;
    private final String comparisonDetails;
    
    private FailureReport(Builder builder) {
        this.success = builder.success;
        this.category = builder.category;
        this.rootCause = builder.rootCause;
        this.suggestions = Collections.unmodifiableList(new ArrayList<>(builder.suggestions));
        this.isKnownIssue = builder.isKnownIssue;
        this.errorMessage = builder.errorMessage;
        this.executionTimeMs = builder.executionTimeMs;
        this.validationErrors = builder.validationErrors;
        this.comparisonDetails = builder.comparisonDetails;
    }
    
    /**
     * Creates a success report (no failure).
     */
    public static FailureReport success() {
        return builder().success(true).build();
    }
    
    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    
    public boolean isSuccess() {
        return success;
    }
    
    public FailureCategory getCategory() {
        return category;
    }
    
    public String getRootCause() {
        return rootCause;
    }
    
    public List<String> getSuggestions() {
        return suggestions;
    }
    
    public boolean isKnownIssue() {
        return isKnownIssue;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public int getValidationErrors() {
        return validationErrors;
    }
    
    public String getComparisonDetails() {
        return comparisonDetails;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "FailureReport{success=true}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("FailureReport{\n");
        sb.append("  category=").append(category).append("\n");
        sb.append("  rootCause='").append(rootCause).append("'\n");
        
        if (isKnownIssue) {
            sb.append("  knownIssue=true\n");
        }
        
        if (!suggestions.isEmpty()) {
            sb.append("  suggestions=[\n");
            suggestions.forEach(s -> sb.append("    - ").append(s).append("\n"));
            sb.append("  ]\n");
        }
        
        if (validationErrors > 0) {
            sb.append("  validationErrors=").append(validationErrors).append("\n");
        }
        
        if (comparisonDetails != null) {
            sb.append("  comparisonDetails='").append(comparisonDetails).append("'\n");
        }
        
        sb.append("  executionTime=").append(executionTimeMs).append("ms\n");
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Builder for FailureReport.
     */
    public static class Builder {
        private boolean success = false;
        private FailureCategory category;
        private String rootCause;
        private List<String> suggestions = new ArrayList<>();
        private boolean isKnownIssue = false;
        private String errorMessage;
        private long executionTimeMs;
        private int validationErrors;
        private String comparisonDetails;
        
        private Builder() {}
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder category(FailureCategory category) {
            this.category = category;
            return this;
        }
        
        public Builder rootCause(String rootCause) {
            this.rootCause = rootCause;
            return this;
        }
        
        public Builder suggestions(List<String> suggestions) {
            this.suggestions = new ArrayList<>(suggestions);
            return this;
        }
        
        public Builder addSuggestion(String suggestion) {
            this.suggestions.add(suggestion);
            return this;
        }
        
        public Builder isKnownIssue(boolean isKnownIssue) {
            this.isKnownIssue = isKnownIssue;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }
        
        public Builder validationErrors(int validationErrors) {
            this.validationErrors = validationErrors;
            return this;
        }
        
        public Builder comparisonDetails(String comparisonDetails) {
            this.comparisonDetails = comparisonDetails;
            return this;
        }
        
        public FailureReport build() {
            return new FailureReport(this);
        }
    }
}

