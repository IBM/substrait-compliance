package io.substrait.compliance.api.repository;

import io.substrait.compliance.api.model.entity.ReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for accessing compliance reports.
 */
@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
    
    /**
     * Finds reports by engine name.
     */
    Page<ReportEntity> findByEngine_Name(String engineName, Pageable pageable);
    
    /**
     * Finds reports by engine name and version.
     */
    Page<ReportEntity> findByEngine_NameAndEngine_Version(
            String engineName, String engineVersion, Pageable pageable);
    
    /**
     * Finds reports by test suite name.
     */
    Page<ReportEntity> findByTestSuiteName(String testSuiteName, Pageable pageable);
    
    /**
     * Finds reports within a score range.
     */
    @Query("SELECT r FROM ReportEntity r WHERE r.complianceScore >= :minScore AND r.complianceScore <= :maxScore")
    Page<ReportEntity> findByScoreRange(
            @Param("minScore") BigDecimal minScore,
            @Param("maxScore") BigDecimal maxScore,
            Pageable pageable);
    
    /**
     * Finds reports within a timestamp range.
     */
    @Query("SELECT r FROM ReportEntity r WHERE r.timestamp >= :fromTimestamp AND r.timestamp <= :toTimestamp")
    Page<ReportEntity> findByTimestampRange(
            @Param("fromTimestamp") Long fromTimestamp,
            @Param("toTimestamp") Long toTimestamp,
            Pageable pageable);
    
    /**
     * Finds the latest report for an engine.
     */
    @Query("SELECT r FROM ReportEntity r WHERE r.engine.name = :engineName ORDER BY r.timestamp DESC")
    List<ReportEntity> findLatestByEngineName(@Param("engineName") String engineName, Pageable pageable);
    
    /**
     * Finds all reports for an engine ordered by timestamp.
     */
    @Query("SELECT r FROM ReportEntity r WHERE r.engine.name = :engineName ORDER BY r.timestamp DESC")
    List<ReportEntity> findByEngineNameOrderByTimestampDesc(@Param("engineName") String engineName);
    
    /**
     * Counts reports by engine name.
     */
    long countByEngine_Name(String engineName);
    
    /**
     * Gets the average compliance score for a test suite.
     */
    @Query("SELECT AVG(r.complianceScore) FROM ReportEntity r WHERE r.testSuiteName = :testSuiteName")
    BigDecimal getAverageScoreByTestSuite(@Param("testSuiteName") String testSuiteName);
    
    /**
     * Gets the highest compliance score for a test suite.
     */
    @Query("SELECT MAX(r.complianceScore) FROM ReportEntity r WHERE r.testSuiteName = :testSuiteName")
    BigDecimal getHighestScoreByTestSuite(@Param("testSuiteName") String testSuiteName);
}

// Made with Bob
