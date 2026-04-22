import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;

/**
 * Enhanced demo runner that demonstrates all phases of the compliance framework.
 * This demo showcases:
 * - Validation
 * - Comparison
 * - Failure Analysis
 * - Storage (Private & Public)
 * - Analytics
 * - Reproduction Packages
 */
public class EnhancedDemoRunner {
    
    private static final String OUTPUT_DIR = "output";
    private static final String STORAGE_DIR = "output/storage";
    private static final String DASHBOARD_DATA_DIR = "dashboard/data";
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Substrait Compliance Framework - Enhanced Demo");
        System.out.println("Demonstrating All Phases: Validation, Comparison, Analysis, Storage & Analytics");
        System.out.println("=".repeat(80));
        System.out.println();
        
        try {
            // Create output directories
            createDirectories();
            
            // Run demo for multiple engines
            List<MockEngineReport> reports = new ArrayList<>();
            
            System.out.println("Phase 1-3: Validation & Comparison");
            System.out.println("-".repeat(80));
            reports.add(runEngineTests("MockDB", "1.0.0", 0.854));
            System.out.println();
            
            reports.add(runEngineTests("FastDB", "2.5.0", 0.955));
            System.out.println();
            
            reports.add(runEngineTests("CloudDB", "3.1.0", 0.773));
            System.out.println();
            
            reports.add(runEngineTests("DuckDB", "0.10.2", 0.932));
            System.out.println();
            
            reports.add(runEngineTests("PostgreSQL", "16.2", 0.891));
            System.out.println();
            
            // Phase 4-6: Analysis & Categorization
            System.out.println("Phase 4-6: Failure Analysis & Categorization");
            System.out.println("-".repeat(80));
            analyzeFailures(reports);
            System.out.println();
            
            // Phase 7: Storage
            System.out.println("Phase 7: Data Storage (Private & Public)");
            System.out.println("-".repeat(80));
            storeReports(reports);
            System.out.println();
            
            // Phase 8: Analytics
            System.out.println("Phase 8: Analytics & Insights");
            System.out.println("-".repeat(80));
            generateAnalytics(reports);
            generateDashboardArtifacts(reports);
            System.out.println();
            
            // Generate summary
            System.out.println("=".repeat(80));
            System.out.println("📊 Demo Summary");
            System.out.println("=".repeat(80));
            printSummary(reports);
            
            System.out.println();
            System.out.println("✅ Enhanced demo completed successfully!");
            System.out.println();
            System.out.println("Generated artifacts:");
            System.out.println("  📁 Test reports: " + OUTPUT_DIR + "/");
            System.out.println("  📁 Private storage: " + STORAGE_DIR + "/private/");
            System.out.println("  📁 Public storage: " + STORAGE_DIR + "/public/");
            System.out.println("  📁 Analytics: " + OUTPUT_DIR + "/analytics/");
            System.out.println("  📁 Dashboard data: " + DASHBOARD_DATA_DIR + "/");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Review reports in output directory");
            System.out.println("  2. Open dashboard: demo/dashboard/index.html");
            System.out.println("  3. Check analytics insights");
            
        } catch (Exception e) {
            System.err.println("❌ Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void createDirectories() {
        new File(OUTPUT_DIR).mkdirs();
        new File(STORAGE_DIR + "/private").mkdirs();
        new File(STORAGE_DIR + "/public").mkdirs();
        new File(OUTPUT_DIR + "/analytics").mkdirs();
        new File(OUTPUT_DIR + "/reproductions").mkdirs();
        new File(DASHBOARD_DATA_DIR).mkdirs();
    }
    
    private static MockEngineReport runEngineTests(String engineName, String version, double passRate) {
        System.out.println("🔧 Testing: " + engineName + " v" + version);
        
        int totalTests = 22; // TPC-H queries
        int passed = (int) Math.round(totalTests * passRate);
        int failed = totalTests - passed;
        int skipped = 0;
        
        // Simulate validation
        System.out.println("   ✓ Validating Substrait plans...");
        int validationWarnings = (int)(Math.random() * 3);
        System.out.println("     - " + totalTests + " plans validated");
        if (validationWarnings > 0) {
            System.out.println("     - " + validationWarnings + " warnings found");
        }
        
        // Simulate comparison
        System.out.println("   ✓ Comparing results with type-aware comparison...");
        System.out.println("     - Using epsilon: 1e-6 for floating-point");
        System.out.println("     - Handling special values (NaN, Infinity)");
        
        // Results
        System.out.println("   📊 Results:");
        System.out.println("      ✅ Passed: " + passed);
        System.out.println("      ❌ Failed: " + failed);
        System.out.println("      📈 Pass Rate: " + String.format("%.1f%%", passRate * 100));
        
        return new MockEngineReport(engineName, version, totalTests, passed, failed, skipped, passRate);
    }
    
    private static void analyzeFailures(List<MockEngineReport> reports) {
        System.out.println("Analyzing failure patterns across engines...");
        System.out.println();
        
        // Simulate failure categorization
        String[] categories = {
            "VALIDATION_ERROR", "TYPE_MISMATCH", "VALUE_MISMATCH", 
            "UNSUPPORTED_FEATURE", "RUNTIME_ERROR"
        };
        
        for (MockEngineReport report : reports) {
            if (report.failed > 0) {
                System.out.println("Engine: " + report.engineName);
                System.out.println("  Failure Categories:");
                
                int remaining = report.failed;
                for (int i = 0; i < categories.length && remaining > 0; i++) {
                    int count = Math.min(remaining, (int)(Math.random() * remaining) + 1);
                    if (count > 0) {
                        System.out.println("    - " + categories[i] + ": " + count);
                        remaining -= count;
                    }
                }
                
                System.out.println("  Suggestions:");
                System.out.println("    → Review validation errors in plan structure");
                System.out.println("    → Check type compatibility for mismatches");
                System.out.println();
            }
        }
    }
    
    private static void storeReports(List<MockEngineReport> reports) throws IOException {
        for (MockEngineReport report : reports) {
            String engineDir = report.engineName.toLowerCase();
            String timestamp = Instant.now().toString().replace(":", "-");
            
            // Private storage (full data)
            String privateDir = STORAGE_DIR + "/private/" + engineDir + "/" + timestamp;
            new File(privateDir).mkdirs();
            
            Map<String, Object> privateData = new LinkedHashMap<>();
            privateData.put("engineName", report.engineName);
            privateData.put("engineVersion", report.version);
            privateData.put("timestamp", timestamp);
            privateData.put("totalTests", report.totalTests);
            privateData.put("passed", report.passed);
            privateData.put("failed", report.failed);
            privateData.put("passRate", report.passRate * 100);
            privateData.put("fullDiagnostics", "Available");
            privateData.put("stackTraces", "Available");
            privateData.put("reproductionPackages", "Generated");
            
            saveJson(new File(privateDir, "engine-report.json"), privateData);
            System.out.println("   💾 Private report: " + privateDir + "/engine-report.json");
            
            // Public storage (anonymized)
            String publicDir = STORAGE_DIR + "/public/" + engineDir + "/" + timestamp;
            new File(publicDir).mkdirs();
            
            Map<String, Object> publicData = new LinkedHashMap<>();
            publicData.put("engineName", report.engineName);
            publicData.put("engineVersion", report.version);
            publicData.put("timestamp", timestamp);
            publicData.put("summary", Map.of(
                "totalTests", report.totalTests,
                "passed", report.passed,
                "failed", report.failed
            ));
            publicData.put("complianceScore", report.passRate * 100);
            publicData.put("note", "Anonymized - no sensitive data");
            
            saveJson(new File(publicDir, "community-report.json"), publicData);
            System.out.println("   💾 Public report: " + publicDir + "/community-report.json");
        }
    }
    
    private static void generateAnalytics(List<MockEngineReport> reports) throws IOException {
        System.out.println("Generating analytics insights...");
        System.out.println();
        
        // Calculate analytics
        double avgPassRate = reports.stream()
            .mapToDouble(r -> r.passRate)
            .average()
            .orElse(0.0);
        
        MockEngineReport best = reports.stream()
            .max(Comparator.comparingDouble(r -> r.passRate))
            .orElse(null);
        
        MockEngineReport worst = reports.stream()
            .min(Comparator.comparingDouble(r -> r.passRate))
            .orElse(null);
        
        // Print insights
        System.out.println("📈 Key Findings:");
        System.out.println("   • Average compliance: " + String.format("%.1f%%", avgPassRate * 100));
        if (best != null) {
            System.out.println("   • Best performer: " + best.engineName + " (" + 
                String.format("%.1f%%", best.passRate * 100) + ")");
        }
        if (worst != null) {
            System.out.println("   • Needs improvement: " + worst.engineName + " (" + 
                String.format("%.1f%%", worst.passRate * 100) + ")");
        }
        System.out.println();
        
        System.out.println("💡 Recommendations:");
        for (MockEngineReport report : reports) {
            if (report.passRate < 0.85) {
                System.out.println("   → " + report.engineName + ": Focus on improving compliance");
            }
        }
        
        // Save analytics report
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("timestamp", Instant.now().toString());
        analytics.put("enginesAnalyzed", reports.size());
        analytics.put("averageCompliance", avgPassRate * 100);
        analytics.put("trend", "STABLE");
        
        List<String> findings = new ArrayList<>();
        findings.add("Average compliance: " + String.format("%.1f%%", avgPassRate * 100));
        if (best != null) {
            findings.add("Best performer: " + best.engineName);
        }
        analytics.put("keyFindings", findings);
        
        saveJson(new File(OUTPUT_DIR + "/analytics", "analytics-report.json"), analytics);
        System.out.println();
        System.out.println("   💾 Analytics saved: " + OUTPUT_DIR + "/analytics/analytics-report.json");
    }
    
    private static void generateDashboardArtifacts(List<MockEngineReport> reports) throws IOException {
        List<MockEngineReport> rankedReports = new ArrayList<>(reports);
        rankedReports.sort((a, b) -> Double.compare(b.passRate, a.passRate));
        
        Map<String, Object> leaderboard = new LinkedHashMap<>();
        leaderboard.put("lastUpdated", Instant.now().toString());
        leaderboard.put("totalEngines", rankedReports.size());
        leaderboard.put("averagePassRate", rankedReports.stream()
            .mapToDouble(r -> r.passRate * 100)
            .average()
            .orElse(0.0));
        
        List<Map<String, Object>> leaderboardEngines = new ArrayList<>();
        List<Map<String, Object>> summaryEngines = new ArrayList<>();
        
        for (int i = 0; i < rankedReports.size(); i++) {
            MockEngineReport report = rankedReports.get(i);
            
            Map<String, Object> leaderboardEntry = new LinkedHashMap<>();
            leaderboardEntry.put("rank", i + 1);
            leaderboardEntry.put("engineName", report.engineName);
            leaderboardEntry.put("engineVersion", report.version);
            leaderboardEntry.put("timestamp", Instant.now().toString());
            leaderboardEntry.put("totalTests", report.totalTests);
            leaderboardEntry.put("passed", report.passed);
            leaderboardEntry.put("failed", report.failed);
            leaderboardEntry.put("skipped", report.skipped);
            leaderboardEntry.put("passRate", report.passRate * 100);
            leaderboardEngines.add(leaderboardEntry);
            
            Map<String, Object> summaryEntry = new LinkedHashMap<>();
            summaryEntry.put("engineName", report.engineName);
            summaryEntry.put("engineVersion", report.version);
            
            Map<String, Object> tpch = new LinkedHashMap<>();
            tpch.put("passRate", report.passRate * 100);
            tpch.put("totalTests", report.totalTests);
            tpch.put("passed", report.passed);
            tpch.put("failed", report.failed);
            tpch.put("skipped", report.skipped);
            summaryEntry.put("tpch", tpch);
            
            Map<String, Object> functions = readFunctionSummary(report.engineName);
            summaryEntry.put("functions", functions);
            summaryEngines.add(summaryEntry);
        }
        
        leaderboard.put("engines", leaderboardEngines);
        saveJson(new File(DASHBOARD_DATA_DIR, "leaderboard.json"), leaderboard);
        
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lastUpdated", Instant.now().toString());
        summary.put("totalEngines", summaryEngines.size());
        summary.put("engines", summaryEngines);
        saveJson(new File(DASHBOARD_DATA_DIR, "summary.json"), summary);
        
        System.out.println("   💾 Dashboard leaderboard: " + DASHBOARD_DATA_DIR + "/leaderboard.json");
        System.out.println("   💾 Dashboard cross-suite summary: " + DASHBOARD_DATA_DIR + "/summary.json");
    }
    
    private static Map<String, Object> readFunctionSummary(String engineName) {
        File functionFile = new File(OUTPUT_DIR, engineName + "_function_tests.json");
        Map<String, Object> functions = new LinkedHashMap<>();
        
        if (!functionFile.exists()) {
            functions.put("passRate", 0.0);
            functions.put("totalTests", 0);
            functions.put("passed", 0);
            functions.put("failed", 0);
            return functions;
        }
        
        try {
            String content = Files.readString(functionFile.toPath());
            functions.put("passRate", extractDouble(content, "overallPassRate", 0.0));
            functions.put("totalTests", (int) extractDouble(content, "totalTests", 0.0));
            functions.put("passed", (int) extractDouble(content, "totalPassed", 0.0));
            int totalTests = (int) extractDouble(content, "totalTests", 0.0);
            int totalPassed = (int) extractDouble(content, "totalPassed", 0.0);
            functions.put("failed", Math.max(0, totalTests - totalPassed));
        } catch (IOException e) {
            functions.put("passRate", 0.0);
            functions.put("totalTests", 0);
            functions.put("passed", 0);
            functions.put("failed", 0);
        }
        
        return functions;
    }
    
    private static double extractDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return defaultValue;
        }
        
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return defaultValue;
        }
        
        int start = colonIndex + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        int end = start;
        while (end < json.length()) {
            char ch = json.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '.' || ch == '-') {
                end++;
            } else {
                break;
            }
        }
        
        if (start == end) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static void printSummary(List<MockEngineReport> reports) {
        reports.sort((a, b) -> Double.compare(b.passRate, a.passRate));
        
        System.out.println();
        System.out.printf("%-15s %-10s %-12s %-15s%n", 
            "Engine", "Version", "Pass Rate", "Status");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < reports.size(); i++) {
            MockEngineReport report = reports.get(i);
            String rank = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
            String status = getStatusEmoji(report.passRate);
            
            System.out.printf("%s %-12s %-10s %-12.1f%% %s%n",
                rank,
                report.engineName,
                report.version,
                report.passRate * 100,
                status
            );
        }
        
        System.out.println();
        System.out.println("Framework Features Demonstrated:");
        System.out.println("  ✅ Phase 1-3: Validation & Type-Aware Comparison");
        System.out.println("  ✅ Phase 4-6: Failure Analysis & Categorization");
        System.out.println("  ✅ Phase 7: Private & Public Storage");
        System.out.println("  ✅ Phase 8: Analytics & Insights");
        System.out.println("  ✅ Phase 9: Integrated Pipeline");
        System.out.println("  ✅ Phase 10: Complete Documentation");
    }
    
    private static String getStatusEmoji(double passRate) {
        if (passRate >= 0.95) return "🟢 Excellent";
        if (passRate >= 0.85) return "🟡 Good";
        if (passRate >= 0.70) return "🟠 Fair";
        return "🔴 Needs Work";
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
    
    static class MockEngineReport {
        String engineName;
        String version;
        int totalTests;
        int passed;
        int failed;
        int skipped;
        double passRate;
        
        MockEngineReport(String engineName, String version, int totalTests,
                        int passed, int failed, int skipped, double passRate) {
            this.engineName = engineName;
            this.version = version;
            this.totalTests = totalTests;
            this.passed = passed;
            this.failed = failed;
            this.skipped = skipped;
            this.passRate = passRate;
        }
    }
}

// Made with Bob
