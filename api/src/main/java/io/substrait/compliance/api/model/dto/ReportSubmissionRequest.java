package io.substrait.compliance.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for submitting a compliance report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for submitting a compliance test report")
public class ReportSubmissionRequest {
    
    @NotNull(message = "Engine information is required")
    @Valid
    @Schema(description = "Information about the database engine", required = true)
    private EngineInfoDto engineInfo;
    
    @NotBlank(message = "Test suite name is required")
    @Schema(description = "Name of the test suite (e.g., 'tpch')", example = "tpch", required = true)
    private String testSuiteName;
    
    @NotNull(message = "Timestamp is required")
    @Schema(description = "Report timestamp in milliseconds since epoch", example = "1713225600000", required = true)
    private Long timestamp;
    
    @NotEmpty(message = "Test results cannot be empty")
    @Valid
    @Schema(description = "List of individual test results", required = true)
    private List<TestResultDto> testResults;
    
    @Schema(description = "Additional metadata (CI info, commit SHA, etc.)")
    private Map<String, Object> metadata;
    
    /**
     * Engine information DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Database engine information")
    public static class EngineInfoDto {
        
        @NotBlank(message = "Engine name is required")
        @Schema(description = "Engine name", example = "DuckDB", required = true)
        private String name;
        
        @NotBlank(message = "Engine version is required")
        @Schema(description = "Engine version", example = "0.10.0", required = true)
        private String version;
        
        @Schema(description = "Engine vendor", example = "DuckDB Labs")
        private String vendor;
        
        @Schema(description = "Supported Substrait version", example = "0.80.0")
        private String substraitVersion;
    }
    
    /**
     * Test result DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual test case result")
    public static class TestResultDto {
        
        @NotBlank(message = "Test ID is required")
        @Schema(description = "Unique test identifier", example = "tpch-q1", required = true)
        private String testId;
        
        @NotNull(message = "Test status is required")
        @Schema(description = "Test execution status", example = "PASSED", required = true)
        private TestStatus status;
        
        @Schema(description = "Error message (if test failed)", example = "Output mismatch")
        private String message;
        
        @NotNull(message = "Duration is required")
        @Schema(description = "Test execution duration in milliseconds", example = "125", required = true)
        private Long durationMs;
    }
    
    /**
     * Test execution status.
     */
    @Schema(description = "Test execution status")
    public enum TestStatus {
        @Schema(description = "Test passed successfully")
        PASSED,
        
        @Schema(description = "Test failed")
        FAILED,
        
        @Schema(description = "Test was skipped")
        SKIPPED
    }
}

