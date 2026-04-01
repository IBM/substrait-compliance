# GitHub Actions Workflows

This directory contains GitHub Actions workflows for the Substrait Compliance Framework.

## Workflows Overview

### 1. SDK Build & Test (`sdk-build-test.yml`)

**Trigger:** Push, Pull Request to `main` or `develop`

**Purpose:** Automatically build and test all SDKs (Java, Python, Rust) on every commit.

**Jobs:**
- `java-sdk`: Build and test Java SDK with JDK 11
- `python-sdk`: Build and test Python SDK with Python 3.8-3.11
- `rust-sdk`: Build and test Rust SDK with stable toolchain
- `summary`: Generate test summary report

**Artifacts:**
- Test results for each SDK
- Coverage reports (JaCoCo, pytest-cov, tarpaulin)

**Status Checks:**
- ✅ All tests must pass
- ✅ Code coverage reports generated
- ✅ Build artifacts created

---

### 2. Release & Publish (`release-publish.yml`)

**Trigger:** 
- Push tags matching `v*.*.*` (e.g., `v1.0.0`)
- Manual workflow dispatch with version input

**Purpose:** Automate the release process for SDKs and test suites.

**Jobs:**
- `validate-version`: Validate version format (X.Y.Z)
- `build-java`: Build Java SDK JAR
- `build-python`: Build Python wheel and sdist
- `build-rust`: Build Rust crate
- `package-test-suite`: Create TPC-H test suite archive with checksums
- `create-release`: Create GitHub release with all artifacts
- `publish-java`: Publish to Maven Central (requires secrets)
- `publish-python`: Publish to PyPI (requires secrets)
- `publish-rust`: Publish to crates.io (requires secrets)
- `summary`: Generate release summary

**Required Secrets:**
- `MAVEN_USERNAME`: Maven Central username
- `MAVEN_PASSWORD`: Maven Central password
- `SIGNING_KEY`: GPG signing key for Maven
- `SIGNING_PASSWORD`: GPG signing password
- `PYPI_API_TOKEN`: PyPI API token
- `CARGO_REGISTRY_TOKEN`: crates.io API token

**Artifacts:**
- `substrait-compliance-java-{version}.jar`
- `substrait_compliance-{version}-py3-none-any.whl`
- `substrait-compliance-{version}.crate`
- `tpch-test-suite-{version}.zip`
- `tpch-checksums-{version}.txt`

---

### 3. Test Suite Validation (`test-suite-validation.yml`)

**Trigger:** 
- Push, Pull Request to `main` or `develop` (when `test-suites/**` changes)
- Manual workflow dispatch

**Purpose:** Validate integrity and correctness of test suites.

**Jobs:**
- `validate-tpch-metadata`: Validate YAML syntax and schema
- `validate-tpch-data`: Validate CSV files and structure
- `validate-tpch-plans`: Validate Substrait plan files
- `generate-checksums`: Generate SHA256 checksums
- `summary`: Generate validation summary

**Validations:**
- ✅ YAML metadata is valid
- ✅ All required fields present
- ✅ Test case IDs are unique
- ✅ All CSV files exist and are not empty
- ✅ CSV structure matches expected columns
- ✅ All plan files referenced in metadata exist
- ✅ JSON plans have valid syntax
- ✅ Checksums generated for verification

---

### 4. Engine Compliance Template (`engine-compliance-template.yml`)

**Purpose:** Template workflow that engine developers can copy to their repositories.

**Usage:**
1. Copy this file to your engine repository's `.github/workflows/` directory
2. Update `ENGINE_NAME` and `ENGINE_VERSION` variables
3. Customize build and test steps for your engine
4. Implement the `ComplianceEngine` interface
5. Run compliance tests in your CI/CD

**Customization Points:**
- Build environment setup
- Engine build commands
- ComplianceEngine implementation
- Test execution commands

**Features:**
- Downloads latest compliance SDK and test suite
- Verifies checksums
- Runs compliance tests
- Generates compliance report
- Creates compliance badge
- Enforces compliance threshold (default: 80%)

