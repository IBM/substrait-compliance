# REST API for Substrait Compliance - Project Summary

## 🎯 Project Overview

The Substrait Compliance REST API is an **open source** addition to the Substrait Compliance Framework, enabling programmatic access to compliance testing results, report submission, and real-time notifications.

## 📜 License & Open Source

- **License**: Apache License 2.0 (same as existing SDK)
- **Repository**: Integrated into the main substrait-compliance repository
- **Community**: Open for contributions from the Substrait community
- **Governance**: Follows Substrait project governance model

## 🏗️ Architecture Summary

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Spring Boot | 2.7.14 |
| **Language** | Java | 11+ |
| **Database** | PostgreSQL | 15 |
| **Authentication** | JWT | - |
| **Build Tool** | Gradle | 7.x |
| **Containerization** | Podman | - |
| **API Documentation** | OpenAPI/Swagger | 3.0 |
| **Testing** | JUnit 5 + TestContainers | - |

### Module Structure

```
substrait-compliance/
├── sdk/java/                    # Existing SDK (library)
│   └── src/main/java/io/substrait/compliance/
│       ├── ComplianceReport.java
│       ├── ComplianceResult.java
│       └── TestResult.java
│
└── api/                         # NEW: REST API Service
    ├── build.gradle
    ├── Containerfile
    ├── docker-compose.yml
    └── src/
        ├── main/
        │   ├── java/io/substrait/compliance/api/
        │   │   ├── controller/      # REST endpoints
        │   │   ├── service/         # Business logic
        │   │   ├── repository/      # Data access
        │   │   ├── model/           # DTOs and entities
        │   │   ├── security/        # JWT authentication
        │   │   ├── webhook/         # Event system
        │   │   └── config/          # Configuration
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/    # Flyway migrations
        └── test/                    # Comprehensive tests
```

## 🔌 API Capabilities

### Core Features

1. **Report Submission**
   - Submit individual compliance reports
   - Batch report submission
   - Automatic validation and storage

2. **Data Querying**
   - Query reports with filtering
   - Pagination support
   - Compliance leaderboard
   - Engine statistics
   - Historical trends

3. **Webhook Notifications**
   - Real-time event notifications
   - Configurable event types
   - Retry mechanism with exponential backoff
   - Delivery tracking

4. **Security**
   - JWT token authentication
   - API key support for CI/CD
   - Scope-based authorization
   - Rate limiting per user/key

5. **Performance**
   - Response caching (Caffeine)
   - Database query optimization
   - Connection pooling
   - Async webhook delivery

### API Endpoints

#### Authentication
- `POST /api/v1/auth/login` - Get JWT token
- `POST /api/v1/auth/api-key` - Generate API key

#### Reports
- `POST /api/v1/reports` - Submit report
- `POST /api/v1/reports/batch` - Batch submission
- `GET /api/v1/reports` - Query reports
- `GET /api/v1/reports/{id}` - Get report details

#### Queries
- `GET /api/v1/engines` - List engines
- `GET /api/v1/engines/{name}/history` - Engine history
- `GET /api/v1/leaderboard` - Compliance leaderboard
- `GET /api/v1/statistics` - Overall statistics

#### Webhooks
- `POST /api/v1/webhooks` - Register webhook
- `GET /api/v1/webhooks` - List webhooks
- `DELETE /api/v1/webhooks/{id}` - Delete webhook
- `GET /api/v1/webhooks/{id}/deliveries` - Delivery history

## 📊 Database Schema

### Tables

1. **engines** - Engine information
2. **compliance_reports** - Test execution reports
3. **test_results** - Individual test results
4. **webhooks** - Webhook registrations
5. **webhook_deliveries** - Delivery tracking
6. **api_keys** - API key management

### Key Features

- Foreign key constraints for data integrity
- Indexes for query performance
- JSONB for flexible metadata storage
- Audit timestamps on all tables

## 🔐 Security Model

### Authentication Methods

1. **JWT Tokens**
   - For interactive users
   - Short-lived (1 hour default)
   - Scope-based permissions

2. **API Keys**
   - For CI/CD pipelines
   - Long-lived (configurable)
   - Per-key rate limits

### Authorization Scopes

- `report:write` - Submit reports
- `report:read` - Query data
- `webhook:manage` - Manage webhooks
- `admin` - Full access

### Rate Limiting

- Token bucket algorithm
- Per-user/key limits
- Configurable tiers
- HTTP 429 responses with retry-after

## 🧪 Testing Strategy

### Test Coverage

- **Unit Tests**: 80%+ coverage target
- **Integration Tests**: Full API flow testing
- **Security Tests**: Authentication and authorization
- **Performance Tests**: Load and stress testing

### Test Infrastructure

- **TestContainers**: PostgreSQL for integration tests
- **MockMvc**: Controller testing
- **Mockito**: Service layer mocking
- **H2**: In-memory database for unit tests

## 🐳 Deployment

### Containerization

