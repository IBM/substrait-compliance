# REST API for Substrait Compliance Results - Implementation Plan

## 📋 Overview

This document outlines the plan for adding a REST API service to the Substrait Compliance Framework. The API will enable programmatic submission of compliance reports, querying of compliance data, webhook notifications, and proper authentication/authorization.

## 🎯 Goals

1. **Report Submission** - Allow engines to submit compliance results via REST API
2. **Data Querying** - Enable programmatic access to compliance data
3. **Webhook Notifications** - Real-time notifications for compliance events
4. **Authentication** - Secure API access with JWT tokens
5. **Rate Limiting** - Prevent abuse and ensure fair usage
6. **Caching** - Optimize performance for frequently accessed data

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Applications                       │
│  (Engine CI/CD, Dashboard, CLI Tools, Third-party Apps)     │
└─────────────────────────────────────────────────────────────┘
                            ↓ HTTPS/REST
┌─────────────────────────────────────────────────────────────┐
│                  Spring Boot REST API                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Controllers (REST Endpoints)                        │   │
│  │  • ReportController                                  │   │
│  │  • QueryController                                   │   │
│  │  • WebhookController                                 │   │
│  │  • AuthController                                    │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Security Layer (JWT Authentication)                 │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Service Layer (Business Logic)                      │   │
│  │  • ReportService                                     │   │
│  │  • QueryService                                      │   │
│  │  • WebhookService                                    │   │
│  │  • CacheService                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Repository Layer (Data Access)                      │   │
│  │  • ReportRepository                                  │   │
│  │  • EngineRepository                                  │   │
│  │  • WebhookRepository                                 │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│  • compliance_reports                                        │
│  • test_results                                              │
│  • engines                                                   │
│  • webhooks                                                  │
│  • api_keys                                                  │
└─────────────────────────────────────────────────────────────┘
```

### Module Structure

```
substrait-compliance/
├── sdk/java/                           # Existing SDK (unchanged)
│   └── src/main/java/io/substrait/compliance/
│       ├── ComplianceReport.java
│       ├── ComplianceResult.java
│       ├── TestResult.java
│       └── ...
│
├── api/                                # NEW: REST API Service
│   ├── build.gradle
│   ├── Containerfile
│   ├── docker-compose.yml
│   └── src/
│       ├── main/
│       │   ├── java/io/substrait/compliance/api/
│       │   │   ├── ComplianceApiApplication.java
│       │   │   │
│       │   │   ├── controller/
│       │   │   │   ├── ReportController.java
│       │   │   │   ├── QueryController.java
│       │   │   │   ├── WebhookController.java
│       │   │   │   └── AuthController.java
│       │   │   │
│       │   │   ├── service/
│       │   │   │   ├── ReportService.java
│       │   │   │   ├── QueryService.java
│       │   │   │   ├── WebhookService.java
│       │   │   │   ├── CacheService.java
│       │   │   │   └── RateLimitService.java
│       │   │   │
│       │   │   ├── repository/
│       │   │   │   ├── ReportRepository.java
│       │   │   │   ├── EngineRepository.java
│       │   │   │   ├── TestResultRepository.java
│       │   │   │   └── WebhookRepository.java
│       │   │   │
│       │   │   ├── model/entity/
│       │   │   │   ├── ReportEntity.java
│       │   │   │   ├── EngineEntity.java
│       │   │   │   ├── TestResultEntity.java
│       │   │   │   └── WebhookEntity.java
│       │   │   │
│       │   │   ├── model/dto/
│       │   │   │   ├── ReportSubmissionRequest.java
│       │   │   │   ├── ReportResponse.java
│       │   │   │   ├── QueryRequest.java
│       │   │   │   └── WebhookRequest.java
│       │   │   │
│       │   │   ├── security/
│       │   │   │   ├── JwtTokenProvider.java
│       │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   └── SecurityConfig.java
│       │   │   │
│       │   │   ├── webhook/
│       │   │   │   ├── WebhookEvent.java
│       │   │   │   ├── WebhookPublisher.java
│       │   │   │   └── WebhookDeliveryService.java
│       │   │   │
│       │   │   ├── config/
│       │   │   │   ├── CacheConfig.java
│       │   │   │   ├── RateLimitConfig.java
│       │   │   │   └── OpenApiConfig.java
│       │   │   │
│       │   │   └── exception/
│       │   │       ├── GlobalExceptionHandler.java
│       │   │       └── ApiException.java
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/
│       │           ├── V1__initial_schema.sql
│       │           └── V2__add_webhooks.sql
│       │
│       └── test/
│           └── java/io/substrait/compliance/api/
│               ├── controller/
│               ├── service/
│               └── integration/
│
└── settings.gradle                     # Updated for multi-module
```

## 📊 Database Schema

### Tables

#### 1. `engines`
```sql
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
```

#### 2. `compliance_reports`
```sql
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
```

#### 3. `test_results`
```sql
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
```

#### 4. `webhooks`
```sql
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
```

#### 5. `webhook_deliveries`
```sql
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

