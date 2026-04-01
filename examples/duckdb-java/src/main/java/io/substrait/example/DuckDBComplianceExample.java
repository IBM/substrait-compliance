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
        EngineInfo info = engine.getInfo();
        System.out.println("Engine: " + info);
        System.out.println();
        
        // 3. Load TPC-H test suite
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        String suitePath = "../../test-suites/tpch/metadata.yaml";
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
        System.out.println("Total:   " + report.getTotalCount());
        System.out.println("Passed:  " + report.getPassedCount());
        System.out.println("Failed:  " + report.getFailedCount());
        System.out.println("Errors:  " + report.getErrorCount());
        System.out.println("Pass Rate: " + String.format("%.1f%%", report.getPassRate()));
        System.out.println();
        
        // 6. Show failed tests
        if (report.getFailedCount() > 0 || report.getErrorCount() > 0) {
            System.out.println("Failed/Error Tests:");
            for (ComplianceResult result : report.getResults()) {
                if (result.getStatus() != TestStatus.PASSED) {
                    System.out.println("  " + result.getTestId() + ": " + 
                                     result.getStatus() + " - " + 
                                     result.getErrorMessage());
                }
            }
        }
        
        // 7. Cleanup
        engine.close();
    }
}
