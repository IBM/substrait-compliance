# SDK Build Verification

How to check that the eight language SDKs build and pass their tests, locally and in CI.

## All SDKs at once

```bash
./scripts/verify_sdk_builds.sh
```

The script builds and tests each SDK whose toolchain is installed (C++ is built without its
tests), prints a summary, and keeps each build's log in `/tmp` (for example
`/tmp/java_test.log`). An SDK whose toolchain is missing is reported as skipped; [`scripts/install_sdk_tools.sh`](../scripts/install_sdk_tools.sh)
installs the toolchains.

## One SDK at a time

The same commands CI runs, from the repository root, with the toolchain versions CI uses:

| SDK | Toolchain | Build and test |
|---|---|---|
| Java | JDK 17 | `cd sdk/java && ./gradlew build` (runs the tests) |
| Python | Python 3.9–3.12 | `cd sdk/python && pip install -e . pytest jsonschema && pytest tests/` |
| Rust | stable | `cd sdk/rust && cargo test --release` |
| Go | Go 1.21 | `cd sdk/go && go test ./...` |
| TypeScript | Node.js 20 | `cd sdk/typescript && npm ci && npm test && npm run build` |
| C# | .NET 10 | `cd sdk/csharp && dotnet test Substrait.Compliance.csproj` |
| Scala | sbt 1.9, JDK 17 | `cd sdk/scala && sbt test` |
| C++ | C++17, CMake 3.15+ | `cmake -S sdk/cpp -B sdk/cpp/build && cmake --build sdk/cpp/build && ctest --test-dir sdk/cpp/build --output-on-failure` |

Every SDK's tests include the shared comparison and pass-rate vectors in
[`sdk/conformance/`](../sdk/conformance/README.md), so a passing build also means the SDK
compares results and computes pass rates the way the leaderboard does. Setting up an SDK and
implementing its engine interface is covered in the [SDK guide](../sdk/README.md).

## In CI

- [`sdk-build-test.yml`](../.github/workflows/sdk-build-test.yml) runs on every push and pull
  request to `main`: one job per SDK (Python on 3.9 to 3.12), plus smoke tests of the DuckDB Java,
  DataFusion Python and DataFusion Rust examples, and coverage for Java, Python and Rust.
- [`sdk-verification.yml`](../.github/workflows/sdk-verification.yml) runs the same checks
  nightly (02:00 UTC) and on demand.

## Common problems

- **C++: missing packages.** The build needs Protobuf and yaml-cpp. On Debian or Ubuntu:
  `sudo apt-get install cmake libprotobuf-dev protobuf-compiler libyaml-cpp-dev pkg-config`.
  An error about a standard header such as `cstddef` usually means the compiler toolchain
  itself is incomplete.
- **Rust: `protoc` not found** when building the DataFusion Rust example: install
  `protobuf-compiler`.
- **Python: import errors.** Install the SDK in editable mode (`pip install -e sdk/python`) in
  the environment you run the tests from.
- **Java: Gradle cannot resolve dependencies.** The build downloads from Maven Central and the
  Gradle plugin portal; check network or proxy access to both.
