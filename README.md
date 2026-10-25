# Substrait Compliance

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI](https://github.com/IBM/substrait-compliance/actions/workflows/sdk-build-test.yml/badge.svg)](https://github.com/IBM/substrait-compliance/actions/workflows/sdk-build-test.yml)
[![Release](https://img.shields.io/github/v/release/IBM/substrait-compliance?include_prereleases)](https://github.com/IBM/substrait-compliance/releases)

Tests how well query engines run [Substrait](https://substrait.io) plans, and publishes the
results.

**Leaderboard: <https://ibm.github.io/substrait-compliance/>**

Substrait is a cross-language format for query plans: one system produces a plan, another
executes it. This repository holds shared test suites for Substrait consumers, the tooling to
run them against an engine, and a public leaderboard with a page per engine showing what
fails and why. New to the project? Start with the [introduction](docs/INTRODUCTION.md).

## What is measured

Engines are scored on two things, run as Substrait plans through the engine's own Substrait
consumer:

- **Functions**: the Substrait project's own 1,315 function test cases (Substrait 0.107.0).
- **TPC-H**: the 22 TPC-H queries.

**Overall** is the average of the two. A pass rate is passed ÷ (total − skipped); see
[how engines are scored](docs/COMPLIANCE_LEADERBOARD.md#how-engines-are-scored).

Each engine's page groups its failures by cause: gaps in its Substrait consumer, cases its own
SQL also gets wrong, failing queries, and cases this repository cannot test yet. The engine's
own SQL results are shown next to the scores, unscored, so a consumer gap can be told apart
from a gap in the engine.

This repository's CI runs **DuckDB** (Substrait community extension) and **Apache DataFusion**
weekly and whenever the suites or runners change. Other engines take part by pull request
([below](#add-your-engine-to-the-leaderboard)).

## Test suites

| Suite | What it is | Cases | On the leaderboard |
|---|---|---:|---|
| `functions-substrait-0.107` | The Substrait project's function test cases ([`tests/cases`](https://github.com/substrait-io/substrait/tree/v0.107.0/tests/cases) at v0.107.0), with 55 documented fixes | 1,315 | **Scored** (Functions) |
| `tpch` | TPC-H queries as Substrait 0.107.0 plans, scale factor 0.01, with expected outputs | 22 | **Scored** (TPC-H) |
| `functions-substrait-0.107-extended` | Cases converted from this repository's earlier function suite, validated and verified | 2,278 | Reference |
| `tpch-substrait-0.81` | The same TPC-H queries in the older Substrait 0.81 encoding | 22 | Reference |
| `…-sql` | The function suites run as the engine's own SQL | | Reference |

CI checks every function case against the Substrait 0.107.0 function definitions. Details of each suite, its data and how expected results were produced:
[test-suites/README.md](test-suites/README.md).

## Try it locally

Run DataFusion against both scored suites, the way CI does (Python 3.11):

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -e sdk/python "datafusion==55.0.0" "substrait-extensions==0.107.0" "substrait-protobuf==0.107.0"

python scripts/run_function_compliance.py --engine datafusion --mode plan \
  --suites-dir test-suites/functions-substrait-0.107/upstream \
  --suite-name functions-substrait-0.107 --output out/datafusion-functions.json
python scripts/run_plan_compliance.py --engine datafusion \
  --test-suite test-suites/tpch/metadata.yaml --output out/datafusion-tpch.json

python scripts/combine_engine_reports.py --engine DataFusion \
  --functions functions-substrait-0.107=out/datafusion-functions.json \
  --queries tpch=out/datafusion-tpch.json --output out/datafusion.json
```

The last command prints the engine's Overall, Functions and TPC-H scores, which match its
leaderboard entry. The report files list every case with its result and the reason for any
failure.

## Add your engine to the leaderboard

No hosted service is involved: you run the suites in your own CI and open a pull request.

1. Run `functions-substrait-0.107` and `tpch` from `main` through a small adapter for your
   engine. Engines without Python bindings use a command that runs one plan
   ([`examples/leaderboard-adapter`](examples/leaderboard-adapter/)).
2. Submit the results with the reusable `submit-results.yml` workflow, or by hand with
   `scripts/prepare_submission.py`.
3. A check on the pull request validates the results against the current suites and shows
   where the engine would rank.
4. Once merged, the engine appears on the leaderboard as **Submitted**, linked to your CI run.

Guide: [submissions/README.md](submissions/README.md).

## Which route do I use?

| Goal | Use |
|---|---|
| Appear on the leaderboard | The Python runners in [`scripts/`](scripts/) through an adapter for your engine, then a pull request ([submissions/README.md](submissions/README.md)) |
| Test your engine in its own test suite, in its own language | A language SDK: Java, Python, Rust, C++, Go, TypeScript, C# or Scala ([sdk/README.md](sdk/README.md)) |

- The **Functions** score comes only from the Python function runner, which builds a plan for
  each Substrait function case.
- The **TPC-H** score can come from the Python plan runner or from the Java SDK runner, so JVM
  engines can use the Java SDK directly.
- The other SDKs are for in-house testing. Their comparators are checked against the
  leaderboard's shared comparison vectors ([sdk/conformance/](sdk/conformance/README.md)), so
  their results mean the same as the leaderboard's.

## Repository layout

| Path | Contents |
|---|---|
| [`test-suites/`](test-suites/README.md) | The suites: function cases, TPC-H plans, data and expected outputs |
| [`scripts/`](scripts/) | Runners, plan builder, validators, scoring and leaderboard generation |
| [`sdk/`](sdk/README.md) | Language SDKs and their shared conformance vectors |
| [`submissions/`](submissions/README.md) | How to submit results |
| [`examples/`](examples/README.md) | Engine integrations: DuckDB (Java, C++), DataFusion (Python, Rust), Velox (C++, needs a Velox build), and the leaderboard adapter |
| [`docs/`](docs/) | Guides: introduction, scoring, CI workflows, benchmarking |
| [`.github/workflows/`](.github/workflows/README.md) | CI: SDK builds, suite validation, engine runs, submission checks, publishing |
| `demo/`, `test-implementations/` | Early prototypes, including a dashboard with simulated engines; not used by the leaderboard |

## Documentation

- [Introduction](docs/INTRODUCTION.md): motivation, architecture and how to get involved
- [Leaderboard and scoring](docs/COMPLIANCE_LEADERBOARD.md): how engines are scored and how results are published
- [Test suites](test-suites/README.md): what each suite contains and how its results were verified
- [Submitting results](submissions/README.md): adapters, the submission workflow and the checks
- [Language SDKs](sdk/README.md): setting up an SDK and implementing the engine interface
- [CI workflows](.github/workflows/README.md) and the [engine CI guide](docs/CI_COMPLIANCE_WORKFLOW_GUIDE.md)
- [Changelog](CHANGELOG.md)

## Contributing

Contributions are welcome: test cases, engine adapters and submissions, SDK improvements and
documentation. See [CONTRIBUTING.md](CONTRIBUTING.md). Questions and problems go to
[GitHub Issues](https://github.com/IBM/substrait-compliance/issues).

The project's [governance](GOVERNANCE.md), [maintainers](MAINTAINERS.md),
[code of conduct](CODE_OF_CONDUCT.md) and [security policy](SECURITY.md) are in the
repository root.

## License

Apache License 2.0; see [LICENSE](LICENSE). The function test cases in
`test-suites/functions-substrait-0.107/upstream` are from the Substrait project, also under
Apache 2.0.

## Acknowledgments

- The [Substrait](https://substrait.io) community, for the specification and its function test
  cases
- The [TPC](https://www.tpc.org), for the TPC-H benchmark
- The DuckDB and Apache DataFusion projects, whose Substrait support is tested here
