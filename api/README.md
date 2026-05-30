# Substrait Compliance API

REST API for programmatic submission and querying of Substrait compliance test results.

> Status: pre-release API surface. The module is buildable and locally deployable, but it should not yet be presented as a stable hosted service contract until release validation, support ownership, and production operations are finalized.

## Features

The following capabilities exist in the current module and are suitable for local evaluation and contributor testing:

- ✅ **Report Submission** - Submit compliance test results via REST API
- ✅ **Query Interface** - Query compliance data with pagination and filtering
- ✅ **JWT Authentication Hooks** - JWT-based authentication code paths and configuration
- ✅ **Rate Limiting Hooks** - Token bucket configuration for API throttling
- ✅ **Response Caching** - In-memory caching with Caffeine
- ✅ **Webhook Notifications** - Event-driven notifications for report events
- ✅ **OpenAPI Documentation** - Interactive API documentation with Swagger UI
- ✅ **Database Migrations** - Versioned schema management with Flyway
- ✅ **Health Checks** - Liveness and readiness endpoints for deployment integration
- ✅ **Metrics Export** - Prometheus metrics for monitoring

## Support Boundary

The current API module is intended for:
- local development
- CI validation
- contributor experimentation
- reference deployment work

The current API module is **not yet declared as**:
- a hosted public service
- a compatibility-guaranteed long-term API contract
- a fully production-certified deployment artifact with on-call support

## Deployment Modes

### 1. Local Development
Use this mode when iterating on code, tests, and schema changes.

### 2. Containerized Evaluation
Use [`docker-compose.yml`](api/docker-compose.yml) and [`Containerfile`](api/Containerfile) for reproducible local or staging-style evaluation.

### 3. Production-Style Validation
Before calling the API production-ready in your environment, validate:
- database migration behavior against your target PostgreSQL version
- JWT secret provisioning and rotation procedures
- reverse-proxy / TLS termination behavior
- webhook delivery and retry behavior against real endpoints
- metrics, health probes, and log forwarding in your deployment platform

## Quick Start

### Using Docker Compose (Recommended)

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Generate JWT secret
export JWT_SECRET=$(openssl rand -base64 32)
echo "JWT_SECRET=$JWT_SECRET" >> .env

# 3. Start services
docker-compose up -d

# 4. Check health
curl http://localhost:8080/actuator/health

# 5. View API docs
open http://localhost:8080/swagger-ui.html
```

### Local Development

```bash
# 1. Generate local secrets for development
export DEV_DB_PASSWORD=$(openssl rand -base64 24)
export JWT_SECRET=$(openssl rand -base64 32)

# 2. Start PostgreSQL
docker run -d --name postgres \
  -e POSTGRES_DB=substrait_compliance \
  -e POSTGRES_USER=substrait \
  -e POSTGRES_PASSWORD="$DEV_DB_PASSWORD" \
  -p 5432:5432 \
  postgres:15-alpine

# 3. Configure application
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/substrait_compliance
export SPRING_DATASOURCE_USERNAME=substrait
export SPRING_DATASOURCE_PASSWORD="$DEV_DB_PASSWORD"

# 4. Run application
./gradlew bootRun

# 4. Run tests
./gradlew test
```

## Documentation

- **[Deployment Guide](DEPLOYMENT.md)** - Complete deployment instructions
- **[API Usage Guide](API_USAGE.md)** - API endpoints and examples
- **[Swagger UI](http://localhost:8080/swagger-ui.html)** - Interactive API documentation
- **[Implementation Plan](../API_IMPLEMENTATION_PLAN.md)** - Architecture and design decisions
- **[Security Policy](../SECURITY.md)** - Verified vs. operator-dependent security guarantees

## Project Structure

```
api/
├── src/
│   ├── main/
│   │   ├── java/io/substrait/compliance/api/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── model/           # DTOs and entities
│   │   │   ├── repository/      # Data access layer
│   │   │   ├── security/        # Authentication & authorization
│   │   │   ├── service/         # Business logic
│   │   │   └── webhook/         # Webhook system
│   │   └── resources/
│   │       ├── application.yml  # Application configuration
│   │       └── db/migration/    # Flyway migrations
│   └── test/
│       ├── java/                # Unit and integration tests
│       └── resources/           # Test configuration
├── build.gradle                 # Build configuration
├── Containerfile               # Container image definition
├── docker-compose.yml          # Multi-container setup
├── .env.example                # Environment template
└── README.md                   # This file
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/reports` | Submit a compliance report |
| GET | `/api/v1/reports` | Query reports with pagination |
| GET | `/api/v1/reports/{id}` | Get a specific report |
| GET | `/api/v1/reports/engine/{name}/history` | Get engine history |
| GET | `/actuator/health` | Health check endpoint |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/swagger-ui.html` | API documentation |

