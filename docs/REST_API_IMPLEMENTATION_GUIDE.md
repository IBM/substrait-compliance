# REST API Implementation Guide

## 📚 Quick Reference

This guide provides step-by-step instructions for implementing the REST API for Substrait Compliance Results.

**Related Documents:**
- [`REST_API_PLAN.md`](REST_API_PLAN.md) - Detailed specifications and requirements
- [`REST_API_ARCHITECTURE.md`](REST_API_ARCHITECTURE.md) - Architecture diagrams and flows

---

## 🎯 Implementation Phases

### Phase 1: Project Setup (Week 1)

#### 1.1 Create Multi-Module Gradle Project

**Update root `settings.gradle`:**
```gradle
rootProject.name = 'substrait-compliance'
include 'sdk:java'
include 'api'
```

**Create `api/build.gradle`:**
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '2.7.14'
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
}

group = 'io.substrait'
version = '1.0.0-SNAPSHOT'
sourceCompatibility = '11'

repositories {
    mavenCentral()
}

dependencies {
    // SDK dependency
    implementation project(':sdk:java')
    
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    
    // Database
    runtimeOnly 'org.postgresql:postgresql:42.6.0'
    runtimeOnly 'com.h2database:h2' // For testing
    
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'
    
    // Rate Limiting
    implementation 'com.github.vladimir-bukhtoyarov:bucket4j-core:7.6.0'
    
    // Caching
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
    
    // OpenAPI/Swagger
    implementation 'org.springdoc:springdoc-openapi-ui:1.7.0'
    
    // Flyway for migrations
    implementation 'org.flywaydb:flyway-core:9.22.0'
    
    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:testcontainers:1.19.0'
    testImplementation 'org.testcontainers:postgresql:1.19.0'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.0'
}

test {
    useJUnitPlatform()
}
```

#### 1.2 Create Application Structure

```bash
mkdir -p api/src/main/java/io/substrait/compliance/api/{controller,service,repository,model/{entity,dto},security,webhook,config,exception}
mkdir -p api/src/main/resources/db/migration
mkdir -p api/src/test/java/io/substrait/compliance/api/{controller,service,integration}
```

#### 1.3 Create Main Application Class

**`api/src/main/java/io/substrait/compliance/api/ComplianceApiApplication.java`:**
```java
package io.substrait.compliance.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class ComplianceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComplianceApiApplication.class, args);
    }
}
```

#### 1.4 Create Application Configuration

**`api/src/main/resources/application.yml`:**
```yaml
spring:
  application:
    name: substrait-compliance-api
  
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/substrait_compliance}
    username: ${SPRING_DATASOURCE_USERNAME:substrait}
    password: ${SPRING_DATASOURCE_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  flyway:
    enabled: true
    baseline-on-migrate: true
  
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m

server:
  port: 8080
  compression:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

jwt:
  secret: ${JWT_SECRET:your-secret-key-change-in-production}
  expiration: ${JWT_EXPIRATION:3600000}

rate-limit:
  enabled: ${RATE_LIMIT_ENABLED:true}
  default-limit: ${RATE_LIMIT_DEFAULT:1000}

webhook:
  retry-attempts: ${WEBHOOK_RETRY_ATTEMPTS:3}
  timeout-seconds: ${WEBHOOK_TIMEOUT_SECONDS:30}

logging:
  level:
    io.substrait.compliance.api: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
```

---

### Phase 2: Database Layer (Week 2)

#### 2.1 Create Database Migration

**`api/src/main/resources/db/migration/V1__initial_schema.sql`:**
```sql
-- Engines table
CREATE TABLE engines (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(100) NOT NULL,
    vendor VARCHAR(255),
    substrait_version VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, version)
);

-- Compliance reports table
CREATE TABLE compliance_reports (
    id BIGSERIAL PRIMARY KEY,
    engine_id BIGINT NOT NULL REFERENCES engines(id),
    test_suite_name VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    total_tests INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    compliance_score DECIMAL(5,2) NOT NULL,
    execution_time_ms BIGINT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (engine_id) REFERENCES engines(id) ON DELETE CASCADE
);

CREATE INDEX idx_reports_engine ON compliance_reports(engine_id);
CREATE INDEX idx_reports_suite ON compliance_reports(test_suite_name);
CREATE INDEX idx_reports_timestamp ON compliance_reports(timestamp DESC);
CREATE INDEX idx_reports_score ON compliance_reports(compliance_score DESC);

