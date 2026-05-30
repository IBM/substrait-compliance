package io.substrait.compliance.api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA entity representing a compliance test execution report.
 * 
 * <p>Each report contains summary information about test execution
 * and is associated with an engine and multiple test results.
 */
@Entity
@Table(name = "compliance_reports", indexes = {
    @Index(name = "idx_reports_engine", columnList = "engine_id"),
    @Index(name = "idx_reports_suite", columnList = "test_suite_name"),
    @Index(name = "idx_reports_timestamp", columnList = "timestamp"),
    @Index(name = "idx_reports_score", columnList = "compliance_score"),
    @Index(name = "idx_reports_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engine_id", nullable = false, foreignKey = @ForeignKey(name = "fk_report_engine"))
    private EngineEntity engine;
    
    @Column(name = "test_suite_name", nullable = false)
    private String testSuiteName;
    
    @Column(nullable = false)
    private Long timestamp;
    
    @Column(name = "total_tests", nullable = false)
    private Integer totalTests;
    
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;
    
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;
    
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount;
    
    @Column(name = "compliance_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal complianceScore;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @org.hibernate.annotations.ColumnTransformer(write = "?::jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TestResultEntity> testResults = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        
        // Validate test counts
        if (totalTests != null && passedCount != null && failedCount != null && skippedCount != null) {
            if (totalTests != passedCount + failedCount + skippedCount) {
                throw new IllegalStateException(
                    "Total tests must equal sum of passed, failed, and skipped counts");
            }
        }
    }
    
    /**
     * Adds a test result to this report.
     * 
     * @param testResult the test result to add
     */
    public void addTestResult(TestResultEntity testResult) {
        testResults.add(testResult);
        testResult.setReport(this);
    }
    
    /**
     * Removes a test result from this report.
     * 
     * @param testResult the test result to remove
     */
    public void removeTestResult(TestResultEntity testResult) {
        testResults.remove(testResult);
        testResult.setReport(null);
    }
    
    /**
     * Calculates the compliance score based on test results.
     * 
     * @return the compliance score as a percentage (0-100)
     */
    public BigDecimal calculateComplianceScore() {
        if (totalTests == null || totalTests == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(passedCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalTests), 2, BigDecimal.ROUND_HALF_UP);
    }
}

// Made with Bob
