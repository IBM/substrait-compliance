package io.substrait.demo.runner;

import io.substrait.compliance.*;
import io.substrait.compliance.loader.*;
import io.substrait.demo.engines.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * TPC-DS demo runner that executes compliance tests for multiple mock engines
 * using the 99 TPC-DS queries and generates reports for dashboard visualization.
 */
public class TPCDSDemoRunner {
    
    private static final String TEST_SUITE_PATH = "../test-suites/tpcds/metadata.yaml";
    private static final String OUTPUT_DIR = "output";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Substrait Compliance Framework - TPC-DS Demo (99 Queries)");
        System.out.println("=".repeat(80));
        System.out.println("Note: all engine results below are from deterministic simulated demo engines.");
        System.out.println();
        
        try {
            // Create output directory
            File outputDir = new File(OUTPUT_DIR);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Initialize engines
            List<ComplianceEngine> engines = new ArrayList<>(Arrays.asList(
                new MockDBEngine(),
                new FastDBEngine(),
                new CloudDBEngine(),
                new DuckDBEngine(),
                new PostgreSQLEngine()
            ));
            
            // Load test suite
            System.out.println("📦 Loading TPC-DS test suite...");
            YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
            Path suitePath = Paths.get(TEST_SUITE_PATH);
            
            if (!suitePath.toFile().exists()) {
                System.err.println("❌ Test suite not found: " + TEST_SUITE_PATH);
                System.err.println("   Please ensure you're running from the demo directory");
                System.exit(1);
            }
            
            TestSuite testSuite = loader.load(suitePath);
            System.out.println("✅ Loaded test suite: " + testSuite.getName());
            System.out.println("   Total test cases: " + testSuite.getTestCases().size());
            System.out.println();
            
            // Run tests for each engine
            List<Map<String, Object>> allReports = new ArrayList<>();
            
            for (ComplianceEngine engine : engines) {
                System.out.println("-".repeat(80));
                runEngineTests(engine, testSuite, allReports);
                System.out.println();
            }
            
            // Generate summary
            System.out.println("=".repeat(80));
            System.out.println("📊 TPC-DS Demo Summary");
            System.out.println("=".repeat(80));
            System.out.println("Note: summary table reflects deterministic simulated demo engines, not upstream engine benchmarks.");
            printSummary(allReports);
            
            // Generate leaderboard
            System.out.println();
            System.out.println("📈 Generating TPC-DS leaderboard...");
            generateLeaderboard(allReports);
            
            System.out.println();
            System.out.println("✅ TPC-DS demo completed successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. View reports in: " + OUTPUT_DIR + "/");
            System.out.println("  2. Open dashboard: demo/dashboard/index.html");
            System.out.println("  3. Or run: python -m http.server 8080 (from dashboard dir)");
            System.out.println();
            System.out.println("📊 Compare with TPC-H:");
            System.out.println("  cd demo && ./runner/run-simple-demo.sh");
            
        } catch (Exception e) {
            System.err.println("❌ TPC-DS demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void runEngineTests(ComplianceEngine engine, TestSuite testSuite,
                                      List<Map<String, Object>> allReports)
            throws Exception {
        
        EngineInfo info = engine.getEngineInfo();
        System.out.println("🔧 Testing: " + info.getEngineName() + " v" + info.getEngineVersion());
        
        // Run compliance tests
        ComplianceRunner runner = new ComplianceRunner(engine);
        ComplianceReport report = runner.runTestSuite(testSuite);
        
        // Print results
        System.out.println("   Total Tests: " + report.getTotalTests());
        System.out.println("   ✅ Passed: " + report.getPassedCount());
        System.out.println("   ❌ Failed: " + report.getFailedCount());
        System.out.println("   ⏭️  Skipped: " + report.getSkippedCount());
        System.out.println("   📊 Pass Rate: " + String.format("%.1f%%", report.getComplianceScore()));
        
        // Convert to JSON-friendly format
        Map<String, Object> reportData = convertReportToMap(report);
        allReports.add(reportData);
        
        // Save individual report with -tpcds suffix
        String filename = info.getEngineName().toLowerCase() + "-tpcds-report.json";
        File reportFile = new File(OUTPUT_DIR, filename);
        JSON_MAPPER.writeValue(reportFile, reportData);
        System.out.println("   💾 Report saved: " + reportFile.getPath());
    }
    
    private static Map<String, Object> convertReportToMap(ComplianceReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        EngineInfo info = report.getEngineInfo();
        
        data.put("engineName", info.getEngineName());
        data.put("engineVersion", info.getEngineVersion());
        data.put("substraitVersion", info.getSubstraitVersion());
        data.put("timestamp", new Date(report.getTimestamp()).toInstant().toString());
        data.put("testSuiteName", report.getTestSuiteName());
        data.put("totalTests", report.getTotalTests());
        data.put("passed", report.getPassedCount());
        data.put("failed", report.getFailedCount());
        data.put("skipped", report.getSkippedCount());
        data.put("passRate", Math.round(report.getComplianceScore() * 10.0) / 10.0);
        
        // Add test results
        List<Map<String, Object>> testResults = new ArrayList<>();
        for (TestResult result : report.getTestResults()) {
            Map<String, Object> testData = new LinkedHashMap<>();
            testData.put("testId", result.getTestId());
            testData.put("status", result.getStatus().toString());
            testData.put("executionTimeMs", result.getDurationMs());
            if (result.getMessage() != null) {
                testData.put("errorMessage", result.getMessage());
            }
            testResults.add(testData);
        }
        data.put("testResults", testResults);
        
        return data;
    }
    
    private static void printSummary(List<Map<String, Object>> reports) {
        // Sort by pass rate
        reports.sort((a, b) -> 
            Double.compare((Double)b.get("passRate"), (Double)a.get("passRate"))
        );
        
        System.out.println();
        System.out.printf("%-15s %-10s %-12s %-15s%n", 
            "Engine", "Version", "Pass Rate", "Status");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < reports.size(); i++) {
            Map<String, Object> report = reports.get(i);
            double passRate = (Double) report.get("passRate");
            // Only award medals when there is something to rank.
            String rank = passRate == 0.0 ? "  " :
                          i == 0          ? "🥇" :
                          i == 1          ? "🥈" : "🥉";
            String status = getStatusLabel(passRate);

            System.out.printf("%s %-12s %-10s %6.1f%%  %s%n",
                rank,
                report.get("engineName"),
                report.get("engineVersion"),
                passRate,
                status
            );
        }
    }

