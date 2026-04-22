package io.substrait.compliance.storage;

import io.substrait.compliance.EnhancedComplianceResult;
import io.substrait.compliance.FailureCategory;
import io.substrait.compliance.comparison.ComparisonResult;
import io.substrait.compliance.validator.ValidationResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete record of a single test execution.
 * Contains all information needed to reproduce and analyze the test.
 */
public class TestExecutionRecord {
  private final String testId;
  private final String testName;
  private final String testSuite;
  private final Instant timestamp;
  private final EngineContext engineInfo;
  private final TestInput input;
  private final TestOutput output;
  private final DiagnosticInfo diagnostics;
  private final PerformanceMetrics metrics;
  private final Map<String, String> metadata;

  private TestExecutionRecord(Builder builder) {
    this.testId = Objects.requireNonNull(builder.testId, "testId cannot be null");
    this.testName = Objects.requireNonNull(builder.testName, "testName cannot be null");
    this.testSuite = Objects.requireNonNull(builder.testSuite, "testSuite cannot be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp cannot be null");
    this.engineInfo = Objects.requireNonNull(builder.engineInfo, "engineInfo cannot be null");
    this.input = Objects.requireNonNull(builder.input, "input cannot be null");
    this.output = Objects.requireNonNull(builder.output, "output cannot be null");
    this.diagnostics = Objects.requireNonNull(builder.diagnostics, "diagnostics cannot be null");
    this.metrics = builder.metrics != null ? builder.metrics : PerformanceMetrics.empty();
    this.metadata = new HashMap<>(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a TestExecutionRecord from an EnhancedComplianceResult.
   */
  public static TestExecutionRecord fromResult(
      EnhancedComplianceResult result,
      String testId,
      String testName,
      String testSuite,
      EngineContext engineInfo,
      byte[] substraitPlan,
      Object testCase) {
    
    // Extract stack trace from exception if available
    String stackTrace = null;
    if (result.getException() != null) {
      java.io.StringWriter sw = new java.io.StringWriter();
      result.getException().printStackTrace(new java.io.PrintWriter(sw));
      stackTrace = sw.toString();
    }
    
    // Build validation results list from plan validation
    List<ValidationResult> validationResults = new ArrayList<>();
    if (result.getPlanValidation() != null) {
      validationResults.add(result.getPlanValidation());
    }
    
    return builder()
        .testId(testId)
        .testName(testName)
        .testSuite(testSuite)
        .timestamp(Instant.now())
        .engineInfo(engineInfo)
        .input(TestInput.builder()
            .substraitPlan(substraitPlan)
            .testCase(testCase)
            .build())
        .output(TestOutput.builder()
            .actualResult(result.getActualResult())
            .expectedResult(result.getExpectedResult())
            .comparisonResult(result.getComparisonResult())
            .passed(result.isSuccess())
            .build())
        .diagnostics(DiagnosticInfo.builder()
            .validationResults(validationResults)
            .failureCategory(result.getFailureCategory())
            .errorMessage(result.getErrorMessage())
            .stackTrace(stackTrace)
            .build())
        .metrics(PerformanceMetrics.builder()
            .executionTimeMs(result.getExecutionTimeMs())
            .build())
        .build();
  }

  // Getters
  public String getTestId() { return testId; }
  public String getTestName() { return testName; }
  public String getTestSuite() { return testSuite; }
  public Instant getTimestamp() { return timestamp; }
  public EngineContext getEngineInfo() { return engineInfo; }
  public TestInput getInput() { return input; }
  public TestOutput getOutput() { return output; }
  public DiagnosticInfo getDiagnostics() { return diagnostics; }
  public PerformanceMetrics getMetrics() { return metrics; }
  public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

  public boolean isPassed() {
    return output.isPassed();
  }

  public boolean isFailed() {
    return !isPassed();
  }

  /**
   * Engine execution context information.
   */
  public static class EngineContext {
    private final String name;
    private final String version;
    private final Map<String, String> capabilities;
    private final Map<String, String> environment;

    private EngineContext(Builder builder) {
      this.name = Objects.requireNonNull(builder.name, "name cannot be null");
      this.version = Objects.requireNonNull(builder.version, "version cannot be null");
      this.capabilities = new HashMap<>(builder.capabilities);
      this.environment = new HashMap<>(builder.environment);
    }

    public static Builder builder() {
      return new Builder();
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public Map<String, String> getCapabilities() { return Collections.unmodifiableMap(capabilities); }
    public Map<String, String> getEnvironment() { return Collections.unmodifiableMap(environment); }

    public static class Builder {
      private String name;
      private String version;
      private Map<String, String> capabilities = new HashMap<>();
      private Map<String, String> environment = new HashMap<>();

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder version(String version) {
        this.version = version;
        return this;
      }

      public Builder capabilities(Map<String, String> capabilities) {
        this.capabilities = new HashMap<>(capabilities);
        return this;
      }

      public Builder addCapability(String key, String value) {
        this.capabilities.put(key, value);
        return this;
      }

      public Builder environment(Map<String, String> environment) {
        this.environment = new HashMap<>(environment);
        return this;
      }

      public Builder addEnvironment(String key, String value) {
        this.environment.put(key, value);
        return this;
      }

      public EngineContext build() {
        return new EngineContext(this);
      }
    }
  }

  /**
   * Test input data.
   */
  public static class TestInput {
    private final String substraitPlanBase64;
    private final Object testCase;

    private TestInput(Builder builder) {
      this.substraitPlanBase64 = builder.substraitPlanBase64;
      this.testCase = builder.testCase;
    }

    public static Builder builder() {
      return new Builder();
    }

    public String getSubstraitPlanBase64() { return substraitPlanBase64; }
    public byte[] getSubstraitPlan() {
      return substraitPlanBase64 != null ? Base64.getDecoder().decode(substraitPlanBase64) : null;
    }
    public Object getTestCase() { return testCase; }

    public static class Builder {
      private String substraitPlanBase64;
      private Object testCase;

      public Builder substraitPlan(byte[] plan) {
        this.substraitPlanBase64 = plan != null ? Base64.getEncoder().encodeToString(plan) : null;
        return this;
      }

      public Builder substraitPlanBase64(String planBase64) {
        this.substraitPlanBase64 = planBase64;
        return this;
      }

      public Builder testCase(Object testCase) {
        this.testCase = testCase;
        return this;
      }

      public TestInput build() {
        return new TestInput(this);
      }
    }
  }

  /**
   * Test output data.
   */
  public static class TestOutput {
    private final Object actualResult;
    private final Object expectedResult;
    private final ComparisonResult comparisonResult;
    private final boolean passed;

    private TestOutput(Builder builder) {
      this.actualResult = builder.actualResult;
      this.expectedResult = builder.expectedResult;
      this.comparisonResult = builder.comparisonResult;
      this.passed = builder.passed;
    }

    public static Builder builder() {
      return new Builder();
    }

    public Object getActualResult() { return actualResult; }
    public Object getExpectedResult() { return expectedResult; }
    public ComparisonResult getComparisonResult() { return comparisonResult; }
    public boolean isPassed() { return passed; }

    public static class Builder {
      private Object actualResult;
      private Object expectedResult;
      private ComparisonResult comparisonResult;
      private boolean passed;

      public Builder actualResult(Object actualResult) {
        this.actualResult = actualResult;
        return this;
      }

      public Builder expectedResult(Object expectedResult) {
        this.expectedResult = expectedResult;
        return this;
      }

      public Builder comparisonResult(ComparisonResult comparisonResult) {
        this.comparisonResult = comparisonResult;
        return this;
      }

      public Builder passed(boolean passed) {
        this.passed = passed;
        return this;
      }

      public TestOutput build() {
        return new TestOutput(this);
      }
    }
  }

  /**
   * Diagnostic information for failures.
   */
  public static class DiagnosticInfo {
    private final List<ValidationResult> validationResults;
    private final FailureCategory failureCategory;
    private final String errorMessage;
    private final String stackTrace;

    private DiagnosticInfo(Builder builder) {
      this.validationResults = new ArrayList<>(builder.validationResults);
      this.failureCategory = builder.failureCategory;
      this.errorMessage = builder.errorMessage;
      this.stackTrace = builder.stackTrace;
    }

    public static Builder builder() {
      return new Builder();
    }

    public List<ValidationResult> getValidationResults() { return Collections.unmodifiableList(validationResults); }
    public FailureCategory getFailureCategory() { return failureCategory; }
    public String getErrorMessage() { return errorMessage; }
    public String getStackTrace() { return stackTrace; }

    public static class Builder {
      private List<ValidationResult> validationResults = new ArrayList<>();
      private FailureCategory failureCategory;
      private String errorMessage;
      private String stackTrace;

      public Builder validationResults(List<ValidationResult> validationResults) {
        this.validationResults = new ArrayList<>(validationResults);
        return this;
      }

      public Builder addValidationResult(ValidationResult result) {
        this.validationResults.add(result);
        return this;
      }

      public Builder failureCategory(FailureCategory failureCategory) {
        this.failureCategory = failureCategory;
        return this;
      }

      public Builder errorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
      }

      public Builder stackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        return this;
      }

      public DiagnosticInfo build() {
        return new DiagnosticInfo(this);
      }
    }
  }

  /**
   * Performance metrics.
   */
  public static class PerformanceMetrics {
    private final long executionTimeMs;
    private final long memoryUsedBytes;
    private final Map<String, Object> customMetrics;

    private PerformanceMetrics(Builder builder) {
      this.executionTimeMs = builder.executionTimeMs;
      this.memoryUsedBytes = builder.memoryUsedBytes;
      this.customMetrics = new HashMap<>(builder.customMetrics);
    }

    public static Builder builder() {
      return new Builder();
    }

    public static PerformanceMetrics empty() {
      return builder().build();
    }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public long getMemoryUsedBytes() { return memoryUsedBytes; }
    public Map<String, Object> getCustomMetrics() { return Collections.unmodifiableMap(customMetrics); }

    public static class Builder {
      private long executionTimeMs;
      private long memoryUsedBytes;
      private Map<String, Object> customMetrics = new HashMap<>();

      public Builder executionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
      }

      public Builder memoryUsedBytes(long memoryUsedBytes) {
        this.memoryUsedBytes = memoryUsedBytes;
        return this;
      }

      public Builder customMetrics(Map<String, Object> customMetrics) {
        this.customMetrics = new HashMap<>(customMetrics);
        return this;
      }

      public Builder addCustomMetric(String key, Object value) {
        this.customMetrics.put(key, value);
        return this;
      }

      public PerformanceMetrics build() {
        return new PerformanceMetrics(this);
      }
    }
  }