#### 6. `api_keys`
```sql
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

## 🔌 REST API Endpoints

### Authentication Endpoints

#### POST `/api/v1/auth/login`
Authenticate and receive JWT token.

**Request:**
```json
{
  "username": "engine-developer",
  "password": "secure-password"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

#### POST `/api/v1/auth/api-key`
Generate a new API key.

**Request:**
```json
{
  "name": "CI/CD Pipeline",
  "description": "API key for automated testing",
  "scopes": ["report:write", "report:read"],
  "expiresInDays": 365
}
```

**Response:**
```json
{
  "apiKey": "sk_live_abc123...",
  "name": "CI/CD Pipeline",
  "scopes": ["report:write", "report:read"],
  "expiresAt": "2027-04-16T00:00:00Z"
}
```

### Report Submission Endpoints

#### POST `/api/v1/reports`
Submit a compliance report.

**Headers:**
```
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

**Request:**
```json
{
  "engineInfo": {
    "name": "DuckDB",
    "version": "0.10.0",
    "vendor": "DuckDB Labs",
    "substraitVersion": "0.80.0"
  },
  "testSuiteName": "tpch",
  "timestamp": 1713225600000,
  "testResults": [
    {
      "testId": "tpch-q1",
      "status": "PASSED",
      "message": null,
      "durationMs": 125
    },
    {
      "testId": "tpch-q2",
      "status": "FAILED",
      "message": "Output mismatch",
      "durationMs": 89
    }
  ],
  "metadata": {
    "ciPipeline": "GitHub Actions",
    "commitSha": "abc123",
    "branch": "main"
  }
}
```

**Response:**
```json
{
  "reportId": 12345,
  "status": "ACCEPTED",
  "summary": {
    "totalTests": 22,
    "passed": 20,
    "failed": 2,
    "skipped": 0,
    "complianceScore": 90.91
  },
  "submittedAt": "2026-04-16T01:00:00Z"
}
```

#### POST `/api/v1/reports/batch`
Submit multiple reports in batch.

**Request:**
```json
{
  "reports": [
    { /* report 1 */ },
    { /* report 2 */ }
  ]
}
```

**Response:**
```json
{
  "accepted": 2,
  "rejected": 0,
  "reportIds": [12345, 12346]
}
```

### Query Endpoints

#### GET `/api/v1/reports`
Query compliance reports with filtering and pagination.

**Query Parameters:**
- `engineName` - Filter by engine name
- `engineVersion` - Filter by engine version
- `testSuite` - Filter by test suite name
- `minScore` - Minimum compliance score (0-100)
- `maxScore` - Maximum compliance score (0-100)
- `fromDate` - Start date (ISO 8601)
- `toDate` - End date (ISO 8601)
- `page` - Page number (default: 0)
- `size` - Page size (default: 20, max: 100)
- `sort` - Sort field (timestamp, score, engineName)
- `order` - Sort order (asc, desc)

**Example:**
```
GET /api/v1/reports?engineName=DuckDB&minScore=80&page=0&size=10&sort=timestamp&order=desc
```

**Response:**
```json
{
  "content": [
    {
      "reportId": 12345,
      "engineInfo": {
        "name": "DuckDB",
        "version": "0.10.0",
        "vendor": "DuckDB Labs"
      },
      "testSuiteName": "tpch",
      "timestamp": "2026-04-16T01:00:00Z",
      "summary": {
        "totalTests": 22,
        "passed": 20,
        "failed": 2,
        "skipped": 0,
        "complianceScore": 90.91
      }
    }
  ],
  "page": {
    "number": 0,
    "size": 10,
    "totalElements": 45,
    "totalPages": 5
  }
}
```

#### GET `/api/v1/reports/{reportId}`
Get detailed report by ID.

**Response:**
```json
{
  "reportId": 12345,
  "engineInfo": {
    "name": "DuckDB",
    "version": "0.10.0",
    "vendor": "DuckDB Labs",
    "substraitVersion": "0.80.0"
  },
  "testSuiteName": "tpch",
  "timestamp": "2026-04-16T01:00:00Z",
  "summary": {
    "totalTests": 22,
    "passed": 20,
    "failed": 2,
    "skipped": 0,
    "complianceScore": 90.91
  },
  "testResults": [
    {
      "testId": "tpch-q1",
      "status": "PASSED",
      "durationMs": 125
    }
  ],
  "metadata": {
    "ciPipeline": "GitHub Actions",
    "commitSha": "abc123"
  }
}
```

#### GET `/api/v1/engines`
List all engines with their latest compliance scores.

**Response:**
```json
{
  "engines": [
    {
      "name": "DuckDB",
      "version": "0.10.0",
      "vendor": "DuckDB Labs",
      "latestScore": 90.91,
      "totalReports": 15,
      "lastReportDate": "2026-04-16T01:00:00Z"
    }
  ]
}
```

#### GET `/api/v1/engines/{engineName}/history`
Get compliance score history for an engine.

**Response:**
```json
{
  "engineName": "DuckDB",
  "history": [
    {
      "version": "0.10.0",
      "timestamp": "2026-04-16T01:00:00Z",
      "score": 90.91,
      "reportId": 12345
    },
    {
      "version": "0.9.0",
      "timestamp": "2026-03-16T01:00:00Z",
      "score": 86.36,
      "reportId": 12300
    }
  ]
}
```

#### GET `/api/v1/leaderboard`
Get compliance leaderboard.

**Query Parameters:**
- `testSuite` - Filter by test suite (optional)
- `limit` - Number of results (default: 10)

**Response:**
```json
{
  "leaderboard": [
    {
      "rank": 1,
      "engineName": "DataFusion",
      "version": "35.0.0",
      "score": 95.45,
      "passedTests": 21,
      "totalTests": 22
    },
    {
      "rank": 2,
      "engineName": "DuckDB",
      "version": "0.10.0",
      "score": 90.91,
      "passedTests": 20,
      "totalTests": 22
    }
  ],
  "generatedAt": "2026-04-16T01:00:00Z"
}
```

#### GET `/api/v1/statistics`
Get overall compliance statistics.

**Response:**
```json
{
  "totalEngines": 5,
  "totalReports": 150,
  "averageScore": 88.5,
  "highestScore": 95.45,
  "lowestScore": 72.73,
  "testSuites": ["tpch"],
  "lastUpdated": "2026-04-16T01:00:00Z"
}
```

### Webhook Endpoints

#### POST `/api/v1/webhooks`
Register a webhook.

**Request:**
```json
{
  "url": "https://example.com/webhooks/compliance",
  "secret": "webhook-secret-key",
  "events": ["report.submitted", "report.failed", "leaderboard.updated"]
}
```

**Response:**
```json
{
  "webhookId": 1,
  "url": "https://example.com/webhooks/compliance",
  "events": ["report.submitted", "report.failed", "leaderboard.updated"],
  "active": true,
  "createdAt": "2026-04-16T01:00:00Z"
}
```

#### GET `/api/v1/webhooks`
List registered webhooks.

**Response:**
```json
{
  "webhooks": [
    {
      "webhookId": 1,
      "url": "https://example.com/webhooks/compliance",
      "events": ["report.submitted"],
      "active": true,
      "createdAt": "2026-04-16T01:00:00Z"
    }
  ]
}
```

#### DELETE `/api/v1/webhooks/{webhookId}`
Delete a webhook.

**Response:**
```json
{
  "message": "Webhook deleted successfully"
}
```

#### GET `/api/v1/webhooks/{webhookId}/deliveries`
Get webhook delivery history.

**Response:**
```json
{
  "deliveries": [
    {
      "deliveryId": 100,
      "eventType": "report.submitted",
      "status": "SUCCESS",
      "responseCode": 200,
      "attemptCount": 1,
      "deliveredAt": "2026-04-16T01:00:00Z"
    }
  ]
}
```

### Webhook Event Payloads

#### `report.submitted`
```json
{
  "event": "report.submitted",
  "timestamp": "2026-04-16T01:00:00Z",
  "data": {
    "reportId": 12345,
    "engineName": "DuckDB",
    "engineVersion": "0.10.0",
    "testSuiteName": "tpch",
    "complianceScore": 90.91,
    "passed": 20,
    "failed": 2,
    "skipped": 0
  }
}
```

#### `report.failed`
```json
{
  "event": "report.failed",
  "timestamp": "2026-04-16T01:00:00Z",
  "data": {
    "reportId": 12346,
    "engineName": "DuckDB",
    "engineVersion": "0.10.0",
    "testSuiteName": "tpch",
    "complianceScore": 68.18,
    "failedTests": ["tpch-q2", "tpch-q8", "tpch-q15"]
  }
}
```

#### `leaderboard.updated`
```json
{
  "event": "leaderboard.updated",
  "timestamp": "2026-04-16T01:00:00Z",
  "data": {
    "topEngines": [
      {"name": "DataFusion", "score": 95.45},
      {"name": "DuckDB", "score": 90.91}
    ]
  }
}
```

## 🔐 Authentication & Authorization

### JWT Token Structure

```json
{
  "sub": "user@example.com",
  "scopes": ["report:write", "report:read", "webhook:manage"],
  "iat": 1713225600,
  "exp": 1713229200
}
```

### Scopes

- `report:write` - Submit compliance reports
- `report:read` - Query compliance data
- `webhook:manage` - Manage webhooks
- `admin` - Full administrative access

### API Key Authentication

Alternative to JWT for CI/CD pipelines:

```bash
curl -H "X-API-Key: sk_live_abc123..." \
     https://api.substrait.io/v1/reports
```

## ⚡ Rate Limiting

### Strategy

- **Token Bucket Algorithm** using Bucket4j
- **Per API Key/User** limits
- **Tiered Limits:**
  - Free tier: 100 requests/hour
  - Standard tier: 1,000 requests/hour
  - Premium tier: 10,000 requests/hour

### Headers

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 950
X-RateLimit-Reset: 1713229200
```

### Rate Limit Exceeded Response

```json
{
  "error": "rate_limit_exceeded",
  "message": "Rate limit exceeded. Try again in 3600 seconds.",
  "retryAfter": 3600
}
```

## 💾 Caching Strategy

### Cache Layers

1. **Application Cache** (Caffeine)
   - Leaderboard data (TTL: 5 minutes)
   - Engine statistics (TTL: 10 minutes)
   - Frequently accessed reports (TTL: 1 hour)

2. **Distributed Cache** (Redis - Optional)
   - For multi-instance deployments
   - Session data
   - Rate limit counters

### Cache Keys

```
leaderboard:tpch
engine:DuckDB:latest
report:12345
statistics:global
```

### Cache Invalidation

- On new report submission
- On webhook configuration changes
- Manual invalidation via admin endpoint

## 🐳 Containerization (Podman)

### Containerfile

```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-11:latest

LABEL maintainer="substrait-compliance@example.com"
LABEL description="Substrait Compliance REST API"

WORKDIR /app

# Copy application JAR
COPY build/libs/substrait-compliance-api-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: substrait-db
    environment:
      POSTGRES_DB: substrait_compliance
      POSTGRES_USER: substrait
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U substrait"]
      interval: 10s
      timeout: 5s
      retries: 5

  api:
    build: .
    container_name: substrait-api
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/substrait_compliance
      SPRING_DATASOURCE_USERNAME: substrait
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres_data:
```

### Podman Commands

```bash
# Build image
podman build -t substrait-compliance-api:latest .

# Run with compose
podman-compose up -d

# View logs
podman logs -f substrait-api

# Stop services
podman-compose down
```

## 🧪 Testing Strategy

### Unit Tests

- Controller tests with MockMvc
- Service layer tests with Mockito
- Repository tests with H2 in-memory database

### Integration Tests

- Full API tests with TestContainers
- Database integration tests
- Webhook delivery tests

### Test Coverage Goals

- Line coverage: >80%
- Branch coverage: >70%
- Critical paths: 100%

## 📚 API Documentation

### OpenAPI/Swagger

- Auto-generated from annotations
- Interactive API explorer at `/swagger-ui.html`
- OpenAPI spec at `/v3/api-docs`

### Example Swagger Configuration

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Substrait Compliance API",
        version = "1.0.0",
        description = "REST API for Substrait compliance testing",
        contact = @Contact(
            name = "Substrait Team",
            email = "compliance@substrait.io"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development"),
        @Server(url = "https://api.substrait.io", description = "Production")
    }
)
public class OpenApiConfig {
}
```

## 🚀 Deployment

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/substrait_compliance
SPRING_DATASOURCE_USERNAME=substrait
SPRING_DATASOURCE_PASSWORD=secure-password

# JWT
JWT_SECRET=your-secret-key-min-256-bits
JWT_EXPIRATION=3600000

# Rate Limiting
RATE_LIMIT_ENABLED=true
RATE_LIMIT_DEFAULT=1000

# Caching
SPRING_CACHE_TYPE=caffeine
CACHE_TTL_MINUTES=10

# Webhooks
WEBHOOK_RETRY_ATTEMPTS=3
WEBHOOK_TIMEOUT_SECONDS=30
```

