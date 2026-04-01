package io.substrait.compliance;

import java.util.ArrayList;
import java.util.List;

/**
 * Report of compliance test execution.
 */
public class ComplianceReport {
    
    private final EngineInfo engineInfo;
    private final String testSuiteName;
    private final List<TestResult> testResults;
    private final long timestamp;
    
    public ComplianceReport(EngineInfo engineInfo, String testSuiteName) {
        this.engineInfo = engineInfo;
        this.testSuiteName = testSuiteName;
        this.testResults = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public void addTestResult(TestResult result) {
        testResults.add(result);
    }
    
    public EngineInfo getEngineInfo() { return engineInfo; }
    public String getTestSuiteName() { return testSuiteName; }
    public List<TestResult> getTestResults() { return new ArrayList<>(testResults); }
    public long getTimestamp() { return timestamp; }
    
    public int getTotalTests() { return testResults.size(); }
    
    public int getPassedCount() {
        return (int) testResults.stream().filter(TestResult::isPassed).count();
    }
    
    public int getFailedCount() {
        return (int) testResults.stream().filter(TestResult::isFailed).count();
    }
    
    public int getSkippedCount() {
        return (int) testResults.stream().filter(TestResult::isSkipped).count();
    }
    
    public double getComplianceScore() {
        if (getTotalTests() == 0) return 0.0;
        return (getPassedCount() * 100.0) / getTotalTests();
    }
    
    @Override
    public String toString() {
        return String.format(
            "ComplianceReport{engine=%s, suite=%s, total=%d, passed=%d, failed=%d, skipped=%d, score=%.1f%%}",
            engineInfo, testSuiteName, getTotalTests(), getPassedCount(), 
            getFailedCount(), getSkippedCount(), getComplianceScore()
        );
    }
}
