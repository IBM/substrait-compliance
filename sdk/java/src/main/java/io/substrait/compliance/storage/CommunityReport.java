package io.substrait.compliance.storage;

import io.substrait.compliance.FailureCategory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Anonymized public report for community consumption.
 * Contains aggregated statistics without sensitive details.
 */
public class CommunityReport {
  private final String engineName;
  private final String engineVersion;
  private final Instant reportTimestamp;
  private final TestSummary summary;
  private final ComplianceScore complianceScore;
  private final Map<FailureCategory, Integer> failureCategories;
  private final PerformanceBenchmarks benchmarks;
  private final List<KnownIssue> knownIssues;

  private CommunityReport(Builder builder) {
    this.engineName = Objects.requireNonNull(builder.engineName, "engineName cannot be null");
    this.engineVersion = Objects.requireNonNull(builder.engineVersion, "engineVersion cannot be null");
    this.reportTimestamp = Objects.requireNonNull(builder.reportTimestamp, "reportTimestamp cannot be null");
    this.summary = Objects.requireNonNull(builder.summary, "summary cannot be null");
    this.complianceScore = Objects.requireNonNull(builder.complianceScore, "complianceScore cannot be null");
    this.failureCategories = new HashMap<>(builder.failureCategories);
    this.benchmarks = builder.benchmarks;
    this.knownIssues = new ArrayList<>(builder.knownIssues);
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public String getEngineName() { return engineName; }
  public String getEngineVersion() { return engineVersion; }
  public Instant getReportTimestamp() { return reportTimestamp; }
  public TestSummary getSummary() { return summary; }
  public ComplianceScore getComplianceScore() { return complianceScore; }
  public Map<FailureCategory, Integer> getFailureCategories() { 
    return Collections.unmodifiableMap(failureCategories); 
  }
  public PerformanceBenchmarks getBenchmarks() { return benchmarks; }
  public List<KnownIssue> getKnownIssues() { return Collections.unmodifiableList(knownIssues); }

  /**
   * Basic test summary statistics.
   */
  public static class TestSummary {
    private final int totalTests;
    private final int passed;
    private final int failed;
    private final int skipped;
    private final double passRate;

    private TestSummary(Builder builder) {
      this.totalTests = builder.totalTests;
      this.passed = builder.passed;
      this.failed = builder.failed;
      this.skipped = builder.skipped;
      this.passRate = totalTests > 0 ? (double) passed / totalTests : 0.0;
    }

    public static Builder builder() {
      return new Builder();
    }

    public int getTotalTests() { return totalTests; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public int getSkipped() { return skipped; }
    public double getPassRate() { return passRate; }

    public static class Builder {
      private int totalTests;
      private int passed;
      private int failed;
      private int skipped;

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

      public TestSummary build() {
        return new TestSummary(this);
      }
    }
  }

  /**
   * Compliance score breakdown.
   */
  public static class ComplianceScore {
    private final double overall;
    private final Map<String, Double> byCategory;
    private final Map<String, Double> byTestSuite;

    private ComplianceScore(Builder builder) {
      this.overall = builder.overall;
      this.byCategory = new HashMap<>(builder.byCategory);
      this.byTestSuite = new HashMap<>(builder.byTestSuite);
    }

    public static Builder builder() {
      return new Builder();
    }

    public double getOverall() { return overall; }
    public Map<String, Double> getByCategory() { return Collections.unmodifiableMap(byCategory); }
    public Map<String, Double> getByTestSuite() { return Collections.unmodifiableMap(byTestSuite); }

    public static class Builder {
      private double overall;
      private Map<String, Double> byCategory = new HashMap<>();
      private Map<String, Double> byTestSuite = new HashMap<>();

      public Builder overall(double overall) {
        this.overall = overall;
        return this;
      }

      public Builder byCategory(Map<String, Double> byCategory) {
        this.byCategory = new HashMap<>(byCategory);
        return this;
      }

      public Builder addCategoryScore(String category, double score) {
        this.byCategory.put(category, score);
        return this;
      }

      public Builder byTestSuite(Map<String, Double> byTestSuite) {
        this.byTestSuite = new HashMap<>(byTestSuite);
        return this;
      }

      public Builder addTestSuiteScore(String suite, double score) {
        this.byTestSuite.put(suite, score);
        return this;
      }

      public ComplianceScore build() {
        return new ComplianceScore(this);
      }
    }
  }

  /**
   * Aggregated performance benchmarks (no sensitive details).
   */
  public static class PerformanceBenchmarks {
    private final double averageExecutionTimeMs;
    private final double medianExecutionTimeMs;
    private final double p95ExecutionTimeMs;
    private final double p99ExecutionTimeMs;
    private final Map<String, Double> byTestSuite;

    private PerformanceBenchmarks(Builder builder) {
      this.averageExecutionTimeMs = builder.averageExecutionTimeMs;
      this.medianExecutionTimeMs = builder.medianExecutionTimeMs;
      this.p95ExecutionTimeMs = builder.p95ExecutionTimeMs;
      this.p99ExecutionTimeMs = builder.p99ExecutionTimeMs;
      this.byTestSuite = new HashMap<>(builder.byTestSuite);
    }

    public static Builder builder() {
      return new Builder();
    }

    public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
    public double getMedianExecutionTimeMs() { return medianExecutionTimeMs; }
    public double getP95ExecutionTimeMs() { return p95ExecutionTimeMs; }
    public double getP99ExecutionTimeMs() { return p99ExecutionTimeMs; }
    public Map<String, Double> getByTestSuite() { return Collections.unmodifiableMap(byTestSuite); }

    public static class Builder {
      private double averageExecutionTimeMs;
      private double medianExecutionTimeMs;
      private double p95ExecutionTimeMs;
      private double p99ExecutionTimeMs;
      private Map<String, Double> byTestSuite = new HashMap<>();

      public Builder averageExecutionTimeMs(double averageExecutionTimeMs) {
        this.averageExecutionTimeMs = averageExecutionTimeMs;
        return this;
      }

      public Builder medianExecutionTimeMs(double medianExecutionTimeMs) {
        this.medianExecutionTimeMs = medianExecutionTimeMs;
        return this;
      }

      public Builder p95ExecutionTimeMs(double p95ExecutionTimeMs) {
        this.p95ExecutionTimeMs = p95ExecutionTimeMs;
        return this;
      }

      public Builder p99ExecutionTimeMs(double p99ExecutionTimeMs) {
        this.p99ExecutionTimeMs = p99ExecutionTimeMs;
        return this;
      }

      public Builder byTestSuite(Map<String, Double> byTestSuite) {
        this.byTestSuite = new HashMap<>(byTestSuite);
        return this;
      }

      public Builder addTestSuiteBenchmark(String suite, double avgTimeMs) {
        this.byTestSuite.put(suite, avgTimeMs);
        return this;
      }

      public PerformanceBenchmarks build() {
        return new PerformanceBenchmarks(this);
      }
    }
  }

  /**
   * Known issue reference (no sensitive details).
   */
  public static class KnownIssue {
    private final String issueId;
    private final String category;
    private final String description;
    private final String status;
    private final int affectedTests;

    private KnownIssue(Builder builder) {
      this.issueId = Objects.requireNonNull(builder.issueId, "issueId cannot be null");
      this.category = builder.category;
      this.description = builder.description;
      this.status = builder.status;
      this.affectedTests = builder.affectedTests;
    }

    public static Builder builder() {
      return new Builder();
    }

    public String getIssueId() { return issueId; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public int getAffectedTests() { return affectedTests; }

    public static class Builder {
      private String issueId;
      private String category;
      private String description;
      private String status;
      private int affectedTests;

      public Builder issueId(String issueId) {
        this.issueId = issueId;
        return this;
      }

      public Builder category(String category) {
        this.category = category;
        return this;
      }

      public Builder description(String description) {
        this.description = description;
        return this;
      }

      public Builder status(String status) {
        this.status = status;
        return this;
      }

      public Builder affectedTests(int affectedTests) {
        this.affectedTests = affectedTests;
        return this;
      }

      public KnownIssue build() {
        return new KnownIssue(this);
      }
    }
  }

  public static class Builder {
    private String engineName;
    private String engineVersion;
    private Instant reportTimestamp;
    private TestSummary summary;
    private ComplianceScore complianceScore;
    private Map<FailureCategory, Integer> failureCategories = new HashMap<>();
    private PerformanceBenchmarks benchmarks;
    private List<KnownIssue> knownIssues = new ArrayList<>();

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

    public Builder summary(TestSummary summary) {
      this.summary = summary;
      return this;
    }

    public Builder complianceScore(ComplianceScore complianceScore) {
      this.complianceScore = complianceScore;
      return this;
    }

    public Builder failureCategories(Map<FailureCategory, Integer> failureCategories) {
      this.failureCategories = new HashMap<>(failureCategories);
      return this;
    }

    public Builder addFailureCategory(FailureCategory category, int count) {
      this.failureCategories.put(category, count);
      return this;
    }

    public Builder benchmarks(PerformanceBenchmarks benchmarks) {
      this.benchmarks = benchmarks;
      return this;
    }

    public Builder knownIssues(List<KnownIssue> knownIssues) {
      this.knownIssues = new ArrayList<>(knownIssues);
      return this;
    }

    public Builder addKnownIssue(KnownIssue issue) {
      this.knownIssues.add(issue);
      return this;
    }

    public CommunityReport build() {
      return new CommunityReport(this);
    }
  }
}

