# API Implementation Guide

Complete implementation guide for the Substrait Compliance REST API, including advanced features, CI/CD integration, and contribution guidelines.

## 📋 Table of Contents

- [Overview](#overview)
- [Implementation Phases](#implementation-phases)
- [Advanced Features](#advanced-features)
- [CI/CD Integration](#cicd-integration)
- [Contributing](#contributing)
- [Testing](#testing)

---

## Overview

This guide covers the complete implementation of the REST API, from initial setup through advanced features and deployment.

### Prerequisites

- Java 17+
- Gradle 7.x+
- PostgreSQL 15
- Podman/Docker
- Git

---

## Implementation Phases

### Phase 1: Foundation (Weeks 1-2)

#### Project Structure
```
api/
├── src/main/java/io/substrait/compliance/api/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   ├── security/
│   ├── webhook/
│   └── config/
└── src/main/resources/
    ├── application.yml
    └── db/migration/
```

#### Database Setup
- Create PostgreSQL database
- Configure Flyway migrations
- Set up JPA entities
- Create repositories

### Phase 2: Core API (Weeks 3-4)

#### Report Submission
- Implement ReportController
- Create ReportService
- Add validation
- Configure error handling

#### Query Endpoints
- Implement QueryController
- Add pagination support
- Create filtering logic
- Optimize database queries

### Phase 3: Security (Week 5)

#### JWT Authentication
- Implement JwtTokenProvider
- Create authentication filter
- Configure Spring Security
- Add scope-based authorization

#### API Keys
- Create API key management
- Implement key rotation
- Add rate limiting per key

### Phase 4: Advanced Features (Week 6)

#### Webhook System
- Implement event publishing
- Create delivery service
- Add retry logic with exponential backoff
- Track delivery history

#### Caching
- Configure Caffeine cache
- Add cache annotations
- Implement cache invalidation
- Monitor cache performance

#### Rate Limiting
- Implement token bucket algorithm
- Add per-user limits
- Configure rate limit tiers
- Return proper HTTP 429 responses

---

## Advanced Features

### Webhook Delivery Service

**Implementation:**
```java
@Service
public class WebhookDeliveryService {
    
    @Async
    public void deliverWebhook(WebhookDelivery delivery) {
        try {
            HttpResponse response = sendWebhook(delivery);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                markAsDelivered(delivery);
            } else {
                scheduleRetry(delivery);
            }
        } catch (Exception e) {
            scheduleRetry(delivery);
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void processRetries() {
        List<WebhookDelivery> pending = findPendingRetries();
        pending.forEach(this::deliverWebhook);
    }
}
```

### Rate Limiting

**Implementation:**
```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) {
        String apiKey = request.getHeader("X-API-Key");
        Bucket bucket = rateLimitService.resolveBucket(apiKey);
        
        if (bucket.tryConsume(1)) {
            return true;
        } else {
            response.setStatus(429);
            response.setHeader("X-Rate-Limit-Retry-After", "60");
            return false;
        }
    }
}
```

### Caching Strategy

**Configuration:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "leaderboard", "statistics", "engines"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES));
        return cacheManager;
    }
}
```

---

## CI/CD Integration

### GitHub Actions Workflow

**`.github/workflows/api-build-test.yml`:**
```yaml
name: API Build and Test

on:
  push:
    branches: [ main ]
    paths:
      - 'api/**'
  pull_request:
    branches: [ main ]
    paths:
      - 'api/**'

jobs:
  build:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: test_db
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build with Gradle
        run: |
          cd api
          ./gradlew build
      
      - name: Run tests
        run: |
          cd api
          ./gradlew test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/test_db
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3
        with:
          files: api/build/reports/jacoco/test/jacocoTestReport.xml
```

### Container Build Workflow

**`.github/workflows/api-container-build.yml`:**
```yaml
name: Build and Push Container

on:
  release:
    types: [published]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Build JAR
        run: |
          cd api
          ./gradlew bootJar
      
      - name: Build container image
        run: |
          cd api
          podman build -t substrait/compliance-api:${{ github.ref_name }} .
      
      - name: Push to registry
        run: |
          echo ${{ secrets.REGISTRY_PASSWORD }} | podman login -u ${{ secrets.REGISTRY_USERNAME }} --password-stdin
          podman push substrait/compliance-api:${{ github.ref_name }}
```

---

## Contributing

### Development Setup

```bash
# Clone repository
git clone https://github.com/IBM/substrait-compliance.git
cd substrait-compliance

# Build SDK
cd sdk/java
./gradlew build publishToMavenLocal

# Build and run API
cd ../../api
./gradlew bootRun
```

### Code Style

**Java:**
- Follow Google Java Style Guide
- Use meaningful variable names
- Add Javadoc for public APIs
- Keep methods under 50 lines

**Example:**
```java
/**
 * Submits a compliance report to the system.
 *
 * @param request the report submission request
 * @return the created report response
 * @throws ValidationException if the request is invalid
 */
@PostMapping
public ResponseEntity<ReportResponse> submitReport(
        @Valid @RequestBody ReportSubmissionRequest request) {
    ReportResponse response = reportService.submitReport(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

### Testing Requirements

- Unit tests for all services
- Integration tests for controllers
- 80%+ code coverage
- All tests must pass before merge

**Example Test:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void submitReport_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validReportJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").exists());
    }
}
```

### Pull Request Process

1. Create feature branch
2. Implement changes
3. Add tests
4. Update documentation
5. Submit PR with description
6. Address review feedback
7. Merge after approval

---

## Testing

### Unit Tests

```bash
cd api
./gradlew test
```

### Integration Tests

```bash
cd api
./gradlew integrationTest
```

### Test Coverage

```bash
cd api
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Performance Tests

```bash
# Using Apache Bench
ab -n 1000 -c 10 http://localhost:8080/api/v1/leaderboard

# Using wrk
wrk -t4 -c100 -d30s http://localhost:8080/api/v1/reports
```

---

## Additional Resources

- [REST API Guide](REST_API_GUIDE.md) - Complete API reference
- [REST API Architecture](REST_API_ARCHITECTURE.md) - Architecture diagrams
- [Main Contributing Guide](../CONTRIBUTING.md) - General contribution guidelines
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)

---

**Version:** 1.0  
**Last Updated:** 2026-05-30  
**Status:** Complete ✅
