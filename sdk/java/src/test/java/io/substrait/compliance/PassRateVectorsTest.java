package io.substrait.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.substrait.compliance.storage.EngineTestReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv). */
class PassRateVectorsTest {

  private static List<int[]> countsAndExpected(List<double[]> out) throws IOException {
    Path csv = Paths.get("..", "conformance", "pass-rate-vectors.csv");
    List<String> lines = Files.readAllLines(csv).stream()
        .map(String::trim)
        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
        .skip(1)
        .collect(Collectors.toList());
    List<int[]> counts = new java.util.ArrayList<>();
    for (String line : lines) {
      String[] f = line.split(",");
      assertThat(f).hasSize(6);
      if (Integer.parseInt(f[3]) > 0 || Integer.parseInt(f[4]) > 0) {
        continue; // no ERROR or UNSUPPORTED status in the Java SDK
      }
      counts.add(new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])});
      out.add(new double[] {Double.parseDouble(f[5])});
    }
    return counts;
  }

  @Test
  void complianceScoreMatchesTheSharedVectors() throws IOException {
    List<double[]> expected = new java.util.ArrayList<>();
    List<int[]> counts = countsAndExpected(expected);
    assertThat(counts).isNotEmpty();
    for (int v = 0; v < counts.size(); v++) {
      int[] c = counts.get(v);
      ComplianceReport report = new ComplianceReport(new EngineInfo("E", "1", "0.107.0"), "suite");
      int id = 0;
      for (int i = 0; i < c[0]; i++) report.addTestResult(TestResult.passed("t" + id++, 1));
      for (int i = 0; i < c[1]; i++) report.addTestResult(TestResult.failed("t" + id++, "x", 1));
      for (int i = 0; i < c[2]; i++) report.addTestResult(TestResult.skipped("t" + id++, "x"));
      assertThat(report.getComplianceScore())
          .as("vector %s", java.util.Arrays.toString(c))
          .isCloseTo(expected.get(v)[0], within(1e-9));
    }
  }

  @Test
  void storedSummariesUseTheSameRuleAsAFraction() throws IOException {
    List<double[]> expected = new java.util.ArrayList<>();
    List<int[]> counts = countsAndExpected(expected);
    for (int v = 0; v < counts.size(); v++) {
      int[] c = counts.get(v);
      EngineTestReport.TestSummary summary = EngineTestReport.TestSummary.builder()
          .totalTests(c[0] + c[1] + c[2]).passed(c[0]).failed(c[1]).skipped(c[2]).build();
      assertThat(summary.getPassRate() * 100.0)
          .as("vector %s", java.util.Arrays.toString(c))
          .isCloseTo(expected.get(v)[0], within(1e-9));
    }
  }
}
