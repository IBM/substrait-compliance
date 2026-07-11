package io.substrait.compliance.storage;

import io.substrait.compliance.FailureCategory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregated test report for a specific engine.
 * Contains all test execution records and summary statistics.
 */
public class EngineTestReport {
  private final String engineName;
  private final String engineVersion;
  private final Instant reportTimestamp;
  private final List<TestExecutionRecord> executionRecords;
  private final TestSummary summary;
  private final Map<String, Object> metadata;

  private EngineTestReport(Builder builder) {
    this.engineName = Objects.requireNonNull(builder.engineName, "engineName cannot be null");
    this.engineVersion = Objects.requireNonNull(builder.engineVersion, "engineVersion cannot be null");
    this.reportTimestamp = Objects.requireNonNull(builder.reportTimestamp, "reportTimestamp cannot be null");
    this.executionRecords = new ArrayList<>(builder.executionRecords);
    this.summary = builder.summary != null ? builder.summary : TestSummary.fromRecords(executionRecords);
    this.metadata = new HashMap<>(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public String getEngineName() { return engineName; }
  public String getEngineVersion() { return engineVersion; }
  public Instant getReportTimestamp() { return reportTimestamp; }
  public List<TestExecutionRecord> getExecutionRecords() { return Collections.unmodifiableList(executionRecords); }
  public TestSummary getSummary() { return summary; }
  public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }

  /**
   * Get all failed test records.
   */
  public List<TestExecutionRecord> getFailures() {
    return executionRecords.stream()
        .filter(TestExecutionRecord::isFailed)
        .collect(Collectors.toList());
  }

  /**
   * Get all passed test records.
   */
  public List<TestExecutionRecord> getPasses() {
    return executionRecords.stream()
        .filter(TestExecutionRecord::isPassed)
        .collect(Collectors.toList());
  }

  /**
   * Get failures by category.
   */
  public Map<FailureCategory, List<TestExecutionRecord>> getFailuresByCategory() {
    return getFailures().stream()
        .filter(r -> r.getDiagnostics().getFailureCategory() != null)
        .collect(Collectors.groupingBy(
            r -> r.getDiagnostics().getFailureCategory()
        ));
  }

  /**
   * Summary statistics for test execution.
   */
  public static class TestSummary {
    private final int totalTests;
    private final int passed;
    private final int failed;
    private final int skipped;
    private final double passRate;
    private final long totalExecutionTimeMs;
    private final Map<FailureCategory, Integer> failureDistribution;

    private TestSummary(Builder builder) {
      this.totalTests = builder.totalTests;
      this.passed = builder.passed;
      this.failed = builder.failed;
      this.skipped = builder.skipped;
      this.passRate = totalTests > 0 ? (double) passed / totalTests : 0.0;
      this.totalExecutionTimeMs = builder.totalExecutionTimeMs;
      this.failureDistribution = new HashMap<>(builder.failureDistribution);
    }

    public static Builder builder() {
      return new Builder();
    }

    /**
     * Create summary from execution records.
     */
    public static TestSummary fromRecords(List<TestExecutionRecord> records) {
      Builder builder = builder();
      
      builder.totalTests(records.size());
      
      int passCount = 0;
      int failCount = 0;
      long totalTime = 0;
      Map<FailureCategory, Integer> distribution = new HashMap<>();
      
      for (TestExecutionRecord record : records) {
        if (record.isPassed()) {
          passCount++;
        } else {
          failCount++;
          FailureCategory category = record.getDiagnostics().getFailureCategory();
          if (category != null) {
            distribution.merge(category, 1, Integer::sum);
          }
        }
        totalTime += record.getMetrics().getExecutionTimeMs();
      }
      
      builder.passed(passCount);
      builder.failed(failCount);
      builder.totalExecutionTimeMs(totalTime);
      builder.failureDistribution(distribution);
      
      return builder.build();
    }

    // Getters
    public int getTotalTests() { return totalTests; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public int getSkipped() { return skipped; }
    public double getPassRate() { return passRate; }
    public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    public Map<FailureCategory, Integer> getFailureDistribution() { 
      return Collections.unmodifiableMap(failureDistribution); 
    }

    public static class Builder {
      private int totalTests;
      private int passed;
      private int failed;
      private int skipped;
      private long totalExecutionTimeMs;
      private Map<FailureCategory, Integer> failureDistribution = new HashMap<>();

      public Builder totalTests(int totalTests) {
        this.totalTests = totalTests;
        return this;
      }

      public Builder passed(int passed) {
        this.passed = passed;
        return this;
      }

      public Builder failed(int failed) {
        this.failed = failed;
        return this;
      }

      public Builder skipped(int skipped) {
        this.skipped = skipped;
        return this;
      }

      public Builder totalExecutionTimeMs(long totalExecutionTimeMs) {
        this.totalExecutionTimeMs = totalExecutionTimeMs;
        return this;
      }

      public Builder failureDistribution(Map<FailureCategory, Integer> failureDistribution) {
        this.failureDistribution = new HashMap<>(failureDistribution);
        return this;
      }

      public TestSummary build() {
        return new TestSummary(this);
      }
    }
  }

  public static class Builder {
    private String engineName;
    private String engineVersion;
    private Instant reportTimestamp;
    private List<TestExecutionRecord> executionRecords = new ArrayList<>();
    private TestSummary summary;
    private Map<String, Object> metadata = new HashMap<>();

    public Builder engineName(String engineName) {
      this.engineName = engineName;
      return this;
    }

    public Builder engineVersion(String engineVersion) {
      this.engineVersion = engineVersion;
      return this;
    }

    public Builder reportTimestamp(Instant reportTimestamp) {
      this.reportTimestamp = reportTimestamp;
      return this;
    }

    public Builder executionRecords(List<TestExecutionRecord> executionRecords) {
      this.executionRecords = new ArrayList<>(executionRecords);
      return this;
    }

    public Builder addExecutionRecord(TestExecutionRecord record) {
      this.executionRecords.add(record);
      return this;
    }

    public Builder summary(TestSummary summary) {
      this.summary = summary;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = new HashMap<>(metadata);
      return this;
    }

    public Builder addMetadata(String key, Object value) {
      this.metadata.put(key, value);
      return this;
    }

    public EngineTestReport build() {
      return new EngineTestReport(this);
    }
  }
}

