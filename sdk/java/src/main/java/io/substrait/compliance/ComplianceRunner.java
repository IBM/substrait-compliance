package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.*;

/**
 * Main test runner that executes compliance test suites against an engine.
 */
public class ComplianceRunner {
    
    private final ComplianceEngine engine;
    private final Map<String, TestSuite> testSuites;
    
    public ComplianceRunner(ComplianceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.testSuites = new HashMap<>();
    }
    
    /**
     * Register a test suite.
     */
    public void registerTestSuite(TestSuite suite) {
        testSuites.put(suite.getName(), suite);
    }
    
    /**
     * Run a specific test suite by name.
     */
    public ComplianceReport runTestSuite(String suiteName) throws ComplianceException {
        TestSuite suite = testSuites.get(suiteName);
        if (suite == null) {
            throw new ComplianceException("Test suite not found: " + suiteName);
        }
        
        return runTestSuite(suite);
    }
    
    /**
     * Run a test suite.
     */
    public ComplianceReport runTestSuite(TestSuite suite) throws ComplianceException {
        engine.initialize();
        
        try {
            ComplianceReport report = new ComplianceReport(
                engine.getEngineInfo(),
                suite.getName()
            );
            
            for (TestCase testCase : suite.getTestCases()) {
                TestResult result = runTestCase(testCase);
                report.addTestResult(result);
            }
            
            return report;
        } finally {
            engine.cleanup();
        }
    }
    
    /**
     * Run a single test case.
     */
    private TestResult runTestCase(TestCase testCase) {
        try {
            // Validate plan first
            PlanValidationResult validation = engine.validatePlan(testCase.getPlan());
            if (!validation.isSupported()) {
                return TestResult.skipped(testCase.getId(), 
                    "Plan not supported: " + validation.getIssues());
            }
            
            // Execute plan
            long startTime = System.currentTimeMillis();
            ComplianceResult result = engine.executePlan(
                testCase.getPlan(),
                testCase.getInputData()
            );
            long duration = System.currentTimeMillis() - startTime;
            
            if (!result.isSuccess()) {
                return TestResult.failed(testCase.getId(), 
                    result.getErrorMessage(), duration);
            }
            
            // Compare results if expected output provided
            if (testCase.getExpectedOutput() != null) {
                boolean matches = compareResults(
                    testCase.getExpectedOutput(),
                    result.getOutputData()
                );
                
                if (matches) {
                    return TestResult.passed(testCase.getId(), duration);
                } else {
                    return TestResult.failed(testCase.getId(), 
                        "Output mismatch", duration);
                }
            }
            
            return TestResult.passed(testCase.getId(), duration);
            
        } catch (Exception e) {
            return TestResult.failed(testCase.getId(), 
                "Exception: " + e.getMessage(), 0);
        }
    }
    
    /**
     * Compare expected and actual results.
     */
    private boolean compareResults(TableData expected, TableData actual) {
        if (expected.getRowCount() != actual.getRowCount()) {
            return false;
        }
        if (expected.getColumnCount() != actual.getColumnCount()) {
            return false;
        }
        
        // Simple comparison - can be enhanced
        return expected.equals(actual);
    }
}
