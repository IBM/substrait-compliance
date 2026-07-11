package io.substrait.compliance.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates self-contained reproduction packages for test failures.
 * Packages include everything needed to reproduce a specific test failure.
 */
public class ReproductionPackager {

  /**
   * Create a reproduction package for a specific test execution.
   */
  public static ReproductionPackage createPackage(TestExecutionRecord record) {
    return ReproductionPackage.builder()
        .testId(record.getTestId())
        .testName(record.getTestName())
        .testSuite(record.getTestSuite())
        .substraitPlan(record.getInput().getSubstraitPlan())
        .expectedResult(record.getOutput().getExpectedResult())
        .actualResult(record.getOutput().getActualResult())
        .errorMessage(record.getDiagnostics().getErrorMessage())
        .stackTrace(record.getDiagnostics().getStackTrace())
        .engineInfo(createEngineInfo(record))
        .instructions(generateInstructions(record))
        .build();
  }

  private static Map<String, String> createEngineInfo(TestExecutionRecord record) {
    Map<String, String> info = new HashMap<>();
    info.put("name", record.getEngineInfo().getName());
    info.put("version", record.getEngineInfo().getVersion());
    return info;
  }

  private static String generateInstructions(TestExecutionRecord record) {
    StringBuilder instructions = new StringBuilder();
    instructions.append("# Test Reproduction Instructions\n\n");
    instructions.append("## Test Information\n");
    instructions.append("- Test ID: ").append(record.getTestId()).append("\n");
    instructions.append("- Test Name: ").append(record.getTestName()).append("\n");
    instructions.append("- Test Suite: ").append(record.getTestSuite()).append("\n");
    instructions.append("- Timestamp: ").append(record.getTimestamp()).append("\n\n");
    
    instructions.append("## Engine Information\n");
    instructions.append("- Engine: ").append(record.getEngineInfo().getName()).append("\n");
    instructions.append("- Version: ").append(record.getEngineInfo().getVersion()).append("\n\n");
    
    instructions.append("## Failure Information\n");
    if (record.getDiagnostics().getFailureCategory() != null) {
      instructions.append("- Category: ").append(record.getDiagnostics().getFailureCategory()).append("\n");
    }
    if (record.getDiagnostics().getErrorMessage() != null) {
      instructions.append("- Error: ").append(record.getDiagnostics().getErrorMessage()).append("\n");
    }
    instructions.append("\n");
    
    instructions.append("## Files Included\n");
    instructions.append("- `substrait-plan.bin` - The Substrait plan (binary format)\n");
    instructions.append("- `substrait-plan.txt` - The Substrait plan (text format, if available)\n");
    instructions.append("- `expected-result.json` - Expected test result\n");
    instructions.append("- `actual-result.json` - Actual result from engine\n");
    instructions.append("- `error.txt` - Error message and stack trace\n\n");
    
    instructions.append("## Reproduction Steps\n");
    instructions.append("1. Load the Substrait plan from `substrait-plan.bin`\n");
    instructions.append("2. Execute the plan using the specified engine\n");
    instructions.append("3. Compare the result with `expected-result.json`\n");
    instructions.append("4. Investigate any differences\n\n");
    
    instructions.append("## Environment Requirements\n");
    instructions.append("- Engine: ").append(record.getEngineInfo().getName())
        .append(" v").append(record.getEngineInfo().getVersion()).append("\n");
    
    return instructions.toString();
  }

  /**
   * A self-contained reproduction package.
   */
  public static class ReproductionPackage {
    private final String testId;
    private final String testName;
    private final String testSuite;
    private final byte[] substraitPlan;
    private final Object expectedResult;
    private final Object actualResult;
    private final String errorMessage;
    private final String stackTrace;
    private final Map<String, String> engineInfo;
    private final String instructions;

    private ReproductionPackage(Builder builder) {
      this.testId = Objects.requireNonNull(builder.testId, "testId cannot be null");
      this.testName = Objects.requireNonNull(builder.testName, "testName cannot be null");
      this.testSuite = Objects.requireNonNull(builder.testSuite, "testSuite cannot be null");
      this.substraitPlan = builder.substraitPlan;
      this.expectedResult = builder.expectedResult;
      this.actualResult = builder.actualResult;
      this.errorMessage = builder.errorMessage;
      this.stackTrace = builder.stackTrace;
      this.engineInfo = new HashMap<>(builder.engineInfo);
      this.instructions = builder.instructions;
    }