### Production Checklist

- [ ] Configure production database
- [ ] Set strong JWT secret
- [ ] Enable HTTPS/TLS
- [ ] Configure rate limiting
- [ ] Set up monitoring (Prometheus/Grafana)
- [ ] Configure logging (ELK stack)
- [ ] Set up backup strategy
- [ ] Configure firewall rules
- [ ] Enable CORS for allowed origins
- [ ] Set up health checks
- [ ] Configure auto-scaling

## 📊 Monitoring & Observability

### Metrics (Micrometer + Prometheus)

- Request count and latency
- Database connection pool metrics
- Cache hit/miss rates
- Webhook delivery success rates
- Rate limit violations

### Health Checks

- `/actuator/health` - Overall health
- `/actuator/health/db` - Database connectivity
- `/actuator/health/diskSpace` - Disk space
- `/actuator/metrics` - Application metrics

### Logging

- Structured JSON logging
- Request/response logging
- Error tracking with stack traces
- Audit logging for sensitive operations

## 🔄 CI/CD Integration

### GitHub Actions Workflow

```yaml
name: API Build and Deploy

on:
  push:
    branches: [main]
    paths:
      - 'api/**'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          
      - name: Build with Gradle
        run: |
          cd api
          ./gradlew build
          
      - name: Run tests
        run: |
          cd api
          ./gradlew test
          
      - name: Build Podman image
        run: |
          cd api
          podman build -t substrait-api:${{ github.sha }} .
          
      - name: Push to registry
        run: |
          podman push substrait-api:${{ github.sha }}
```