  public static class Builder {
    private String testId;
    private String testName;
    private String testSuite;
    private Instant timestamp;
    private EngineContext engineInfo;
    private TestInput input;
    private TestOutput output;
    private DiagnosticInfo diagnostics;
    private PerformanceMetrics metrics;
    private Map<String, String> metadata = new HashMap<>();

    public Builder testId(String testId) {
      this.testId = testId;
      return this;
    }

    public Builder testName(String testName) {
      this.testName = testName;
      return this;
    }

    public Builder testSuite(String testSuite) {
      this.testSuite = testSuite;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder engineInfo(EngineContext engineInfo) {
      this.engineInfo = engineInfo;
      return this;
    }

    public Builder input(TestInput input) {
      this.input = input;
      return this;
    }

    public Builder output(TestOutput output) {
      this.output = output;
      return this;
    }

    public Builder diagnostics(DiagnosticInfo diagnostics) {
      this.diagnostics = diagnostics;
      return this;
    }

    public Builder metrics(PerformanceMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = new HashMap<>(metadata);
      return this;
    }

    public Builder addMetadata(String key, String value) {
      this.metadata.put(key, value);
      return this;
    }

    public TestExecutionRecord build() {
      return new TestExecutionRecord(this);
    }
  }
}

// Made with Bob
