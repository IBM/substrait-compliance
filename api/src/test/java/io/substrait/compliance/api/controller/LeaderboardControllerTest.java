package io.substrait.compliance.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.substrait.compliance.api.security.JwtTokenProvider;
import io.substrait.compliance.api.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaderboardService leaderboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(authorities = "report:read")
    void getLeaderboard_ReturnsThePublishedDocument() throws Exception {
        when(leaderboardService.getLeaderboard()).thenReturn(objectMapper.readTree(
            "{\"schemaVersion\":2,\"engines\":[{\"rank\":1,\"engineName\":\"DuckDB\",\"passRate\":62.0,"
                + "\"provenance\":{\"source\":\"ci\"}}]}"));

        mockMvc.perform(get("/api/v1/leaderboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engines[0].engineName").value("DuckDB"))
            .andExpect(jsonPath("$.engines[0].passRate").value(62.0))
            .andExpect(jsonPath("$.engines[0].provenance.source").value("ci"));
    }

    @Test
    @WithMockUser(authorities = "report:read")
    void getLeaderboard_Unavailable_Returns503() throws Exception {
        when(leaderboardService.getLeaderboard())
            .thenThrow(new LeaderboardService.LeaderboardUnavailableException("down", null));

        mockMvc.perform(get("/api/v1/leaderboard"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getLeaderboard_Unauthenticated_IsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/leaderboard"))
            .andExpect(status().is4xxClientError());
    }
}
