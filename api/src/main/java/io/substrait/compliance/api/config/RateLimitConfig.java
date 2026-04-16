package io.substrait.compliance.api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for API rate limiting using Bucket4j.
 * 
 * <p>This configuration implements the token bucket algorithm to limit
 * the number of requests per user/API key within a time window.
 * 
 * <p>Features:
 * <ul>
 *   <li>Per-user/API key rate limiting</li>
 *   <li>Configurable limits and refill rates</li>
 *   <li>In-memory bucket storage</li>
 *   <li>Automatic token refill</li>
 * </ul>
 */
@Configuration
@Slf4j
public class RateLimitConfig {
    
    @Value("${rate-limit.enabled:true}")
    private boolean enabled;
    
    @Value("${rate-limit.default-limit:1000}")
    private int defaultLimit;
    
    @Value("${rate-limit.refill-duration-minutes:60}")
    private int refillDurationMinutes;
    
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    /**
     * Resolves a rate limit bucket for the given key (username or API key).
     * 
     * @param key the user identifier or API key
     * @return the rate limit bucket
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createBucket(defaultLimit));
    }
    
    /**
     * Resolves a rate limit bucket with a custom limit.
     * 
     * @param key the user identifier or API key
     * @param limit the custom rate limit
     * @return the rate limit bucket
     */
    public Bucket resolveBucket(String key, int limit) {
        return cache.computeIfAbsent(key, k -> createBucket(limit));
    }
    
    /**
     * Creates a new rate limit bucket with the specified capacity.
     * 
     * @param capacity the maximum number of tokens
     * @return the configured bucket
     */
    private Bucket createBucket(int capacity) {
        Bandwidth limit = Bandwidth.classic(
            capacity,
            Refill.intervally(capacity, Duration.ofMinutes(refillDurationMinutes))
        );
        
        log.debug("Created rate limit bucket: capacity={}, refill={}min", capacity, refillDurationMinutes);
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    /**
     * Checks if rate limiting is enabled.
     * 
     * @return true if rate limiting is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the default rate limit.
     * 
     * @return the default limit
     */
    public int getDefaultLimit() {
        return defaultLimit;
    }
    
    /**
     * Clears the rate limit cache for a specific key.
     * 
     * @param key the key to clear
     */
    public void clearBucket(String key) {
        cache.remove(key);
        log.debug("Cleared rate limit bucket for key: {}", key);
    }
    
    /**
     * Clears all rate limit buckets.
     */
    public void clearAllBuckets() {
        cache.clear();
        log.info("Cleared all rate limit buckets");
    }
}

// Made with Bob
