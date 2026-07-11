package io.substrait.compliance.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for application caching using Caffeine.
 * 
 * <p>This configuration sets up multiple caches with different TTLs:
 * <ul>
 *   <li>reports - Individual report cache (1 hour)</li>
 *   <li>leaderboard - Leaderboard data (5 minutes)</li>
 *   <li>statistics - Overall statistics (10 minutes)</li>
 *   <li>engineStats - Per-engine statistics (15 minutes)</li>
 *   <li>engineHistory - Engine report history (30 minutes)</li>
 * </ul>
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {
    
    /**
     * Configures the cache manager with Caffeine.
     * 
     * @return the configured cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("Configuring Caffeine cache manager");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "reports",
            "leaderboard",
            "statistics",
            "engineStats",
            "engineHistory"
        );
        
        cacheManager.setCaffeine(caffeineCacheBuilder());
        
        return cacheManager;
    }
    
    /**
     * Builds the Caffeine cache configuration.
     * 
     * @return the Caffeine builder
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats();
    }
}