    public static Builder builder() {
      return new Builder();
    }

    /**
     * Save the reproduction package as a ZIP file.
     */
    public void saveToFile(Path outputPath) throws IOException {
      byte[] zipData = createZipArchive();
      Files.write(outputPath, zipData);
    }

    /**
     * Create a ZIP archive containing all reproduction files.
     */
    public byte[] createZipArchive() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        // Add instructions
        addTextEntry(zos, "README.md", instructions);
        
        // Add Substrait plan
        if (substraitPlan != null) {
          addBinaryEntry(zos, "substrait-plan.bin", substraitPlan);
          addTextEntry(zos, "substrait-plan.txt", 
              "Base64: " + Base64.getEncoder().encodeToString(substraitPlan));
        }
        
        // Add expected result
        if (expectedResult != null) {
          addTextEntry(zos, "expected-result.json", 
              formatAsJson(expectedResult));
        }
        
        // Add actual result
        if (actualResult != null) {
          addTextEntry(zos, "actual-result.json", 
              formatAsJson(actualResult));
        }
        
        // Add error information
        if (errorMessage != null || stackTrace != null) {
          StringBuilder error = new StringBuilder();
          if (errorMessage != null) {
            error.append("Error Message:\n").append(errorMessage).append("\n\n");
          }
          if (stackTrace != null) {
            error.append("Stack Trace:\n").append(stackTrace);
          }
          addTextEntry(zos, "error.txt", error.toString());
        }
        
        // Add engine info
        StringBuilder engineInfoStr = new StringBuilder();
        engineInfoStr.append("Engine Information:\n");
        for (Map.Entry<String, String> entry : engineInfo.entrySet()) {
          engineInfoStr.append(entry.getKey()).append(": ")
              .append(entry.getValue()).append("\n");
        }
        addTextEntry(zos, "engine-info.txt", engineInfoStr.toString());
      }
      
      return baos.toByteArray();
    }

    private void addTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
      ZipEntry entry = new ZipEntry(name);
      zos.putNextEntry(entry);
      zos.write(content.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    private void addBinaryEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
      ZipEntry entry = new ZipEntry(name);
      zos.putNextEntry(entry);
      zos.write(data);
      zos.closeEntry();
    }

    private String formatAsJson(Object obj) {
      // Simple JSON formatting - in production, use Jackson
      if (obj == null) {
        return "null";
      }
      return obj.toString();
    }

    // Getters
    public String getTestId() { return testId; }
    public String getTestName() { return testName; }
    public String getTestSuite() { return testSuite; }
    public byte[] getSubstraitPlan() { return substraitPlan; }
    public Object getExpectedResult() { return expectedResult; }
    public Object getActualResult() { return actualResult; }
    public String getErrorMessage() { return errorMessage; }
    public String getStackTrace() { return stackTrace; }
    public Map<String, String> getEngineInfo() { return engineInfo; }
    public String getInstructions() { return instructions; }

    public static class Builder {
      private String testId;
      private String testName;
      private String testSuite;
      private byte[] substraitPlan;
      private Object expectedResult;
      private Object actualResult;
      private String errorMessage;
      private String stackTrace;
      private Map<String, String> engineInfo = new HashMap<>();
      private String instructions;

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

      public Builder substraitPlan(byte[] substraitPlan) {
        this.substraitPlan = substraitPlan;
        return this;
      }

      public Builder expectedResult(Object expectedResult) {
        this.expectedResult = expectedResult;
        return this;
      }

      public Builder actualResult(Object actualResult) {
        this.actualResult = actualResult;
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

      public Builder engineInfo(Map<String, String> engineInfo) {
        this.engineInfo = new HashMap<>(engineInfo);
        return this;
      }

      public Builder instructions(String instructions) {
        this.instructions = instructions;
        return this;
      }

      public ReproductionPackage build() {
        return new ReproductionPackage(this);
      }
    }
  }
}

