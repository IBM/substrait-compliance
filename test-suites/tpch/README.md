# TPC-H Compliance Test Suite

## Overview

This test suite contains 22 TPC-H queries for Substrait compliance testing.

- **Scale Factor:** 0.01 (~86K rows total)
- **Version:** 1.0.0
- **Queries:** 22 (Q1-Q22)
- **Result verification:** ✅ Full — all 22 expected output files are present

## Structure

```
tpch/
├── metadata.yaml                # Suite "tpch": plans/ (Substrait 0.107.0)
├── metadata-substrait-0.81.yaml # Suite "tpch-substrait-0.81": same queries, original plans
├── plans/
│   ├── q01.bin           # Binary Substrait plans (44 files: q01–q22), Substrait 0.107.0
│   ├── q01.json          # JSON Substrait plans
│   └── ...
├── plans-substrait-0.81/ # The original plans, as produced with Substrait 0.81
├── data/
│   ├── lineitem.csv      # Input data (60,175 rows)
│   ├── orders.csv        # Input data (15,000 rows)
│   └── ...              # 8 CSV files total
└── expected/             # ✅ Reference outputs for all 22 queries
    ├── q01.csv           # Pipe-delimited, header row included
    └── ...              # q01.csv – q22.csv
```

When your engine runs TPC-H tests against the compliance runner, each query
result is compared against the corresponding file in `expected/`. A `PASSED`
result means the engine produced the correct answer. A `FAILED` result means
output was produced but did not match. A `SKIPPED` result is not expected for
this suite — if you see it, the expected files are missing from your checkout
(run `git status test-suites/tpch/expected/` to check).

## Plan encodings

Each plan set is named after the **Substrait spec version** it conforms to. The version is
recorded in every plan's `version` field and noted in a comment in the suite's metadata
file (not as a metadata field: released SDKs such as Java 0.1.1 reject unknown fields):

| Suite | Plans | Substrait | Meaning |
|---|---|---|---|
| `tpch` | `plans/` | 0.107.0 | The maintained plans; validated against the 0.107.0 protobuf definitions |
| `tpch-substrait-0.81` | `plans-substrait-0.81/` | 0.81.0 | The original plans (Isthmus), unchanged — tests whether an engine still accepts older plans |

Both suites share `data/` and `expected/`, so results are directly comparable.
DuckDB 1.5.6, for example, passes `tpch` 12/22 and `tpch-substrait-0.81` 3/22; the 10
remaining `tpch` failures are queries with subqueries, which DuckDB's Substrait extension
does not support.

### What changed between 0.81 and 0.107.0

The original plans used two encodings that were deprecated and later **removed** from the
spec. Current consumers no longer read them, so grouping keys and LIMIT clauses were silently
lost:

