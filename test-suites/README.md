# Test Suites

Pre-packaged test suites for Substrait compliance testing. Each suite contains Substrait plan
binaries, input data, and expected reference outputs.

## Suites

| Suite | Queries | Data files | Expected outputs | Oracle |
|-------|---------|------------|-----------------|--------|
| [TPC-H](tpch/) | 22 | 8 CSV (86,805 rows, scale 0.01) | 22 CSV | DuckDB 1.2.0 |
| [TPC-DS](tpcds/) | 99 | 24 CSV (scale 0.01) | 99 CSV | DuckDB 1.2.0 |
| [Functions](functions/) | — | — | inline in .test files | Substrait spec |

## Function Tests

136 `.test` files across 14 semantic categories with 5,041 individual test assertions.
Expected values are embedded inline in each `.test` file — no separate expected-output directory.

Categories: `aggregate`, `arithmetic`, `array`, `cast`, `comparison`, `conditional`,
`datetime`, `geospatial`, `json`, `map`, `set`, `string`, `struct`, `window`.

See [functions/README.md](functions/README.md) for the full category breakdown.

## Result Semantics

| Result | Meaning |
|--------|---------|
| `PASSED` | Actual output matches expected output (within epsilon for floats) |
| `FAILED` | Actual output does not match expected output |
| `SKIPPED` | Expected output file missing — run is incomplete, not a pass |

A `SKIPPED` result means the expected file is absent from your checkout. Run
`git status test-suites/tpch/expected/` or `git status test-suites/tpcds/expected/`
to check. All expected files are committed and should be present on a clean clone.

## Adding Expected Outputs

Expected CSV files use pipe (`|`) as the delimiter. The first row is the header.
Numeric types are compared with epsilon tolerance (see `ResultComparator` in the Java SDK).

See the per-suite READMEs for oracle provenance and data generation notes.
