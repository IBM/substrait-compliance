# REST API Guide

Complete guide for the Substrait Compliance REST API - submission, querying, webhooks, and deployment.

## 📋 Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [API Endpoints](#api-endpoints)
- [Authentication](#authentication)
- [Database Schema](#database-schema)
- [Webhook System](#webhook-system)
- [Implementation Guide](#implementation-guide)
- [Deployment](#deployment)
- [Security](#security)
- [Monitoring](#monitoring)

---

## Overview

The Substrait Compliance REST API enables programmatic access to compliance testing results, report submission, and real-time notifications.

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Spring Boot | 2.7.14 |
| Language | Java | 11+ |
| Database | PostgreSQL | 15 |
| Authentication | JWT | - |
| Build Tool | Gradle | 7.x |
| Containerization | Podman | - |
| API Documentation | OpenAPI/Swagger | 3.0 |

### Key Features

1. **Report Submission** - Submit compliance results via REST API
2. **Data Querying** - Programmatic access to compliance data
3. **Webhook Notifications** - Real-time event notifications
4. **Authentication** - JWT tokens and API keys
5. **Rate Limiting** - Fair usage enforcement
6. **Caching** - Optimized performance

---

## Quick Start

### Run with Docker Compose

```bash
cd api
podman-compose up -d
```

### Access the API

- **API Base URL:** http://localhost:8080/api/v1
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health Check:** http://localhost:8080/actuator/health

### Submit Your First Report

```bash
# Get JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# Submit report
curl -X POST http://localhost:8080/api/v1/reports \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d @report.json
```

---

## API Endpoints

### Authentication

#### POST /api/v1/auth/login
Get JWT token for API access.

**Request:**
```json
{
  "username": "admin",
  "password": "password"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600000
}
```

#### POST /api/v1/auth/api-key
Generate API key for CI/CD integration.

**Request:**
```json
{
  "name": "CI Pipeline Key",
  "scopes": ["report:write", "report:read"],
  "expiresAt": "2027-12-31T23:59:59Z"
}
```

### Report Submission

#### POST /api/v1/reports
Submit a compliance report.

**Request:**
```json
{
  "engineName": "DuckDB",
  "engineVersion": "0.10.0",
  "substraitVersion": "0.20.0",
  "testSuiteName": "tpch",
  "timestamp": 1714485600000,
  "totalTests": 22,
  "passed": 20,
  "failed": 2,
  "skipped": 0,
  "testResults": [
    {
      "testId": "tpch-q01",
      "status": "PASSED",
      "durationMs": 127
    }
  ]
}
```

**Response:**
```json
{
  "reportId": 123,
  "status": "ACCEPTED",
  "complianceScore": 90.9
}
```

#### POST /api/v1/reports/batch
Submit multiple reports at once.

### Query Endpoints

#### GET /api/v1/reports
Query compliance reports with filtering.

**Parameters:**
- `engineName` - Filter by engine
- `testSuiteName` - Filter by test suite
- `minScore` - Minimum compliance score
- `maxScore` - Maximum compliance score
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

**Example:**
```bash
GET /api/v1/reports?engineName=DuckDB&minScore=90&page=0&size=10
```

#### GET /api/v1/reports/{reportId}
Get detailed report by ID.

#### GET /api/v1/engines
List all tested engines.

#### GET /api/v1/engines/{engineName}/history
Get historical compliance data for an engine.

#### GET /api/v1/leaderboard
Get compliance leaderboard.

**Response:**
```json
{
  "engines": [
    {
      "rank": 1,
      "engineName": "DuckDB",
      "engineVersion": "0.10.0",
      "complianceScore": 95.5,
      "totalTests": 22,
      "passed": 21,
      "failed": 1
    }
  ]
}
```

#### GET /api/v1/statistics
Get overall compliance statistics.

### Webhook Endpoints

#### POST /api/v1/webhooks
Register a webhook for event notifications.

**Request:**
```json
{
  "url": "https://your-domain.com/webhooks/compliance",
  "secret": "your-webhook-secret",
  "events": ["report.submitted", "report.failed"],
  "active": true
}
```

#### GET /api/v1/webhooks
List all registered webhooks.

#### DELETE /api/v1/webhooks/{webhookId}
Delete a webhook.

#### GET /api/v1/webhooks/{webhookId}/deliveries
Get webhook delivery history.

---

## Authentication

### JWT Tokens

**For interactive users:**
- Short-lived (1 hour default)
- Scope-based permissions
- Refresh token support

**Token Structure:**
```json
{
  "sub": "username",
  "scopes": ["report:write", "report:read"],
  "iat": 1714485600,
  "exp": 1714489200
}
```

### API Keys

**For CI/CD pipelines:**
- Long-lived (configurable expiration)
- Per-key rate limits
- Rotation support

**Usage:**
```bash
curl -H "X-API-Key: your-api-key-here" \
  http://localhost:8080/api/v1/reports
```

### Authorization Scopes

- `report:write` - Submit reports
- `report:read` - Query data
- `webhook:manage` - Manage webhooks
- `admin` - Full access

---

## Database Schema

### Tables

#### engines
Stores engine information.

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

#### compliance_reports
Stores test execution reports.

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### test_results
Stores individual test results.

```sql
CREATE TABLE test_results (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES compliance_reports(id),
    test_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### webhooks
Stores webhook registrations.

```sql
CREATE TABLE webhooks (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    events VARCHAR(255)[] NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### webhook_deliveries
Tracks webhook delivery attempts.

```sql
CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES webhooks(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_code INTEGER,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Webhook System

### Event Types

1. **report.submitted** - New report submitted
2. **report.failed** - Report with low compliance score
3. **leaderboard.updated** - Leaderboard updated

### Webhook Delivery

**Request Format:**
```http
POST /your-webhook-endpoint HTTP/1.1
Content-Type: application/json
X-Event-Type: report.submitted
X-Delivery-ID: 12345
X-Webhook-Signature: base64-encoded-hmac-signature

{
  "reportId": 123,
  "engineName": "DuckDB",
  "complianceScore": 90.9
}
```

### Signature Verification

**Python Example:**
```python
import hmac
import hashlib
import base64

def verify_signature(payload, signature, secret):
    expected = base64.b64encode(
        hmac.new(
            secret.encode('utf-8'),
            payload.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    return hmac.compare_digest(signature, expected)
```

### Retry Logic

Failed deliveries are retried with exponential backoff:

| Attempt | Delay | Total Time |
|---------|-------|------------|
| 1 | Immediate | 0 min |
| 2 | 1 minute | 1 min |
| 3 | 2 minutes | 3 min |
| 4 | 4 minutes | 7 min |
| 5 | 8 minutes | 15 min |

---

## Implementation Guide

### Project Setup

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

dependencies {
    implementation project(':sdk:java')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    runtimeOnly 'org.postgresql:postgresql:42.6.0'
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
}
```

### Application Configuration

**`api/src/main/resources/application.yml`:**
```yaml
spring:
  application:
    name: substrait-compliance-api
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/substrait_compliance}
    username: ${SPRING_DATASOURCE_USERNAME:substrait}
    password: ${SPRING_DATASOURCE_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

server:
  port: 8080

jwt:
  secret: ${JWT_SECRET:change-in-production}
  expiration: 3600000

rate-limit:
  enabled: true
  default-limit: 1000
```

### Build and Run

```bash
# Build the project
cd api
./gradlew build

# Run tests
./gradlew test

# Run application
./gradlew bootRun
```

---

## Deployment

### Containerfile

```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-11:latest

WORKDIR /app
COPY build/libs/substrait-compliance-api-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: substrait_compliance
      POSTGRES_USER: substrait
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  api:
    build: .
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/substrait_compliance
      SPRING_DATASOURCE_USERNAME: substrait
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"

volumes:
  postgres_data:
```

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/substrait_compliance
SPRING_DATASOURCE_USERNAME=substrait
SPRING_DATASOURCE_PASSWORD=your-password

# Security
JWT_SECRET=your-secret-key-min-256-bits
JWT_EXPIRATION=3600000

# Rate Limiting
RATE_LIMIT_ENABLED=true
RATE_LIMIT_DEFAULT=1000

# Webhooks
WEBHOOK_RETRY_ATTEMPTS=5
WEBHOOK_TIMEOUT_SECONDS=30
```

---

## Security

### Best Practices

1. **Use HTTPS** in production
2. **Rotate secrets** regularly
3. **Enable rate limiting**
4. **Validate all inputs**
5. **Use strong JWT secrets** (minimum 256 bits)
6. **Implement audit logging**

### Security Headers

The API automatically includes:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security` (HSTS)
- `Content-Security-Policy`

### Rate Limiting

- Token bucket algorithm
- Per-user/key limits
- Configurable tiers
- HTTP 429 responses with retry-after header

---

## Monitoring

### Health Checks

- `/actuator/health` - Overall health
- `/actuator/health/db` - Database connectivity
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus format

### Metrics (Micrometer)

- Request rates and latencies
- Database connection pool stats
- Cache hit/miss rates
- Webhook delivery success rates
- Rate limit violations

### Logging

```yaml
logging:
  level:
    io.substrait.compliance.api: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
```

---

## Troubleshooting

### Common Issues

#### Database Connection Failed
```bash
# Check PostgreSQL is running
podman ps | grep postgres

# Test connection
psql -h localhost -U substrait -d substrait_compliance
```

#### JWT Token Invalid
- Verify JWT_SECRET matches on both sides
- Check token expiration
- Ensure proper Authorization header format: `Bearer <token>`

#### Webhook Not Delivering
- Verify webhook URL is accessible
- Check webhook is active
- Review delivery history for errors
- Verify signature verification logic

### Debug Mode

Enable debug logging:
```yaml
logging:
  level:
    io.substrait.compliance.api: DEBUG
```

---

## API Reference

### Complete Endpoint List

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/login` | Get JWT token | No |
| POST | `/api/v1/auth/api-key` | Generate API key | Yes |
| POST | `/api/v1/reports` | Submit report | Yes |
| POST | `/api/v1/reports/batch` | Batch submission | Yes |
| GET | `/api/v1/reports` | Query reports | Yes |
| GET | `/api/v1/reports/{id}` | Get report details | Yes |
| GET | `/api/v1/engines` | List engines | Yes |
| GET | `/api/v1/engines/{name}/history` | Engine history | Yes |
| GET | `/api/v1/leaderboard` | Compliance leaderboard | Yes |
| GET | `/api/v1/statistics` | Overall statistics | Yes |
| POST | `/api/v1/webhooks` | Register webhook | Yes |
| GET | `/api/v1/webhooks` | List webhooks | Yes |
| DELETE | `/api/v1/webhooks/{id}` | Delete webhook | Yes |
| GET | `/api/v1/webhooks/{id}/deliveries` | Delivery history | Yes |

---

## Additional Resources

- [REST API Architecture](REST_API_ARCHITECTURE.md) - Detailed architecture diagrams
- [Main README](../README.md) - Project overview
- [Contributing Guide](../CONTRIBUTING.md) - How to contribute
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)

---

**Version:** 1.0  
**Last Updated:** 2026-05-30  
**License:** Apache 2.0  
**Status:** Production Ready ✅