| What | Substrait 0.81 plans | Substrait 0.107.0 plans | Deprecated / removed |
|---|---|---|---|
| Aggregate grouping keys | `groupings[].grouping_expressions` | aggregate-level `grouping_expressions` + `groupings[].expression_references` ([spec](https://substrait.io/relations/logical_relations/#aggregate-operation)) | 0.57.0 ([#706](https://github.com/substrait-io/substrait/issues/706)) / 0.88.0 ([#1002](https://github.com/substrait-io/substrait/issues/1002)) |
| LIMIT / OFFSET | `FetchRel.count` / `offset` | `FetchRel.count_expr` / `offset_expr` ([spec](https://substrait.io/relations/logical_relations/#fetch-operation)) | 0.63.0 ([#748](https://github.com/substrait-io/substrait/issues/748)) / 0.98.0 ([#1130](https://github.com/substrait-io/substrait/issues/1130)) |
| Function `args` (always empty here) | present | dropped | removed in 0.93.0 ([#1085](https://github.com/substrait-io/substrait/issues/1085)) |
| Extension URIs | `extension_uris` + URNs | URNs only | removed in 0.85.0 ([#971](https://github.com/substrait-io/substrait/issues/971)) |

The 0.81 definitions, with these fields marked deprecated, are in
[algebra.proto at v0.81.0](https://github.com/substrait-io/substrait/blob/v0.81.0/proto/substrait/algebra.proto).

`plans/` was produced from `plans-substrait-0.81/` by `scripts/modernize_substrait_plans.py`,
which converts only the fields above and refuses any other field unknown to 0.107.0.
`scripts/verify_modernized_plans.py` checks the result: each `.bin` matches its `.json`;
apart from the converted fields and the version, every plan is identical to the original;
every grouping key and LIMIT/OFFSET value is preserved.

### Known upcoming change

The 0.107.0 plans still use one deprecated field: `OuterReference.steps_out` (deprecated
in 0.101.0 in favour of `rel_reference`), in the correlated-subquery plans q02, q04, q17,
q20, q21 and q22. When it is removed from the spec, these plans will need
converting again, following "Adding a new spec version" below.

### Adding a new spec version

When a spec change requires the plans to change:

1. Copy `plans/` to `plans-substrait-<its current version>/` and add a matching
   `metadata-substrait-<version>.yaml` (suite `tpch-substrait-<version>`).
2. Convert `plans/` (bump `TARGET_VERSION` in the converter), update the version comment
   in `metadata.yaml`, and verify.
3. Keep at most one or two older plan sets; remove the oldest when adding a new one.

Suite names therefore never change meaning: `tpch` is always the maintained plans,
and `tpch-substrait-<version>` always means exactly that version.

To run every TPC-H suite through DuckDB locally:
`python scripts/duckdb_benchmark_local.py` (needs `pip install duckdb`).

## Usage

### Java

```java
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load(Paths.get("test-suites/tpch/metadata.yaml"));

ComplianceRunner runner = new ComplianceRunner(myEngine);
ComplianceReport report = runner.runTestSuite(suite);
```

### Python

```python
from substrait_compliance import YamlTestSuiteLoader, ComplianceRunner

loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/tpch/metadata.yaml")

runner = ComplianceRunner(my_engine)
report = runner.run_test_suite(suite)
```

## Engines

| Engine | How the plans run | Runner | `tpch` (0.107.0) | `tpch-substrait-0.81` |
|---|---|---|---:|---:|
| DuckDB 1.5.6 | `substrait` community extension, `from_substrait()` (JDBC) | Java SDK, `examples/duckdb-java` | 12/22 | 3/22 |
| DataFusion 55.0.0 | `datafusion.substrait` consumer (Python) | `scripts/run_plan_compliance.py` | 8/22 | 8/22 |

`scripts/run_plan_compliance.py` loads inputs and scores results with the same rules as the Java
runner (typed inputs from each plan's read schema, values trimmed; case-insensitive column names;
numeric type family; rows in order; numbers within 1e-9 relative, and a column the plan declares
as `decimal(P,S)` within 10^-S), so the two engines are judged alike:

```bash
pip install -e sdk/python "datafusion==55.0.0"
python scripts/run_plan_compliance.py --engine datafusion \
    --test-suite test-suites/tpch/metadata.yaml --output /tmp/datafusion-tpch.json
```

DataFusion 55.0.0 passes 8 of 22 in both encodings. Its consumer does not support `varchar`
literals (13 queries), nor the enum argument of `extract(YEAR, …)` that q09 uses. DuckDB's 10
failures are the queries with subqueries, which its `substrait` extension does not run.

**Decimal columns.** The expected outputs hold doubles computed in full precision, but the
plans declare most money and average columns as decimals, e.g. q01's `avg_qty` as
`decimal(15,2)`. Such an answer is only defined to its declared scale, and engines differ in
how many extra digits they keep (DataFusion's `avg` keeps 6). Both comparators therefore read
each column's declared type from the plan and accept a value within one unit in the last
declared place (10^-S, so rounded and truncated results both match); other columns keep the
1e-9 rule. `sdk/python/tests/test_tpch_expected_outputs.py` checks that every expected output has the
plan's column names and type families.



- **SIMPLE** (3): Q1, Q6, Q14
- **MEDIUM** (7): Q3, Q4, Q10, Q12, Q13, Q16, Q19
- **COMPLEX** (8): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22
- **VERY_COMPLEX** (4): Q2, Q8, Q20, Q21

## Data Files

All data files are in CSV format with headers:

| File | Rows |
|------|------|
| `region.csv` | 5 |
| `nation.csv` | 25 |
| `part.csv` | 2,000 |
| `supplier.csv` | 100 |
| `partsupp.csv` | 8,000 |
| `customer.csv` | 1,500 |
| `orders.csv` | 15,000 |
| `lineitem.csv` | 60,175 |

**Total:** ~86,630 rows

## Expected Output File Format

Expected output files in `expected/` are pipe-delimited CSV files with a
**typed header** on line 1. Every column name carries a `:type` annotation
that tells the loader what Java/Python type to use when parsing values, and
tells the comparator what precision rules to apply when matching engine output.

### Syntax

```
col_name:type|col_name:type|...
value|value|...
```

- Delimiter: `|` (pipe)
- Header: required, line 1, every column must carry `:type`
- Data: lines 2 onwards, one row per line
- Quoting: double-quotes wrap values that contain the delimiter; `""` encodes a
  literal quote or an empty/null value
- Encoding: UTF-8, LF or CRLF line endings (the loader normalises both)

### Type vocabulary

| Token in header | Normalises to | Parsed as | Comparator rule |
|---|---|---|---|
| `string`, `varchar`, `char`, `text`, `utf8` | `string` | `String` | exact string equality |
| `integer`, `int`, `i32`, `i8`, `i16`, `int4`, `smallint`, `tinyint` | `integer` | `Integer` | numeric equality within ε = 1e-9 |
| `bigint`, `long`, `i64`, `int8` | `bigint` | `Long` | numeric equality within ε = 1e-9 |
| `double`, `fp64`, `decimal`, `numeric`, `float8`, `number` | `double` | `Double` | numeric equality within ε = 1e-9 |
| `float`, `fp32`, `real`, `float4` | `float` | `Float` | numeric equality within ε = 1e-9 |
| `boolean`, `bool` | `boolean` | `Boolean` | case-insensitive `true`/`false` |
| `date`, `timestamp`, `timestamptz`, `time` | `string` | `String` | exact string equality |

Type tokens are **case-insensitive**. All numeric types use epsilon comparison
(within 1e-9, relative for large values), so floating-point representation differences
(e.g. `380456.0` vs `380456`) do not cause spurious failures. Dates and
timestamps are treated as opaque strings — the engine must format them
identically to the reference value. Where the plan declares a column as `decimal(P,S)`, the
leaderboard's comparators also accept a value within 10^-S (see "Decimal columns" above).

**Cross-type matching:** if the engine returns a `Number` for a column the
expected CSV declared as `string` (or vice-versa), the comparator attempts to
parse the string as a number and compares numerically. This means a reference
CSV with `revenue:string` containing `"1193053.22"` will match an engine that
returns `Double(1193053.22)`.

### Compatibility with engine TableData

The comparator checks, in order:

1. **Row count** must match exactly.
2. **Column count** must match exactly.
3. **Column names** must match exactly (case-sensitive).
4. **Column types** are normalised through the table above; `integer` and
   `bigint` are distinct, but any two aliases for the same canonical type match
   (e.g. engine returning `"numeric"` matches reference `"double"`).
5. **Values** are compared per the comparator rule for the column's canonical
   type.

Engine-side `TableData` column types must therefore use one of the tokens from
the vocabulary table, or a canonical name (`integer`, `bigint`, `double`,
`float`, `boolean`, `string`).

### Adding expected outputs for a new suite

1. Run the query against a trusted source (e.g. DuckDB directly over the input
   CSV files) and export to pipe-delimited CSV.
2. Replace the auto-generated plain header with a typed header — use the type
   tokens above that best match the SQL result column types.
3. Use `double` for any `DECIMAL`/`NUMERIC` column; use `string` for date
   columns unless the engine is known to format dates exactly.
4. Store the file at `expected/<query_id>.csv` and add the path to
   `metadata.yaml` under `expectedOutput`.
5. Run the framework against a known-correct engine to confirm 22/22 PASSED
   before treating the golden file as authoritative.

### Example

```
# expected/q01.csv — first two lines
l_returnflag:string|l_linestatus:string|sum_qty:double|count_order:integer
A|F|380456.0|14876
```

## Expected Outputs

All 22 expected output files are present in `expected/` (typed-header
pipe-delimited CSV — see format section above). They were generated from the
same input data at scale factor 0.01 and serve as the reference for
correctness comparison.

### Oracle provenance

The expected outputs were produced by **DuckDB 1.2.0** executing the
Substrait plans directly against the scale-0.01 CSV data in `data/`.  DuckDB
is therefore the de-facto reference for result semantics in this suite.

**What this means for your engine:**

- An engine that produces the same results as DuckDB on this dataset will
  **PASS**.
- An engine whose SQL semantics differ from DuckDB — for example different
  numeric rounding, different NULL ordering, or different DATE string
  formatting — may **FAIL** even when its behaviour is otherwise correct.
- Engines with incomplete Substrait support may report **SKIPPED** for plans
  whose features they do not support.

To regenerate the expected outputs against a different reference engine, run
a script that runs each query on your preferred engine and writes the typed CSVs.

**Status:** ✅ Complete — result correctness is fully verifiable for all 22 queries.
