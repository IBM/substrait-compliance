package io.substrait.compliance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a Substrait plan.
 */
public class PlanValidationResult {
    
    private final boolean supported;
    private final List<String> issues;
    private final List<String> warnings;
    
    private PlanValidationResult(boolean supported, List<String> issues, List<String> warnings) {
        this.supported = supported;
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }
    
    public static PlanValidationResult supported() {
        return new PlanValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }
    
    public static PlanValidationResult unsupported(List<String> issues) {
        return new PlanValidationResult(false, issues, Collections.emptyList());
    }
    
    public static PlanValidationResult supportedWithWarnings(List<String> warnings) {
        return new PlanValidationResult(true, Collections.emptyList(), warnings);
    }
    
    public boolean isSupported() { return supported; }
    public List<String> getIssues() { return issues; }
    public List<String> getWarnings() { return warnings; }
    
    @Override
    public String toString() {
        if (supported) {
            return warnings.isEmpty() ? "Supported" : "Supported with warnings: " + warnings;
        } else {
            return "Unsupported: " + issues;
        }
    }
}
