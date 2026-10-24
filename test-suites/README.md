# Test Suites

Pre-packaged test suites for Substrait compliance testing. Each suite contains Substrait plan
binaries, input data, and expected reference outputs.

## Suites

| Suite | Queries | Data files | Expected outputs | Oracle |
|-------|---------|------------|-----------------|--------|
| [TPC-H](tpch/README.md) | 22 | 8 CSV (86,805 rows, scale 0.01) | 22 CSV | DuckDB 1.2.0 |
| [Functions (Substrait 0.107)](functions-substrait-0.107/README.md) | — | — | inline in .test files | Substrait spec, validated against the 0.107.0 extension YAMLs |
| [Functions (legacy)](functions/README.md) | — | — | inline in .test files | replaced by the suite above; shown as `functions-legacy`, not scored |

## Suites at a glance

Each name is a suite the leaderboard shows, as it appears in
[`registry.json`](registry.json) and in the engines' reports (`<engine>-<name>.json`). Cases
are counted as the runners count them.

| Report name | What it runs | Plans or SQL | Cases | Leaderboard |
|---|---|---|---|---|
| `functions-substrait-0.107` | The Substrait project's own function cases ([`functions-substrait-0.107/upstream`](functions-substrait-0.107/README.md)) | Substrait plans, through the engine's Substrait consumer | 1,315 | **Scored: Functions** |
| `tpch` | The 22 TPC-H queries at scale 0.01, plans in the current 0.107.0 encoding ([`tpch`](tpch/README.md)) | Substrait plans | 22 | **Scored: Queries** |
| `functions-substrait-0.107-sql` | The same 1,315 function cases | The engine's own SQL | 1,315 | Reference. The difference from the plan run is the **consumer gap**: what the engine can do but its Substrait consumer cannot. |
| `functions-substrait-0.107-extended` | 2,278 verified cases converted from the legacy suite ([`functions-substrait-0.107/extended`](functions-substrait-0.107/README.md)) | Substrait plans | 2,278 | Reference |
| `functions-substrait-0.107-extended-sql` | The same 2,278 cases | The engine's own SQL | 2,278 | Reference |
| `tpch-substrait-0.81` | The same 22 TPC-H queries, plans in the legacy 0.81 encoding | Substrait plans | 22 | Reference |
| `functions-legacy` | The legacy function suite ([`functions`](functions/README.md)), replaced by `functions-substrait-0.107` | SQL | 5,037 | Reference. DuckDB only; not in the registry, so not accepted in submissions. |

- An engine's **Overall** score is the average of its Functions and Queries pass rates; reference
  suites never change it ([how engines are scored](../docs/COMPLIANCE_LEADERBOARD.md#how-engines-are-scored)).
- DuckDB and DataFusion, the engines this repository's CI runs, run every suite above;
  `functions-legacy` is run for DuckDB only.

## Function Tests

[`functions-substrait-0.107`](functions-substrait-0.107/README.md) is the current-format function
suite: the Substrait project's own 1,315 cases (with documented fixes) plus 2,278 verified cases
converted from the legacy suite. Every case is validated against the Substrait 0.107.0 function
definitions. Its `upstream/` cases, run as Substrait plans through each engine's Substrait
consumer, are the leaderboard's **Functions** score; the same cases as SQL and the `extended/`
cases are shown next to it, not scored. The legacy suite below is shown as `functions-legacy`,
not scored.

The legacy suite has 136 `.test` files across 14 semantic categories with 5,041 individual test assertions.
Expected values are embedded inline in each `.test` file — no separate expected-output directory.

Categories: `aggregate`, `arithmetic`, `array`, `cast`, `comparison`, `conditional`,
`datetime`, `geospatial`, `json`, `map`, `set`, `string`, `struct`, `window`.

See [functions/README.md](functions/README.md) for the full category breakdown.

## Result Semantics

| Result | Counts as | Meaning |
|--------|-----------|---------|
| `PASSED` | passed | The output matches the expected output ([how results are compared](../sdk/conformance/README.md#comparison-vectorsjson)) |
| `FAILED` | not passed | The output differs, or the engine rejected or crashed on the plan |
| `ERROR`, `UNSUPPORTED` | not passed | Reported separately by some SDKs: the run raised an error, or the engine declares it cannot run the plan |
| `SKIPPED` | neither | The case does not apply: an option mode the engine does not claim, an `<!UNDEFINED>` expected result, a case the plan builder cannot express yet, or no expected output |

Pass rate = passed ÷ (total − skipped); see
[how the pass rate is calculated](../docs/COMPLIANCE_LEADERBOARD.md#how-the-pass-rate-is-calculated).

For TPC-H a `SKIPPED` result means the expected file is absent from your checkout.
Run `git status test-suites/tpch/expected/` to check. All expected files are committed and should be present on a clean clone.

## Adding Expected Outputs

Expected CSV files use pipe (`|`) as the delimiter. The first row is the header, typed
(`name:type`). Values are compared by the shared rule in
[`sdk/conformance/README.md`](../sdk/conformance/README.md#comparison-vectorsjson): numbers within a relative
tolerance, and decimal columns at the scale the plan declares.

See the per-suite READMEs for oracle provenance and data generation notes.
