package io.substrait.compliance.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LeaderboardServiceTest {

    private static final String URL = "https://example.test/leaderboard.json";

    private MockRestServiceServer server;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new LeaderboardService(restTemplate, new ObjectMapper(), URL);
    }

    @Test
    void returnsThePublishedLeaderboardUnchanged() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
            "{\"lastUpdated\":\"2026-10-22T23:22:00Z\",\"engines\":[{\"engineName\":\"DuckDB\",\"passRate\":62.0},"
                + "{\"engineName\":\"DataFusion\",\"passRate\":49.2}]}",
            MediaType.APPLICATION_JSON));

        JsonNode leaderboard = service.getLeaderboard();

        assertThat(leaderboard.get("engines")).hasSize(2);
        assertThat(leaderboard.get("engines").get(1).get("passRate").asDouble()).isEqualTo(49.2);
        server.verify();
    }

    @Test
    void anUnreachableLeaderboardIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.getLeaderboard())
            .isInstanceOf(LeaderboardService.LeaderboardUnavailableException.class);
    }

    @Test
    void aDocumentThatIsNotALeaderboardIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"hello\":1}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getLeaderboard())
            .isInstanceOf(LeaderboardService.LeaderboardUnavailableException.class)
            .hasMessageContaining("not a leaderboard");
    }

    @Test
    void invalidJsonIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> service.getLeaderboard())
            .isInstanceOf(LeaderboardService.LeaderboardUnavailableException.class);
    }
}