    /**
     * Compliance tier thresholds:
     *   VERIFIED  ≥ 90% — engine produces correct output for nearly all queries
     *   EDGE      ≥ 70% — most common queries pass; complex plans have gaps
     *   BASIC     ≥ 50% — core operators work; advanced plans rejected or wrong
     *   NONE       < 50% — insufficient coverage to be useful
     */
    private static String getStatusLabel(double passRate) {
        if (passRate >= 90) return "🟢 VERIFIED";
        if (passRate >= 70) return "🔵 EDGE";
        if (passRate >= 50) return "🟡 BASIC";
        if (passRate  > 0)  return "🔴 NONE";
        return "⚫ NO RESULTS";
    }
    
    private static void generateLeaderboard(List<Map<String, Object>> reports) throws Exception {
        // Sort by pass rate
        reports.sort((a, b) -> 
            Double.compare((Double)b.get("passRate"), (Double)a.get("passRate"))
        );
        
        Map<String, Object> leaderboard = new LinkedHashMap<>();
        leaderboard.put("lastUpdated", new Date().toInstant().toString());
        leaderboard.put("totalEngines", reports.size());
        leaderboard.put("testSuite", "TPC-DS");
        leaderboard.put("queryCount", 99);
        
        double avgPassRate = reports.stream()
            .mapToDouble(r -> (Double)r.get("passRate"))
            .average()
            .orElse(0.0);
        leaderboard.put("averagePassRate", Math.round(avgPassRate * 10.0) / 10.0);
        
        List<Map<String, Object>> engines = new ArrayList<>();
        for (int i = 0; i < reports.size(); i++) {
            Map<String, Object> report = reports.get(i);
            Map<String, Object> engineData = new LinkedHashMap<>();
            engineData.put("rank", i + 1);
            // "name" is the dashboard-facing key; "engineName" kept for back-compat
            engineData.put("name", report.get("engineName"));
            engineData.put("engineName", report.get("engineName"));
            engineData.put("version", report.get("engineVersion"));
            engineData.put("engineVersion", report.get("engineVersion"));
            engineData.put("passRate", report.get("passRate"));
            engineData.put("passed", report.get("passed"));
            engineData.put("failed", report.get("failed"));
            engineData.put("skipped", report.get("skipped"));
            engineData.put("totalTests", report.get("totalTests"));
            engineData.put("timestamp", report.get("timestamp"));
            engines.add(engineData);
        }
        leaderboard.put("engines", engines);
        
        // Save leaderboard with -tpcds suffix
        File leaderboardFile = new File(OUTPUT_DIR, "leaderboard-tpcds.json");
        JSON_MAPPER.writeValue(leaderboardFile, leaderboard);
        System.out.println("   💾 Leaderboard saved: " + leaderboardFile.getPath());
        
        // Copy to dashboard data directory
        File dashboardData = new File("dashboard/data/leaderboard-tpcds.json");
        dashboardData.getParentFile().mkdirs();
        JSON_MAPPER.writeValue(dashboardData, leaderboard);
        System.out.println("   💾 Dashboard data updated: " + dashboardData.getPath());
    }
}
