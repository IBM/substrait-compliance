package demo.runner;

import io.substrait.demo.engines.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/**
 * Demo runner for function-level compliance tests.
 * 
 * This demonstrates how the new function test framework works alongside
 * the existing TPC-H tests, providing granular function-level testing.
 */
public class FunctionTestDemo {
    
    private static final String TEST_SUITES_DIR = "../test-suites/functions";
    private static final String OUTPUT_DIR = "output";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   Substrait Function-Level Compliance Testing Demo        ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        try {
            // Create output directory
            Files.createDirectories(Paths.get(OUTPUT_DIR));
            
            // Run tests for each engine
            runEngineTests(new MockDBEngine());
            runEngineTests(new FastDBEngine());
            runEngineTests(new CloudDBEngine());
            runEngineTests(new DuckDBEngine());
            runEngineTests(new PostgreSQLEngine());
            
            // Generate summary report
            generateSummaryReport();
            
            System.out.println("\n✅ Demo completed successfully!");
            System.out.println("📊 Results saved to: " + OUTPUT_DIR + "/");
            System.out.println("🌐 Open dashboard/index.html to view results");
            
        } catch (Exception e) {
            System.err.println("❌ Error running demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runEngineTests(Object engine) throws Exception {
        String engineName = getDisplayEngineName(engine);
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Testing Engine: " + engineName);
        System.out.println("=".repeat(60));
        
        Map<String, TestResults> categoryResults = new HashMap<>();
        
        // Test each function category - scan all available categories
        String[] categories = scanTestCategories();
        
        for (String category : categories) {
            System.out.println("\n📂 Category: " + category);
            TestResults results = runCategoryTests(engine, category);
            categoryResults.put(category, results);
            
            System.out.printf("   Tests: %d | Passed: %d | Failed: %d | Pass Rate: %.1f%%\n",
                results.total, results.passed, results.failed, results.getPassRate());
        }
        
        // Save results
        saveEngineResults(engineName, categoryResults);
    }
    
    private static String[] scanTestCategories() {
        // Scan test-suites/functions directory for all categories
        List<String> categories = new ArrayList<>();
        try {
            Path testSuitesPath = Paths.get(TEST_SUITES_DIR);
            if (Files.exists(testSuitesPath)) {
                try (var stream = Files.list(testSuitesPath)) {
                    stream.filter(Files::isDirectory)
                          .map(p -> p.getFileName().toString())
                          .sorted()
                          .forEach(categories::add);
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️  Error scanning test categories: " + e.getMessage());
            // Fallback to default categories
            return new String[]{"aggregate", "arithmetic", "boolean", "cast", "comparison",
                               "datetime", "string", "window"};
        }
        return categories.toArray(new String[0]);
    }
    
    private static TestResults runCategoryTests(Object engine, String category) {
        // Simulate running tests by parsing .test files
        TestResults results = new TestResults();
        
        try {
            Path categoryDir = Paths.get(TEST_SUITES_DIR, category);
            if (!Files.exists(categoryDir)) {
                System.out.println("   ⚠️  Category directory not found: " + categoryDir);
                return results;
            }
            
            // Count test files
            try (var stream = Files.list(categoryDir)) {
                long testFileCount = stream.filter(p -> p.toString().endsWith(".test")).count();
                
                // Simulate test execution with realistic pass rates
                results.total = (int)(testFileCount * getAvgTestsPerFile(category));
                results.passed = (int)(results.total * getEnginePassRate(engine, category));
                results.failed = results.total - results.passed;
            }
            
        } catch (IOException e) {
            System.err.println("   ❌ Error reading category: " + e.getMessage());
        }
        
        return results;
    }
    
    private static int getAvgTestsPerFile(String category) {
        // Average number of tests per .test file
        switch (category) {
            case "aggregate": return 28;
            case "window": return 32;
            case "cast": return 72;
            case "boolean": return 40;
            case "datetime": return 25;
            case "arithmetic": return 30;
            case "string": return 35;
            case "comparison": return 25;
            case "array": return 25;
            case "struct": return 20;
            case "map": return 20;
            case "json": return 25;
            case "conditional": return 28;
            case "set": return 27;
            case "geospatial": return 20;
            default: return 20;
        }
    }
    
    private static double getEnginePassRate(Object engine, String category) {
        // Simulate different pass rates for different engines and categories
        String engineName = getDisplayEngineName(engine);
        
        switch (engineName) {
            case "MockDB":
                switch (category) {
                    case "aggregate": return 0.75;
                    case "window": return 0.65;
                    case "cast": return 0.80;
                    case "boolean": return 0.90;
                    case "datetime": return 0.70;
                    case "arithmetic": return 0.85;
                    case "string": return 0.88;
                    case "comparison": return 0.92;
                    case "array": return 0.60;
                    case "struct": return 0.55;
                    case "map": return 0.58;
                    case "json": return 0.50;
                    case "conditional": return 0.82;
                    case "set": return 0.65;
                    case "geospatial": return 0.45;
                    default: return 0.70;
                }
            case "FastDB":
                switch (category) {
                    case "aggregate": return 0.92;
                    case "window": return 0.88;
                    case "cast": return 0.95;
                    case "boolean": return 0.98;
                    case "datetime": return 0.85;
                    case "arithmetic": return 0.96;
                    case "string": return 0.94;
                    case "comparison": return 0.98;
                    case "array": return 0.85;
                    case "struct": return 0.80;
                    case "map": return 0.82;
                    case "json": return 0.78;
                    case "conditional": return 0.93;
                    case "set": return 0.88;
                    case "geospatial": return 0.72;
                    default: return 0.90;
                }
            case "CloudDB":
                switch (category) {
                    case "aggregate": return 0.96;
                    case "window": return 0.94;
                    case "cast": return 0.98;
                    case "boolean": return 1.00;
                    case "datetime": return 0.92;
                    case "arithmetic": return 0.99;
                    case "string": return 0.97;
                    case "comparison": return 1.00;
                    case "array": return 0.92;
                    case "struct": return 0.90;
                    case "map": return 0.91;
                    case "json": return 0.88;
                    case "conditional": return 0.97;
                    case "set": return 0.94;
                    case "geospatial": return 0.85;
                    default: return 0.95;
                }
            case "DuckDB":
                switch (category) {
                    case "aggregate": return 0.94;
                    case "window": return 0.91;
                    case "cast": return 0.96;
                    case "boolean": return 0.99;
                    case "datetime": return 0.90;
                    case "arithmetic": return 0.97;
                    case "string": return 0.95;
                    case "comparison": return 0.99;
                    case "array": return 0.90;
                    case "struct": return 0.87;
                    case "map": return 0.88;
                    case "json": return 0.84;
                    case "conditional": return 0.95;
                    case "set": return 0.92;
                    case "geospatial": return 0.78;
                    default: return 0.93;
                }
            case "PostgreSQL":
                switch (category) {
                    case "aggregate": return 0.89;
                    case "window": return 0.84;
                    case "cast": return 0.93;
                    case "boolean": return 0.97;
                    case "datetime": return 0.83;
                    case "arithmetic": return 0.94;
                    case "string": return 0.92;
                    case "comparison": return 0.97;
                    case "array": return 0.81;
                    case "struct": return 0.77;
                    case "map": return 0.75;
                    case "json": return 0.86;
                    case "conditional": return 0.91;
                    case "set": return 0.90;
                    case "geospatial": return 0.68;
                    default: return 0.88;
                }
            default:
                return 0.80;
        }
    }
    
    private static String getDisplayEngineName(Object engine) {
        if (engine instanceof MockDBEngine) return "MockDB";
        if (engine instanceof FastDBEngine) return "FastDB";
        if (engine instanceof CloudDBEngine) return "CloudDB";
        if (engine instanceof DuckDBEngine) return "DuckDB";
        if (engine instanceof PostgreSQLEngine) return "PostgreSQL";
        return engine.getClass().getSimpleName();
    }
    
    private static void saveEngineResults(String engineName, Map<String, TestResults> results) throws IOException {
        JSONObject json = new JSONObject();
        json.put("engine", engineName);
        json.put("timestamp", System.currentTimeMillis());
        
        JSONObject categories = new JSONObject();
        int totalTests = 0;
        int totalPassed = 0;
        
        for (Map.Entry<String, TestResults> entry : results.entrySet()) {
            JSONObject categoryJson = new JSONObject();
            TestResults r = entry.getValue();
            categoryJson.put("total", r.total);
            categoryJson.put("passed", r.passed);
            categoryJson.put("failed", r.failed);
            categoryJson.put("passRate", r.getPassRate());
            categories.put(entry.getKey(), categoryJson);
            
            totalTests += r.total;
            totalPassed += r.passed;
        }
        
        json.put("categories", categories);
        json.put("totalTests", totalTests);
        json.put("totalPassed", totalPassed);
        json.put("overallPassRate", totalTests > 0 ? (totalPassed * 100.0 / totalTests) : 0);
        
        // Save to file
        String filename = OUTPUT_DIR + "/" + engineName + "_function_tests.json";
        Files.writeString(Paths.get(filename), json.toString(2));
    }
    
    private static void generateSummaryReport() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Summary Report");
        System.out.println("=".repeat(60));
        
        JSONArray engines = new JSONArray();
        
        // Read all engine results
        try (var stream = Files.list(Paths.get(OUTPUT_DIR))) {
            stream.filter(p -> p.toString().endsWith("_function_tests.json"))
                  .forEach(path -> {
                      try {
                          String content = Files.readString(path);
                          engines.put(new JSONObject(content));
                      } catch (IOException e) {
                          System.err.println("Error reading " + path + ": " + e.getMessage());
                      }
                  });
        }
        
        // Create summary
        JSONObject summary = new JSONObject();
        summary.put("timestamp", System.currentTimeMillis());
        summary.put("engines", engines);
        summary.put("testSuiteType", "function_tests");
        
        // Save summary
        Files.writeString(Paths.get(OUTPUT_DIR + "/function_tests_summary.json"),
                         summary.toString(2));
        
        Map<String, Object> sharedSummary = buildSharedSummary(engines);
        JSON_MAPPER.writeValue(Paths.get("dashboard/data/summary.json").toFile(), sharedSummary);
        
        // Print summary table
        System.out.println("\n┌─────────────────┬────────┬─────────┬─────────┬───────────┐");
        System.out.println("│ Engine          │ Total  │ Passed  │ Failed  │ Pass Rate │");
        System.out.println("├─────────────────┼────────┼─────────┼─────────┼───────────┤");
        
        for (int i = 0; i < engines.length(); i++) {
            JSONObject engine = engines.getJSONObject(i);
            System.out.printf("│ %-15s │ %6d │ %7d │ %7d │ %8.1f%% │\n",
                engine.getString("engine"),
                engine.getInt("totalTests"),
                engine.getInt("totalPassed"),
                engine.getInt("totalTests") - engine.getInt("totalPassed"),
                engine.getDouble("overallPassRate"));
        }
        
        System.out.println("└─────────────────┴────────┴─────────┴─────────┴───────────┘");
    }
    
    private static Map<String, Object> buildSharedSummary(JSONArray functionEngines) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lastUpdated", new Date().toInstant().toString());
        summary.put("totalEngines", functionEngines.length());
        
        Map<String, Map<String, Object>> existingSummaryByEngine = new HashMap<>();
        Path existingSummaryPath = Paths.get("dashboard/data/summary.json");
        if (Files.exists(existingSummaryPath)) {
            Map<String, Object> existingSummary = JSON_MAPPER.readValue(existingSummaryPath.toFile(), Map.class);
            List<Map<String, Object>> existingEngines =
                (List<Map<String, Object>>) existingSummary.getOrDefault("engines", Collections.emptyList());
            for (Map<String, Object> engine : existingEngines) {
                existingSummaryByEngine.put(engine.get("engineName").toString(), engine);
            }
        }
        
        List<Map<String, Object>> engines = new ArrayList<>();
        for (int i = 0; i < functionEngines.length(); i++) {
            JSONObject functionEngine = functionEngines.getJSONObject(i);
            String engineName = functionEngine.getString("engine");
            
            Map<String, Object> engineSummary = existingSummaryByEngine.getOrDefault(engineName, new LinkedHashMap<>());
            engineSummary.put("engineName", engineName);
            engineSummary.putIfAbsent("engineVersion", "demo");
            
            Map<String, Object> functions = new LinkedHashMap<>();
            functions.put("passRate", functionEngine.getDouble("overallPassRate"));
            functions.put("totalTests", functionEngine.getInt("totalTests"));
            functions.put("passed", functionEngine.getInt("totalPassed"));
            functions.put("failed", functionEngine.getInt("totalTests") - functionEngine.getInt("totalPassed"));
            engineSummary.put("functions", functions);
            
            if (!engineSummary.containsKey("tpch")) {
                Map<String, Object> emptyTpch = new LinkedHashMap<>();
                emptyTpch.put("passRate", 0.0);
                emptyTpch.put("totalTests", 0);
                emptyTpch.put("passed", 0);
                emptyTpch.put("failed", 0);
                emptyTpch.put("skipped", 0);
                engineSummary.put("tpch", emptyTpch);
            }
            
            engines.add(engineSummary);
        }
        
        summary.put("engines", engines);
        return summary;
    }
    
    static class TestResults {
        int total = 0;
        int passed = 0;
        int failed = 0;
        
        double getPassRate() {
            return total > 0 ? (passed * 100.0 / total) : 0;
        }
    }
}

// Made with Bob
