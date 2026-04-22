package io.substrait.compliance.analytics;

import io.substrait.compliance.FailureCategory;
import io.substrait.compliance.storage.EngineTestReport;
import io.substrait.compliance.storage.TestExecutionRecord;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main analytics engine that orchestrates all analysis components.
 * Analyzes test results to identify patterns, trends, and insights.
 */
public class AnalyticsEngine {

  /**
   * Analyze a collection of test reports and generate comprehensive insights.
   */
  public AnalyticsReport analyze(List<EngineTestReport> reports) {
    if (reports == null || reports.isEmpty()) {
      return AnalyticsReport.empty();
    }

    // Sort reports by timestamp
    List<EngineTestReport> sortedReports = reports.stream()
        .sorted(Comparator.comparing(EngineTestReport::getReportTimestamp))
        .collect(Collectors.toList());

    // Perform different analyses
    FailurePatternAnalysis failurePatterns = analyzeFailurePatterns(sortedReports);
    TrendAnalysis trends = analyzeTrends(sortedReports);
    ComplianceAnalysis compliance = analyzeCompliance(sortedReports);
    PerformanceAnalysis performance = analyzePerformance(sortedReports);

    // Generate key findings and recommendations
    List<String> keyFindings = generateKeyFindings(failurePatterns, trends, compliance, performance);
    List<String> recommendations = generateRecommendations(failurePatterns, trends, compliance, performance);

    return AnalyticsReport.builder()
        .engineName(sortedReports.get(0).getEngineName())
        .analysisTimestamp(Instant.now())
        .reportCount(sortedReports.size())
        .failurePatterns(failurePatterns)
        .trends(trends)
        .compliance(compliance)
        .performance(performance)
        .keyFindings(keyFindings)
        .recommendations(recommendations)
        .build();
  }

  private FailurePatternAnalysis analyzeFailurePatterns(List<EngineTestReport> reports) {
    Map<String, Integer> recurringFailures = new HashMap<>();
    Map<FailureCategory, Integer> categoryDistribution = new HashMap<>();
    Map<String, List<String>> failureClusters = new HashMap<>();
    
    // Analyze all reports
    for (EngineTestReport report : reports) {
      for (TestExecutionRecord record : report.getFailures()) {
        // Track recurring failures
        String testKey = record.getTestSuite() + ":" + record.getTestName();
        recurringFailures.merge(testKey, 1, Integer::sum);
        
        // Track category distribution
        FailureCategory category = record.getDiagnostics().getFailureCategory();
        if (category != null) {
          categoryDistribution.merge(category, 1, Integer::sum);
        }
        
        // Track failure clusters by test suite
        failureClusters.computeIfAbsent(record.getTestSuite(), k -> new ArrayList<>())
            .add(record.getTestName());
      }
    }
    
    // Identify most problematic tests (failing in multiple reports)
    List<String> problematicTests = recurringFailures.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(10)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    
    return FailurePatternAnalysis.builder()
        .recurringFailures(recurringFailures)
        .categoryDistribution(categoryDistribution)
        .failureClusters(failureClusters)
        .problematicTests(problematicTests)
        .build();
  }

  private TrendAnalysis analyzeTrends(List<EngineTestReport> reports) {
    List<Double> passRates = new ArrayList<>();
    List<Long> avgExecutionTimes = new ArrayList<>();
    Map<FailureCategory, List<Integer>> categoryTrends = new HashMap<>();
    
    for (EngineTestReport report : reports) {
      // Pass rate trend
      passRates.add(report.getSummary().getPassRate());
      
      // Execution time trend
      long avgTime = report.getSummary().getTotalExecutionTimeMs() / 
          Math.max(1, report.getSummary().getTotalTests());
      avgExecutionTimes.add(avgTime);
      
      // Category trends
      report.getSummary().getFailureDistribution().forEach((category, count) -> {
        categoryTrends.computeIfAbsent(category, k -> new ArrayList<>()).add(count);
      });
    }
    
    // Calculate trend direction
    String passRateTrend = calculateTrendDirection(passRates);
    String performanceTrend = calculateTrendDirection(
        avgExecutionTimes.stream().map(Long::doubleValue).collect(Collectors.toList())
    );
    
    return TrendAnalysis.builder()
        .passRates(passRates)
        .passRateTrend(passRateTrend)
        .avgExecutionTimes(avgExecutionTimes)
        .performanceTrend(performanceTrend)
        .categoryTrends(categoryTrends)
        .build();
  }

