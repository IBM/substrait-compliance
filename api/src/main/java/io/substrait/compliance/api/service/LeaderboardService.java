package io.substrait.compliance.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Serves the published compliance leaderboard.
 *
 * <p>The leaderboard is computed in one place only: the repository's Compliance Leaderboard
 * workflow (scripts/generate_leaderboard.py), which publishes leaderboard.json on GitHub Pages
 * (docs/COMPLIANCE_LEADERBOARD.md). This service returns that document as published, so the
 * API never computes a second, possibly different, ranking. It is cached for five minutes
 * (cache "leaderboard", {@link io.substrait.compliance.api.config.CacheConfig}).
 */
@Service
@Slf4j
public class LeaderboardService {

    static final String DEFAULT_URL = "https://ibm.github.io/substrait-compliance/leaderboard.json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String url;

    @Autowired
    public LeaderboardService(
            ObjectMapper objectMapper,
            @Value("${leaderboard.url:" + DEFAULT_URL + "}") String url,
            @Value("${leaderboard.timeout-seconds:10}") int timeoutSeconds) {
        this(newRestTemplate(timeoutSeconds), objectMapper, url);
    }

    LeaderboardService(RestTemplate restTemplate, ObjectMapper objectMapper, String url) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.url = url;
    }

    private static RestTemplate newRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(factory);
    }

    /**
     * The published leaderboard document: rankings, per-engine categories, suites and
     * provenance, exactly as on GitHub Pages.
     *
     * @throws LeaderboardUnavailableException if it cannot be fetched or is not a leaderboard
     */
    @Cacheable("leaderboard")
    public JsonNode getLeaderboard() {
        String body;
        try {
            body = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.warn("Could not fetch the published leaderboard from {}: {}", url, e.getMessage());
            throw new LeaderboardUnavailableException("The published leaderboard could not be fetched", e);
        }
        JsonNode leaderboard;
        try {
            leaderboard = body == null ? null : objectMapper.readTree(body);
        } catch (Exception e) {
            throw new LeaderboardUnavailableException("The published leaderboard is not valid JSON", e);
        }
        if (leaderboard == null || !leaderboard.path("engines").isArray()) {
            throw new LeaderboardUnavailableException("The published document is not a leaderboard", null);
        }
        return leaderboard;
    }

    /** The published leaderboard is not available (HTTP 503). */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class LeaderboardUnavailableException extends RuntimeException {
        public LeaderboardUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
