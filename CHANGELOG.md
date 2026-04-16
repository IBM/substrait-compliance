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
  - Rate limiting and caching for performance
- **PostgreSQL database** with Flyway migrations
  - Entity models for reports, engines, test results, and webhooks
  - Optimized indexes for query performance
- **API documentation** in `docs/` directory
  - REST API overview and specifications
  - Architecture diagrams
  - Implementation guides
  - Contributing guidelines

#### CI/CD Workflows
- **Comprehensive GitHub Actions workflows** for API lifecycle
  - `api-pr-validation.yml` - Validate PRs with automated testing
  - `api-build-test.yml` - Continuous integration on every push
  - `api-container-build.yml` - Multi-platform container builds (amd64, arm64)
  - `api-deploy-staging.yml` - Automatic staging deployments
  - `api-deploy-production.yml` - Production releases with approval gates
  - `api-release.yml` - Automated versioning and publishing
- **Dependabot configuration** for automated dependency updates
- **Workflow documentation** in `.github/workflows/README.md`

#### Interactive Demo System
- **Mock database engines** for testing compliance framework
  - MockDBEngine - Basic implementation
  - FastDBEngine - Optimized performance
  - CloudDBEngine - Distributed architecture simulation
- **Demo runners** with multiple execution modes
  - `DemoRunner.java` - Full-featured runner
  - `SimpleDemoRunner.java` - Simplified execution
  - Shell scripts for easy execution
- **Interactive dashboard** with real-time visualization
  - HTML/CSS dashboard with responsive design
  - Real-time test result updates
  - Visual charts and statistics
  - Export functionality for reports
- **Comprehensive demo documentation**
  - Quick start guide (`START_HERE.md`)
  - Usage instructions (`DEMO_USAGE.md`)
  - Dashboard features (`DASHBOARD_FEATURES.md`)
  - Test instructions (`TEST_INSTRUCTIONS.md`)

#### Project Structure
- **Gradle multi-module configuration** (`settings.gradle`)
  - Root project setup
  - SDK and API module integration
  - Maven publication configuration
- **Documentation reorganization**
  - Moved implementation docs to `docs/` directory
  - Consolidated CI/CD and deployment guides
  - Added API-specific documentation

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

**Last Updated**: 2026-04-16