  private ComplianceAnalysis analyzeCompliance(List<EngineTestReport> reports) {
    EngineTestReport latestReport = reports.get(reports.size() - 1);
    
    // Calculate current compliance
    double currentCompliance = latestReport.getSummary().getPassRate();
    
    // Calculate compliance by test suite
    Map<String, Double> complianceBySuite = latestReport.getExecutionRecords().stream()
        .collect(Collectors.groupingBy(
            TestExecutionRecord::getTestSuite,
            Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                  long passed = list.stream().filter(TestExecutionRecord::isPassed).count();
                  return list.isEmpty() ? 0.0 : (double) passed / list.size() * 100.0;
                }
            )
        ));
    
    // Identify gaps (suites with low compliance)
    List<String> complianceGaps = complianceBySuite.entrySet().stream()
        .filter(e -> e.getValue() < 80.0)
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    
    // Calculate improvement from first to last report
    double initialCompliance = reports.get(0).getSummary().getPassRate();
    double improvement = currentCompliance - initialCompliance;
    
    return ComplianceAnalysis.builder()
        .currentCompliance(currentCompliance)
        .initialCompliance(initialCompliance)
        .improvement(improvement)
        .complianceBySuite(complianceBySuite)
        .complianceGaps(complianceGaps)
        .build();
  }

  private PerformanceAnalysis analyzePerformance(List<EngineTestReport> reports) {
    EngineTestReport latestReport = reports.get(reports.size() - 1);
    
    // Calculate current performance metrics
    List<Long> executionTimes = latestReport.getExecutionRecords().stream()
        .map(r -> r.getMetrics().getExecutionTimeMs())
        .filter(t -> t > 0)
        .sorted()
        .collect(Collectors.toList());
    
    double avgTime = executionTimes.isEmpty() ? 0.0 : 
        executionTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
    
    // Identify slow tests (top 10% slowest)
    int slowThreshold = (int) (executionTimes.size() * 0.9);
    List<String> slowTests = new ArrayList<>();
    if (!executionTimes.isEmpty() && slowThreshold < executionTimes.size()) {
      long slowTimeThreshold = executionTimes.get(slowThreshold);
      slowTests = latestReport.getExecutionRecords().stream()
          .filter(r -> r.getMetrics().getExecutionTimeMs() >= slowTimeThreshold)
          .map(r -> r.getTestSuite() + ":" + r.getTestName())
          .limit(10)
          .collect(Collectors.toList());
    }
    
    // Detect performance regression
    boolean hasRegression = false;
    if (reports.size() > 1) {
      EngineTestReport previousReport = reports.get(reports.size() - 2);
      long prevAvgTime = previousReport.getSummary().getTotalExecutionTimeMs() / 
          Math.max(1, previousReport.getSummary().getTotalTests());
      long currentAvgTime = latestReport.getSummary().getTotalExecutionTimeMs() / 
          Math.max(1, latestReport.getSummary().getTotalTests());
      hasRegression = currentAvgTime > prevAvgTime * 1.2; // 20% slower
    }
    
    return PerformanceAnalysis.builder()
        .averageExecutionTime(avgTime)
        .slowTests(slowTests)
        .hasRegression(hasRegression)
        .build();
  }

  private String calculateTrendDirection(List<Double> values) {
    if (values.size() < 2) {
      return "STABLE";
    }
    
    // Simple linear regression to determine trend
    double firstHalf = values.subList(0, values.size() / 2).stream()
        .mapToDouble(Double::doubleValue).average().orElse(0.0);
    double secondHalf = values.subList(values.size() / 2, values.size()).stream()
        .mapToDouble(Double::doubleValue).average().orElse(0.0);
    
    double change = secondHalf - firstHalf;
    if (Math.abs(change) < 1.0) {
      return "STABLE";
    } else if (change > 0) {
      return "IMPROVING";
    } else {
      return "DECLINING";
    }
  }

  private List<String> generateKeyFindings(
      FailurePatternAnalysis patterns,
      TrendAnalysis trends,
      ComplianceAnalysis compliance,
      PerformanceAnalysis performance) {
    
    List<String> findings = new ArrayList<>();
    
    // Compliance findings
    findings.add(String.format("Current compliance: %.1f%%", compliance.getCurrentCompliance()));
    if (compliance.getImprovement() > 0) {
      findings.add(String.format("Compliance improved by %.1f%% since first report", 
          compliance.getImprovement()));
    } else if (compliance.getImprovement() < 0) {
      findings.add(String.format("Compliance declined by %.1f%% since first report", 
          Math.abs(compliance.getImprovement())));
    }
    
    // Pattern findings
    if (!patterns.getProblematicTests().isEmpty()) {
      findings.add(String.format("Found %d recurring failures across reports", 
          patterns.getProblematicTests().size()));
    }
    
    // Trend findings
    findings.add(String.format("Pass rate trend: %s", trends.getPassRateTrend()));
    findings.add(String.format("Performance trend: %s", trends.getPerformanceTrend()));
    
    // Performance findings
    if (performance.isHasRegression()) {
      findings.add("Performance regression detected in latest report");
    }
    
    return findings;
  }

  private List<String> generateRecommendations(
      FailurePatternAnalysis patterns,
      TrendAnalysis trends,
      ComplianceAnalysis compliance,
      PerformanceAnalysis performance) {
    
    List<String> recommendations = new ArrayList<>();
    
    // Compliance recommendations
    if (!compliance.getComplianceGaps().isEmpty()) {
      recommendations.add("Focus on improving compliance in: " + 
          String.join(", ", compliance.getComplianceGaps()));
    }
    
    // Pattern recommendations
    if (!patterns.getProblematicTests().isEmpty()) {
      recommendations.add("Investigate recurring failures: " + 
          String.join(", ", patterns.getProblematicTests().subList(0, 
              Math.min(3, patterns.getProblematicTests().size()))));
    }
    
    // Trend recommendations
    if ("DECLINING".equals(trends.getPassRateTrend())) {
      recommendations.add("Pass rate is declining - review recent changes");
    }
    
    // Performance recommendations
    if (performance.isHasRegression()) {
      recommendations.add("Address performance regression in latest version");
    }
    if (!performance.getSlowTests().isEmpty()) {
      recommendations.add("Optimize slow tests: " + 
          String.join(", ", performance.getSlowTests().subList(0, 
              Math.min(3, performance.getSlowTests().size()))));
    }
    
    // Category recommendations
    FailureCategory topCategory = patterns.getCategoryDistribution().entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
    if (topCategory != null) {
      recommendations.add("Most common failure category: " + topCategory + 
          " - prioritize fixes in this area");
    }
    
    return recommendations;
  }
}

// Made with Bob
