package io.substrait.compliance.storage;

import io.substrait.compliance.FailureCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Anonymizes sensitive data from engine test reports for public consumption.
 * Removes stack traces, environment details, and aggregates performance metrics.
 */
public class DataAnonymizer {

  /**
   * Create an anonymized community report from an engine test report.
   */
  public static CommunityReport anonymize(EngineTestReport engineReport) {
    return CommunityReport.builder()
        .engineName(engineReport.getEngineName())
        .engineVersion(engineReport.getEngineVersion())
        .reportTimestamp(engineReport.getReportTimestamp())
        .summary(createAnonymizedSummary(engineReport))
        .complianceScore(calculateComplianceScore(engineReport))
        .failureCategories(aggregateFailureCategories(engineReport))
        .benchmarks(aggregatePerformanceBenchmarks(engineReport))
        .knownIssues(new ArrayList<>()) // Placeholder for future implementation
        .build();
  }

  private static CommunityReport.TestSummary createAnonymizedSummary(EngineTestReport report) {
    EngineTestReport.TestSummary summary = report.getSummary();
    
    return CommunityReport.TestSummary.builder()
        .totalTests(summary.getTotalTests())
        .passed(summary.getPassed())
        .failed(summary.getFailed())
        .skipped(summary.getSkipped())
        .build();
  }

  private static CommunityReport.ComplianceScore calculateComplianceScore(EngineTestReport report) {
    EngineTestReport.TestSummary summary = report.getSummary();
    
    // Calculate overall compliance score
    double overall = summary.getTotalTests() > 0 
        ? (double) summary.getPassed() / summary.getTotalTests() * 100.0
        : 0.0;
    
    // Calculate scores by test suite
    Map<String, Double> byTestSuite = calculateTestSuiteScores(report);
    
    // Calculate scores by category (based on failure categories)
    Map<String, Double> byCategory = calculateCategoryScores(report);
    
    return CommunityReport.ComplianceScore.builder()
        .overall(overall)
        .byCategory(byCategory)
        .byTestSuite(byTestSuite)
        .build();
  }

  private static Map<String, Double> calculateTestSuiteScores(EngineTestReport report) {
    Map<String, List<TestExecutionRecord>> byTestSuite = report.getExecutionRecords().stream()
        .collect(Collectors.groupingBy(TestExecutionRecord::getTestSuite));
    
    Map<String, Double> scores = new HashMap<>();
    for (Map.Entry<String, List<TestExecutionRecord>> entry : byTestSuite.entrySet()) {
      List<TestExecutionRecord> records = entry.getValue();
      long passed = records.stream().filter(TestExecutionRecord::isPassed).count();
      double score = records.size() > 0 ? (double) passed / records.size() * 100.0 : 0.0;
      scores.put(entry.getKey(), score);
    }
    
    return scores;
  }

  private static Map<String, Double> calculateCategoryScores(EngineTestReport report) {
    // For now, return empty map. In future, could categorize by function type, etc.
    return new HashMap<>();
  }

  private static Map<FailureCategory, Integer> aggregateFailureCategories(EngineTestReport report) {
    return report.getSummary().getFailureDistribution();
  }

  private static CommunityReport.PerformanceBenchmarks aggregatePerformanceBenchmarks(
      EngineTestReport report) {
    
    List<Long> executionTimes = report.getExecutionRecords().stream()
        .map(r -> r.getMetrics().getExecutionTimeMs())
        .filter(t -> t > 0)
        .sorted()
        .collect(Collectors.toList());
    
    if (executionTimes.isEmpty()) {
      return CommunityReport.PerformanceBenchmarks.builder()
          .averageExecutionTimeMs(0.0)
          .medianExecutionTimeMs(0.0)
          .p95ExecutionTimeMs(0.0)
          .p99ExecutionTimeMs(0.0)
          .build();
    }
    
    double average = executionTimes.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0.0);
    
    double median = calculatePercentile(executionTimes, 50);
    double p95 = calculatePercentile(executionTimes, 95);
    double p99 = calculatePercentile(executionTimes, 99);
    
    // Calculate benchmarks by test suite
    Map<String, Double> byTestSuite = calculateTestSuiteBenchmarks(report);
    
    return CommunityReport.PerformanceBenchmarks.builder()
        .averageExecutionTimeMs(average)
        .medianExecutionTimeMs(median)
        .p95ExecutionTimeMs(p95)
        .p99ExecutionTimeMs(p99)
        .byTestSuite(byTestSuite)
        .build();
  }

  private static Map<String, Double> calculateTestSuiteBenchmarks(EngineTestReport report) {
    Map<String, List<TestExecutionRecord>> byTestSuite = report.getExecutionRecords().stream()
        .collect(Collectors.groupingBy(TestExecutionRecord::getTestSuite));
    
    Map<String, Double> benchmarks = new HashMap<>();
    for (Map.Entry<String, List<TestExecutionRecord>> entry : byTestSuite.entrySet()) {
      double avgTime = entry.getValue().stream()
          .mapToLong(r -> r.getMetrics().getExecutionTimeMs())
          .filter(t -> t > 0)
          .average()
          .orElse(0.0);
      benchmarks.put(entry.getKey(), avgTime);
    }
    
    return benchmarks;
  }

  private static double calculatePercentile(List<Long> sortedValues, int percentile) {
    if (sortedValues.isEmpty()) {
      return 0.0;
    }
    
    int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));
    
    return sortedValues.get(index).doubleValue();
  }

  /**
   * Sanitize an error message by removing sensitive information.
   */
  public static String sanitizeErrorMessage(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }
    
    // Remove file paths
    String sanitized = errorMessage.replaceAll("/[^\\s]+", "[PATH]");
    
    // Remove IP addresses
    sanitized = sanitized.replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", "[IP]");
    
    // Remove potential credentials (basic patterns)
    sanitized = sanitized.replaceAll("password[=:]\\s*\\S+", "password=[REDACTED]");
    sanitized = sanitized.replaceAll("token[=:]\\s*\\S+", "token=[REDACTED]");
    
    return sanitized;
  }

  /**
   * Remove stack trace information.
   */
  public static String removeStackTrace(String stackTrace) {
    // Simply return null to remove all stack trace information
    return null;
  }

  /**
   * Anonymize environment details.
   */
  public static Map<String, String> anonymizeEnvironment(Map<String, String> environment) {
    // Return only non-sensitive environment information
    Map<String, String> anonymized = new HashMap<>();
    
    // Keep only general information
    if (environment.containsKey("os.name")) {
      anonymized.put("os.name", environment.get("os.name"));
    }
    if (environment.containsKey("os.version")) {
      anonymized.put("os.version", environment.get("os.version"));
    }
    if (environment.containsKey("java.version")) {
      anonymized.put("java.version", environment.get("java.version"));
    }
    
    return anonymized;
  }
}

// Made with Bob
