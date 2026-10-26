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
| `scala-sdk` | `sbt compile` + `sbt test` (26 ScalaTest tests) |
| `csharp-sdk` | `dotnet build` + `dotnet test` (20 xUnit tests) |
| `cpp-sdk` | CMake configure (`-S/-B`) + parallel build + CTest (30 GTest tests via FetchContent fallback) |
| `duckdb-java-example` | Builds SDK fat jar, compiles DuckDB example, smoke-tests class loading |
| `datafusion-python-example` | `pip install -e .` + DataFusion/PyArrow import check |
| `datafusion-rust-example` | `cargo check` with `protoc` available (build check, no execution) |
| `summary` | Gate job — fails the workflow if any of the 8 SDK jobs failed |

### `test-suite-validation.yml` — Test suite integrity

Validates that all test suite files are well-formed (YAML, plan binaries, CSV data and expected
outputs). Runs on push to `main` and on schedule.

### `duckdb-compliance.yml` — DuckDB engine compliance & dashboard workflow ⭐

End-to-end working CI workflow demonstrating how DuckDB integrates the Substrait compliance
framework, compiles and executes tests using the `ComplianceEngine` interface, generates
compliance reports, combines them into DuckDB's leaderboard entry (artifact
`duckdb-leaderboard-report`), and fails on a regression from the last published result
(`scripts/check_compliance_regression.py`).
This workflow serves as a reference implementation that can be parameterized for any database engine.
See the comprehensive [Engine CI Integration Guide](../../docs/CI_COMPLIANCE_WORKFLOW_GUIDE.md) for step-by-step setup instructions and CLI commands.

### `datafusion-compliance.yml` — DataFusion engine compliance

Runs Apache DataFusion (Python bindings) on TPC-H in both plan encodings
(`scripts/run_plan_compliance.py`) and on the function suite as Substrait plans and as SQL.
It combines the reports into DataFusion's leaderboard entry (artifact
`datafusion-leaderboard-report`) and runs the same regression check as DuckDB.

How both engines' entries reach the leaderboard: [docs/COMPLIANCE_LEADERBOARD.md](../../docs/COMPLIANCE_LEADERBOARD.md).

### `engine-compliance-template.yml` — Template for engine developers ⭐

Copy this file into your own repository's `.github/workflows/` directory to enable automated
Substrait compliance testing. Customize:

- `ENGINE_NAME` / `ENGINE_VERSION` — your engine's identity
- build commands — whatever produces the JAR/binary your engine needs
- `COMPLIANCE_THRESHOLD` — minimum pass rate before the job fails (default 80 %)
- `COMPLIANCE_SDK_VERSION` — framework version to pull (currently `0.1.2`)

### `release-publish.yml` — Versioned release and publishing

Triggered by a semver tag (`v*.*.*`). Builds the Java SDK fat jar and the Python wheel, then
publishes both as GitHub Release assets. Maven Central and PyPI publishing require credentials
stored as repository secrets; they are wired but gated on the secrets being present.

### `sdk-verification.yml` — SDK cross-build verification (scheduled)

Runs daily and on pushes that touch `sdk/**`. Verifies that all SDK build artefacts are
consistent with the test-suite data on disk. Supplements `sdk-build-test.yml` with a scheduled
freshness check independent of developer commits.

### `submission-validation.yml` — Leaderboard submission check

Runs on pull requests that change `submissions/**`. Checks the submission
(`scripts/validate_submission.py`) with the checker, suites and schemas of the base branch, never
the pull request's, and shows in the job summary where it would rank. See
[submissions/README.md](../../submissions/README.md).

### `submit-results.yml` — Reusable workflow for engine teams

Called from an engine team's own CI (`uses: IBM/substrait-compliance/.github/workflows/submit-results.yml@main`).
Packages their suite reports as a submission (`scripts/prepare_submission.py`), checks it, and
opens the pull request from a fork. `dry-run: true` only builds and checks it.

### `compliance-leaderboard.yml` — Leaderboard aggregation

Runs weekly (Sunday 00:00 UTC), on `workflow_dispatch`, and on `repository_dispatch` events of
type `compliance-report-submitted`, and when a DuckDB or DataFusion compliance run on `main`
finishes, and when a submission is merged. Collects each engine's leaderboard entry from its
latest successful run on `main`, adds each submitted engine's latest merged submission
(`scripts/collect_submissions.py`), and regenerates the public leaderboard (`scripts/generate_leaderboard.py --format all`: Markdown, JSON
and the GitHub Pages page). Engines are ranked by the average of their Functions and TPC-H pass
rates. See [docs/COMPLIANCE_LEADERBOARD.md](../../docs/COMPLIANCE_LEADERBOARD.md).

## Notes

- The `summary` job in `sdk-build-test.yml` is the single required status check for branch
  protection. All eight SDK jobs must pass; the two example smoke-test jobs are advisory.
- The C++ job uses a `FetchContent` fallback in `sdk/cpp/tests/CMakeLists.txt` — it downloads
  and builds GTest from source when the system package is unavailable.
- The `datafusion-rust` CI job runs `cargo check` only; a full `cargo build --release` requires
  `protoc` ≥ 3.15 and takes ~5 minutes on the free runner tier.