- **Podman-compatible** Containerfile
- Multi-stage builds for optimization
- Health checks included
- Resource limits configurable

### Orchestration

- docker-compose.yml for local development
- Kubernetes manifests (future)
- Environment-based configuration
- Secrets management

### Environments

1. **Development**: Local with H2/PostgreSQL
2. **Staging**: Container with PostgreSQL
3. **Production**: Scaled deployment with monitoring

## 📈 Monitoring & Observability

### Metrics (Micrometer)

- Request rates and latencies
- Database connection pool
- Cache hit/miss rates
- Webhook delivery success
- Rate limit violations

### Health Checks

- `/actuator/health` - Overall health
- `/actuator/health/db` - Database connectivity
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus format

### Logging

- Structured JSON logging
- Request/response logging
- Error tracking
- Audit trail for sensitive operations

## 🤝 Community Contribution

### How to Contribute

1. **Read Guidelines**: See [`API_CONTRIBUTING.md`](API_CONTRIBUTING.md)
2. **Find Issues**: Check GitHub issues labeled `api`
3. **Discuss First**: For major changes, open an issue
4. **Submit PR**: Follow PR template and guidelines
5. **Code Review**: Respond to feedback

### Contribution Areas

- 🐛 Bug fixes
- ✨ New features
- 📚 Documentation
- 🧪 Tests
- ⚡ Performance
- 🔒 Security

### Recognition

Contributors are recognized in:
- CONTRIBUTORS.md
- Release notes
- Project README

## 📚 Documentation

### Planning Documents

1. **[REST_API_PLAN.md](REST_API_PLAN.md)** (1,089 lines)
   - Complete API specifications
   - Database schema
   - Authentication strategy
   - Webhook system design

2. **[REST_API_ARCHITECTURE.md](REST_API_ARCHITECTURE.md)** (363 lines)
   - Architecture diagrams
   - Request flows
   - Component interactions
   - Deployment architecture

3. **[REST_API_IMPLEMENTATION_GUIDE.md](REST_API_IMPLEMENTATION_GUIDE.md)** (835 lines)
   - Step-by-step implementation
   - Code examples
   - Testing strategies
   - Deployment instructions

4. **[API_CONTRIBUTING.md](API_CONTRIBUTING.md)** (565 lines)
   - Contribution guidelines
   - Coding standards
   - Testing requirements
   - PR process

### API Documentation

- **OpenAPI Spec**: Auto-generated from annotations
- **Swagger UI**: Interactive API explorer
- **JavaDoc**: Comprehensive code documentation
- **Examples**: Sample requests and responses

## 🗓️ Implementation Timeline

### Phase 1-2: Foundation & Database (Weeks 1-2)
- ✅ Project structure
- ✅ Gradle multi-module setup
- ✅ Database schema
- ✅ JPA entities

### Phase 3-4: Security & Core API (Weeks 3-4)
- JWT authentication
- Report submission endpoints
- Query endpoints
- Basic tests

### Phase 5-6: Advanced Features (Weeks 5-6)
- Webhook system
- Rate limiting
- Caching
- Comprehensive tests

### Phase 7-8: Deployment & Documentation (Weeks 7-8)
- Containerization
- CI/CD integration
- Documentation
- Community launch

## 🎯 Success Criteria

- [ ] All API endpoints functional
- [ ] 80%+ test coverage
- [ ] Security audit passed
- [ ] Performance benchmarks met
- [ ] Documentation complete
- [ ] Community guidelines published
- [ ] CI/CD pipeline automated
- [ ] Container images published

## 🚀 Getting Started

### For Users

```bash
# Pull and run the API
podman pull substrait/compliance-api:latest
podman run -p 8080:8080 substrait/compliance-api:latest
```

### For Contributors

```bash
# Clone repository
git clone https://github.com/substrait-io/substrait-compliance.git
cd substrait-compliance

# Build SDK
cd sdk/java && ./gradlew build publishToMavenLocal

# Build and run API
cd ../../api
./gradlew bootRun
```

### For Developers

See [`API_CONTRIBUTING.md`](API_CONTRIBUTING.md) for detailed setup instructions.

## 📞 Support & Communication

- **Issues**: [GitHub Issues](https://github.com/substrait-io/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)
- **Slack**: Substrait Community Slack (if available)

## 🙏 Acknowledgments

- **Substrait Community**: For the amazing specification
- **Spring Team**: For the excellent framework
- **Contributors**: Everyone who helps improve the API

## 📋 Next Steps

1. **Review Planning Documents**: Ensure alignment with requirements
2. **Begin Implementation**: Start with Phase 1-2 (Foundation & Database)
3. **Community Feedback**: Gather input from Substrait community
4. **Iterate**: Refine based on feedback

---

**Project Status**: Planning Complete ✅  
**License**: Apache 2.0  
**Maintainers**: See MAINTAINERS.md  
**Last Updated**: 2026-04-16

**Ready for Implementation** 🚀