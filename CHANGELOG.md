# Changelog

All notable changes to the Substrait Compliance Framework will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2025-07-14

### Highlights

- **8 Language SDKs in-repo**: Java, Python, Rust, Go, C++, TypeScript, C#, and Scala
- **276 Function Test Files**: 140 standard + 136 enhanced, across 14 categories
- **TPC-H Benchmark**: 22 queries included for testing and benchmarking
- **REST API Implementation**: Spring Boot API included in the repository as pre-release functionality
- **Interactive Demo**: Dashboard and mock-engine demo for evaluation and walkthroughs
- **CI/CD Workflows**: GitHub Actions workflows included for validation and release preparation
- **10-Phase Framework Components**: Validation, analysis, storage, analytics, and reproducibility components included in the repository

### Added - Core Framework

#### Multi-Language SDK Support
- **Java SDK** - Complete implementation with Gradle build system
  - ComplianceEngine interface for engine integration
  - TestSuiteLoader for YAML test suite parsing
  - ComplianceRunner for test execution
  - Benchmark framework with statistical analysis
  - Result comparison with type-aware comparators
  - Plan validation and failure analysis
- **Python SDK** - Full-featured Python implementation
  - Type-hinted interfaces for better IDE support
  - Async execution support
  - pytest integration
  - Comprehensive examples
- **Rust SDK** - High-performance Rust implementation
  - Zero-cost abstractions
  - Memory-safe execution
  - Cargo integration
  - Benchmark support
- **Go SDK** - Idiomatic Go implementation
  - Goroutine-based parallel execution
  - Go modules support
  - Standard library integration
- **C++ SDK** - Modern C++17 implementation
  - CMake build system
  - Header-only option
  - Cross-platform support
- **TypeScript SDK** - Full TypeScript/JavaScript support
  - npm package ready
  - Type definitions included
  - Jest testing integration
- **C# SDK** - .NET implementation
  - NuGet package ready
  - .NET 10.0 support
  - xUnit integration
- **Scala SDK** - Functional Scala implementation
  - sbt build system
  - Future-based async execution
  - ScalaTest integration

#### Test Suites
- **TPC-H Benchmark** - 22 industry-standard queries
  - Complete query implementations
  - Sample data generation
  - Performance benchmarking
- **Function Tests** - 276 test files across 14 categories
  - Arithmetic operations (48 functions)
  - String operations (25 functions)
  - Comparison operations (15 functions)
  - Aggregate functions (6 functions)
  - Date/time operations (7 functions)
  - Geospatial functions (4 functions)
  - JSON operations (2 functions)
  - Map operations (3 functions)
  - Set operations (3 functions)
  - Cast operations (1 function)
- **Enhanced Test Suite** - 136 additional test files
  - Extended coverage for complex scenarios
  - Edge case testing
  - Performance-focused tests

### Fixed - SDK Improvements
- **Rust SDK** - Fixed benchmark API mismatches
  - Removed unused `quick_benchmark` static method from `benchmark/mod.rs`
  - Updated `benchmark_example.rs` to use instance method pattern
  - Build status: ✅ Successful (3 minor unused import warnings)
- **Go SDK** - Fixed go.mod parsing errors
  - Removed invalid HTML comment from `go.mod`
  - Added proper `go.sum` file through `go mod tidy`
  - Build status: ✅ Successful
- **Scala SDK** - Fixed 22 compilation errors
  - Created new `EngineResult` type to separate engine execution from test results
  - Updated `ComplianceEngine.scala` return type from `Future[ComplianceResult]` to `Future[EngineResult]`
  - Fixed method call syntax across multiple files (removed parentheses for parameterless methods)
  - Updated `ComplianceRunner.scala`, `BenchmarkExample.scala`, `BenchmarkRunner.scala`, and `ExampleEngine.scala`
  - Build status: ✅ Successful (7 minor unused import warnings)
- **TypeScript SDK** - Fixed npm cache issues
  - Added proper npm scripts to `package.json`: `clean`, `prepare`, `prepack`, `format:check`
  - Added Jest, Prettier, and ESLint configurations inline
  - Created `.npmrc` with cache settings (`cache-min=3600`, `prefer-offline=true`)
  - Enhanced `README.md` with comprehensive troubleshooting section
  - Build status: ✅ Configuration updated

### Changed
- **C++ SDK** - Enhanced toolchain setup documentation
  - Added detailed compiler requirements (GCC 7+, Clang 5+, MSVC 2017+, Apple Clang)
  - Added platform-specific installation guides (Ubuntu/Debian, macOS, Windows)
  - Added CMake configuration options and build instructions
  - Added package manager integration (vcpkg, Conan)
  - Added verification steps
- **Updated README.md** to reflect current project state and SDK fixes
  - Added new "SDK Status & Recent Fixes" section with comprehensive status table
  - Documented all SDK improvements with links to modified files
  - Updated test suite statistics: 276 total function test files (140 standard + 136 enhanced)
  - Corrected project statistics table with accurate counts
  - Added test-suites-enhanced directory to repository structure
  - Updated function test categories to include all 14 categories
  - Enhanced demo features documentation (query drill-down, complexity filtering, 10-phase framework)
  - Updated CI/CD workflow count to 12
  - Added REST API endpoints to project statistics
  - Clarified TPC-DS status (99 queries complete)

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
- **AI-enhanced function test quality checks** using Claude via LiteLLM
  - Added semantic quality analysis for correctness, coverage, completeness, organization, and best practices
  - Improved qualitative review of expected results, null handling, overflow scenarios, and edge-case coverage
  - Introduced a three-step workflow for quality check, enhancement generation, and before/after comparison
  - Strengthened function-test quality across arithmetic, boolean, string, and other core function categories
  - Documented 95%+ AI-enhanced quality score for function test suites
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
#### Documentation & Community
- **Comprehensive Documentation**
  - README.md with quick start guide
  - CONTRIBUTING.md with contribution guidelines
  - CODE_OF_CONDUCT.md (Contributor Covenant)
  - SECURITY.md with vulnerability reporting
  - LICENSE (Apache 2.0)
  - Integration Guide with 10-phase examples
  - API documentation and usage guides
  - SDK-specific README files
  - Troubleshooting guides
- **Community Infrastructure**
  - GitHub Discussions for Q&A
  - Issue templates for bugs and features
  - Pull request template
  - Mailing list integration
  - Slack channel

### Security
- JWT authentication for REST API
- Rate limiting and DDoS protection
- Security headers configuration
- Audit logging for security events
- Vulnerability disclosure policy
- Regular dependency updates via Dependabot

### Performance
- Parallel test execution support
- Caching for API responses
- Optimized database queries with indexes
- Benchmark framework with statistical analysis
- Performance regression detection

### Known Issues
- Some SDKs have minor unused import warnings
- Performance benchmarking features are in beta

### Contributors
Thank you to all contributors who made this release possible! See the [Contributors page](https://github.com/IBM/substrait-compliance/graphs/contributors) for a complete list.

### Links
- [Documentation](https://github.com/IBM/substrait-compliance/tree/main/docs)
- [Substrait Website](https://substrait.io/)

---

## Release Notes

### Version Numbering
- **Major version** (X.0.0): Breaking changes
- **Minor version** (0.X.0): New features, backward compatible
- **Patch version** (0.0.X): Bug fixes, backward compatible

### Support
- **Issues**: [GitHub Issues](https://github.com/IBM/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.com/IBM/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)

---

**Last Updated**: 2026-04-23