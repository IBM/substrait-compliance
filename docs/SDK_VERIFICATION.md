# SDK Build Verification Guide

This guide explains how to verify that all Substrait Compliance SDKs are building correctly and their tests are passing.

## Table of Contents

- [Quick Start](#quick-start)
- [Automated Verification](#automated-verification)
- [Manual Verification](#manual-verification)
- [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)
- [SDK-Specific Details](#sdk-specific-details)

## Quick Start

### Run All SDK Verifications

```bash
# From project root
chmod +x scripts/verify_sdk_builds.sh
./scripts/verify_sdk_builds.sh
```

This script will:
- ✅ Check if required tools are installed
- ✅ Build each SDK
- ✅ Run tests for each SDK
- ✅ Generate a summary report
- ✅ Create a JSON report at `/tmp/sdk_build_report.json`

### Expected Output

```
╔════════════════════════════════════════════════════════════╗
║   Substrait Compliance SDK Build Verification             ║
╚════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. Python SDK
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ PASSED: Build successful, 15 tests passed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Overall Statistics:
  Total SDKs:   8
  Passed:       8
  Failed:       0
  Skipped:      0
  Pass Rate:    100%
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Automated Verification

### GitHub Actions CI/CD

The project includes automated SDK verification via GitHub Actions:

**File:** `.github/workflows/sdk-verification.yml`

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop`
- Daily at 2 AM UTC
- Manual workflow dispatch

**Features:**
- ✅ Tests multiple versions of each language
- ✅ Runs in parallel for faster feedback
- ✅ Generates coverage reports
- ✅ Uploads test artifacts
- ✅ Creates summary report

**View Results:**
- Go to GitHub Actions tab
- Select "SDK Build Verification" workflow
- View individual SDK job results

### Local Pre-commit Hook

Add to `.git/hooks/pre-commit`:

```bash
#!/bin/bash
echo "Running SDK verification..."
./scripts/verify_sdk_builds.sh

if [ $? -ne 0 ]; then
    echo "SDK verification failed. Commit aborted."
    exit 1
fi
```

## Manual Verification

### Python SDK

```bash
cd sdk/python

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -e .

# Run tests
pytest tests/ -v

# Run with coverage
pytest tests/ --cov=substrait_compliance --cov-report=html

# Deactivate
deactivate
```

**Expected:** All tests pass, coverage > 80%

### Rust SDK

```bash
cd sdk/rust

# Check formatting
cargo fmt -- --check

# Run linter
cargo clippy -- -D warnings

# Build
cargo build --release

# Run tests
cargo test --verbose

# Run benchmarks
cargo bench
```

**Expected:** Clean build, all tests pass, no clippy warnings

### Java SDK

```bash
cd sdk/java

# Build with Gradle
./gradlew build

# Run tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

**Expected:** BUILD SUCCESSFUL, all tests pass

### C++ SDK

```bash
cd sdk/cpp

# Create build directory
mkdir -p build && cd build

# Configure
cmake ..

# Build
cmake --build . --config Release

# Run tests
ctest --output-on-failure
```

**Expected:** All tests pass

### Go SDK

```bash
cd sdk/go

# Download dependencies
go mod download

# Build
go build ./...

# Run tests
go test ./... -v

# Run with race detection
go test ./... -race

# Generate coverage
go test ./... -coverprofile=coverage.out
go tool cover -html=coverage.out
```

**Expected:** PASS for all packages

### TypeScript SDK

```bash
cd sdk/typescript

# Install dependencies
npm install

# Lint
npm run lint

# Build
npm run build

# Run tests
npm test

# Run with coverage
npm test -- --coverage
```

**Expected:** All tests pass, no lint errors

### C# SDK

```bash
cd sdk/csharp

# Restore dependencies
dotnet restore

# Build
dotnet build --configuration Release

# Run tests
dotnet test --configuration Release

# Generate coverage
dotnet test --collect:"XPlat Code Coverage"
```

**Expected:** Test Run Successful

### Scala SDK

```bash
cd sdk/scala

# Compile
sbt compile

# Run tests
sbt test

# Generate coverage
sbt coverage test coverageReport

# View report
open target/scala-2.13/scoverage-report/index.html
```

**Expected:** All tests pass

## CI/CD Integration

### GitHub Actions Matrix Strategy

Each SDK is tested against multiple versions:

| SDK | Versions Tested |
|-----|----------------|
| Python | 3.9, 3.10, 3.11, 3.12 |
| Rust | stable, beta, nightly |
| Java | 11, 17, 21 |
| C++ | g++, clang++ |
| Go | 1.20, 1.21, 1.22 |
| TypeScript | Node 18, 20, 21 |
| C# | .NET 6.0, 7.0, 8.0 |
| Scala | 2.13, 3.3 |

### Coverage Reports

Coverage reports are automatically uploaded to Codecov:

```yaml
- name: Upload coverage
  uses: codecov/codecov-action@v3
  with:
    file: ./coverage.xml
    flags: python
```

**View Coverage:**
- Visit: `https://codecov.io/gh/YOUR_ORG/substrait-compliance`
- Check per-SDK coverage
- View coverage trends over time

### Build Badges

Add to README.md:

```markdown
[![SDK Verification](https://github.com/YOUR_ORG/substrait-compliance/actions/workflows/sdk-verification.yml/badge.svg)](https://github.com/YOUR_ORG/substrait-compliance/actions/workflows/sdk-verification.yml)
```

## Troubleshooting

### Common Issues

#### 1. Tool Not Installed

**Error:** `command not found: cargo`

**Solution:**
```bash
# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Install Go
brew install go  # macOS
sudo apt install golang-go  # Ubuntu

# Install Node.js
brew install node  # macOS
sudo apt install nodejs npm  # Ubuntu
```

#### 2. Dependency Resolution Failed

**Error:** `Could not resolve dependencies`

**Solution:**
```bash
# Python
pip install --upgrade pip
pip cache purge

# Rust
cargo clean
rm Cargo.lock
cargo update

# Node.js
rm -rf node_modules package-lock.json
npm install

# Java
./gradlew clean
rm -rf ~/.gradle/caches
```

#### 3. Tests Failing

**Error:** `Test suite failed`

**Solution:**
```bash
# Run tests in verbose mode
pytest -vv  # Python
cargo test -- --nocapture  # Rust
npm test -- --verbose  # TypeScript

# Check test logs
cat /tmp/python_test.log
cat /tmp/rust_test.log
```

#### 4. Build Timeout

**Error:** `Build exceeded time limit`

**Solution:**
```bash
# Increase timeout in CI
timeout-minutes: 30

# Use caching
uses: actions/cache@v3

# Build in parallel
cargo build -j 4
```

### SDK-Specific Issues

#### Python: Import Errors

```bash
# Ensure package is installed in editable mode
pip install -e .

# Check PYTHONPATH
export PYTHONPATH="${PYTHONPATH}:$(pwd)"
```

#### Rust: Linker Errors

```bash
# Install build essentials
sudo apt-get install build-essential

# Update Rust
rustup update
```

#### Java: OutOfMemoryError

```bash
# Increase heap size
export GRADLE_OPTS="-Xmx2g"
./gradlew build
```

#### Go: Module Issues

```bash
# Tidy modules
go mod tidy

# Verify modules
go mod verify
```

## SDK-Specific Details

### Python SDK

**Requirements:**
- Python 3.9+
- pip
- virtualenv (recommended)

**Key Files:**
- `setup.py` - Package configuration
- `requirements.txt` - Dependencies
- `tests/` - Test suite
- `pytest.ini` - Test configuration

**Test Framework:** pytest

### Rust SDK

**Requirements:**
- Rust 1.70+
- Cargo

**Key Files:**
- `Cargo.toml` - Package manifest
- `src/` - Source code
- `tests/` - Integration tests
- `benches/` - Benchmarks

**Test Framework:** Built-in cargo test

### Java SDK

**Requirements:**
- JDK 11+
- Gradle 7.0+

**Key Files:**
- `build.gradle` - Build configuration
- `src/main/java/` - Source code
- `src/test/java/` - Tests

**Test Framework:** JUnit 5

### C++ SDK

**Requirements:**
- CMake 3.15+
- C++17 compiler (g++ or clang++)

**Key Files:**
- `CMakeLists.txt` - Build configuration
- `src/` - Source code
- `tests/` - Tests

**Test Framework:** Google Test (recommended)

### Go SDK

**Requirements:**
- Go 1.20+

**Key Files:**
- `go.mod` - Module definition
- `go.sum` - Dependency checksums
- `*_test.go` - Test files

**Test Framework:** Built-in go test

### TypeScript SDK

**Requirements:**
- Node.js 18+
- npm or yarn

**Key Files:**
- `package.json` - Package configuration
- `tsconfig.json` - TypeScript config
- `src/` - Source code
- `tests/` - Tests

**Test Framework:** Jest

### C# SDK

**Requirements:**
- .NET 6.0+

**Key Files:**
- `*.csproj` - Project file
- `src/` - Source code
- `tests/` - Tests

**Test Framework:** xUnit or NUnit

### Scala SDK

**Requirements:**
- JDK 11+
- SBT 1.5+

**Key Files:**
- `build.sbt` - Build definition
- `src/main/scala/` - Source code
- `src/test/scala/` - Tests

**Test Framework:** ScalaTest

## Best Practices

### 1. Run Verification Before Commits

```bash
# Add to your workflow
git add .
./scripts/verify_sdk_builds.sh && git commit -m "Your message"
```

### 2. Keep Dependencies Updated

```bash
# Python
pip list --outdated

# Rust
cargo outdated

# Node.js
npm outdated

# Java
./gradlew dependencyUpdates
```

### 3. Monitor Build Times

```bash
# Track build duration
time ./scripts/verify_sdk_builds.sh

# Optimize slow builds
- Use caching
- Parallelize tests
- Remove unused dependencies
```

### 4. Maintain Test Coverage

**Target:** > 80% code coverage for all SDKs

```bash
# Generate coverage reports regularly
# Review uncovered code
# Add tests for critical paths
```

## Continuous Improvement

### Metrics to Track

1. **Build Success Rate:** Target 100%
2. **Test Pass Rate:** Target 100%
3. **Build Duration:** Track trends
4. **Code Coverage:** Target > 80%
5. **Dependency Freshness:** Update quarterly

### Regular Maintenance

- **Weekly:** Review failed builds
- **Monthly:** Update dependencies
- **Quarterly:** Review and optimize build process
- **Annually:** Evaluate new testing tools

## Support

For issues or questions:
- Open an issue on GitHub
- Check existing issues for solutions
- Consult SDK-specific README files
- Review CI/CD logs for detailed errors

---

**Last Updated:** 2026-05-30
**Maintained By:** Substrait Compliance Team