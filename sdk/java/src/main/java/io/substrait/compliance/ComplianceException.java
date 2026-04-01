package io.substrait.compliance;

/**
 * Exception thrown during compliance testing.
 */
public class ComplianceException extends Exception {
    
    public ComplianceException(String message) {
        super(message);
    }
    
    public ComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ComplianceException(Throwable cause) {
        super(cause);
    }
}
