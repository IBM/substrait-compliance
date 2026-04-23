# Changelog

All notable changes to the Substrait Compliance Framework will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

#### REST API Infrastructure
- **Spring Boot REST API** with comprehensive compliance reporting endpoints
  - JWT authentication and security configuration
  - Report submission endpoint (`POST /api/v1/reports`)
  - Query endpoints with filtering and pagination
  - Webhook system for real-time notifications
  - Rate limiting (token bucket algorithm) and caching (Caffeine)
  - OpenAPI 3.0 documentation with Swagger UI
  - Health checks and Prometheus metrics
- **PostgreSQL database** with Flyway migrations
  - Entity models for reports, engines, test results, and webhooks
  - Optimized indexes for query performance
  - Versioned schema management
- **Container deployment** support
  - Multi-platform Docker images (amd64, arm64)
  - Docker Compose orchestration
  - Kubernetes-ready with health probes
  - Trivy security scanning and SBOM generation
- **API documentation** in `api/` directory
  - Complete API usage guide (`API_USAGE.md`)
  - Deployment instructions (`DEPLOYMENT.md`)
  - Environment configuration examples

#### CI/CD Workflows
- **Comprehensive GitHub Actions workflows** for API lifecycle
  - `api-pr-validation.yml` - Validate PRs with automated testing and coverage
  - `api-build-test.yml` - Continuous integration on every push
  - `api-container-build.yml` - Multi-platform container builds with security scanning
  - `api-deploy-staging.yml` - Automatic staging deployments with smoke tests
  - `api-deploy-production.yml` - Production releases with approval gates and rollback
  - `api-release.yml` - Automated versioning and GitHub releases
- **Dependabot configuration** for automated dependency updates
- **Workflow documentation** in `.github/workflows/README.md`
  - Setup instructions with secrets configuration
  - Environment configuration (staging/production)
  - Usage examples and troubleshooting
  - Best practices and monitoring

#### Enhanced Demo System
- **Mock database engines** for testing compliance framework
  - MockDBEngine - Basic implementation (85.4% pass rate)
  - FastDBEngine - Optimized performance (95.5% pass rate)
  - CloudDBEngine - Distributed architecture simulation (77.3% pass rate)
- **Demo runners** with multiple execution modes
  - `DemoRunner.java` - Full-featured runner with all phases
  - `SimpleDemoRunner.java` - Simplified execution for quick demos
  - `EnhancedDemoRunner.java` - Complete integration of all 10 phases
  - Shell scripts for easy execution (`run-simple-demo.sh`, `run-enhanced-demo.sh`)
- **Interactive dashboard** with advanced visualization
  - HTML/CSS/JavaScript dashboard with responsive design
  - Real-time test result updates with Chart.js
  - **Query-level drill-down modal** - Click any engine to see detailed results
  - **Complexity filtering** - Filter by SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX
  - Visual charts (bar charts, doughnut charts) and statistics
  - Color-coded status indicators and complexity badges
  - Smooth animations and professional UI
- **Comprehensive demo documentation**
  - Quick start guide (`START_HERE.md`)
  - Usage instructions (`DEMO_USAGE.md`)
  - Dashboard features (`DASHBOARD_FEATURES.md`)
  - Enhancement summary (`ENHANCEMENT_SUMMARY.md`)
  - Test instructions (`TEST_INSTRUCTIONS.md`)
  - Quick demo guide (`QUICK_DEMO_GUIDE.md`)
  - Success verification (`DEMO_SUCCESS.md`)

#### Enhanced Compliance Framework (10 Phases)
- **Phase 1-2: Validation & Comparison**
  - Substrait plan validation with detailed error reporting
  - Type-aware result comparison with floating-point tolerance
- **Phase 3-4: Failure Analysis & Categorization**
  - Systematic failure analysis with root cause identification
  - Failure categorization (PLAN_INVALID, TYPE_MISMATCH, etc.)
  - Actionable suggestions for fixing failures
- **Phase 5-6: Data Storage**
  - Private storage for engine teams (full diagnostic data)
  - Public storage for community (anonymized results)
  - Structured JSON format with comprehensive metadata
- **Phase 7-8: Analytics & Insights**
  - Performance trend analysis across test runs
  - Regression detection and alerting
  - Key findings and recommendations generation