**Example Engines:**
- DuckDB (Java)
- DataFusion (Python)
- Spark (Java/Scala)
- Presto (Java)

---

### 5. Compliance Leaderboard (`compliance-leaderboard.yml`)

**Trigger:**
- Weekly schedule (Sunday at 00:00 UTC)
- Manual workflow dispatch
- Repository dispatch event `compliance-report-submitted`

**Purpose:** Aggregate compliance reports from all engines and generate public leaderboard.

**Jobs:**
- `collect-reports`: Fetch compliance reports from engine repositories
- `generate-leaderboard`: Generate leaderboard in Markdown and JSON
- `publish-leaderboard`: Commit leaderboard to repository
- `deploy-to-pages`: Deploy interactive leaderboard to GitHub Pages
- `notify`: Send notifications about leaderboard updates
- `summary`: Generate workflow summary

**Outputs:**
- `docs/leaderboard.md`: Markdown leaderboard
- `docs/leaderboard.json`: JSON leaderboard data
- GitHub Pages site: Interactive HTML leaderboard

**Leaderboard Features:**
- Rankings by pass rate
- Color-coded status indicators
- Detailed results per engine
- Statistics (average, highest, lowest)
- Automatic updates

---

## Setup Instructions

### For Framework Maintainers

1. **Enable GitHub Actions:**
   - Go to repository Settings → Actions → General
   - Enable "Allow all actions and reusable workflows"

2. **Configure Secrets:**
   ```
   Settings → Secrets and variables → Actions → New repository secret
   ```
   Add the following secrets:
   - `MAVEN_USERNAME`
   - `MAVEN_PASSWORD`
   - `SIGNING_KEY`
   - `SIGNING_PASSWORD`
   - `PYPI_API_TOKEN`
   - `CARGO_REGISTRY_TOKEN`

3. **Enable GitHub Pages:**
   - Go to Settings → Pages
   - Source: GitHub Actions
   - This enables the leaderboard website

4. **Create Release:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   This triggers the release workflow.

### For Engine Developers

1. **Copy Template:**
   ```bash
   cp .github/workflows/engine-compliance-template.yml \
      your-engine/.github/workflows/substrait-compliance.yml
   ```

2. **Customize Variables:**
   ```yaml
   env:
     ENGINE_NAME: "YourEngine"
     ENGINE_VERSION: "1.0.0"
     COMPLIANCE_THRESHOLD: 80
   ```

3. **Implement ComplianceEngine:**
   - See examples in `/examples/` directory
   - Implement the interface for your engine
   - Build compliance implementation

4. **Run Tests:**
   - Push to your repository
   - Workflow runs automatically
   - View results in Actions tab

---

## Workflow Dependencies

```
sdk-build-test.yml
    ↓
release-publish.yml
    ↓
[Engines use template]
    ↓
compliance-leaderboard.yml
    ↓
GitHub Pages
```

---

## Monitoring and Debugging

### View Workflow Runs
- Go to Actions tab in GitHub
- Click on workflow name
- View individual job logs

### Common Issues

**Build Failures:**
- Check dependency versions
- Verify secrets are configured
- Review error logs in failed job

**Test Failures:**
- Review test output in artifacts
- Check coverage reports
- Verify test data integrity

**Release Failures:**
- Verify version format (X.Y.Z)
- Check secrets are valid
- Ensure tag exists

**Leaderboard Issues:**
- Verify report JSON format
- Check GitHub Pages is enabled
- Review script output

---

## Best Practices

1. **Always run tests locally before pushing**
2. **Use semantic versioning for releases**
3. **Keep secrets secure and rotated**
4. **Monitor workflow execution times**
5. **Review artifacts before publishing**
6. **Test template customizations locally**
7. **Keep compliance threshold realistic**

---

## Support

For issues or questions:
- Open an issue in the repository
- Check workflow logs for errors
- Review documentation in `/docs/`
- See examples in `/examples/`

---

## Contributing

To add new workflows:
1. Create workflow file in `.github/workflows/`
2. Test with workflow dispatch
3. Document in this README
4. Submit pull request

---

Last Updated: 2026-04-01