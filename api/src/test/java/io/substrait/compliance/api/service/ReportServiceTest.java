package io.substrait.compliance.api.service;

import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.model.entity.EngineEntity;
import io.substrait.compliance.api.model.entity.ReportEntity;
import io.substrait.compliance.api.model.entity.TestResultEntity;
import io.substrait.compliance.api.repository.EngineRepository;
import io.substrait.compliance.api.repository.ReportRepository;
import io.substrait.compliance.api.webhook.WebhookPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private EngineRepository engineRepository;

    @Mock
    private WebhookPublisher webhookPublisher;

    @InjectMocks
    private ReportService reportService;

    private EngineEntity testEngine;
    private ReportEntity testReport;
    private ReportSubmissionRequest testRequest;

    @BeforeEach
    void setUp() {
        // Setup test engine
        testEngine = EngineEntity.builder()
                .id(1L)
                .name("TestEngine")
                .version("1.0.0")
                .vendor("TestVendor")
                .substraitVersion("0.20.0")
                .build();

        // Setup test report
        testReport = ReportEntity.builder()
                .id(1L)
                .engine(testEngine)
                .testSuiteName("arithmetic_functions")
                .timestamp(System.currentTimeMillis())
                .totalTests(100)
                .passedCount(95)
                .failedCount(5)
                .skippedCount(0)
                .complianceScore(new BigDecimal("95.00"))
                .executionTimeMs(5000L)
                .metadata(new HashMap<>())
                .build();

        // Setup test request
        ReportSubmissionRequest.EngineInfoDto engineInfo = ReportSubmissionRequest.EngineInfoDto.builder()
                .name("TestEngine")
                .version("1.0.0")
                .vendor("TestVendor")
                .substraitVersion("0.20.0")
                .build();

        List<ReportSubmissionRequest.TestResultDto> testResults = Arrays.asList(
                ReportSubmissionRequest.TestResultDto.builder()
                        .testId("test1")
                        .status(ReportSubmissionRequest.TestStatus.PASSED)
                        .message("Test passed")
                        .durationMs(50L)
                        .build(),
                ReportSubmissionRequest.TestResultDto.builder()
                        .testId("test2")
                        .status(ReportSubmissionRequest.TestStatus.FAILED)
                        .message("Test failed")
                        .durationMs(100L)
                        .build()
        );

        testRequest = ReportSubmissionRequest.builder()
                .engineInfo(engineInfo)
                .testSuiteName("arithmetic_functions")
                .timestamp(System.currentTimeMillis())
                .testResults(testResults)
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    void submitReport_NewEngine_CreatesEngineAndReport() {
        // Given
        when(engineRepository.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(engineRepository.save(any(EngineEntity.class)))
                .thenReturn(testEngine);
        when(reportRepository.save(any(ReportEntity.class)))
                .thenReturn(testReport);
        doNothing().when(webhookPublisher).publishReportSubmitted(any());

        // When
        ReportResponse response = reportService.submitReport(testRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getReportId()).isEqualTo(1L);
        assertThat(response.getEngineInfo().getName()).isEqualTo("TestEngine");
        assertThat(response.getSummary().getTotalTests()).isEqualTo(100);
        
        verify(engineRepository).findByNameAndVersion("TestEngine", "1.0.0");
        verify(engineRepository).save(any(EngineEntity.class));
        verify(reportRepository).save(any(ReportEntity.class));
        verify(webhookPublisher).publishReportSubmitted(any(ReportEntity.class));
    }

    @Test
    void submitReport_ExistingEngine_UsesExistingEngine() {
        // Given
        when(engineRepository.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.of(testEngine));
        when(reportRepository.save(any(ReportEntity.class)))
                .thenReturn(testReport);
        doNothing().when(webhookPublisher).publishReportSubmitted(any());

        // When
        ReportResponse response = reportService.submitReport(testRequest);

        // Then
        assertThat(response).isNotNull();
        verify(engineRepository).findByNameAndVersion("TestEngine", "1.0.0");
        verify(engineRepository, never()).save(any(EngineEntity.class));
        verify(reportRepository).save(any(ReportEntity.class));
    }

    @Test
    void submitReport_WebhookFailure_DoesNotFailSubmission() {
        // Given
        when(engineRepository.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.of(testEngine));
        when(reportRepository.save(any(ReportEntity.class)))
                .thenReturn(testReport);
        doThrow(new RuntimeException("Webhook failed"))
                .when(webhookPublisher).publishReportSubmitted(any());

        // When
        ReportResponse response = reportService.submitReport(testRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getReportId()).isEqualTo(1L);
        verify(reportRepository).save(any(ReportEntity.class));
    }

    @Test
    void getReport_ExistingReport_ReturnsReport() {
        // Given
        when(reportRepository.findById(1L))
                .thenReturn(Optional.of(testReport));

        // When
        ReportResponse response = reportService.getReport(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getReportId()).isEqualTo(1L);
        assertThat(response.getEngineInfo().getName()).isEqualTo("TestEngine");
        verify(reportRepository).findById(1L);
    }

    @Test
    void getReport_NonExistingReport_ThrowsException() {
        // Given
        when(reportRepository.findById(999L))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> reportService.getReport(999L))
                .isInstanceOf(ReportService.ResourceNotFoundException.class)
                .hasMessageContaining("Report not found with ID: 999");
    }

    @Test
    void queryReports_WithPagination_ReturnsPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReportEntity> page = new PageImpl<>(Collections.singletonList(testReport));
        when(reportRepository.findAll(pageable))
                .thenReturn(page);

        // When
        Page<ReportResponse> response = reportService.queryReports(pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReportId()).isEqualTo(1L);
        verify(reportRepository).findAll(pageable);
    }

    @Test
    void getReportsByEngine_ExistingEngine_ReturnsReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<ReportEntity> page = new PageImpl<>(Collections.singletonList(testReport));
        when(reportRepository.findByEngine_Name("TestEngine", pageable))
                .thenReturn(page);

        // When
        Page<ReportResponse> response = reportService.getReportsByEngine("TestEngine", pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        verify(reportRepository).findByEngine_Name("TestEngine", pageable);
    }

    @Test
    void getEngineHistory_ExistingEngine_ReturnsHistory() {
        // Given
        when(reportRepository.findByEngineNameOrderByTimestampDesc("TestEngine"))
                .thenReturn(Collections.singletonList(testReport));

        // When
        List<ReportResponse> response = reportService.getEngineHistory("TestEngine");

        // Then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getReportId()).isEqualTo(1L);
        verify(reportRepository).findByEngineNameOrderByTimestampDesc("TestEngine");
    }

    @Test
    void submitReport_CalculatesComplianceScoreCorrectly() {
        // Given
        when(engineRepository.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.of(testEngine));
        when(reportRepository.save(any(ReportEntity.class)))
                .thenAnswer(invocation -> {
                    ReportEntity saved = invocation.getArgument(0);
                    // Verify compliance score calculation: 1 passed out of 2 = 50%
                    assertThat(saved.getComplianceScore()).isEqualByComparingTo(new BigDecimal("50.00"));
                    return testReport;
                });

        // When
        reportService.submitReport(testRequest);

        // Then
        verify(reportRepository).save(any(ReportEntity.class));
    }
}

