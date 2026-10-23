# Conformance vectors shared by the SDKs

Files in this folder define behaviour every SDK must share. Each SDK's test suite reads
them, so the SDKs cannot drift apart.

## `pass-rate-vectors.csv`

How a report's pass rate is computed:

    pass rate = passed / (total - skipped) * 100        (0 when nothing was executed)

- **Skipped** results count neither way. A test that does not apply to an engine, or an
  option mode it does not claim, says nothing about compliance.
- **Failed**, **error** and **unsupported** results all count as not passed. An engine that
  cannot run a test is not compliant with it, and declaring tests unsupported must not
  raise a score.

This is the rule the public leaderboard uses (`scripts/combine_engine_reports.py`,
[docs/COMPLIANCE_LEADERBOARD.md](../../docs/COMPLIANCE_LEADERBOARD.md)).

Each row gives the counts of each status and the expected pass rate in percent. An SDK
without an ERROR or UNSUPPORTED status skips rows that use it. Compare with a tolerance of
1e-9.

| SDK | Test |
|---|---|
| Python | `sdk/python/tests/test_pass_rate_vectors.py` |
| Java | `sdk/java/src/test/java/io/substrait/compliance/PassRateVectorsTest.java` |
| Go | `sdk/go/pass_rate_vectors_test.go` |
| TypeScript | `sdk/typescript/tests/pass-rate-vectors.test.ts` |
| Rust | `sdk/rust/tests/pass_rate_vectors_test.rs` |
| Scala | `sdk/scala/src/test/scala/io/substrait/compliance/PassRateVectorsSpec.scala` |
| C# | `sdk/csharp/Tests/PassRateVectorsTests.cs` |
| C++ | `sdk/cpp/tests/test_pass_rate_vectors.cpp` |

## `comparison-vectors.json`

How a query's output is compared with its expected output: column names
(case-insensitive), column types (one numeric family: integer, decimal and double; one
text family including dates), rows in order, and values. Numbers match within a relative
tolerance of 1e-9, with an absolute floor of 1e-9 near zero; a column the plan declares
as `decimal(P,S)` also matches within one unit in the last declared place, 10^-S (a
vector's optional `declaredScales` gives these scales). NaN matches NaN, infinity
only the same infinity, and NULL only NULL. A numeric string matches the number. Strings
compare exactly.

Each vector gives an expected and an actual table and whether they match. They are
checked against the two comparators that score the leaderboard's Queries, and against the
language SDKs' own comparators, so an SDK's in-house result means the same as the
leaderboard's:

| Comparator | Test |
|---|---|
| Java `ComplianceRunner.describeMismatch` (DuckDB, TPC-H) | `sdk/java/src/test/java/io/substrait/compliance/ComparisonVectorsTest.java` |
| Python `scripts/run_plan_compliance.py` (DataFusion, TPC-H) | `sdk/python/tests/test_comparison_vectors.py` |
| Rust `describe_mismatch` (`sdk/rust/src/runner.rs`) | `sdk/rust/tests/comparison_vectors_test.rs` |
| Scala `ResultComparison.describeMismatch` | `sdk/scala/src/test/scala/io/substrait/compliance/ComparisonVectorsSpec.scala` |
| Go `DescribeMismatch` (`sdk/go/comparison.go`) | `sdk/go/comparison_vectors_test.go` |
| TypeScript `ResultComparator.describeMismatch` | `sdk/typescript/tests/comparison-vectors.test.ts` |
| C# `ResultComparator.DescribeMismatch` | `sdk/csharp/Tests/ComparisonVectorsTests.cs` |
| C++ `ResultComparator::describe_mismatch` | `sdk/cpp/tests/test_comparison_vectors.cpp` |

A declared decimal's scale comes from the plan's JSON form: `PlanOutputTypes` (Java and
Scala), `declared_scales` (Python), `plan_types` (Rust), `DeclaredScales` (Go),
`declaredScales` (TypeScript), `PlanTypes` (C#) and `plan_types` (C++) work it out the same
way, and agree on all 143 TPC-H (both encodings) and TPC-DS plans.

A comparator is only as good as the tables it is given: the TypeScript and C# loaders read
their own YAML format rather than this repository's suite metadata, and the Go loader does not
load input tables, so those SDKs cannot yet run the repository's TPC-H and TPC-DS suites as
they stand.