- **Phase 9-10: Reproducibility**
  - Reproduction package creation for failed tests
  - Complete test context preservation
  - Easy sharing and debugging support

#### Integration & Documentation
- **Integration Guide** (`INTEGRATION_GUIDE.md`)
  - Complete integration examples for all 10 phases
  - Step-by-step engine adapter creation
  - Configuration and best practices
  - Troubleshooting and support
- **Project Structure**
  - Gradle multi-module configuration (`settings.gradle`)
  - Root project setup with SDK and API module integration
  - Maven publication configuration
- **Documentation reorganization**
  - Moved implementation docs to `docs/` directory
  - Consolidated CI/CD and deployment guides
  - Added API-specific documentation
  - Enhanced README with comprehensive feature overview

#### Test Suites & Functions
- **Expanded function test coverage** (100+ test files)
  - Arithmetic functions (abs, add, multiply, power, sqrt, trigonometry, etc.)
  - Boolean functions (and, or, not, xor)
  - Comparison functions (equal, gt, lt, between, coalesce, nullif, etc.)
  - String functions (lower, trim, replace, regexp_replace, etc.)
  - Geospatial functions (st_area, st_contains, st_distance, st_intersects)
  - JSON functions (json_extract, json_parse)
  - Set operations (union, intersect, except)
  - Struct operations (construct, extract)
  - Window functions (rank, dense_rank, first_value)
- **TPC-H benchmark suite**
  - 22 queries with complexity classification
  - 8 CSV data files (86,805 rows)
  - Binary and JSON plan formats
  - Complete metadata with expected results

### Changed
- **Updated README.md** with new features and structure
  - Added REST API section with quick start
  - Updated repository structure diagram
  - Added interactive demo section
  - Reorganized documentation links
  - Updated CI/CD integration section
  - Marked completed roadmap items
- **Reorganized workflows directory** with API-focused workflows
- **Enhanced project statistics** to reflect new components

### Removed
- **Root-level documentation files** (moved to `docs/`)
  - `CI_CD_IMPLEMENTATION.md` → `docs/CI_CD_IMPLEMENTATION.md`
  - `DEPLOYMENT_GUIDE.md` → `docs/DEPLOYMENT_GUIDE.md`
  - `IMPLEMENTATION_SUMMARY.md` → `docs/IMPLEMENTATION_SUMMARY.md`

### Technical Details

#### API Technology Stack
- Spring Boot 3.2.0
- Spring Security with JWT
- PostgreSQL 15
- Flyway for migrations
- JUnit 5 for testing
- Docker/Podman for containerization

#### CI/CD Features
- Multi-stage deployments (staging/production)
- Automated testing and coverage reporting
- Container registry integration (GitHub Container Registry)
- Approval gates for production deployments
- Automated rollback capabilities

#### Demo System Features
- Three mock engines with different characteristics
- Real-time dashboard updates
- Export to JSON/CSV formats
- Comprehensive test coverage
- Easy setup and execution

### Breaking Changes
- **Project structure reorganized** with multi-module Gradle setup
  - Root `settings.gradle` now manages all modules
  - API is now a separate module under `api/`
  - SDK remains under `sdk/java`

### Migration Guide
For existing users:
1. Pull latest changes
2. Review new `settings.gradle` configuration
3. Update build scripts if referencing old paths
4. Check documentation in new `docs/` location

### Security
- JWT-based authentication for API access
- Rate limiting to prevent abuse
- Input validation on all endpoints
- Secure webhook delivery with signatures

### Performance
- Database query optimization with indexes
- Redis caching for frequently accessed data
- Connection pooling for database access
- Async webhook delivery

---

## [1.0.0] - 2024-XX-XX (Previous Release)

### Added
- Initial release of Substrait Compliance Framework
- Java, Python, and Rust SDKs
- TPC-H test suite with 22 queries
- Basic CI/CD workflows
- Example implementations (DuckDB, DataFusion)

---

## Release Notes

### Version Numbering
- **Major version** (X.0.0): Breaking changes
- **Minor version** (0.X.0): New features, backward compatible
- **Patch version** (0.0.X): Bug fixes, backward compatible

### Support
- **Issues**: [GitHub Issues](https://github.com/substrait-io/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)

---

**Last Updated**: 2026-04-23