## Technology Stack

- **Spring Boot 2.7.18** - Application framework
- **PostgreSQL 15** - Primary database
- **JWT** - Authentication tokens
- **Bucket4j** - Rate limiting
- **Caffeine** - In-memory caching
- **Flyway** - Database migrations
- **OpenAPI 3.0** - API documentation
- **TestContainers** - Integration testing
- **Gradle 8.5** - Build tool

## Production-Style Validation Checklist

Use the following checklist before presenting this module as an externally consumable OSS API surface:

- [ ] Run [`./gradlew test`](api/build.gradle:68) successfully in your target branch
- [ ] Validate startup against PostgreSQL with Flyway migrations enabled
- [ ] Confirm health endpoints through your ingress or reverse proxy
- [ ] Confirm JWT secret injection from your secret-management path
- [ ] Exercise webhook signing and retry behavior against a test receiver
- [ ] Confirm metrics scraping from [`/actuator/prometheus`](api/README.md:110)
- [ ] Review rate-limit behavior under representative traffic
- [ ] Document your operator ownership, escalation path, and support window

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `JWT_SECRET` | Yes | - | JWT signing secret (256+ bits) |
| `SPRING_DATASOURCE_URL` | Yes | - | PostgreSQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | - | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | - | Database password |
| `RATE_LIMIT_DEFAULT_LIMIT` | No | 1000 | Max requests per window |
| `RATE_LIMIT_REFILL_DURATION_MINUTES` | No | 60 | Rate limit window |

See [DEPLOYMENT.md](DEPLOYMENT.md) for complete configuration options.

## Development

### Build

```bash
# Build JAR
./gradlew bootJar

# Build container image
podman build -t substrait-compliance-api:latest -f Containerfile .
```

### Test

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests ReportServiceTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Database Migrations

```bash
# Apply migrations
./gradlew flywayMigrate

# Check migration status
./gradlew flywayInfo

# Repair failed migration
./gradlew flywayRepair
```

## Monitoring

### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Liveness probe (Kubernetes)
curl http://localhost:8080/actuator/health/liveness

# Readiness probe (Kubernetes)
curl http://localhost:8080/actuator/health/readiness
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Application metrics
curl http://localhost:8080/actuator/metrics
```

## Security

### Generate JWT Secret

```bash
# Generate 256-bit secret
openssl rand -base64 32

# Generate 512-bit secret (more secure)
openssl rand -base64 64
```

### API Key Management

Treat API key handling as deployment-specific until your environment has validated issuance, rotation, revocation, and audit procedures end to end.

```sql
-- Example bootstrap record for local evaluation only
INSERT INTO api_keys (key_hash, name, scopes, active)
VALUES (
  encode(digest('your-api-key', 'sha256'), 'hex'),
  'Evaluation Client',
  '["read", "write"]'::jsonb,
  true
);
```

## Troubleshooting

### Common Issues

**Database connection failed:**
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Test connection
psql -U substrait -h localhost -d substrait_compliance
```

**JWT token invalid:**
```bash
# Verify JWT_SECRET is set
echo $JWT_SECRET

# Check token hasn't expired (default 24h)
```

**Rate limit exceeded:**
```bash
# Check rate limit headers
curl -I http://localhost:8080/api/v1/reports

# Wait for refill window or contact admin
```

See [DEPLOYMENT.md](DEPLOYMENT.md#troubleshooting) for more solutions.

## Stability Notes

- Endpoint shapes may still evolve before a stable OSS API release.
- Authentication, rate limiting, and webhook behavior should be treated as pre-release surfaces unless validated in your deployment.
- Backward-compatibility guarantees for external clients are not yet claimed.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests for new features
4. Ensure all tests pass: `./gradlew test`
5. Submit a pull request

## License

Apache License 2.0 - See LICENSE file for details

## Support

- **Issues**: https://github.com/substrait-io/substrait-compliance/issues
- **Documentation**: https://substrait.io/compliance
- **Email**: support@substrait.io

---

**Version**: 1.0.0-SNAPSHOT
**Last Updated**: 2026-05-30