package io.substrait.compliance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ComplianceReportTest {
    
    @Test
    void testReportCreation() {
        EngineInfo info = new EngineInfo("TestEngine", "1.0", "0.80.0");
        ComplianceReport report = new ComplianceReport(info, "test-suite");
        
        assertThat(report.getEngineInfo()).isEqualTo(info);
        assertThat(report.getTestSuiteName()).isEqualTo("test-suite");
        assertThat(report.getTotalTests()).isZero();
    }
    
    @Test
    void testAddingResults() {
        EngineInfo info = new EngineInfo("TestEngine", "1.0", "0.80.0");
        ComplianceReport report = new ComplianceReport(info, "test-suite");
        
        report.addTestResult(TestResult.passed("test1", 10));
        report.addTestResult(TestResult.failed("test2", "Error", 20));
        report.addTestResult(TestResult.skipped("test3", "Not supported"));
        
        assertThat(report.getTotalTests()).isEqualTo(3);
        assertThat(report.getPassedCount()).isEqualTo(1);
        assertThat(report.getFailedCount()).isEqualTo(1);
        assertThat(report.getSkippedCount()).isEqualTo(1);
    }
    
    @Test
    void testComplianceScore() {
        EngineInfo info = new EngineInfo("TestEngine", "1.0", "0.80.0");
        ComplianceReport report = new ComplianceReport(info, "test-suite");
        
        report.addTestResult(TestResult.passed("test1", 10));
        report.addTestResult(TestResult.passed("test2", 10));
        report.addTestResult(TestResult.failed("test3", "Error", 10));
        report.addTestResult(TestResult.failed("test4", "Error", 10));
        
        assertThat(report.getComplianceScore()).isEqualTo(50.0);
    }
}
