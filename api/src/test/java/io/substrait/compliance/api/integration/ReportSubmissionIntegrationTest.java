package io.substrait.compliance.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.substrait.compliance.api.ComplianceApiApplication;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.model.entity.ReportEntity;
import io.substrait.compliance.api.repository.ReportRepository;
import io.substrait.compliance.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for report submission with TestContainers.
 */
@SpringBootTest(classes = ComplianceApiApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ReportSubmissionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;
    private ReportSubmissionRequest testRequest;

    @BeforeEach
    void setUp() {
        // Clear database
        reportRepository.deleteAll();

        // Generate auth token with write scope
        authToken = jwtTokenProvider.generateToken("testuser", Arrays.asList("report:write", "report:read"));

        // Setup test request
        ReportSubmissionRequest.EngineInfoDto engineInfo = ReportSubmissionRequest.EngineInfoDto.builder()
                .name("IntegrationTestEngine")
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
                        .status(ReportSubmissionRequest.TestStatus.PASSED)
                        .message("Test passed")
                        .durationMs(75L)
                        .build(),
                ReportSubmissionRequest.TestResultDto.builder()
                        .testId("test3")
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
    void submitReport_EndToEnd_SavesAndRetrievesReport() throws Exception {
        // Submit report
        String response = mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").exists())
                .andExpect(jsonPath("$.engineInfo.name").value("IntegrationTestEngine"))
                .andExpect(jsonPath("$.summary.totalTests").value(3))
                .andExpect(jsonPath("$.summary.passed").value(2))
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.summary.complianceScore").value(66.67))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify database
        List<ReportEntity> reports = reportRepository.findByEngineNameOrderByTimestampDesc("IntegrationTestEngine");
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getTestSuiteName()).isEqualTo("arithmetic_functions");
        assertThat(reports.get(0).getTotalTests()).isEqualTo(3);
        assertThat(reports.get(0).getPassedCount()).isEqualTo(2);
        assertThat(reports.get(0).getFailedCount()).isEqualTo(1);

        // Extract report ID and retrieve it
        Long reportId = reports.get(0).getId();
        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(reportId))
                .andExpect(jsonPath("$.engineInfo.name").value("IntegrationTestEngine"));
    }

    @Test
    void submitMultipleReports_QueryWithPagination_ReturnsCorrectPage() throws Exception {
        // Submit 3 reports
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/reports")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(testRequest)))
                    .andExpect(status().isCreated());
        }

        // Query with pagination
        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void submitReport_GetEngineHistory_ReturnsAllReportsForEngine() throws Exception {
        // Submit 2 reports for same engine
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated());

        // Get engine history
        mockMvc.perform(get("/api/v1/reports/engine/IntegrationTestEngine/history")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].engineInfo.name").value("IntegrationTestEngine"))
                .andExpect(jsonPath("$[1].engineInfo.name").value("IntegrationTestEngine"));
    }

    @Test
    void submitReport_WithoutAuthentication_ReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReport_WithInvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitReport_WithReadOnlyScope_ReturnsForbidden() throws Exception {
        // Generate token with only read scope
        String readOnlyToken = jwtTokenProvider.generateToken("readonly-user", Arrays.asList("report:read"));

        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReport_CreatesEngineIfNotExists() throws Exception {
        // Submit first report - should create engine
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated());

        // Submit second report - should reuse engine
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated());

        // Verify only one engine was created
        List<ReportEntity> reports = reportRepository.findAll();
        assertThat(reports).hasSize(2);
        assertThat(reports.get(0).getEngine().getId())
                .isEqualTo(reports.get(1).getEngine().getId());
    }
}

// Made with Bob
