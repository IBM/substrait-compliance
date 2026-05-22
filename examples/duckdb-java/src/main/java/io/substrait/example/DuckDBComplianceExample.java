package io.substrait.example;

import io.substrait.compliance.*;
import io.substrait.compliance.loader.YamlTestSuiteLoader;
import java.nio.file.Paths;

/**
 * Example of running DuckDB compliance tests.
 */
public class DuckDBComplianceExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("DuckDB Substrait Compliance Testing");
        System.out.println("====================================");
        System.out.println();
        
        // 1. Create DuckDB engine
        DuckDBComplianceEngine engine = new DuckDBComplianceEngine();
        
        // 2. Print engine info
        EngineInfo info = engine.getEngineInfo();
        System.out.println("Engine: " + info);
        System.out.println();
        
        // 3. Load test suite
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        String suitePath = "../../test-suites/functions/arithmetic/add.test";
        TestSuite suite = loader.load(Paths.get(suitePath));
        
        System.out.println("Loaded test suite: " + suite.getName());
        System.out.println("Test cases: " + suite.getTestCases().size());
        System.out.println();
        
        // 4. Run compliance tests
        ComplianceRunner runner = new ComplianceRunner(engine);
        ComplianceReport report = runner.runTestSuite(suite);
        
        // 5. Print results
        System.out.println("Results:");
        System.out.println("--------");
        System.out.println("Total:   " + report.getTotalTests());
        System.out.println("Passed:  " + report.getPassedCount());
        System.out.println("Failed:  " + report.getFailedCount());
        System.out.println("Skipped: " + report.getSkippedCount());
        System.out.println("Compliance Score: " + String.format("%.1f%%", report.getComplianceScore()));
        System.out.println();
        
        // 6. Show failed tests
        if (report.getFailedCount() > 0) {
            System.out.println("Failed Tests:");
            for (TestResult result : report.getTestResults()) {
                if (result.isFailed()) {
                    System.out.println("  " + result.getTestId() + ": " +
                                     result.getStatus() + " - " +
                                     result.getMessage());
                }
            }
        }
        
        // 7. Cleanup
        engine.close();
    }
}
