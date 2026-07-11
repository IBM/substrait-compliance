package io.substrait.compliance.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a Substrait plan.
 * 
 * <p>Contains errors, warnings, and informational messages from validation.
 * A plan is considered valid if it has no errors (warnings are allowed).
 */
public class ValidationResult {
    
    private final List<ValidationIssue> errors = new ArrayList<>();
    private final List<ValidationIssue> warnings = new ArrayList<>();
    private final List<ValidationIssue> info = new ArrayList<>();
    
    /**
     * Validation severity levels.
     */
    public enum ValidationLevel {
        /** Critical error that blocks execution */
        ERROR,
        /** Warning that may cause issues but doesn't block execution */
        WARNING,
        /** Informational message */
        INFO
    }
    
    /**
     * A single validation issue.
     */
    public static class ValidationIssue {
        private final ValidationLevel level;
        private final String message;
        private final String location;
        private final String suggestion;
        
        public ValidationIssue(ValidationLevel level, String message, String location, String suggestion) {
            this.level = level;
            this.message = message;
            this.location = location;
            this.suggestion = suggestion;
        }
        
        public ValidationIssue(ValidationLevel level, String message) {
            this(level, message, null, null);
        }
        
        public ValidationLevel getLevel() {
            return level;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getLocation() {
            return location;
        }
        
        public String getSuggestion() {
            return suggestion;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(level).append("] ");
            if (location != null) {
                sb.append(location).append(": ");
            }
            sb.append(message);
            if (suggestion != null) {
                sb.append(" (Suggestion: ").append(suggestion).append(")");
            }
            return sb.toString();
        }
    }
    
    // Methods to add issues
    
    public void addError(String message) {
        errors.add(new ValidationIssue(ValidationLevel.ERROR, message));
    }
    
    public void addError(String message, String location, String suggestion) {
        errors.add(new ValidationIssue(ValidationLevel.ERROR, message, location, suggestion));
    }
    
    public void addWarning(String message) {
        warnings.add(new ValidationIssue(ValidationLevel.WARNING, message));
    }
    
    public void addWarning(String message, String location, String suggestion) {
        warnings.add(new ValidationIssue(ValidationLevel.WARNING, message, location, suggestion));
    }
    
    public void addInfo(String message) {
        info.add(new ValidationIssue(ValidationLevel.INFO, message));
    }
    
    public void addInfo(String message, String location) {
        info.add(new ValidationIssue(ValidationLevel.INFO, message, location, null));
    }
    
    // Query methods
    
    /**
     * Returns true if the plan is valid (no errors).
     * Warnings do not affect validity.
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Returns true if there are any informational messages.
     */
    public boolean hasInfo() {
        return !info.isEmpty();
    }
    
    // Getters for issues
    
    public List<ValidationIssue> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    public List<ValidationIssue> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
    
    public List<ValidationIssue> getInfo() {
        return Collections.unmodifiableList(info);
    }
    
    /**
     * Returns all issues (errors, warnings, and info) combined.
     */
    public List<ValidationIssue> getAllIssues() {
        List<ValidationIssue> all = new ArrayList<>();
        all.addAll(errors);
        all.addAll(warnings);
        all.addAll(info);
        return Collections.unmodifiableList(all);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{\n");
        sb.append("  valid=").append(isValid()).append("\n");
        sb.append("  errors=").append(errors.size()).append("\n");
        sb.append("  warnings=").append(warnings.size()).append("\n");
        sb.append("  info=").append(info.size()).append("\n");
        
        if (!errors.isEmpty()) {
            sb.append("\n  Errors:\n");
            errors.forEach(e -> sb.append("    ").append(e).append("\n"));
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n  Warnings:\n");
            warnings.forEach(w -> sb.append("    ").append(w).append("\n"));
        }
        
        if (!info.isEmpty()) {
            sb.append("\n  Info:\n");
            info.forEach(i -> sb.append("    ").append(i).append("\n"));
        }
        
        sb.append("}");
        return sb.toString();
    }
}

