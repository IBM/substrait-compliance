import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Simplified demo runner that generates mock compliance reports
 * without requiring full SDK compilation.
 */
public class SimpleDemoRunner {
    
    private static final String OUTPUT_DIR = "output";
    private static final String DASHBOARD_DATA_DIR = "dashboard/data";
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Substrait Compliance Framework - Demo");
        System.out.println("=".repeat(80));
        System.out.println();
        
        try {
            // Create output directories
            new File(OUTPUT_DIR).mkdirs();
            new File(DASHBOARD_DATA_DIR).mkdirs();
            
            // Generate mock reports for five engines
            List<Map<String, Object>> reports = new ArrayList<>();
            
            System.out.println("-".repeat(80));
            reports.add(generateEngineReport("MockDB", "1.0.0", 0.854)); // 85.4% pass rate
            System.out.println();
            
            System.out.println("-".repeat(80));
            reports.add(generateEngineReport("FastDB", "2.5.0", 0.955)); // 95.5% pass rate
            System.out.println();
            
            System.out.println("-".repeat(80));
            reports.add(generateEngineReport("CloudDB", "3.1.0", 0.773)); // 77.3% pass rate
            System.out.println();
            
            System.out.println("-".repeat(80));
            reports.add(generateEngineReport("DuckDB", "0.10.0", 0.923)); // 92.3% pass rate
            System.out.println();
            
            System.out.println("-".repeat(80));
            reports.add(generateEngineReport("PostgreSQL", "16.0", 0.882)); // 88.2% pass rate
            System.out.println();
            
            // Generate summary
            System.out.println("=".repeat(80));
            System.out.println("📊 Demo Summary");
            System.out.println("=".repeat(80));
            printSummary(reports);
            
            // Generate leaderboard
            System.out.println();
            System.out.println("📈 Generating leaderboard...");
            generateLeaderboard(reports);
            
            System.out.println();
            System.out.println("✅ Demo completed successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. View reports in: " + OUTPUT_DIR + "/");
            System.out.println("  2. Open dashboard: demo/dashboard/index.html");
            System.out.println("  3. Or run: python -m http.server 8080 (from dashboard dir)");
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static Map<String, Object> generateEngineReport(String engineName, 
                                                            String version, 
                                                            double passRate) throws IOException {
        System.out.println("🔧 Testing: " + engineName + " v" + version);
        
        int totalTests = 22; // TPC-H has 22 queries
        int passed = (int) Math.round(totalTests * passRate);
        int failed = totalTests - passed;
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("engineName", engineName);
        report.put("engineVersion", version);
        report.put("substraitVersion", "0.20.0");
        report.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        report.put("testSuiteName", "TPC-H");
        report.put("totalTests", totalTests);
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("skipped", 0);
        report.put("passRate", Math.round(passRate * 1000.0) / 10.0);
        
        // Generate test results with complexity
        List<Map<String, Object>> testResults = new ArrayList<>();
        for (int i = 1; i <= totalTests; i++) {
            Map<String, Object> testResult = new LinkedHashMap<>();
            String queryId = String.format("tpch-q%02d", i);
            testResult.put("testId", queryId);
            testResult.put("complexity", getQueryComplexity(i));
            
            // Determine if this test passed based on pass rate
            boolean testPassed = i <= passed;
            testResult.put("status", testPassed ? "PASSED" : "FAILED");
            testResult.put("executionTimeMs", 50 + (int)(Math.random() * 300));
            
            if (!testPassed) {
                testResult.put("errorMessage", "Query execution failed: simulated failure");
            }
            
            testResults.add(testResult);
        }
        report.put("testResults", testResults);
        
        // Print results
        System.out.println("   Total Tests: " + totalTests);
        System.out.println("   ✅ Passed: " + passed);
        System.out.println("   ❌ Failed: " + failed);
        System.out.println("   ⏭️  Skipped: 0");
        System.out.println("   📊 Pass Rate: " + String.format("%.1f%%", passRate * 100));
        
        // Save report
        String filename = engineName.toLowerCase() + "-report.json";
        saveJson(new File(OUTPUT_DIR, filename), report);
        System.out.println("   💾 Report saved: " + OUTPUT_DIR + "/" + filename);
        
        return report;
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
    
    private static void generateLeaderboard(List<Map<String, Object>> reports) throws IOException {
        // Sort by pass rate
        reports.sort((a, b) -> 
            Double.compare((Double)b.get("passRate"), (Double)a.get("passRate"))
        );
        
        Map<String, Object> leaderboard = new LinkedHashMap<>();
        leaderboard.put("lastUpdated", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
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
        saveJson(new File(OUTPUT_DIR, "leaderboard.json"), leaderboard);
        System.out.println("   💾 Leaderboard saved: " + OUTPUT_DIR + "/leaderboard.json");
        
        // Copy to dashboard data directory
        saveJson(new File(DASHBOARD_DATA_DIR, "leaderboard.json"), leaderboard);
        System.out.println("   💾 Dashboard data updated: " + DASHBOARD_DATA_DIR + "/leaderboard.json");
    }
    
    private static void saveJson(File file, Map<String, Object> data) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(toJson(data, 0));
        }
    }
    
    private static String toJson(Object obj, int indent) {
        String indentStr = "  ".repeat(indent);
        String nextIndentStr = "  ".repeat(indent + 1);
        
        if (obj == null) {
            return "null";
        } else if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map.isEmpty()) return "{}";
            
            StringBuilder sb = new StringBuilder("{\n");
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                sb.append(nextIndentStr).append("\"").append(entry.getKey()).append("\": ");
                sb.append(toJson(entry.getValue(), indent + 1));
                if (it.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append(indentStr).append("}");
            return sb.toString();
        } else if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) obj;
            if (list.isEmpty()) return "[]";
            
            StringBuilder sb = new StringBuilder("[\n");
            Iterator<Object> it = list.iterator();
            while (it.hasNext()) {
                sb.append(nextIndentStr).append(toJson(it.next(), indent + 1));
                if (it.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append(indentStr).append("]");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        } else {
            return obj.toString();
        }
    }
    
    private static String getQueryComplexity(int queryNum) {
        // TPC-H query complexity classification
        switch (queryNum) {
            case 1: case 6: case 14:
                return "SIMPLE";
            case 3: case 4: case 10: case 12: case 13: case 16: case 19:
                return "MEDIUM";
            case 5: case 7: case 9: case 11: case 15: case 17: case 18: case 22:
                return "COMPLEX";
            case 2: case 8: case 20: case 21:
                return "VERY_COMPLEX";
            default:
                return "MEDIUM";
        }
    }
}

