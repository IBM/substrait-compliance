package io.substrait.compliance;

import io.substrait.compliance.loader.YamlTestSuiteLoader;
import io.substrait.proto.Plan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.IdentityHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Proves the full comparator loop in both directions.
 *
 * A PassThroughEngine is pre-loaded with the expected output of every test
 * case (keyed by plan bytes hash). When executePlan() is called it returns
 * the matching expected output verbatim, so the comparator sees
 * expected == actual and every test with a valid expected CSV must PASS.
 *
 * This validates:
 *  - typed CSV headers are loaded and type-normalised correctly
 *  - compareColumnTypes accepts self-identical type lists
 *  - valuesMatch handles the typed Object values produced by convertValue
 *  - no off-by-one, CRLF, or encoding artefact causes a spurious failure
 */
class ComplianceRunnerComparatorTest {

    /**
     * Engine that echoes back a pre-registered expected output for each plan.
     * Plans are keyed by their serialised byte-array hash so no string IDs
     * need to be threaded through the ComplianceRunner.
     */
    static class PassThroughEngine implements ComplianceEngine {

        /**
         * Keyed by Plan object identity so that two different TestCase instances
         * loaded from identical plan binaries (duplicate Substrait plans in the
         * test suite) are still distinguished — each TestCase holds its own Plan
         * instance, so identity is a safe surrogate for "which test case is this".
         */
        private final IdentityHashMap<Plan, TableData> expectedByPlan = new IdentityHashMap<>();

        void register(Plan plan, TableData expected) {
            expectedByPlan.put(plan, expected);
        }

