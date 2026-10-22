# Function tests — Substrait 0.107

Function test cases in the **current upstream Substrait test-case format**, checked against the
function definitions of Substrait **v0.107.0**. Its `upstream/` cases, run as **Substrait plans**
through each engine's Substrait consumer, are the leaderboard's **Functions** score (the overall
score averages it with the Queries score). The `extended/` cases and both parts run as SQL are shown
next to it, not scored. It replaces
[`test-suites/functions`](../functions/README.md), which still runs and is shown on the leaderboard
as `functions-legacy`, marked *not scored*.

| Part | Cases | What it is |
|---|---:|---|
| [`upstream/`](upstream/UPSTREAM.md) | 1,315 | The Substrait project's own test cases (`tests/cases` at v0.107.0), with 55 documented fixes |
| [`extended/`](extended/CONVERSION.md) | 2,180 | Cases from the legacy suite for standard functions, converted, validated and verified |

Both parts are generated — do not edit the `.test` files by hand (see [Regenerating](#regenerating)).

## Format

The format is defined by the Substrait project:
[tests/README.md](https://github.com/substrait-io/substrait/blob/v0.107.0/tests/README.md) and the
[ANTLR grammar](https://github.com/substrait-io/substrait/tree/v0.107.0/grammar). Files are read with
upstream's own parser, vendored in
[`sdk/python/substrait_compliance/testcase_parser`](../../sdk/python/substrait_compliance/testcase_parser/README.md).

```
### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: extension:io.substrait:functions_arithmetic

# basic: Basic examples without any special cases
add(120::i8, 5::i8) = 125::i8
add(120::i8, 10::i8) [overflow:ERROR] = <!ERROR>
```

Differences from the legacy format, in short: extensions are referenced by URN; temporal literals
are unquoted and use the precision types (`2016-12-31T13:30:15::pts<6>`, not
`'2016-12-31T13:30:15'::ts`); enum arguments are written `YEAR::enum`; nullable types carry `?`
(`null::i32?`); aggregates with several arguments and window functions are written over a table
(`((1), (2)) fn(col0::i32, …)`, `DEFINE f1(i32) = … / fn(col0) OVER f1`).

## What makes a case valid

[`scripts/validate_function_tests.py`](../../scripts/validate_function_tests.py) checks every case
against the extension YAMLs of the `substrait-extensions` package (0.107.0) and CI fails on any
invalid case. A case must parse; call a function defined in its `SUBSTRAIT_INCLUDE` extension; match
a non-deprecated signature of the kind the file tests (scalar, aggregate or window: argument count,
types, enum values); call only defined functions in nested calls and lambda bodies; use only
declared options and option values; expect the signature's return type; and use well-formed
literals (decimals with a precision and scale that fit the value, integers within range). This is stricter than upstream's own coverage check,
which is how the upstream problems listed in [`upstream/UPSTREAM.md`](upstream/UPSTREAM.md) were found.

## How cases are run: Substrait plans (scored) and SQL

[`scripts/run_function_compliance.py`](../../scripts/run_function_compliance.py) runs each case on an
engine — DuckDB (default) or Apache DataFusion (`--engine datafusion`) — in one of two modes:

- **`--mode plan` (scored).** [`scripts/function_plans.py`](../../scripts/function_plans.py) builds a
  Substrait 0.107.0 protobuf plan for the case — the function bound by extension URN and compound
  signature name (e.g. `add:i8_i8`), its arguments as typed Substrait literals, its options and enum
  arguments in the plan — and the engine runs it through its own Substrait consumer (DuckDB: the
  `substrait` extension's `from_substrait()`; DataFusion: `datafusion.substrait`). Only the input
  tables and the expected value use SQL. A plan the consumer rejects is a failure. The engine runs in
  a child process, so a plan that crashes the consumer fails that case and the run continues. Cases
  the builder cannot express yet (unsigned `u!` types, lambdas) are skipped.
- **`--mode sql` (not scored, the `*-sql` rows).** Each case is translated to the engine's SQL with
  [`scripts/function_sql.py`](../../scripts/function_sql.py). This measures the engine's SQL functions
  without its Substrait consumer. Only direct SQL spellings are mapped; a function an engine lacks
  is not emulated, and its cases are skipped.

| Engine | `upstream/` plans | `extended/` plans | `upstream/` SQL | `extended/` SQL | CI |
|---|---:|---:|---:|---:|---|
| DuckDB 1.5.6 | **69.5%** (808 / 355 / 152 skipped) | 90.0% (1,927 / 215 / 38) | 93.9% | 99.9% | `duckdb-compliance.yml` |
| DataFusion 55.0.0 | **71.2%** (820 / 332 / 163) | 86.5% (1,859 / 289 / 32) | 93.2% | 99.4% | `datafusion-compliance.yml` |

Only the `upstream/` plan rate (bold) is scored.

The gap between the two modes is the engines' Substrait consumers: functions they do not map
(DuckDB: `negate`, `shift_*`, `bitwise_not`, `and_not`, `is_true`, `regexp_*_substring`, …; DataFusion:
`regexp_*`, `shift_*`, `sign`, `std_dev`, `variance`, …), enum arguments (DataFusion rejects them;
DuckDB's extension crashes on `std_dev`/`variance` plans), typed null literals (DuckDB loses the
type), window functions (DuckDB) and wrong mappings (DuckDB maps list `cardinality` to its map-only
function and `extract(MILLISECOND)` to a different definition).

Because DuckDB is one of the verifiers of `extended/`, that part favours DuckDB; compare engines on
`upstream/`.

For each case:

- **passed / failed** — the engine's result does or does not match the expected value, compared by
  the expected type: integers and decimals exactly; booleans only with booleans; floats with a
  relative tolerance only (1e-9, 1e-6 for `fp32` — no absolute slack, so `0.0` means exactly zero);
  intervals and nanosecond timestamps by the engine's text form; other values (strings, dates,
  timestamps) by value. Result *types* are not scored (DuckDB widening `i64` to HUGEINT, or returning
  a timestamp at midnight for a date, still passes when the value is right);
- `<!ERROR>` passes only if the engine raises an error at run time — not when it cannot bind the call;
- options: a case pinned to a mode the engine implements (for DuckDB `overflow:ERROR`,
  `division_type:TRUNCATE`) is scored normally and the option's other values are **skipped**; for any
  other option a failure is reported as skipped ("mode not implemented"), since engines need to
  implement only one mode;
- **skipped** also covers `<!UNDEFINED>` results and cases the translator cannot express in the
  engine's SQL (including results too large to build, such as `repeat('a', 2147483647)`).

Pass rate = passed / (passed + failed). Every non-passing case is listed with its reason in the
report. A suite file that does not parse fails the run.

## Running on another engine

```bash
pip install -e sdk/python "datafusion==55.0.0"
python scripts/run_function_compliance.py --engine datafusion --output /tmp/df.json \
    --suites-dir test-suites/functions-substrait-0.107/upstream --suite-name functions-substrait-0.107
```

To add an engine, give it a `Dialect` in `scripts/function_sql.py` (type names and the functions it
spells differently) and an engine class in `scripts/run_function_compliance.py` (how to run a query,
how its errors classify, which option modes it implements).

## Regenerating

```bash
pip install -e sdk/python "duckdb==1.5.6" "substrait-extensions==0.107.0" mpmath numpy

# upstream/ from a substrait checkout at the release tag
git clone --depth 1 --branch v0.107.0 https://github.com/substrait-io/substrait.git /tmp/substrait
python scripts/sync_upstream_function_tests.py --upstream-dir /tmp/substrait \
    --dest test-suites/functions-substrait-0.107/upstream

# extended/ from the legacy suite (needs upstream/ first)
python scripts/convert_legacy_function_tests.py \
    --legacy test-suites/functions --suite test-suites/functions-substrait-0.107

python scripts/validate_function_tests.py test-suites/functions-substrait-0.107
python scripts/run_function_compliance.py --output /tmp/upstream.json \
    --suites-dir test-suites/functions-substrait-0.107/upstream --suite-name functions-substrait-0.107
```

The sync stops if a listed fix no longer matches the upstream file (for example because upstream
fixed it), so fixes never silently stop applying. The conversion is deterministic for a given
DuckDB version.

## Moving to a newer Substrait version

1. Create `functions-substrait-<new>` with the commands above at the new tag and the matching
   `substrait-extensions` version; review which `FIXES` upstream has resolved.
2. Run it next to this suite, not scored (`combine_engine_reports.py --reference`); compare the
   per-case reports.
3. Make it the scored suite and deprecate this one — the same steps as for the legacy suite below.

## The legacy suite

[`test-suites/functions`](../functions/README.md) has been replaced by this suite. It still runs in
CI and appears on the leaderboard as `functions-legacy` (passed to `combine_engine_reports.py` with
`--reference`, so it is shown but *not scored*). Remaining clean-up, when wanted:

1. Review the quarantined cases in [`extended/CONVERSION.md`](extended/CONVERSION.md) — they are
   valid but unverified — and add the ones that are right.
2. Stop running the legacy suite and remove `test-suites/functions` (or move it to an archive)
   together with its regex-based runner path.
