package io.substrait.demo.runner;

import io.substrait.compliance.*;
import io.substrait.compliance.loader.*;
import io.substrait.demo.engines.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Enhanced demo runner that exercises all observable phases of the compliance
 * framework against the real SDK runner and real TPC-H expected outputs.
 *
 * Phases demonstrated:
 *   1-3  Plan loading, validation, execution and type-aware result comparison
 *   4-6  Per-engine failure categorisation and improvement suggestions
 *   7    Private (full) and public (anonymised) JSON report storage
 *   8    Cross-engine analytics and dashboard artifact generation
 */
public class EnhancedDemoRunner {

    private static final String TEST_SUITE_PATH = "../test-suites/tpch/metadata.yaml";
    private static final String OUTPUT_DIR      = "output";
    private static final String STORAGE_DIR     = "output/storage";
    private static final String DASHBOARD_DIR   = "dashboard/data";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Substrait Compliance Framework - Enhanced Demo");
        System.out.println("Demonstrating All Phases: Validation, Comparison, Analysis, Storage & Analytics");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            createDirectories();

            // ── Phases 1-3: Load suite, run each engine through the real runner ──
            System.out.println("Phase 1-3: Validation & Comparison");
            System.out.println("-".repeat(80));

            YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
            Path suitePath = Paths.get(TEST_SUITE_PATH);
            if (!suitePath.toFile().exists()) {
                System.err.println("❌ Test suite not found: " + TEST_SUITE_PATH);
                System.err.println("   Run from the demo/ directory.");
                System.exit(1);
            }

            TestSuite testSuite = loader.load(suitePath);

            List<ComplianceEngine> engines = Arrays.asList(
                    new MockDBEngine(),
                    new FastDBEngine(),
                    new CloudDBEngine(),
                    new DuckDBEngine(),
                    new PostgreSQLEngine()
            );

            List<ComplianceReport> reports = new ArrayList<>();
            List<Map<String, Object>> reportMaps = new ArrayList<>();

            for (ComplianceEngine engine : engines) {
                EngineInfo info = engine.getEngineInfo();
                System.out.println("🔧 Testing: " + info.getEngineName()
                        + " v" + info.getEngineVersion() + " (demo engine)");

                System.out.println("   ✓ Loading and validating Substrait plans...");
                System.out.println("     - " + testSuite.getTestCases().size() + " plans loaded");
                System.out.println("   ✓ Executing plans with type-aware result comparison...");
                System.out.println("     - epsilon: 1e-9 for floating-point values");
                System.out.println("     - NaN / Infinity handled as per SQL standard");

                ComplianceRunner runner = new ComplianceRunner(engine);
                ComplianceReport report = runner.runTestSuite(testSuite);
                reports.add(report);

                int total   = report.getTotalTests();
                int passed  = report.getPassedCount();
                int failed  = report.getFailedCount();
                int skipped = report.getSkippedCount();
                double rate = report.getComplianceScore();

                System.out.println("   📊 Results:");
                System.out.println("      ✅ Passed:  " + passed);
                System.out.println("      ❌ Failed:  " + failed);
                System.out.println("      ⏭️  Skipped: " + skipped);
                System.out.println("      📈 Pass Rate: " + String.format("%.1f%%", rate));
                System.out.println();

                reportMaps.add(buildReportMap(report));
            }

            // ── Phases 4-6: Failure categorisation ──
            System.out.println("Phase 4-6: Failure Analysis & Categorization");
            System.out.println("-".repeat(80));
            analyzeFailures(reports);
            System.out.println();

            // ── Phase 7: Storage ──
            System.out.println("Phase 7: Data Storage (Private & Public)");
            System.out.println("-".repeat(80));
            storeReports(reportMaps);
            System.out.println();

            // ── Phase 8: Analytics + dashboard ──
            System.out.println("Phase 8: Analytics & Insights");
            System.out.println("-".repeat(80));
            generateAnalytics(reportMaps);
            System.out.println();

            // ── Summary table ──
            System.out.println("=".repeat(80));
            System.out.println("📊 Demo Summary");
            System.out.println("=".repeat(80));
            System.out.println("Note: demo engines are deterministic stubs — results reflect framework "
                    + "mechanics, not real engine SQL execution.");
            printSummary(reportMaps);

