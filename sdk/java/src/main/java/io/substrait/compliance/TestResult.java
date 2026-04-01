package io.substrait.compliance;

/**
 * Result of running a single test case.
 */
public class TestResult {
    
    public enum Status { PASSED, FAILED, SKIPPED }
    
    private final String testId;
    private final Status status;
    private final String message;
    private final long durationMs;
    
    private TestResult(String testId, Status status, String message, long durationMs) {
        this.testId = testId;
        this.status = status;
        this.message = message;
        this.durationMs = durationMs;
    }
    
    public static TestResult passed(String testId, long durationMs) {
        return new TestResult(testId, Status.PASSED, null, durationMs);
    }
    
    public static TestResult failed(String testId, String message, long durationMs) {
        return new TestResult(testId, Status.FAILED, message, durationMs);
    }
    
    public static TestResult skipped(String testId, String reason) {
        return new TestResult(testId, Status.SKIPPED, reason, 0);
    }
    
    public String getTestId() { return testId; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public long getDurationMs() { return durationMs; }
    
    public boolean isPassed() { return status == Status.PASSED; }
    public boolean isFailed() { return status == Status.FAILED; }
    public boolean isSkipped() { return status == Status.SKIPPED; }
}