        @Override public EngineInfo getEngineInfo() {
            return new EngineInfo("PassThrough", "1.0", "0.20.0");
        }
        @Override public EngineCapabilities getCapabilities() {
            return EngineCapabilities.builder().build();
        }
        @Override public PlanValidationResult validatePlan(Plan plan) {
            return PlanValidationResult.supported();
        }
        @Override public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) {
            TableData expected = expectedByPlan.get(plan);
            if (expected == null) {
                return ComplianceResult.failure("No expected output registered for plan", null);
            }
            return ComplianceResult.success(expected, 0);
        }
        @Override public void initialize() {}
        @Override public void cleanup() {}
    }

    /**
     * Identity test: every expected CSV that the loader produces, when fed
     * back verbatim as engine output, must compare as PASSED.
     *
     * The test builds the suite from a minimal inline YAML that references a
     * single well-formed typed CSV so it runs without any external file system
     * dependency and exercises the full code path.
     */
    @Test
    void passThroughEngineProducesPassForTypedExpectedOutput(@TempDir Path tempDir) throws Exception {
        // -- Arrange: write a minimal test suite with a typed CSV --
        String csv = "col_a:string|col_b:double|col_c:integer\n"
                   + "foo|1.5|10\n"
                   + "bar|2.5|20\n";

        // A one-byte fake plan binary — Plan.parseFrom(byte[]{0}) gives a valid empty Plan
        byte[] fakePlan = new byte[]{};
        Path planFile = tempDir.resolve("q00.bin");
        Files.write(planFile, fakePlan);

        Path csvFile = tempDir.resolve("expected.csv");
        Files.writeString(csvFile, csv);

        String yaml = "name: \"unit-test-suite\"\n"
                    + "version: \"1.0.0\"\n"
                    + "description: \"Unit test\"\n"
                    + "testCases:\n"
                    + "  - id: \"q00\"\n"
                    + "    description: \"pass-through test\"\n"
                    + "    planBinary: \"q00.bin\"\n"
                    + "    inputTables: []\n"
                    + "    expectedOutput: \"expected.csv\"\n";

        Path yamlFile = tempDir.resolve("metadata.yaml");
        Files.writeString(yamlFile, yaml);

        // -- Load --
        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(yamlFile);

        assertThat(suite.getTestCases()).hasSize(1);
        TableData expectedOutput = suite.getTestCases().get(0).getExpectedOutput();
        assertThat(expectedOutput).isNotNull();

        // Verify types were loaded correctly from typed header
        assertThat(expectedOutput.getColumnTypes()).containsExactly("string", "double", "integer");
        // Verify values were typed correctly
        assertThat(expectedOutput.getValue(0, 0)).isEqualTo("foo");
        assertThat(expectedOutput.getValue(0, 1)).isInstanceOf(Double.class);
        assertThat(((Double) expectedOutput.getValue(0, 1))).isEqualTo(1.5);
        assertThat(expectedOutput.getValue(0, 2)).isInstanceOf(Integer.class);
        assertThat(expectedOutput.getValue(0, 2)).isEqualTo(10);

        // -- Build pass-through engine --
        PassThroughEngine engine = new PassThroughEngine();
        engine.register(suite.getTestCases().get(0).getPlan(), expectedOutput);

        // -- Run --
        ComplianceRunner runner = new ComplianceRunner(engine);
        runner.registerTestSuite(suite);
        ComplianceReport report = runner.runTestSuite("unit-test-suite");

        // -- Assert: pass-through must yield 100% pass --
        assertThat(report.getPassedCount())
                .as("Pass-through engine must PASS all tests with expected output")
                .isEqualTo(1);
        assertThat(report.getFailedCount()).isEqualTo(0);
        assertThat(report.getSkippedCount()).isEqualTo(0);
    }

    /**
     * Verifies that mismatched data produces FAILED, not PASSED.
     * This closes the other direction of the loop.
     */
    @Test
    void mismatchedOutputProducesFailed(@TempDir Path tempDir) throws Exception {
        String expectedCsv = "col_a:string|col_b:double\nfoo|1.5\n";

        byte[] fakePlan = new byte[]{};
        Path planFile = tempDir.resolve("q00.bin");
        Files.write(planFile, fakePlan);

        Path expectedCsvFile = tempDir.resolve("expected.csv");
        Files.writeString(expectedCsvFile, expectedCsv);

        String yaml = "name: \"unit-test-suite\"\n"
                    + "version: \"1.0.0\"\n"
                    + "description: \"Mismatch test\"\n"
                    + "testCases:\n"
                    + "  - id: \"q00\"\n"
                    + "    description: \"mismatch test\"\n"
                    + "    planBinary: \"q00.bin\"\n"
                    + "    inputTables: []\n"
                    + "    expectedOutput: \"expected.csv\"\n";

        Path yamlFile = tempDir.resolve("metadata.yaml");
        Files.writeString(yamlFile, yaml);

        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(yamlFile);

        // Build the wrong TableData directly.
        List<Object> wrongRow = new ArrayList<>();
        wrongRow.add("foo");
        wrongRow.add(9.9);
        TableData wrongOutput = new TableData(
                Arrays.asList("col_a", "col_b"),
                Arrays.asList("string", "double"),
                Collections.singletonList(wrongRow)
        );

        PassThroughEngine engine = new PassThroughEngine();
        engine.register(suite.getTestCases().get(0).getPlan(), wrongOutput);

        ComplianceRunner runner = new ComplianceRunner(engine);
        runner.registerTestSuite(suite);
        ComplianceReport report = runner.runTestSuite("unit-test-suite");

        assertThat(report.getFailedCount())
                .as("Wrong output must produce a FAILED result")
                .isEqualTo(1);
        assertThat(report.getPassedCount()).isEqualTo(0);
    }

    /**
     * Verifies the Number/String cross-type matching added to valuesMatch():
     * when an expected CSV carries a String value that looks like a number (e.g. a
     * column typed "string" whose value is "1.5") and the engine returns a Double,
     * the comparator should still match via numeric comparison.
     *
     * This situation arises when an engine returns a more precise type for a column
     * that the reference CSV annotated as string (e.g. a date column that comes back
     * as a date object whose toString() matches the expected string value).
     */
    @Test
    void numberStringCrossTypeMatchingWorks(@TempDir Path tempDir) throws Exception {
        // Typed CSV where second column is declared "string" but holds a numeric string value.
        // This simulates a legacy CSV where the column type annotation was overly conservative.
        String typedCsv = "col_a:string|col_b:string\nfoo|1.5\n";

        byte[] fakePlan = new byte[]{};
        Path planFile = tempDir.resolve("q00.bin");
        Files.write(planFile, fakePlan);

        Path csvFile = tempDir.resolve("expected.csv");
        Files.writeString(csvFile, typedCsv);

        String yaml = "name: \"unit-test-suite\"\n"
                    + "version: \"1.0.0\"\n"
                    + "description: \"Cross-type test\"\n"
                    + "testCases:\n"
                    + "  - id: \"q00\"\n"
                    + "    description: \"cross type\"\n"
                    + "    planBinary: \"q00.bin\"\n"
                    + "    inputTables: []\n"
                    + "    expectedOutput: \"expected.csv\"\n";

        Path yamlFile = tempDir.resolve("metadata.yaml");
        Files.writeString(yamlFile, yaml);

        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(yamlFile);

        TableData expected = suite.getTestCases().get(0).getExpectedOutput();
        // Both columns typed "string" — values are loaded as String objects
        assertThat(expected.getColumnTypes()).containsExactly("string", "string");
        assertThat(expected.getValue(0, 0)).isEqualTo("foo");
        assertThat(expected.getValue(0, 1)).isEqualTo("1.5"); // String

        // Engine returns col_b as Double — type names match via normalizeType (both "string"),
        // and valuesMatch String("1.5") vs Double(1.5) → numeric cross-type branch → PASSED.
        List<Object> typedRow = new ArrayList<>();
        typedRow.add("foo");
        typedRow.add(1.5);  // Double — engine returned typed numeric
        TableData typedOutput = new TableData(
                Arrays.asList("col_a", "col_b"),
                Arrays.asList("string", "string"),   // same type names as expected
                Collections.singletonList(typedRow)
        );

        PassThroughEngine engine = new PassThroughEngine();
        engine.register(suite.getTestCases().get(0).getPlan(), typedOutput);

        ComplianceRunner runner = new ComplianceRunner(engine);
        runner.registerTestSuite(suite);
        ComplianceReport report = runner.runTestSuite("unit-test-suite");

        // The new numeric cross-type branch in valuesMatch() matches String("1.5") to Double(1.5)
        assertThat(report.getPassedCount())
                .as("String(\"1.5\") vs Double(1.5) must PASS via numeric cross-type matching")
                .isEqualTo(1);
        assertThat(report.getFailedCount()).isEqualTo(0);
    }

    /**
     * PASS-direction proof against the real TPC-H suite.
     *
     * Loads all 22 TPC-H expected CSVs and plan binaries from the real repository
     * test-suite directory, registers each expected TableData in a PassThroughEngine
     * keyed by plan hash, then runs the full ComplianceRunner.  Every test must PASS.
     *
     * This proves:
     *  - all 22 typed CSV headers parse without error
     *  - the typed Object values produced by convertValue() round-trip through
     *    compareColumnTypes() and valuesMatch() without a spurious failure
     *  - no CRLF, encoding, or whitespace artefact breaks the comparison
     *
     * Note: input data tables are intentionally omitted from the synthetic metadata
     * so that the test does not load the large TPC-H source files (lineitem.csv has
     * 60k rows) into memory.  The pass-through engine does not need input data.
     *
     * Oracle provenance: the expected outputs were produced by DuckDB 1.2.0 against
     * the scale-0.01 CSV data shipped with this repository.  DuckDB is therefore the
     * de-facto reference for result semantics in this suite.  Any Substrait engine
     * that produces the same results as DuckDB on this dataset will PASS; engines
     * whose SQL semantics differ (e.g. different numeric rounding, NULL ordering,
     * or DATE formatting) may FAIL even when their behaviour is otherwise correct.
     */
    @Test
    void realTpcHSuitePassesThroughWithExpectedOutput(@TempDir Path tempDir) throws Exception {
        String rootDir = System.getProperty("substrait.compliance.rootDir");
        Assumptions.assumeTrue(rootDir != null,
                "System property substrait.compliance.rootDir must be set (check build.gradle)");
        Path tpchDir = Path.of(rootDir).resolve("test-suites/tpch");
        Assumptions.assumeTrue(tpchDir.resolve("metadata.yaml").toFile().exists(),
                "TPC-H metadata.yaml must be present at " + tpchDir);

        // Build a synthetic metadata YAML that references the real plan binaries
        // and expected CSVs but has no inputTables — avoids loading the large
        // source data files (lineitem.csv = 60k rows) into the test JVM heap.
        String[] queryIds = {
            "q01","q02","q03","q04","q05","q06","q07","q08","q09","q10",
            "q11","q12","q13","q14","q15","q16","q17","q18","q19","q20",
            "q21","q22"
        };
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: \"tpch\"\nversion: \"1.0.0\"\ndescription: \"TPC-H pass-through\"\ntestCases:\n");
        for (String qid : queryIds) {
            Path planBin = tpchDir.resolve("plans/" + qid + ".bin");
            Path expectedCsv = tpchDir.resolve("expected/" + qid + ".csv");
            if (!planBin.toFile().exists() || !expectedCsv.toFile().exists()) continue;
            yaml.append("  - id: \"").append(qid).append("\"\n");
            yaml.append("    description: \"").append(qid).append("\"\n");
            // Use absolute paths so the loader can resolve them from tempDir
            yaml.append("    planBinary: \"").append(planBin.toAbsolutePath()).append("\"\n");
            yaml.append("    inputTables: []\n");
            yaml.append("    expectedOutput: \"").append(expectedCsv.toAbsolutePath()).append("\"\n");
        }

        Path syntheticYaml = tempDir.resolve("tpch-passthrough.yaml");
        Files.writeString(syntheticYaml, yaml.toString());

        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(syntheticYaml);

        assertThat(suite.getTestCases())
                .as("All 22 TPC-H queries must be loaded")
                .hasSize(22);

        // Register each expected output in the pass-through engine.
        PassThroughEngine engine = new PassThroughEngine();
        for (TestCase tc : suite.getTestCases()) {
            assertThat(tc.getExpectedOutput())
                    .as("Test case " + tc.getId() + " must have a non-null expected output")
                    .isNotNull();
            engine.register(tc.getPlan(), tc.getExpectedOutput());
        }

        ComplianceRunner runner = new ComplianceRunner(engine);
        runner.registerTestSuite(suite);
        ComplianceReport report = runner.runTestSuite("tpch");

        // Collect failures for a clear error message if something regresses.
        List<String> failures = new ArrayList<>();
        for (TestResult result : report.getTestResults()) {
            if (!result.isPassed()) {
                failures.add(result.getTestId() + " [" + result.getStatus() + "]: " + result.getMessage());
            }
        }

        assertThat(failures)
                .as("Pass-through engine must produce zero non-PASSED results.\n"
                  + "A failure indicates a typed header, convertValue, or comparator bug.\n"
                  + "Non-passed: " + failures)
                .isEmpty();

        assertThat(report.getPassedCount())
                .as("All 22 TPC-H queries must PASS with pass-through engine")
                .isEqualTo(22);
    }

    /**
     * PASS-direction proof against the real TPC-DS suite.
     *
     * Same protocol as {@link #realTpcHSuitePassesThroughWithExpectedOutput} but for
     * TPC-DS with 99 queries.  TPC-DS expected outputs have substantially different
     * shapes from TPC-H: 1-column string results (q01), 8-column wide rows with mixed
     * bigint/double ratios (q02), negative double values (q05), stddev columns (q10),
     * and 45 queries that legitimately return zero rows on the scale-0.01 dataset.
     * These shapes exercise comparator paths that TPC-H does not reach.
     *
     * The test is split into two assertions:
     *  1. Queries with non-empty expected output must PASS (the comparator correctly
     *     round-trips every typed value shape present in the TPC-DS goldens).
     *  2. Queries with empty expected output (header-only CSV, zero rows) must also
     *     PASS — the pass-through engine returns an empty TableData and the comparator
     *     must accept empty == empty without a spurious row-count error.
     *
     * Oracle provenance: TPC-DS expected outputs were produced by DuckDB 1.2.0 against
     * the scale-0.01 data in test-suites/tpcds/data/, using the TPC-DS standard
     * parameter substitutions for that scale factor (YEAR=2000, STATE=TN, MONTH=11,
     * etc.) via generate_expected.py.  DuckDB is the de-facto reference for result
     * semantics in this suite; see tpcds/README.md for full provenance details.
     */
    @Test
    void realTpcDsSuitePassesThroughWithExpectedOutput(@TempDir Path tempDir) throws Exception {
        String rootDir = System.getProperty("substrait.compliance.rootDir");
        Assumptions.assumeTrue(rootDir != null,
                "System property substrait.compliance.rootDir must be set (check build.gradle)");
        Path tpcdsDir = Path.of(rootDir).resolve("test-suites/tpcds");
        Assumptions.assumeTrue(tpcdsDir.resolve("metadata.yaml").toFile().exists(),
                "TPC-DS metadata.yaml must be present at " + tpcdsDir);

        // Build synthetic YAML: real plan binaries + real expected CSVs, no inputTables.
        // Avoids loading the large TPC-DS source data into the test JVM heap.
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: \"tpcds\"\nversion: \"1.0.0\"\ndescription: \"TPC-DS pass-through\"\ntestCases:\n");

        List<String> queryIds = new ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            queryIds.add(String.format("q%02d", i));
        }

        int added = 0;
        for (String qid : queryIds) {
            Path planBin     = tpcdsDir.resolve("plans/" + qid + ".bin");
            Path expectedCsv = tpcdsDir.resolve("expected/" + qid + ".csv");
            if (!planBin.toFile().exists() || !expectedCsv.toFile().exists()) continue;
            yaml.append("  - id: \"").append(qid).append("\"\n");
            yaml.append("    description: \"").append(qid).append("\"\n");
            yaml.append("    planBinary: \"").append(planBin.toAbsolutePath()).append("\"\n");
            yaml.append("    inputTables: []\n");
            yaml.append("    expectedOutput: \"").append(expectedCsv.toAbsolutePath()).append("\"\n");
            added++;
        }

        Assumptions.assumeTrue(added > 0, "No TPC-DS plan+expected pairs found under " + tpcdsDir);

        Path syntheticYaml = tempDir.resolve("tpcds-passthrough.yaml");
        Files.writeString(syntheticYaml, yaml.toString());

        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(syntheticYaml);

        assertThat(suite.getTestCases())
                .as("All 99 TPC-DS queries must be loaded")
                .hasSize(99);

        // Register each expected output in the pass-through engine.
        PassThroughEngine engine = new PassThroughEngine();
        int withRows  = 0;
        int withEmpty = 0;
        for (TestCase tc : suite.getTestCases()) {
            assertThat(tc.getExpectedOutput())
                    .as("Test case " + tc.getId() + " must have a non-null expected output")
                    .isNotNull();
            engine.register(tc.getPlan(), tc.getExpectedOutput());
            if (tc.getExpectedOutput().getRows().isEmpty()) withEmpty++;
            else withRows++;
        }

        // Verify the 54/45 split is what we expect from generate_expected.py.
        // If this fails it means the golden files changed and the comment needs updating.
        assertThat(withRows)
                .as("Expected 54 non-empty TPC-DS goldens (queries with rows on scale-0.01 data)")
                .isEqualTo(54);
        assertThat(withEmpty)
                .as("Expected 45 empty TPC-DS goldens (queries whose filters match no scale-0.01 rows)")
                .isEqualTo(45);

        ComplianceRunner runner = new ComplianceRunner(engine);
        runner.registerTestSuite(suite);
        ComplianceReport report = runner.runTestSuite("tpcds");

        // Collect failures for a clear error message if something regresses.
        List<String> failures = new ArrayList<>();
        for (TestResult result : report.getTestResults()) {
            if (!result.isPassed()) {
                failures.add(result.getTestId() + " [" + result.getStatus() + "]: " + result.getMessage());
            }
        }

        assertThat(failures)
                .as("Pass-through engine must produce zero non-PASSED results.\n"
                  + "A failure indicates a typed header, convertValue, or comparator bug.\n"
                  + "Non-passed: " + failures)
                .isEmpty();

        assertThat(report.getPassedCount())
                .as("All 99 TPC-DS queries must PASS with pass-through engine")
                .isEqualTo(99);
    }
}
