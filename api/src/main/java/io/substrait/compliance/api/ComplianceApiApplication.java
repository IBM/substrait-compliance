package io.substrait.compliance.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot application class for Substrait Compliance REST API.
 * 
 * <p>This application provides REST endpoints for:
 * <ul>
 *   <li>Submitting compliance reports</li>
 *   <li>Querying compliance data</li>
 *   <li>Managing webhooks</li>
 *   <li>Authentication and authorization</li>
 * </ul>
 * 
 * @see <a href="https://github.com/IBM/substrait-compliance">Substrait Compliance Framework</a>
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class ComplianceApiApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ComplianceApiApplication.class, args);
    }
}

