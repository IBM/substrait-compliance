package io.substrait.compliance.analytics;

import io.substrait.compliance.FailureCategory;
import java.time.Instant;
import java.util.*;

/**
 * Comprehensive analytics report containing all analysis results.
 */
public class AnalyticsReport {
  private final String engineName;
  private final Instant analysisTimestamp;
  private final int reportCount;
  private final FailurePatternAnalysis failurePatterns;
  private final TrendAnalysis trends;
  private final ComplianceAnalysis compliance;
  private final PerformanceAnalysis performance;
  private final List<String> keyFindings;
  private final List<String> recommendations;

  private AnalyticsReport(Builder builder) {
    this.engineName = builder.engineName;
    this.analysisTimestamp = builder.analysisTimestamp;
    this.reportCount = builder.reportCount;
    this.failurePatterns = builder.failurePatterns;
    this.trends = builder.trends;
    this.compliance = builder.compliance;
    this.performance = builder.performance;
    this.keyFindings = new ArrayList<>(builder.keyFindings);
    this.recommendations = new ArrayList<>(builder.recommendations);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static AnalyticsReport empty() {
    return builder()
        .engineName("Unknown")
        .analysisTimestamp(Instant.now())
        .reportCount(0)
        .failurePatterns(FailurePatternAnalysis.empty())
        .trends(TrendAnalysis.empty())
        .compliance(ComplianceAnalysis.empty())
        .performance(PerformanceAnalysis.empty())
        .build();
  }

  // Getters
  public String getEngineName() { return engineName; }
  public Instant getAnalysisTimestamp() { return analysisTimestamp; }
  public int getReportCount() { return reportCount; }
  public FailurePatternAnalysis getFailurePatterns() { return failurePatterns; }
  public TrendAnalysis getTrends() { return trends; }
  public ComplianceAnalysis getCompliance() { return compliance; }
  public PerformanceAnalysis getPerformance() { return performance; }
  public List<String> getKeyFindings() { return Collections.unmodifiableList(keyFindings); }
  public List<String> getRecommendations() { return Collections.unmodifiableList(recommendations); }

  public static class Builder {
    private String engineName;
    private Instant analysisTimestamp;
    private int reportCount;
    private FailurePatternAnalysis failurePatterns;
    private TrendAnalysis trends;
    private ComplianceAnalysis compliance;
    private PerformanceAnalysis performance;
    private List<String> keyFindings = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();

    public Builder engineName(String engineName) {
      this.engineName = engineName;
      return this;
    }

    public Builder analysisTimestamp(Instant analysisTimestamp) {
      this.analysisTimestamp = analysisTimestamp;
      return this;
    }

    public Builder reportCount(int reportCount) {
      this.reportCount = reportCount;
      return this;
    }

    public Builder failurePatterns(FailurePatternAnalysis failurePatterns) {
      this.failurePatterns = failurePatterns;
      return this;
    }

    public Builder trends(TrendAnalysis trends) {
      this.trends = trends;
      return this;
    }

    public Builder compliance(ComplianceAnalysis compliance) {
      this.compliance = compliance;
      return this;
    }

    public Builder performance(PerformanceAnalysis performance) {
      this.performance = performance;
      return this;
    }

    public Builder keyFindings(List<String> keyFindings) {
      this.keyFindings = new ArrayList<>(keyFindings);
      return this;
    }

    public Builder recommendations(List<String> recommendations) {
      this.recommendations = new ArrayList<>(recommendations);
      return this;
    }

    public AnalyticsReport build() {
      return new AnalyticsReport(this);
    }
  }
}

/**
 * Analysis of failure patterns.
 */
class FailurePatternAnalysis {
  private final Map<String, Integer> recurringFailures;
  private final Map<FailureCategory, Integer> categoryDistribution;
  private final Map<String, List<String>> failureClusters;
  private final List<String> problematicTests;

  private FailurePatternAnalysis(Builder builder) {
    this.recurringFailures = new HashMap<>(builder.recurringFailures);
    this.categoryDistribution = new HashMap<>(builder.categoryDistribution);
    this.failureClusters = new HashMap<>(builder.failureClusters);
    this.problematicTests = new ArrayList<>(builder.problematicTests);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static FailurePatternAnalysis empty() {
    return builder().build();
  }

  public Map<String, Integer> getRecurringFailures() { return Collections.unmodifiableMap(recurringFailures); }
  public Map<FailureCategory, Integer> getCategoryDistribution() { return Collections.unmodifiableMap(categoryDistribution); }
  public Map<String, List<String>> getFailureClusters() { return Collections.unmodifiableMap(failureClusters); }
  public List<String> getProblematicTests() { return Collections.unmodifiableList(problematicTests); }

  static class Builder {
    private Map<String, Integer> recurringFailures = new HashMap<>();
    private Map<FailureCategory, Integer> categoryDistribution = new HashMap<>();
    private Map<String, List<String>> failureClusters = new HashMap<>();
    private List<String> problematicTests = new ArrayList<>();

    public Builder recurringFailures(Map<String, Integer> recurringFailures) {
      this.recurringFailures = new HashMap<>(recurringFailures);
      return this;
    }

    public Builder categoryDistribution(Map<FailureCategory, Integer> categoryDistribution) {
      this.categoryDistribution = new HashMap<>(categoryDistribution);
      return this;
    }

    public Builder failureClusters(Map<String, List<String>> failureClusters) {
      this.failureClusters = new HashMap<>(failureClusters);
      return this;
    }

    public Builder problematicTests(List<String> problematicTests) {
      this.problematicTests = new ArrayList<>(problematicTests);
      return this;
    }

    public FailurePatternAnalysis build() {
      return new FailurePatternAnalysis(this);
    }
  }
}

/**
 * Analysis of trends over time.
 */
class TrendAnalysis {
  private final List<Double> passRates;
  private final String passRateTrend;
  private final List<Long> avgExecutionTimes;
  private final String performanceTrend;
  private final Map<FailureCategory, List<Integer>> categoryTrends;