-- Test results table
CREATE TABLE test_results (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES compliance_reports(id),
    test_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (report_id) REFERENCES compliance_reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_results_report ON test_results(report_id);
CREATE INDEX idx_test_results_status ON test_results(status);

-- API keys table
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    scopes VARCHAR(100)[] NOT NULL,
    rate_limit INTEGER NOT NULL DEFAULT 1000,
    active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_active ON api_keys(active);
```

**`api/src/main/resources/db/migration/V2__add_webhooks.sql`:**
```sql
-- Webhooks table
CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    events VARCHAR(255)[] NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhooks_active ON webhooks(active);

-- Webhook deliveries table
CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES webhooks(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_code INTEGER,
    response_body TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE
);

CREATE INDEX idx_deliveries_webhook ON webhook_deliveries(webhook_id);
CREATE INDEX idx_deliveries_status ON webhook_deliveries(status);
```

#### 2.2 Create JPA Entities

**Example: `api/src/main/java/io/substrait/compliance/api/model/entity/ReportEntity.java`:**
```java
package io.substrait.compliance.api.model.entity;

import org.hibernate.annotations.Type;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "compliance_reports")
public class ReportEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engine_id", nullable = false)
    private EngineEntity engine;
    
    @Column(name = "test_suite_name", nullable = false)
    private String testSuiteName;
    
    @Column(nullable = false)
    private Long timestamp;
    
    @Column(name = "total_tests", nullable = false)
    private Integer totalTests;
    
    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;
    
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;
    
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount;
    
    @Column(name = "compliance_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal complianceScore;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestResultEntity> testResults = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    
    // Getters and setters
}
```

#### 2.3 Create Repositories

**Example: `api/src/main/java/io/substrait/compliance/api/repository/ReportRepository.java`:**
```java
package io.substrait.compliance.api.repository;

import io.substrait.compliance.api.model.entity.ReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
    
    Page<ReportEntity> findByEngine_Name(String engineName, Pageable pageable);
    
    Page<ReportEntity> findByTestSuiteName(String testSuiteName, Pageable pageable);
    
    @Query("SELECT r FROM ReportEntity r WHERE r.complianceScore >= :minScore AND r.complianceScore <= :maxScore")
    Page<ReportEntity> findByScoreRange(BigDecimal minScore, BigDecimal maxScore, Pageable pageable);
    
    @Query("SELECT r FROM ReportEntity r WHERE r.engine.name = :engineName ORDER BY r.timestamp DESC")
    List<ReportEntity> findLatestByEngineName(String engineName, Pageable pageable);
}
```

---

### Phase 3: Security Layer (Week 3)

#### 3.1 JWT Token Provider

**`api/src/main/java/io/substrait/compliance/api/security/JwtTokenProvider.java`:**
```java
package io.substrait.compliance.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    
    private final SecretKey key;
    private final long expiration;
    
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }
    
    public String generateToken(String username, List<String> scopes) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .setSubject(username)
                .claim("scopes", scopes)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

#### 3.2 Security Configuration

**`api/src/main/java/io/substrait/compliance/api/security/SecurityConfig.java`:**
```java
package io.substrait.compliance.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                .antMatchers("/api/v1/auth/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .antMatchers("/api/v1/reports/**").hasAuthority("report:write")
                .antMatchers("/api/v1/query/**").hasAuthority("report:read")
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

### Phase 4: Service & Controller Layer (Week 4)

#### 4.1 Report Service

**`api/src/main/java/io/substrait/compliance/api/service/ReportService.java`:**
```java
package io.substrait.compliance.api.service;

import io.substrait.compliance.ComplianceReport;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.model.entity.ReportEntity;
import io.substrait.compliance.api.repository.ReportRepository;
import io.substrait.compliance.api.webhook.WebhookPublisher;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    
    private final ReportRepository reportRepository;
    private final WebhookPublisher webhookPublisher;
    
    public ReportService(ReportRepository reportRepository, 
                        WebhookPublisher webhookPublisher) {
        this.reportRepository = reportRepository;
        this.webhookPublisher = webhookPublisher;
    }
    
    @Transactional
    @CacheEvict(value = {"leaderboard", "statistics"}, allEntries = true)
    public ReportResponse submitReport(ReportSubmissionRequest request) {
        // Convert DTO to entity
        ReportEntity entity = convertToEntity(request);
        
        // Save to database
        ReportEntity saved = reportRepository.save(entity);
        
        // Publish webhook event
        webhookPublisher.publishReportSubmitted(saved);
        
        // Return response
        return convertToResponse(saved);
    }
    
    // Helper methods...
}
```

#### 4.2 Report Controller

**`api/src/main/java/io/substrait/compliance/api/controller/ReportController.java`:**
```java
package io.substrait.compliance.api.controller;

import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Compliance report submission endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    
    private final ReportService reportService;
    
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
    
    @PostMapping
    @Operation(summary = "Submit a compliance report")
    public ResponseEntity<ReportResponse> submitReport(
            @Valid @RequestBody ReportSubmissionRequest request) {
        ReportResponse response = reportService.submitReport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{reportId}")
    @Operation(summary = "Get report by ID")
    public ResponseEntity<ReportResponse> getReport(@PathVariable Long reportId) {
        ReportResponse response = reportService.getReport(reportId);
        return ResponseEntity.ok(response);
    }
}
```

---

### Phase 5: Advanced Features (Weeks 5-6)

#### 5.1 Webhook System

**`api/src/main/java/io/substrait/compliance/api/webhook/WebhookPublisher.java`:**
```java
package io.substrait.compliance.api.webhook;

