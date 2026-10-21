package io.substrait.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.substrait.compliance.*;
import io.substrait.compliance.loader.YamlTestSuiteLoader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Example and CLI runner for DuckDB Substrait compliance testing.
 */
public class DuckDBComplianceExample {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        System.out.println("DuckDB Substrait Compliance Testing");
        System.out.println("====================================");
        System.out.println();

        boolean dryRun = false;
        String singleSuitePath = null;
        String suitesDirPath = null;
        String outputReportPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run":
                    dryRun = true;
                    break;
                case "--test-suite":
                    if (i + 1 < args.length) singleSuitePath = args[++i];
                    break;
                case "--test-suites":
                    if (i + 1 < args.length) suitesDirPath = args[++i];
                    break;
                case "--output":
                case "--output-report":
                    if (i + 1 < args.length) outputReportPath = args[++i];
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    return;
                default:
                    break;
            }
        }

        // 1. Create DuckDB engine
        DuckDBComplianceEngine engine = new DuckDBComplianceEngine();

        // 2. Print engine info & capabilities
        EngineInfo info = engine.getEngineInfo();
        System.out.println("Engine: " + info);
        System.out.println("Capabilities: " + engine.getCapabilities());
        System.out.println();

        if (dryRun) {
            System.out.println("✅ Dry run completed successfully (engine initialized).");
            engine.close();
            return;
        }

        // 3. Resolve test suites
        List<Path> suiteFiles = new ArrayList<>();
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();

        if (singleSuitePath != null) {
            suiteFiles.add(Paths.get(singleSuitePath));
        } else if (suitesDirPath != null) {
            Path dir = Paths.get(suitesDirPath);
            if (Files.exists(dir)) {
                try (Stream<Path> stream = Files.walk(dir)) {
                    stream.filter(p -> p.toString().endsWith(".test") || p.toString().endsWith("metadata.yaml"))
                          .sorted()
                          .forEach(suiteFiles::add);
                }
            }
        } else {
            String[] candidatePaths = {
                "test-suites/functions/arithmetic/add.test",
                "../../test-suites/functions/arithmetic/add.test",
                "test-suites/tpch/metadata.yaml",
                "../../test-suites/tpch/metadata.yaml"
            };
            for (String cp : candidatePaths) {
                Path p = Paths.get(cp);
                if (Files.exists(p)) {
                    suiteFiles.add(p);
                    break;
                }
            }
        }

        if (suiteFiles.isEmpty()) {
            System.err.println("⚠️ No test suite files found to execute.");
            engine.close();
            return;
        }

        // 4. Run compliance tests
        ComplianceRunner runner = new ComplianceRunner(engine);
        int totalTests = 0;
        int passedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        List<Map<String, Object>> testResultDetails = new ArrayList<>();

        for (Path suitePath : suiteFiles) {
            try {
                TestSuite suite = loader.load(suitePath);
                System.out.println("Running suite: " + suite.getName() + " (" + suite.getTestCases().size() + " tests)");
                ComplianceReport report = runner.runTestSuite(suite);

                totalTests += report.getTotalTests();
                passedCount += report.getPassedCount();
                failedCount += report.getFailedCount();
                skippedCount += report.getSkippedCount();

                for (TestResult result : report.getTestResults()) {
                    Map<String, Object> resMap = new LinkedHashMap<>();
                    resMap.put("testId", result.getTestId());
                    resMap.put("status", result.getStatus().toString());
                    resMap.put("executionTimeMs", result.getDurationMs());
                    if (result.getMessage() != null) {
                        resMap.put("message", result.getMessage());
                    }
                    testResultDetails.add(resMap);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not run suite " + suitePath + ": " + e.getMessage());
            }
        }

        double passRate = totalTests > 0 ? (passedCount * 100.0) / totalTests : 0.0;

        // 5. Print results summary
        System.out.println();
        System.out.println("Results Summary:");
        System.out.println("----------------");
        System.out.println("Total:            " + totalTests);
        System.out.println("Passed:           " + passedCount);
        System.out.println("Failed:           " + failedCount);
        System.out.println("Skipped:          " + skippedCount);
        System.out.printf("Compliance Score: %.1f%%\n", passRate);
        System.out.println();

        // 6. Write JSON report if requested
        if (outputReportPath != null) {
            Map<String, Object> outputReport = new LinkedHashMap<>();
            outputReport.put("engineName", info.getEngineName());
            outputReport.put("engineVersion", info.getEngineVersion());
            outputReport.put("substraitVersion", info.getSubstraitVersion());
            outputReport.put("timestamp", Instant.now().toString());
            outputReport.put("testSuiteName", singleSuitePath != null ? Paths.get(singleSuitePath).getFileName().toString() : "all-suites");
            outputReport.put("totalTests", totalTests);
            outputReport.put("passed", passedCount);
            outputReport.put("failed", failedCount);
            outputReport.put("skipped", skippedCount);
            outputReport.put("passRate", Math.round(passRate * 10.0) / 10.0);
            outputReport.put("testResults", testResultDetails);

            File outFile = new File(outputReportPath);
            File parentDir = outFile.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            MAPPER.writeValue(outFile, outputReport);
            System.out.println("📄 Compliance report written to: " + outputReportPath);
        }

        // 7. Cleanup
        engine.close();
    }

    private static void printUsage() {
        System.out.println("Usage: java ... DuckDBComplianceExample [options]");
        System.out.println("Options:");
        System.out.println("  --dry-run                 Initialize engine and exit without running tests");
        System.out.println("  --test-suite <file>       Run a single test suite file (.test or metadata.yaml)");
        System.out.println("  --test-suites <dir>       Run all test suites in a directory");
        System.out.println("  --output-report <file>    Write JSON compliance report to file");
        System.out.println("  --help, -h                Show this help message");
    }
}
