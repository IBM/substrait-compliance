package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.compliance.loader.YamlTestSuiteLoader;
import io.substrait.proto.Plan;

import java.nio.file.*;
import java.util.*;

/**
 * Shared base for all demo engines.
 *
 * On initialize() loads all 22 TPC-H expected CSVs from the test-suites
 * directory (using a lightweight synthetic YAML with no inputTables to avoid
 * loading the 60k-row lineitem.csv).  Subclasses call expectedFor(plan) to
 * retrieve the matching TableData, then apply any per-engine perturbations
 * before returning it from executePlan().
 */
public abstract class DemoEngineBase implements ComplianceEngine {

    /** plan-bytes hash → expected TableData */
    protected final Map<Integer, TableData> expectedByHash = new HashMap<>();

    /** plan-bytes hash → test case id (e.g. "q01") */
    protected final Map<Integer, String>    idByHash       = new HashMap<>();

    protected final String tpchDir;

    protected DemoEngineBase(String tpchDir) {
        this.tpchDir = tpchDir;
    }

    /** Returns the expected TableData for the given plan, or null. */
    protected final TableData expectedFor(Plan plan) {
        return expectedByHash.get(Arrays.hashCode(plan.toByteArray()));
    }

    /** Returns the test-case ID (e.g. "q01") for the given plan, or null. */
    protected final String idFor(Plan plan) {
        return idByHash.get(Arrays.hashCode(plan.toByteArray()));
    }

    protected final void loadExpectedOutputs() throws ComplianceException {
        Path metaPath = Paths.get(tpchDir, "metadata.yaml");
        if (!metaPath.toFile().exists()) {
            throw new ComplianceException(
                "TPC-H metadata.yaml not found at " + metaPath.toAbsolutePath()
                + " — run the demo from the demo/ directory");
        }
        try {
            Path synth = buildSyntheticYaml();
            YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
            TestSuite suite = loader.load(synth);
            for (TestCase tc : suite.getTestCases()) {
                if (tc.getExpectedOutput() != null) {
                    int h = Arrays.hashCode(tc.getPlan().toByteArray());
                    expectedByHash.put(h, tc.getExpectedOutput());
                    idByHash.put(h, tc.getId());
                }
            }
            Files.deleteIfExists(synth);
        } catch (ComplianceException e) {
            throw e;
        } catch (Exception e) {
            throw new ComplianceException("Failed to load expected outputs", e);
        }
    }

    private Path buildSyntheticYaml() throws Exception {
        Path base = Paths.get(tpchDir).toAbsolutePath();
        StringBuilder sb = new StringBuilder();
        sb.append("name: \"tpch\"\nversion: \"1.0.0\"\ndescription: \"\"\ntestCases:\n");
        String[] ids = {
            "q01","q02","q03","q04","q05","q06","q07","q08","q09","q10",
            "q11","q12","q13","q14","q15","q16","q17","q18","q19","q20",
            "q21","q22"
        };
        for (String id : ids) {
            Path bin = base.resolve("plans/" + id + ".bin");
            Path csv = base.resolve("expected/" + id + ".csv");
            if (!bin.toFile().exists() || !csv.toFile().exists()) continue;
            sb.append("  - id: \"").append(id).append("\"\n");
            sb.append("    description: \"\"\n");
            sb.append("    planBinary: \"").append(bin).append("\"\n");
            sb.append("    inputTables: []\n");
            sb.append("    expectedOutput: \"").append(csv).append("\"\n");
        }
        Path tmp = Files.createTempFile("demo-suite-", ".yaml");
        Files.writeString(tmp, sb.toString());
        return tmp;
    }
}