            System.out.println();
            System.out.println("Framework Phases Demonstrated:");
            System.out.println("  ✅ Phase 1-3: Plan loading, execution & type-aware comparison");
            System.out.println("  ✅ Phase 4-6: Failure analysis & categorisation");
            System.out.println("  ✅ Phase 7:   Private & public report storage");
            System.out.println("  ✅ Phase 8:   Cross-engine analytics & dashboard artifacts");

            System.out.println();
            System.out.println("✅ Enhanced demo completed successfully!");
            System.out.println();
            System.out.println("Generated artifacts:");
            System.out.println("  📁 " + OUTPUT_DIR + "/              — individual engine reports");
            System.out.println("  📁 " + STORAGE_DIR + "/private/ — full diagnostic reports");
            System.out.println("  📁 " + STORAGE_DIR + "/public/  — anonymised community reports");
            System.out.println("  📁 " + OUTPUT_DIR + "/analytics/    — cross-engine analytics");
            System.out.println("  📁 " + DASHBOARD_DIR + "/        — dashboard data");

        } catch (Exception e) {
            System.err.println("❌ Enhanced demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void createDirectories() {
        for (String dir : new String[]{
                OUTPUT_DIR, OUTPUT_DIR + "/analytics",
                STORAGE_DIR + "/private", STORAGE_DIR + "/public",
                DASHBOARD_DIR}) {
            new File(dir).mkdirs();
        }
    }

    private static Map<String, Object> buildReportMap(ComplianceReport report) {
        Map<String, Object> m = new LinkedHashMap<>();
        EngineInfo info = report.getEngineInfo();
        m.put("name",          info.getEngineName());
        m.put("engineName",    info.getEngineName());
        m.put("version",       info.getEngineVersion());
        m.put("engineVersion", info.getEngineVersion());
        m.put("substraitVersion", info.getSubstraitVersion());
        m.put("timestamp",     new Date(report.getTimestamp()).toInstant().toString());
        m.put("testSuiteName", report.getTestSuiteName());
        m.put("totalTests",    report.getTotalTests());
        m.put("passed",        report.getPassedCount());
        m.put("failed",        report.getFailedCount());
        m.put("skipped",       report.getSkippedCount());
        m.put("passRate",      Math.round(report.getComplianceScore() * 10.0) / 10.0);

        List<Map<String, Object>> results = new ArrayList<>();
        for (TestResult r : report.getTestResults()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("testId",         r.getTestId());
            t.put("status",         r.getStatus().toString());
            t.put("executionTimeMs", r.getDurationMs());
            if (r.getMessage() != null) t.put("message", r.getMessage());
            results.add(t);
        }
        m.put("testResults", results);
        return m;
    }

    private static void analyzeFailures(List<ComplianceReport> reports) {
        System.out.println("Analyzing failure patterns across engines...");
        System.out.println();

        for (ComplianceReport report : reports) {
            String engineName = report.getEngineInfo().getEngineName();
            long failed = report.getTestResults().stream()
                    .filter(r -> r.getStatus() == TestResult.Status.FAILED).count();
            if (failed == 0) continue;

            // Bucket failures by message prefix into categories
            Map<String, Integer> categories = new LinkedHashMap<>();
            for (TestResult r : report.getTestResults()) {
                if (r.getStatus() != TestResult.Status.FAILED) continue;
                String msg = r.getMessage() != null ? r.getMessage() : "unknown";
                String category = msg.startsWith("Output mismatch") ? "VALUE_MISMATCH"
                        : msg.startsWith("Plan not supported") ? "UNSUPPORTED_FEATURE"
                        : msg.startsWith("Exception") ? "RUNTIME_ERROR"
                        : "OTHER";
                categories.merge(category, 1, Integer::sum);
            }

            System.out.println("Engine: " + engineName);
            System.out.println("  Failure Categories:");
            categories.forEach((cat, cnt) ->
                    System.out.println("    - " + cat + ": " + cnt));
            System.out.println("  Suggestions:");
            if (categories.containsKey("VALUE_MISMATCH"))
                System.out.println("    → Output does not match expected — implement real SQL execution");
            if (categories.containsKey("UNSUPPORTED_FEATURE"))
                System.out.println("    → Expand engine's supported Substrait relations/functions");
            if (categories.containsKey("RUNTIME_ERROR"))
                System.out.println("    → Check exception stack traces in individual test reports");
            System.out.println();
        }
    }

    private static void storeReports(List<Map<String, Object>> reports) throws Exception {
        for (Map<String, Object> report : reports) {
            String engine    = report.get("engineName").toString().toLowerCase();
            String timestamp = Instant.now().toString().replace(":", "-");

            // Private — full data
            File privateDir = new File(STORAGE_DIR + "/private/" + engine + "/" + timestamp);
            privateDir.mkdirs();
            Map<String, Object> priv = new LinkedHashMap<>(report);
            priv.put("diagnosticsLevel", "full");
            JSON.writeValue(new File(privateDir, "engine-report.json"), priv);
            System.out.println("   💾 Private: " + privateDir.getPath() + "/engine-report.json");

            // Public — anonymised (drop individual test messages)
            File publicDir = new File(STORAGE_DIR + "/public/" + engine + "/" + timestamp);
            publicDir.mkdirs();
            Map<String, Object> pub = new LinkedHashMap<>(report);
            pub.remove("testResults");
            pub.put("diagnosticsLevel", "public");
            JSON.writeValue(new File(publicDir, "community-report.json"), pub);
            System.out.println("   💾 Public:  " + publicDir.getPath() + "/community-report.json");
        }
    }

    private static void generateAnalytics(List<Map<String, Object>> reports) throws Exception {
        double avg = reports.stream()
                .mapToDouble(r -> ((Number) r.get("passRate")).doubleValue())
                .average().orElse(0);
        Map<String, Object> best = reports.stream()
                .max(Comparator.comparingDouble(r -> ((Number) r.get("passRate")).doubleValue()))
                .orElse(null);
        Map<String, Object> worst = reports.stream()
                .min(Comparator.comparingDouble(r -> ((Number) r.get("passRate")).doubleValue()))
                .orElse(null);

        System.out.println("📈 Key Findings:");
        System.out.printf("   • Average compliance: %.1f%%%n", avg);
        if (best  != null) System.out.println("   • Best performer:  " + best.get("name")
                + " (" + best.get("passRate") + "%)");
        if (worst != null) System.out.println("   • Needs attention: " + worst.get("name")
                + " (" + worst.get("passRate") + "%)");

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("generatedAt", Instant.now().toString());
        analytics.put("averagePassRate", Math.round(avg * 10.0) / 10.0);
        analytics.put("bestEngine",  best  != null ? best.get("name")  : null);
        analytics.put("worstEngine", worst != null ? worst.get("name") : null);
        analytics.put("engines", reports);

        File analyticsFile = new File(OUTPUT_DIR + "/analytics/analytics-report.json");
        JSON.writeValue(analyticsFile, analytics);
        System.out.println("   💾 Analytics: " + analyticsFile.getPath());

        // Dashboard leaderboard
        List<Map<String, Object>> sorted = new ArrayList<>(reports);
        sorted.sort((a, b) -> Double.compare(
                ((Number) b.get("passRate")).doubleValue(),
                ((Number) a.get("passRate")).doubleValue()));
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).put("rank", i + 1);

        Map<String, Object> leaderboard = new LinkedHashMap<>();
        leaderboard.put("lastUpdated", Instant.now().toString());
        leaderboard.put("totalEngines", sorted.size());
        leaderboard.put("averagePassRate", Math.round(avg * 10.0) / 10.0);
        leaderboard.put("engines", sorted);

        File lbFile = new File(DASHBOARD_DIR + "/leaderboard.json");
        JSON.writeValue(lbFile, leaderboard);
        System.out.println("   💾 Dashboard leaderboard: " + lbFile.getPath());
    }

    private static void printSummary(List<Map<String, Object>> reports) {
        List<Map<String, Object>> sorted = new ArrayList<>(reports);
        sorted.sort((a, b) -> Double.compare(
                ((Number) b.get("passRate")).doubleValue(),
                ((Number) a.get("passRate")).doubleValue()));

        System.out.println();
        System.out.printf("%-15s %-10s %-12s %-15s%n", "Engine", "Version", "Pass Rate", "Status");
        System.out.println("-".repeat(80));
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < sorted.size(); i++) {
            Map<String, Object> r = sorted.get(i);
            String medal = i < medals.length ? medals[i] : "  ";
            double rate  = ((Number) r.get("passRate")).doubleValue();
            String badge = rate >= 95 ? "🟢 VERIFIED" : rate >= 80 ? "🔵 EDGE"
                         : rate >= 60 ? "🟡 BASIC" : "🔴 NONE";
            System.out.printf("%s %-12s %-10s %-12.1f%% %s%n",
                    medal, r.get("name"), r.get("version"), rate, badge);
        }
    }
}