## 📝 Implementation Phases

### Phase 1: Foundation (Weeks 1-2)
- Set up Spring Boot project structure
- Configure database and JPA entities
- Implement basic CRUD operations
- Add JWT authentication

### Phase 2: Core API (Weeks 3-4)
- Implement report submission endpoints
- Implement query endpoints
- Add pagination and filtering
- Write unit tests

### Phase 3: Advanced Features (Weeks 5-6)
- Implement webhook system
- Add rate limiting
- Add caching layer
- Write integration tests

### Phase 4: Documentation & Deployment (Week 7)
- Generate OpenAPI documentation
- Create Containerfile and compose files
- Write deployment documentation
- Set up CI/CD workflows

### Phase 5: Testing & Refinement (Week 8)
- End-to-end testing
- Performance testing
- Security audit
- Bug fixes and optimizations

## 🎯 Success Criteria

- [ ] All REST endpoints functional and tested
- [ ] JWT authentication working correctly
- [ ] Rate limiting preventing abuse
- [ ] Webhooks delivering reliably
- [ ] API documentation complete and accurate
- [ ] Podman containers building and running
- [ ] Integration tests passing (>95%)
- [ ] Performance benchmarks met (p95 < 200ms)
- [ ] Security scan passing (no critical vulnerabilities)
- [ ] CI/CD pipeline automated

## 📚 References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security JWT](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Bucket4j Rate Limiting](https://github.com/bucket4j/bucket4j)
- [TestContainers](https://www.testcontainers.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Podman Documentation](https://docs.podman.io/)

---

**Document Version:** 1.0  
**Last Updated:** 2026-04-16  
**Status:** Ready for Review