import io.substrait.compliance.api.model.entity.ReportEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class WebhookPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public WebhookPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    public void publishReportSubmitted(ReportEntity report) {
        WebhookEvent event = new WebhookEvent(
            this,
            "report.submitted",
            createPayload(report)
        );
        eventPublisher.publishEvent(event);
    }
    
    private Map<String, Object> createPayload(ReportEntity report) {
        // Create webhook payload
    }
}
```

#### 5.2 Rate Limiting

**`api/src/main/java/io/substrait/compliance/api/config/RateLimitConfig.java`:**
```java
package io.substrait.compliance.api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {
    
    @Value("${rate-limit.default-limit}")
    private int defaultLimit;
    
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    public Bucket resolveBucket(String apiKey) {
        return cache.computeIfAbsent(apiKey, k -> createBucket());
    }
    
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
            defaultLimit,
            Refill.intervally(defaultLimit, Duration.ofHours(1))
        );
        return Bucket4j.builder().addLimit(limit).build();
    }
}
```

---

### Phase 6: Testing (Week 7)

#### 6.1 Unit Tests

**Example: `api/src/test/java/io/substrait/compliance/api/service/ReportServiceTest.java`:**
```java
package io.substrait.compliance.api.service;

import io.substrait.compliance.api.repository.ReportRepository;
import io.substrait.compliance.api.webhook.WebhookPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    
    @Mock
    private ReportRepository reportRepository;
    
    @Mock
    private WebhookPublisher webhookPublisher;
    
    @InjectMocks
    private ReportService reportService;
    
    @Test
    void submitReport_shouldSaveAndPublishEvent() {
        // Test implementation
    }
}
```

#### 6.2 Integration Tests

**Example: `api/src/test/java/io/substrait/compliance/api/integration/ReportApiIntegrationTest.java`:**
```java
package io.substrait.compliance.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportApiIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void submitReport_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isCreated());
    }
}
```

---

### Phase 7: Containerization (Week 8)

#### 7.1 Containerfile

**`api/Containerfile`:**
```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-11:latest

LABEL maintainer="substrait-compliance@example.com"
LABEL description="Substrait Compliance REST API"
LABEL version="1.0.0"

WORKDIR /app

# Copy application JAR
COPY build/libs/substrait-compliance-api-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

#### 7.2 Docker Compose

**`api/docker-compose.yml`:**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: substrait-db
    environment:
      POSTGRES_DB: substrait_compliance
      POSTGRES_USER: substrait
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U substrait"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - substrait-network

  api:
    build:
      context: .
      dockerfile: Containerfile
    container_name: substrait-api
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/substrait_compliance
      SPRING_DATASOURCE_USERNAME: substrait
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-changeme}
      JWT_SECRET: ${JWT_SECRET:-change-this-in-production}
      RATE_LIMIT_ENABLED: "true"
      RATE_LIMIT_DEFAULT: "1000"
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - substrait-network

volumes:
  postgres_data:

networks:
  substrait-network:
    driver: bridge
```

---

## 🚀 Quick Start Commands

### Build and Run Locally

```bash
# Build the project
cd api
./gradlew build

# Run tests
./gradlew test

# Run application
./gradlew bootRun

# Or run JAR directly
java -jar build/libs/substrait-compliance-api-*.jar
```

### Build and Run with Podman

```bash
# Build image
cd api
podman build -t substrait-compliance-api:latest -f Containerfile .

# Run with compose
podman-compose up -d

# View logs
podman logs -f substrait-api

# Stop services
podman-compose down
```

### Access the API

- **API Base URL:** http://localhost:8080/api/v1
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health Check:** http://localhost:8080/actuator/health
- **Metrics:** http://localhost:8080/actuator/metrics

---

## 📋 Implementation Checklist

- [ ] Multi-module Gradle setup
- [ ] Spring Boot application structure
- [ ] Database schema and migrations
- [ ] JPA entities and repositories
- [ ] JWT authentication
- [ ] Report submission endpoints
- [ ] Query endpoints
- [ ] Webhook system
- [ ] Rate limiting
- [ ] Caching
- [ ] OpenAPI documentation
- [ ] Unit tests (>80% coverage)
- [ ] Integration tests
- [ ] Containerfile
- [ ] docker-compose.yml
- [ ] CI/CD workflow
- [ ] Deployment documentation

---

## 📚 Additional Resources

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Spring Security](https://docs.spring.io/spring-security/reference/)
- [Flyway Migrations](https://flywaydb.org/documentation/)
- [TestContainers](https://www.testcontainers.org/)
- [Podman Documentation](https://docs.podman.io/)

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-16  
**Status:** Ready for Implementation