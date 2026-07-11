package io.substrait.compliance.api.service;

import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.model.entity.EngineEntity;
import io.substrait.compliance.api.model.entity.ReportEntity;
import io.substrait.compliance.api.model.entity.TestResultEntity;
import io.substrait.compliance.api.repository.EngineRepository;
import io.substrait.compliance.api.repository.ReportRepository;
import io.substrait.compliance.api.webhook.WebhookPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing compliance reports.
 */
@Service
@Slf4j
public class ReportService {
    
    private final ReportRepository reportRepository;
    private final EngineRepository engineRepository;
    private final WebhookPublisher webhookPublisher;
    
    public ReportService(ReportRepository reportRepository,
                        EngineRepository engineRepository,
                        WebhookPublisher webhookPublisher) {
        this.reportRepository = reportRepository;
        this.engineRepository = engineRepository;
        this.webhookPublisher = webhookPublisher;
    }
    
    /**
     * Submits a new compliance report.
     */
    @Transactional
    @CacheEvict(value = {"leaderboard", "statistics", "engineStats"}, allEntries = true)
    public ReportResponse submitReport(ReportSubmissionRequest request) {
        log.info("Submitting report for engine: {}-{}, suite: {}",
                request.getEngineInfo().getName(),
                request.getEngineInfo().getVersion(),
                request.getTestSuiteName());
        
        // Find or create engine
        EngineEntity engine = findOrCreateEngine(request.getEngineInfo());
        
        // Calculate test counts
        long passedCount = request.getTestResults().stream()
                .filter(r -> r.getStatus() == ReportSubmissionRequest.TestStatus.PASSED)
                .count();
        long failedCount = request.getTestResults().stream()
                .filter(r -> r.getStatus() == ReportSubmissionRequest.TestStatus.FAILED)
                .count();
        long skippedCount = request.getTestResults().stream()
                .filter(r -> r.getStatus() == ReportSubmissionRequest.TestStatus.SKIPPED)
                .count();
        
        // Calculate compliance score
        BigDecimal complianceScore = BigDecimal.valueOf(passedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(request.getTestResults().size()), 2, BigDecimal.ROUND_HALF_UP);
        
        // Create report entity
        ReportEntity report = ReportEntity.builder()
                .engine(engine)
                .testSuiteName(request.getTestSuiteName())
                .timestamp(request.getTimestamp())
                .totalTests(request.getTestResults().size())
                .passedCount((int) passedCount)
                .failedCount((int) failedCount)
                .skippedCount((int) skippedCount)
                .complianceScore(complianceScore)
                .metadata(request.getMetadata())
                .build();
        
        // Add test results
        for (ReportSubmissionRequest.TestResultDto testResultDto : request.getTestResults()) {
            TestResultEntity testResult = TestResultEntity.builder()
                    .testId(testResultDto.getTestId())
                    .status(TestResultEntity.TestStatus.valueOf(testResultDto.getStatus().name()))
                    .message(testResultDto.getMessage())
                    .durationMs(testResultDto.getDurationMs())
                    .build();
            report.addTestResult(testResult);
        }
        
        // Calculate total execution time
        long totalExecutionTime = request.getTestResults().stream()
                .mapToLong(ReportSubmissionRequest.TestResultDto::getDurationMs)
                .sum();
        report.setExecutionTimeMs(totalExecutionTime);
        
        // Save report
        ReportEntity savedReport = reportRepository.save(report);
        
        log.info("Report submitted successfully with ID: {}, score: {}%",
                savedReport.getId(), savedReport.getComplianceScore());
        
        // Publish webhook event for successful submission
        try {
            webhookPublisher.publishReportSubmitted(savedReport);
        } catch (Exception e) {
            log.error("Failed to publish webhook event for report {}", savedReport.getId(), e);
            // Don't fail the submission if webhook fails
        }
        
        return convertToResponse(savedReport);
    }
    
    /**
     * Gets a report by ID.
     */
    @Cacheable(value = "reports", key = "#reportId")
    public ReportResponse getReport(Long reportId) {
        log.debug("Fetching report with ID: {}", reportId);
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + reportId));
        return convertToResponse(report);
    }
    
    /**
     * Queries reports with pagination.
     */
    public Page<ReportResponse> queryReports(Pageable pageable) {
        log.debug("Querying reports with pagination: {}", pageable);
        return reportRepository.findAll(pageable)
                .map(this::convertToResponse);
    }
    
    /**
     * Gets reports by engine name.
     */
    public Page<ReportResponse> getReportsByEngine(String engineName, Pageable pageable) {
        log.debug("Fetching reports for engine: {}", engineName);
        return reportRepository.findByEngine_Name(engineName, pageable)
                .map(this::convertToResponse);
    }
    
    /**
     * Gets report history for an engine.
     */
    @Cacheable(value = "engineHistory", key = "#engineName")
    public List<ReportResponse> getEngineHistory(String engineName) {
        log.debug("Fetching history for engine: {}", engineName);
        return reportRepository.findByEngineNameOrderByTimestampDesc(engineName).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Finds or creates an engine entity.
     */
    private EngineEntity findOrCreateEngine(ReportSubmissionRequest.EngineInfoDto engineInfo) {
        return engineRepository.findByNameAndVersion(engineInfo.getName(), engineInfo.getVersion())
                .orElseGet(() -> {
                    EngineEntity newEngine = EngineEntity.builder()
                            .name(engineInfo.getName())
                            .version(engineInfo.getVersion())
                            .vendor(engineInfo.getVendor())
                            .substraitVersion(engineInfo.getSubstraitVersion())
                            .build();
                    return engineRepository.save(newEngine);
                });
    }
    
    /**
     * Converts a report entity to a response DTO.
     */
    private ReportResponse convertToResponse(ReportEntity report) {
        return ReportResponse.builder()
                .reportId(report.getId())
                .engineInfo(ReportResponse.EngineInfoDto.builder()
                        .name(report.getEngine().getName())
                        .version(report.getEngine().getVersion())
                        .vendor(report.getEngine().getVendor())
                        .substraitVersion(report.getEngine().getSubstraitVersion())
                        .build())
                .testSuiteName(report.getTestSuiteName())
                .timestamp(Instant.ofEpochMilli(report.getTimestamp()))
                .summary(ReportResponse.SummaryDto.builder()
                        .totalTests(report.getTotalTests())
                        .passed(report.getPassedCount())
                        .failed(report.getFailedCount())
                        .skipped(report.getSkippedCount())
                        .complianceScore(report.getComplianceScore())
                        .executionTimeMs(report.getExecutionTimeMs())
                        .build())
                .metadata(report.getMetadata())
                .createdAt(report.getCreatedAt())
                .build();
    }
    
    /**
     * Exception thrown when a resource is not found.
     */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}

