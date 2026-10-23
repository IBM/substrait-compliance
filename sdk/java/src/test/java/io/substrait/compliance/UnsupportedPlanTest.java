package io.substrait.compliance;

import io.substrait.compliance.loader.YamlTestSuiteLoader;
import io.substrait.proto.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * A plan the engine declares unsupported counts as not passed: declaring plans unsupported
 * must not raise a score (sdk/conformance/pass-rate-vectors.csv).
 */
class UnsupportedPlanTest {

    /** Supports nothing; would pass every plan it were asked to run. */
    static class SupportsNothingEngine implements ComplianceEngine {
        @Override public EngineInfo getEngineInfo() { return new EngineInfo("None", "1.0", "0.20.0"); }
        @Override public EngineCapabilities getCapabilities() { return EngineCapabilities.builder().build(); }
        @Override public PlanValidationResult validatePlan(Plan plan) {
            return PlanValidationResult.unsupported(Collections.singletonList("not implemented"));
        }
        @Override public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) {
            throw new AssertionError("an unsupported plan must not be executed");
        }
        @Override public void initialize() {}
        @Override public void cleanup() {}
    }

    @Test
    void unsupportedPlanIsAFailureNotASkip(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("q00.bin"), new byte[]{});
        Files.writeString(dir.resolve("expected.csv"), "a:integer\n1\n");
        Files.writeString(dir.resolve("metadata.yaml"),
            "name: \"unsupported\"\nversion: \"1.0.0\"\ndescription: \"\"\ntestCases:\n"
          + "  - id: \"q00\"\n    planBinary: \"q00.bin\"\n    inputTables: []\n"
          + "    expectedOutput: \"expected.csv\"\n");
        TestSuite suite = new YamlTestSuiteLoader().load(dir.resolve("metadata.yaml"));

        ComplianceReport report = new ComplianceRunner(new SupportsNothingEngine()).runTestSuite(suite);

        assertThat(report.getFailedCount()).isEqualTo(1);
        assertThat(report.getSkippedCount()).isZero();
        assertThat(report.getComplianceScore()).isZero();
        assertThat(report.getTestResults().get(0).getMessage()).startsWith("Plan not supported");
    }
}
