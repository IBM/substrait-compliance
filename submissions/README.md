# Leaderboard submissions

Engine teams run the compliance suites in **their own CI** and submit the results here by
**pull request**. Each submission is checked automatically, scored with the same rules as
every other engine, and published on the
[leaderboard](https://ibm.github.io/substrait-compliance/) once merged. The leaderboard
shows it as **Submitted**, with a link to the team's CI run. Engines run by this repository's
own CI (DuckDB, DataFusion) are shown as **Run here**.

How the leaderboard is scored and published is described in
[docs/COMPLIANCE_LEADERBOARD.md](../docs/COMPLIANCE_LEADERBOARD.md).

There is no server and no account to request: a GitHub account that can open pull requests
is enough.

## What to run

The accepted suites and how they count are listed in
[`test-suites/registry.json`](../test-suites/registry.json):

| Suite | Counts as | Required |
|---|---|---|
| `functions-substrait-0.107` | **Functions** score: the upstream function cases, run as Substrait plans (`--mode plan`) | yes |
| `tpch` | **Queries** score: TPC-H plans, current encoding | yes |
| `functions-substrait-0.107-sql` | shown, not scored: the same cases as your engine's SQL | no |
| `functions-substrait-0.107-extended`, `-extended-sql` | shown, not scored | no |
| `tpch-substrait-0.81` | shown, not scored: TPC-H plans in the 0.81 encoding | no |

Run them from a checkout of this repository's `main` branch, so the suite files match the
ones the check expects. The Python runners take your engine as a class of your own,
`--engine package.module:ClassName` (on `PYTHONPATH`), so nothing in this repository has to
change:

```bash
export PYTHONPATH=/path/to/your/engine/adapter:$PYTHONPATH
python scripts/run_function_compliance.py --engine my_engine:MyFunctionEngine --mode plan \
    --suites-dir test-suites/functions-substrait-0.107/upstream \
    --suite-name functions-substrait-0.107 --output suite-reports/functions-substrait-0.107.json
python scripts/run_plan_compliance.py --engine my_engine:MyPlanEngine \
    --test-suite test-suites/tpch/metadata.yaml --output suite-reports/tpch.json
```

Name each report after its suite: `suite-reports/<suite>.json`. The Java SDK runner
(`--output-report`) writes TPC-H reports in the accepted format too.

### Your engine class

Model it on `DataFusionEngine` (`scripts/run_function_compliance.py`) and
`DataFusionPlanEngine` (`scripts/run_plan_compliance.py`); subclassing them is fine.

**Query plans** (`run_plan_compliance.py`):

| Member | |
|---|---|
| `name`, `version` | engine name and version, recorded in the report |
| `execute(plan_bytes, tables)` | run a Substrait plan; `tables` maps each input table name to a `pyarrow.Table`. Return `(column_names, column_types, rows)` |

**Function cases** (`run_function_compliance.py`):

| Member | |
|---|---|
| `name`, `version`, `extensions` | recorded in the report |
| `dialect` | a `function_sql.Dialect`: how your SQL spells types and functions (`function_sql.DUCKDB` is the default spelling) |
| `implemented_modes` | the option modes you implement, e.g. `{"overflow": {"ERROR"}}`; other modes are skipped |
| `query(sql)` | run SQL, return the first value of the first row (or `None`) |
| `error_kind(self, exc)` | classify an SQL error: `("parser" \| "extension" \| "missing" \| "bind" \| "runtime", function_name_or_None)`; must not use `self` |
| `enable_plans()`, `create_table(name, select_sql)` | plan mode: load your Substrait consumer; create an input table from a `SELECT` |
| `run_plan(plan_bytes)` | plan mode: run a Substrait plan, return the first column's values as a list |
| `plan_error_kind(self, exc)` | `("rejected" \| "runtime", None)`: the consumer refused the plan, or it failed while running; must not use `self` |
| `_version()`, `_extensions()` | plan mode: return `version` and `extensions` (the engine runs in a child process) |

Function cases use your engine's SQL for two things only: creating the input tables and
evaluating the expected values. In plan mode the function itself always goes through
your Substrait consumer.

### Engines not written in Python

The runners are Python, but your engine need not be: the engine class above is a small
adapter, and it can reach the engine any way that suits it. Use the language SDKs
(`sdk/`) for your own in-house tests; for the leaderboard, use these runners through an
adapter, whatever language the engine is written in.

| Your engine | Adapter |
|---|---|
| Has Python bindings (DataFusion; many Rust engines have them through PyO3) | Call the bindings, as `DataFusionEngine` and `DataFusionPlanEngine` do |
| No Python bindings (Rust, C++, Go, ...) | Write a small command that runs one plan; for TPC-H, [`examples/leaderboard-adapter`](../examples/leaderboard-adapter/) is a ready-made adapter that calls it |
| JVM (Java, Scala) | TPC-H: the Java SDK runner, which Scala engines can call directly. Function cases: an adapter that talks to a JVM process kept running for the whole run |

Two things to plan for:

- **Your engine runs SQL too.** The function runner creates each case's input table and
  evaluates its expected value with your engine's SQL (`create_table`, `query`); only the
  function under test goes through the Substrait consumer.
- **Start the engine once.** The runner keeps one engine object, in one child process, for
  the whole run, and starts a new one only after a crash. Start a JVM, server or
  connection in `__init__` or `enable_plans()`, not per case: the function suite runs
  about 1,300 plans, and a JVM started for each would take about 20 minutes.

## Submitting

### From your CI (recommended)

Call the reusable workflow after your compliance job. It builds the submission, checks it,
and opens the pull request:

```yaml
jobs:
  submit:
    needs: run-compliance          # the job that uploads the reports artifact
    uses: IBM/substrait-compliance/.github/workflows/submit-results.yml@<commit-sha>   # pin it
    with:
      engine: Velox
      engine-version: 1.4.0
      consumer: velox-substrait 1.4.0
      reports-artifact: compliance-suite-reports    # artifact with suite-reports/<suite>.json
    secrets:
      submission-token: ${{ secrets.SUBSTRAIT_COMPLIANCE_TOKEN }}
```

`submission-token` belongs to the account that opens the pull request. A classic personal
access token with the `public_repo` scope works. Such a token can write to every public
repository of its account, so:

- use a **dedicated bot account** that owns nothing else;
- **pin the workflow to a commit SHA** rather than `@main`, because the workflow receives
  the token.

The workflow forks this repository into that account (once), pushes a branch there and
opens the pull request. If the fork has another name, pass it as `fork-repository`. Use
`dry-run: true` to build and check a submission without opening a pull request; the
submission is then uploaded as an artifact. The
[engine template](../.github/workflows/engine-compliance-template.yml) has this job ready to
enable.

### By hand

```bash
pip install -e sdk/python jsonschema
python scripts/prepare_submission.py \
    --engine Velox --version 1.4.0 --consumer "velox-substrait 1.4.0" \
    --run-url https://github.com/acme/velox/actions/runs/123456 \
    --reports-dir suite-reports
git checkout -b submission/velox-1.4.0
git add submissions/velox/1.4.0
git commit -m "submission: Velox 1.4.0"
# push to your fork and open a pull request against main
```

`prepare_submission.py` runs the same checks as the pull request, so a submission it
accepts will pass.

## What a submission contains

```
submissions/<engine>/<version>/
├── manifest.json            # engine, consumer, CI run, suite hashes
└── reports/
    ├── functions-substrait-0.107.json
    ├── tpch.json
    └── ...                  # one per suite, named after it
```

- `manifest.json` follows [`schemas/submission-manifest.v1.json`](../schemas/submission-manifest.v1.json).
  `suiteHash` is the hash of the suite files the run used. The Python runners record it in
  each report from the files they actually ran, and `prepare_submission.py` copies it.
  For a report without one, such as the Java runner's, it is computed from the checkout
  you package from. `python scripts/suite_hash.py` prints the current hashes.
- Each report follows [`schemas/suite-report.v1.json`](../schemas/suite-report.v1.json):
  totals plus **per-case results**, either `testResults` (every case) or `nonPassingResults`
  (every failed or skipped case). This is the format the runners in this repository write.

`<engine>` is the engine name in lower case with other characters replaced by `-` (e.g.
`velox`). `<version>` is the engine version. A new version is a new folder; submitting the
same version again replaces it. The leaderboard shows each engine's latest submission, by
the run date in its manifest. Earlier ones stay in the repository's history.

## What the pull-request check verifies

The check runs the checker, the suites and the schemas from `main`, never from the pull
request:

- the pull request only changes files in one engine's folder, `submissions/<engine>/`;
- the files match the schemas, and only `manifest.json` and `reports/<suite>.json` are present;
- the folder matches the engine name and version, and the reports' `engineName` and
  `engineVersion` match the manifest;
- the engine is not one this repository runs itself (`reservedEngineNames` in the registry);
- the run date is a valid date, not in the future;
- both scored suites are included, and every suite is in the registry;
- each `suiteHash` matches the current suite on `main`, and the hash a runner recorded
  matches the manifest. Results recorded on modified or older test files are therefore
  rejected;
- each report covers exactly the suite's cases: `totalTests` equals the number of cases,
  every per-case id exists in the suite, and the per-case results add up to the totals;
- function suites were run in the required mode (`plan`, or `sql` for the `-sql` suites).

It then scores the submission with the leaderboard's rules and shows, in the check's
summary, where it would rank. Submitted pass rates and per-extension figures are never
used. The leaderboard recomputes the scores from the counts, and the per-extension figures
from the per-case results and the suite itself.

**What the check does not prove.** Results are self-reported. The check catches
inconsistent, incomplete and mistaken submissions; it cannot detect deliberately invented
ones. For example, with `nonPassingResults` a failure that is left out simply counts as a
pass, and the run mode is what the report says. The safeguards against that are review of
the pull request, the linked CI run, and maintainers re-running an engine when results look
wrong.

## Review and publishing

[`CODEOWNERS`](../.github/CODEOWNERS) decides who approves submissions:

- a new engine is approved by a maintainer of this repository;
- once an engine is listed (a `CODEOWNERS` line for `/submissions/<engine>/`), its team
  approves its own updates.

When the pull request is merged, `compliance-leaderboard.yml` republishes the leaderboard
with every engine's latest submission (`scripts/collect_submissions.py`) and the engines
run here.

When a suite changes on `main`, existing submissions stay on the leaderboard, marked † as
run on an earlier revision of that suite, until the team submits a run on the new one. A
suite removed from the registry is dropped from older submissions. If a newly required
suite is missing, that category shows as not run (0%).

## Rules

- Results must come from an automated run of the engine version named, using the suites
  from `main` as they are. Do not edit test files or reports.
- Link the CI run that produced the results; keep its logs available.
- A submission is data only. Nothing in it is executed.
- Maintainers may re-run a submitted engine to confirm its results, and remove submissions
  that cannot be reproduced.
