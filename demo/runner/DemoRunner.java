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
 * Main demo runner that executes compliance tests for multiple mock engines
 * and generates reports for dashboard visualization.
 */
public class DemoRunner {
    
    private static final String TEST_SUITE_PATH = "../test-suites/tpch/metadata.yaml";
    private static final String OUTPUT_DIR = "output";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Substrait Compliance Framework - Demo");
        System.out.println("=".repeat(80));
        System.out.println();
        
        try {
            // Create output directory
            File outputDir = new File(OUTPUT_DIR);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Initialize engines
            List<ComplianceEngine> engines = Arrays.asList(
                new MockDBEngine(),
                new FastDBEngine(),
                new CloudDBEngine(),
                new DuckDBEngine(),
                new PostgreSQLEngine()
            );
            
            // Load test suite
            System.out.println("📦 Loading TPC-H test suite...");
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
            System.out.println("📊 Demo Summary");
            System.out.println("=".repeat(80));
            printSummary(allReports);
            
            // Generate leaderboard
            System.out.println();
            System.out.println("📈 Generating leaderboard...");
            generateLeaderboard(allReports);
            
            System.out.println();
            System.out.println("✅ Demo completed successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. View reports in: " + OUTPUT_DIR + "/");
            System.out.println("  2. Open dashboard: demo/dashboard/index.html");
            System.out.println("  3. Or run: python -m http.server 8000 (from dashboard dir)");
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
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
        
        // Save individual report
        String filename = info.getEngineName().toLowerCase() + "-report.json";
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
            String rank = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
            double passRate = (Double) report.get("passRate");
            String status = getStatusEmoji(passRate);
            
            System.out.printf("%s %-12s %-10s %-12.1f%% %s%n",
                rank,
                report.get("engineName"),
                report.get("engineVersion"),
                passRate,
                status
            );
        }
    }
    
    private static String getStatusEmoji(double passRate) {
        if (passRate >= 95) return "🟢 Excellent";
        if (passRate >= 85) return "🟡 Good";
        if (passRate >= 70) return "🟠 Fair";
        return "🔴 Needs Work";
    }
    
    private static void generateLeaderboard(List<Map<String, Object>> reports) throws Exception {
        // Sort by pass rate
        reports.sort((a, b) -> 
            Double.compare((Double)b.get("passRate"), (Double)a.get("passRate"))
        );
        
        Map<String, Object> leaderboard = new LinkedHashMap<>();
        leaderboard.put("lastUpdated", new Date().toInstant().toString());
        leaderboard.put("totalEngines", reports.size());
        
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
            engineData.put("engineName", report.get("engineName"));
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
        
        // Save leaderboard
        File leaderboardFile = new File(OUTPUT_DIR, "leaderboard.json");
        JSON_MAPPER.writeValue(leaderboardFile, leaderboard);
        System.out.println("   💾 Leaderboard saved: " + leaderboardFile.getPath());
        
        // Copy to dashboard data directory
        File dashboardData = new File("dashboard/data/leaderboard.json");
        dashboardData.getParentFile().mkdirs();
        JSON_MAPPER.writeValue(dashboardData, leaderboard);
        System.out.println("   💾 Dashboard data updated: " + dashboardData.getPath());
        
        generateSharedSummary(reports);
    }
    
    private static void generateSharedSummary(List<Map<String, Object>> tpchReports) throws Exception {
        File functionSummaryFile = new File(OUTPUT_DIR, "function_tests_summary.json");
        if (!functionSummaryFile.exists()) {
            System.out.println("   ℹ️  Shared dashboard summary skipped - function test summary not found yet");
            return;
        }
        
        Map<String, Object> functionSummary = JSON_MAPPER.readValue(functionSummaryFile, Map.class);
        List<Map<String, Object>> functionEngines =
            (List<Map<String, Object>>) functionSummary.getOrDefault("engines", Collections.emptyList());
        
        Map<String, Map<String, Object>> functionByEngine = new HashMap<>();
        for (Map<String, Object> engine : functionEngines) {
            Object engineName = engine.get("engine");
            if (engineName != null) {
                functionByEngine.put(engineName.toString(), engine);
            }
        }
        
        List<Map<String, Object>> engines = new ArrayList<>();
        for (Map<String, Object> tpchReport : tpchReports) {
            String engineName = tpchReport.get("engineName").toString();
            Map<String, Object> functionEngine = functionByEngine.get(engineName);
            
            Map<String, Object> engineSummary = new LinkedHashMap<>();
            engineSummary.put("engineName", engineName);
            engineSummary.put("engineVersion", tpchReport.get("engineVersion"));
            
            Map<String, Object> tpch = new LinkedHashMap<>();
            tpch.put("passRate", tpchReport.get("passRate"));
            tpch.put("totalTests", tpchReport.get("totalTests"));
            tpch.put("passed", tpchReport.get("passed"));
            tpch.put("failed", tpchReport.get("failed"));
            tpch.put("skipped", tpchReport.get("skipped"));
            engineSummary.put("tpch", tpch);
            
            Map<String, Object> functions = new LinkedHashMap<>();
            if (functionEngine != null) {
                functions.put("passRate", functionEngine.get("overallPassRate"));
                functions.put("totalTests", functionEngine.get("totalTests"));
                functions.put("passed", functionEngine.get("totalPassed"));
                Number totalTests = (Number) functionEngine.getOrDefault("totalTests", 0);
                Number totalPassed = (Number) functionEngine.getOrDefault("totalPassed", 0);
                functions.put("failed", totalTests.intValue() - totalPassed.intValue());
            } else {
                functions.put("passRate", 0.0);
                functions.put("totalTests", 0);
                functions.put("passed", 0);
                functions.put("failed", 0);
            }
            engineSummary.put("functions", functions);
            
            engines.add(engineSummary);
        }
        
        Map<String, Object> sharedSummary = new LinkedHashMap<>();
        sharedSummary.put("lastUpdated", new Date().toInstant().toString());
        sharedSummary.put("totalEngines", engines.size());
        sharedSummary.put("engines", engines);
        
        File sharedSummaryFile = new File("dashboard/data/summary.json");
        sharedSummaryFile.getParentFile().mkdirs();
        JSON_MAPPER.writeValue(sharedSummaryFile, sharedSummary);
        System.out.println("   💾 Shared dashboard summary updated: " + sharedSummaryFile.getPath());
    }
}

// Made with Bob
