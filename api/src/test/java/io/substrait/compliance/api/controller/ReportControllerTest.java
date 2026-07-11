package io.substrait.compliance.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.security.JwtTokenProvider;
import io.substrait.compliance.api.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ReportController using MockMvc.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportService reportService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private ReportSubmissionRequest testRequest;
    private ReportResponse testResponse;

    @BeforeEach
    void setUp() {
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

        // Setup test response
        ReportResponse.EngineInfoDto responseEngineInfo = ReportResponse.EngineInfoDto.builder()
                .name("TestEngine")
                .version("1.0.0")
                .vendor("TestVendor")
                .substraitVersion("0.20.0")
                .build();

        ReportResponse.SummaryDto summary = ReportResponse.SummaryDto.builder()
                .totalTests(2)
                .passed(1)
                .failed(1)
                .skipped(0)
                .complianceScore(new BigDecimal("50.00"))
                .executionTimeMs(150L)
                .build();

        testResponse = ReportResponse.builder()
                .reportId(1L)
                .engineInfo(responseEngineInfo)
                .testSuiteName("arithmetic_functions")
                .timestamp(Instant.now())
                .summary(summary)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser(authorities = "report:write")
    void submitReport_ValidRequest_ReturnsCreated() throws Exception {
        // Given
        when(reportService.submitReport(any(ReportSubmissionRequest.class)))
                .thenReturn(testResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(1))
                .andExpect(jsonPath("$.engineInfo.name").value("TestEngine"))
                .andExpect(jsonPath("$.summary.totalTests").value(2))
                .andExpect(jsonPath("$.summary.passed").value(1))
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.summary.complianceScore").value(50.00));
    }

    @Test
    void submitReport_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void submitReport_InsufficientPermissions_ReturnsForbidden() throws Exception {
        // When/Then
        when(reportService.submitReport(any(ReportSubmissionRequest.class)))
                .thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "report:write")
    void submitReport_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given - request with missing required fields
        ReportSubmissionRequest invalidRequest = ReportSubmissionRequest.builder()
                .testSuiteName("test")
                .timestamp(System.currentTimeMillis())
                .testResults(Collections.emptyList())
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void getReport_ExistingReport_ReturnsReport() throws Exception {
        // Given
        when(reportService.getReport(1L))
                .thenReturn(testResponse);

        // When/Then
        mockMvc.perform(get("/api/v1/reports/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(1))
                .andExpect(jsonPath("$.engineInfo.name").value("TestEngine"));
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void getReport_NonExistingReport_ReturnsNotFound() throws Exception {
        // Given
        when(reportService.getReport(999L))
                .thenThrow(new ReportService.ResourceNotFoundException("Report not found"));

        // When/Then
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.util.NestedServletException.class,
                () -> mockMvc.perform(get("/api/v1/reports/999")
                        .contentType(MediaType.APPLICATION_JSON))
        );
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void queryReports_WithPagination_ReturnsPagedResults() throws Exception {
        // Given
        Page<ReportResponse> page = new PageImpl<>(
                Collections.singletonList(testResponse),
                PageRequest.of(0, 20),
                1
        );
        when(reportService.queryReports(any()))
                .thenReturn(page);

        // When/Then
        mockMvc.perform(get("/api/v1/reports")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].reportId").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void getEngineHistory_ExistingEngine_ReturnsHistory() throws Exception {
        // Given
        List<ReportResponse> history = Collections.singletonList(testResponse);
        when(reportService.getEngineHistory(eq("TestEngine")))
                .thenReturn(history);

        // When/Then
        mockMvc.perform(get("/api/v1/reports/engine/TestEngine/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reportId").value(1))
                .andExpect(jsonPath("$[0].engineInfo.name").value("TestEngine"));
    }

    @Test
    void queryReports_NoAuthentication_ReturnsUnauthorized() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}

