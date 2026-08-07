# CI/CD Workflows

This directory contains all GitHub Actions workflows for the Substrait Compliance Framework.

## Workflows

### `sdk-build-test.yml` — Primary CI gate

Runs on every push and pull request to `main` and `develop`. Builds and tests all eight SDKs
in parallel, then runs smoke tests for the two fully-executable examples.

| Job | What it does |
|-----|--------------|
| `java-sdk` | Gradle build + 88 unit and integration tests + JaCoCo coverage |
| `python-sdk` | `pip install -e .[dev]` + pytest across Python 3.8/3.9/3.10/3.11 matrix |
| `rust-sdk` | `cargo fmt --check`, `cargo clippy -D warnings`, 22 tests, `cargo-tarpaulin` coverage |
| `go-sdk` | `go build ./...` + `go test ./...` |
| `typescript-sdk` | `npm ci` + `npm run build` + 17 Jest tests |
| `scala-sdk` | `sbt compile` + `sbt test` (21 ScalaTest tests) |
| `csharp-sdk` | `dotnet build` + `dotnet test` (20 xUnit tests) |
| `cpp-sdk` | CMake configure (`-S/-B`) + parallel build + CTest (30 GTest tests via FetchContent fallback) |
| `duckdb-java-example` | Builds SDK fat jar, compiles DuckDB example, smoke-tests class loading |
| `datafusion-python-example` | `pip install -e .` + DataFusion/PyArrow import check |
| `datafusion-rust-example` | `cargo check` with `protoc` available (build check, no execution) |
| `summary` | Gate job — fails the workflow if any of the 8 SDK jobs failed |

### `test-suite-validation.yml` — Test suite integrity

Validates that all test suite files are well-formed (YAML, plan binaries, CSV data and expected
outputs). Runs on push to `main` and on schedule.

### `engine-compliance-template.yml` — Template for engine developers ⭐

Copy this file into your own repository's `.github/workflows/` directory to enable automated
Substrait compliance testing. Customize:

- `ENGINE_NAME` / `ENGINE_VERSION` — your engine's identity
- build commands — whatever produces the JAR/binary your engine needs
- `COMPLIANCE_THRESHOLD` — minimum pass rate before the job fails (default 80 %)
- `COMPLIANCE_SDK_VERSION` — framework version to pull (currently `0.1.1`)

### `release-publish.yml` — Versioned release and publishing

Triggered by a semver tag (`v*.*.*`). Builds the Java SDK fat jar and the Python wheel, then
publishes both as GitHub Release assets. Maven Central and PyPI publishing require credentials
stored as repository secrets; they are wired but gated on the secrets being present.

### `api-build-test.yml` — REST API build and test

Builds the Spring Boot REST API and runs its unit tests. The API is pre-release functionality.

### `api-container-build.yml` — Multi-platform container image

Builds a multi-platform (`linux/amd64`, `linux/arm64`) Docker image for the REST API.

### `api-deploy-staging.yml` / `api-deploy-production.yml` — Staged deployments

Deploy the REST API container to staging and production environments. Require environment secrets.

### `api-pr-validation.yml` — REST API pull-request validation

Runs on pull requests that touch `api/**` or `sdk/java/**`. Builds and tests the Spring Boot API
and comments results on the PR. Complements `api-build-test.yml` (which runs on every push).

### `sdk-verification.yml` — SDK cross-build verification (scheduled)

Runs daily and on pushes that touch `sdk/**`. Verifies that all SDK build artefacts are
consistent with the test-suite data on disk. Supplements `sdk-build-test.yml` with a scheduled
freshness check independent of developer commits.

### `compliance-leaderboard.yml` — Leaderboard aggregation

Runs weekly (Sunday 00:00 UTC), on `workflow_dispatch`, and on `repository_dispatch` events of
type `compliance-report-submitted`. Collects compliance reports from participating engines and
regenerates the public leaderboard JSON.

## Notes

- The `summary` job in `sdk-build-test.yml` is the single required status check for branch
  protection. All eight SDK jobs must pass; the two example smoke-test jobs are advisory.
- The C++ job uses a `FetchContent` fallback in `sdk/cpp/tests/CMakeLists.txt` — it downloads
  and builds GTest from source when the system package is unavailable.
- The `datafusion-rust` CI job runs `cargo check` only; a full `cargo build --release` requires
  `protoc` ≥ 3.15 and takes ~5 minutes on the free runner tier.
