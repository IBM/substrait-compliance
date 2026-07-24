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
├── metadata.yaml          # Test suite definition
├── plans/
│   ├── q01.bin           # Binary Substrait plans (44 files: q01–q22)
│   ├── q01.json          # JSON Substrait plans
│   └── ...
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

## Query Complexity

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
(absolute difference < 1e-9), so floating-point representation differences
(e.g. `380456.0` vs `380456`) do not cause spurious failures. Dates and
timestamps are treated as opaque strings — the engine must format them
identically to the reference value.

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

### Adding expected outputs for a new suite (e.g. TPC-DS)

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

**Status:** ✅ Complete — result correctness is fully verifiable for all 22 queries.
