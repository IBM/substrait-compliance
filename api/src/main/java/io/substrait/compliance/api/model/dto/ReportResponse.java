package io.substrait.compliance.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for compliance report operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Compliance report response")
public class ReportResponse {
    
    @Schema(description = "Unique report identifier", example = "12345")
    private Long reportId;
    
    @Schema(description = "Engine information")
    private EngineInfoDto engineInfo;
    
    @Schema(description = "Test suite name", example = "tpch")
    private String testSuiteName;
    
    @Schema(description = "Report timestamp", example = "2026-04-16T01:00:00Z")
    private Instant timestamp;
    
    @Schema(description = "Test execution summary")
    private SummaryDto summary;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Report creation timestamp", example = "2026-04-16T01:00:00Z")
    private Instant createdAt;
    
    /**
     * Engine information DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Database engine information")
    public static class EngineInfoDto {
        
        @Schema(description = "Engine name", example = "DuckDB")
        private String name;
        
        @Schema(description = "Engine version", example = "0.10.0")
        private String version;
        
        @Schema(description = "Engine vendor", example = "DuckDB Labs")
        private String vendor;
        
        @Schema(description = "Supported Substrait version", example = "0.80.0")
        private String substraitVersion;
    }
    
    /**
     * Test execution summary DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Test execution summary")
    public static class SummaryDto {
        
        @Schema(description = "Total number of tests", example = "22")
        private Integer totalTests;
        
        @Schema(description = "Number of passed tests", example = "20")
        private Integer passed;
        
        @Schema(description = "Number of failed tests", example = "2")
        private Integer failed;
        
        @Schema(description = "Number of skipped tests", example = "0")
        private Integer skipped;
        
        @Schema(description = "Compliance score (0-100)", example = "90.91")
        private BigDecimal complianceScore;
        
        @Schema(description = "Total execution time in milliseconds", example = "5432")
        private Long executionTimeMs;
    }
}