  private TrendAnalysis(Builder builder) {
    this.passRates = new ArrayList<>(builder.passRates);
    this.passRateTrend = builder.passRateTrend;
    this.avgExecutionTimes = new ArrayList<>(builder.avgExecutionTimes);
    this.performanceTrend = builder.performanceTrend;
    this.categoryTrends = new HashMap<>(builder.categoryTrends);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static TrendAnalysis empty() {
    return builder().passRateTrend("STABLE").performanceTrend("STABLE").build();
  }

  public List<Double> getPassRates() { return Collections.unmodifiableList(passRates); }
  public String getPassRateTrend() { return passRateTrend; }
  public List<Long> getAvgExecutionTimes() { return Collections.unmodifiableList(avgExecutionTimes); }
  public String getPerformanceTrend() { return performanceTrend; }
  public Map<FailureCategory, List<Integer>> getCategoryTrends() { return Collections.unmodifiableMap(categoryTrends); }

  static class Builder {
    private List<Double> passRates = new ArrayList<>();
    private String passRateTrend;
    private List<Long> avgExecutionTimes = new ArrayList<>();
    private String performanceTrend;
    private Map<FailureCategory, List<Integer>> categoryTrends = new HashMap<>();

    public Builder passRates(List<Double> passRates) {
      this.passRates = new ArrayList<>(passRates);
      return this;
    }

    public Builder passRateTrend(String passRateTrend) {
      this.passRateTrend = passRateTrend;
      return this;
    }

    public Builder avgExecutionTimes(List<Long> avgExecutionTimes) {
      this.avgExecutionTimes = new ArrayList<>(avgExecutionTimes);
      return this;
    }

    public Builder performanceTrend(String performanceTrend) {
      this.performanceTrend = performanceTrend;
      return this;
    }

    public Builder categoryTrends(Map<FailureCategory, List<Integer>> categoryTrends) {
      this.categoryTrends = new HashMap<>(categoryTrends);
      return this;
    }

    public TrendAnalysis build() {
      return new TrendAnalysis(this);
    }
  }
}

/**
 * Analysis of compliance scores and gaps.
 */
class ComplianceAnalysis {
  private final double currentCompliance;
  private final double initialCompliance;
  private final double improvement;
  private final Map<String, Double> complianceBySuite;
  private final List<String> complianceGaps;

  private ComplianceAnalysis(Builder builder) {
    this.currentCompliance = builder.currentCompliance;
    this.initialCompliance = builder.initialCompliance;
    this.improvement = builder.improvement;
    this.complianceBySuite = new HashMap<>(builder.complianceBySuite);
    this.complianceGaps = new ArrayList<>(builder.complianceGaps);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ComplianceAnalysis empty() {
    return builder().build();
  }

  public double getCurrentCompliance() { return currentCompliance; }
  public double getInitialCompliance() { return initialCompliance; }
  public double getImprovement() { return improvement; }
  public Map<String, Double> getComplianceBySuite() { return Collections.unmodifiableMap(complianceBySuite); }
  public List<String> getComplianceGaps() { return Collections.unmodifiableList(complianceGaps); }

  static class Builder {
    private double currentCompliance;
    private double initialCompliance;
    private double improvement;
    private Map<String, Double> complianceBySuite = new HashMap<>();
    private List<String> complianceGaps = new ArrayList<>();

    public Builder currentCompliance(double currentCompliance) {
      this.currentCompliance = currentCompliance;
      return this;
    }

    public Builder initialCompliance(double initialCompliance) {
      this.initialCompliance = initialCompliance;
      return this;
    }

    public Builder improvement(double improvement) {
      this.improvement = improvement;
      return this;
    }

    public Builder complianceBySuite(Map<String, Double> complianceBySuite) {
      this.complianceBySuite = new HashMap<>(complianceBySuite);
      return this;
    }

    public Builder complianceGaps(List<String> complianceGaps) {
      this.complianceGaps = new ArrayList<>(complianceGaps);
      return this;
    }

    public ComplianceAnalysis build() {
      return new ComplianceAnalysis(this);
    }
  }
}

/**
 * Analysis of performance metrics.
 */
class PerformanceAnalysis {
  private final double averageExecutionTime;
  private final List<String> slowTests;
  private final boolean hasRegression;

  private PerformanceAnalysis(Builder builder) {
    this.averageExecutionTime = builder.averageExecutionTime;
    this.slowTests = new ArrayList<>(builder.slowTests);
    this.hasRegression = builder.hasRegression;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PerformanceAnalysis empty() {
    return builder().build();
  }

  public double getAverageExecutionTime() { return averageExecutionTime; }
  public List<String> getSlowTests() { return Collections.unmodifiableList(slowTests); }
  public boolean isHasRegression() { return hasRegression; }

  static class Builder {
    private double averageExecutionTime;
    private List<String> slowTests = new ArrayList<>();
    private boolean hasRegression;

    public Builder averageExecutionTime(double averageExecutionTime) {
      this.averageExecutionTime = averageExecutionTime;
      return this;
    }

    public Builder slowTests(List<String> slowTests) {
      this.slowTests = new ArrayList<>(slowTests);
      return this;
    }

    public Builder hasRegression(boolean hasRegression) {
      this.hasRegression = hasRegression;
      return this;
    }

    public PerformanceAnalysis build() {
      return new PerformanceAnalysis(this);
    }
  }
}

