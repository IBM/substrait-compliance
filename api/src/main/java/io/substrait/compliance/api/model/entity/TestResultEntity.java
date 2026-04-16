package io.substrait.compliance.api.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing an individual test case result within a compliance report.
 * 
 * <p>Each test result captures the outcome of executing a single test case,
 * including its status, duration, and any error messages.
 */
@Entity
@Table(name = "test_results", indexes = {
    @Index(name = "idx_test_results_report", columnList = "report_id"),
    @Index(name = "idx_test_results_status", columnList = "status"),
    @Index(name = "idx_test_results_test_id", columnList = "test_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, foreignKey = @ForeignKey(name = "fk_result_report"))
    private ReportEntity report;
    
    @Column(name = "test_id", nullable = false)
    private String testId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TestStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    /**
     * Test execution status.
     */
    public enum TestStatus {
        /** Test passed successfully */
        PASSED,
        
        /** Test failed */
        FAILED,
        
        /** Test was skipped */
        SKIPPED
    }
    
    /**
     * Checks if this test passed.
     * 
     * @return true if the test passed
     */
    public boolean isPassed() {
        return status == TestStatus.PASSED;
    }
    
    /**
     * Checks if this test failed.
     * 
     * @return true if the test failed
     */
    public boolean isFailed() {
        return status == TestStatus.FAILED;
    }
    
    /**
     * Checks if this test was skipped.
     * 
     * @return true if the test was skipped
     */
    public boolean isSkipped() {
        return status == TestStatus.SKIPPED;
    }
}

// Made with Bob
