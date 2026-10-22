# Function tests — Substrait 0.107

Function test cases in the **current upstream Substrait test-case format**, checked against the
function definitions of Substrait **v0.107.0**. This is the **scored** function suite: both parts
count towards an engine's headline pass rate on the leaderboard. It replaces
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

## How cases are scored

[`scripts/run_function_compliance.py`](../../scripts/run_function_compliance.py) runs each case on an
engine — DuckDB (default) or Apache DataFusion (`--engine datafusion`) — translating it to that
engine's SQL with [`scripts/function_sql.py`](../../scripts/function_sql.py), and compares the result
with the expected literal evaluated by the same engine. Only direct SQL spellings are mapped: a
function an engine lacks (e.g. DataFusion has no list lambdas, `isfinite` or bitwise NOT) is not
emulated, and its cases are skipped as untranslatable.

| Engine | `upstream/` | `extended/` | CI |
|---|---|---|---|
| DuckDB 1.5.6 | 93.9% (1,189 passed, 77 failed, 49 skipped) | 99.9% (2,151 / 2 / 27) | `duckdb-compliance.yml`, on the leaderboard |
| DataFusion 55.0.0 | 93.2% (1,089 passed, 80 failed, 146 skipped) | 99.4% (2,068 / 12 / 100) | `datafusion-compliance.yml`, report artifact |

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
2. Run it next to this suite, not scored (`--informational`); compare the per-case reports.
3. Make it the scored suite and deprecate this one — the same steps as for the legacy suite below.

## The legacy suite

[`test-suites/functions`](../functions/README.md) has been replaced by this suite. It still runs in
CI and appears on the leaderboard as `functions-legacy` (passed to `combine_engine_reports.py` with
`--informational`, so it is shown but *not scored*). Remaining clean-up, when wanted:

1. Review the quarantined cases in [`extended/CONVERSION.md`](extended/CONVERSION.md) — they are
   valid but unverified — and add the ones that are right.
2. Stop running the legacy suite and remove `test-suites/functions` (or move it to an archive)
   together with its regex-based runner path.
