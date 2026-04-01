package io.substrait.compliance;

/**
 * Result of executing a Substrait plan in a compliance test.
 */
public class ComplianceResult {
    
    private final boolean success;
    private final TableData outputData;
    private final long executionTimeMs;
    private final String errorMessage;
    private final Throwable exception;
    
    private ComplianceResult(boolean success, TableData outputData, 
                            long executionTimeMs, String errorMessage, 
                            Throwable exception) {
        this.success = success;
        this.outputData = outputData;
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.exception = exception;
    }
    
    public static ComplianceResult success(TableData outputData, long executionTimeMs) {
        return new ComplianceResult(true, outputData, executionTimeMs, null, null);
    }
    
    public static ComplianceResult failure(String errorMessage, Throwable exception) {
        return new ComplianceResult(false, null, 0, errorMessage, exception);
    }
    
    public static ComplianceResult failure(String errorMessage, Throwable exception, 
                                          long executionTimeMs) {
        return new ComplianceResult(false, null, executionTimeMs, errorMessage, exception);
    }
    
    public boolean isSuccess() { return success; }
    public TableData getOutputData() { return outputData; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getErrorMessage() { return errorMessage; }
    public Throwable getException() { return exception; }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("ComplianceResult{success=true, rows=%d, time=%dms}",
                outputData != null ? outputData.getRowCount() : 0, executionTimeMs);
        } else {
            return String.format("ComplianceResult{success=false, error='%s', time=%dms}",
                errorMessage, executionTimeMs);
        }
    }
}
