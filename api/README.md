# Substrait Compliance API

REST API for programmatic submission and querying of Substrait compliance test results.

## Features

- ✅ **Report Submission** - Submit compliance test results via REST API
- ✅ **Query Interface** - Query compliance data with pagination and filtering
- ✅ **JWT Authentication** - Secure token-based authentication
- ✅ **Rate Limiting** - Token bucket algorithm with configurable limits
- ✅ **Response Caching** - In-memory caching with Caffeine
- ✅ **Webhook Notifications** - Event-driven notifications for report events
- ✅ **OpenAPI Documentation** - Interactive API documentation with Swagger UI
- ✅ **Database Migrations** - Versioned schema management with Flyway
- ✅ **Health Checks** - Kubernetes-ready liveness and readiness probes
- ✅ **Metrics Export** - Prometheus metrics for monitoring

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

API keys are stored in the database with bcrypt hashing:

```sql
-- Create API key
INSERT INTO api_keys (key_hash, name, scopes, active)
VALUES (
  encode(digest('your-api-key', 'sha256'), 'hex'),
  'Production Client',
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
**Last Updated**: 2026-04-16