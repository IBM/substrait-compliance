#!/usr/bin/env python3
"""
Run the Substrait function test suites against a real engine: DuckDB, or
Apache DataFusion (--engine datafusion, current-format suites only).

Two file formats are supported and detected per file:

  * the current upstream Substrait format (``### SUBSTRAIT_INCLUDE: extension:...``),
    used by test-suites/functions-substrait-0.107. These files are parsed with the
    upstream parser (substrait_compliance.function_cases) and translated to SQL by
    scripts/function_sql.py; scalar, aggregate and window cases all run.
  * the older format of test-suites/functions (quoted include paths), handled by the
    regex-based SubstraitExpressionMapper as before.

This runner only reports results it actually measured:

  * Scalar tests: the function call and the expected value are both evaluated by
    DuckDB and compared in Python (with float tolerance and NaN/inf handling).
  * <!ERROR> expectations pass only if DuckDB raises an error.
  * Aggregate tests ``fn((v1, v2, ...)::type, extra_args...)`` are executed as a
    real ``SELECT fn(x, ...) FROM (SELECT UNNEST([...]::type[]) AS x)`` query.

Tests are SKIPPED (reported, but excluded from the pass rate) when:
  * the expected result is <!UNDEFINED> (any behaviour is allowed),
  * the test requires an option mode the engine does not support
    (e.g. overflow:SATURATE on an engine that raises an error on overflow),
  * the test line cannot be translated to SQL (DuckDB raises a parser error),
  * the Substrait function has no SQL mapping in DuckDB ("function does not
    exist"); these are listed under ``unmappedFunctions`` in the report,
  * a test pinned to an option mode (e.g. [on_domain_error:NAN]) fails: engines
    only need to implement one mode, so the failure means "mode not implemented".
  * it is a window-function test (not yet supported by this runner).

Pass rate = passed / (total - skipped) * 100, i.e. passed / (passed + failed) * 100
(docs/COMPLIANCE_LEADERBOARD.md#how-the-pass-rate-is-calculated).

For current-format files, results are compared by the expected Substrait type:
integers and decimals exactly, booleans only with booleans, floats with a relative
tolerance only (1e-9, or 1e-6 for fp32), intervals and nanosecond timestamps by
DuckDB's text form. A pinned option is scored normally when DuckDB implements that
mode (the engine's implemented_modes), skipped up front for its other values, and
otherwise turned from a failure into a skip ("mode not implemented"). <!ERROR>
passes only on a runtime error, not when the engine cannot bind the call. A
current-format file that does not parse makes the run fail. Legacy-format files
keep the original rules above.

Exit status is non-zero if the engine cannot be loaded or no test was executed,
so CI can never silently fall back to made-up numbers.
"""

import argparse
import json
import math
import re
import sys
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "sdk" / "python"))

from substrait_compliance.function_test_parser import FunctionTestParser, TestType  # noqa: E402
from substrait_compliance.expression_mapper import SubstraitExpressionMapper as Mapper  # noqa: E402
from substrait_compliance.function_cases import (  # noqa: E402
    CaseLiteral, ParseError, SubstraitError, is_upstream_format, parse_case_file,
)

sys.path.insert(0, str(Path(__file__).resolve().parent))
import function_sql as fsql  # noqa: E402

PASSED, FAILED, SKIPPED = "passed", "failed", "skipped"

# Option modes DuckDB implements. A test that requests a different value for one of
# these keys is skipped as "unsupported option"; keys not listed here are executed.
DUCKDB_OPTIONS = {
    "overflow": "ERROR",
}

FLOAT_REL_TOL = 1e-9
FLOAT_ABS_TOL = 1e-12
# fp32 expectations are written at single precision; DuckDB often computes in double
FP32_REL_TOL = 1e-6


class Untranslatable(Exception):
    """The test line could not be turned into executable SQL."""


def _is_parser_error(exc: Exception) -> bool:
    return type(exc).__name__ == "ParserException"


MISSING_FN = re.compile(r"Function with name \"?(\w+)\"? does not exist")
NEEDS_EXT = re.compile(r"exists in the (\w+) extension")


def _missing_function(exc: Exception):
    """Name of a Substrait function with no SQL mapping, or None."""
    if type(exc).__name__ != "CatalogException":
        return None
    m = MISSING_FN.search(str(exc))
    return m.group(1) if m else None


def _needs_extension(exc: Exception):
    m = NEEDS_EXT.search(str(exc)) if type(exc).__name__ == "CatalogException" else None
    return m.group(1) if m else None


SPECIAL_FLOATS = {"inf": "'inf'", "+inf": "'inf'", "-inf": "'-inf'", "nan": "'nan'",
                  "infinity": "'inf'", "-infinity": "'-inf'"}

# Aggregates whose Substrait name maps to a different SQL form
AGG_TEMPLATES = {
    "count_distinct": "count(DISTINCT {args})",
}


def values_equal(actual, expected, rel_tol=FLOAT_REL_TOL) -> bool:
    if actual is None or expected is None:
        return actual is None and expected is None
    if isinstance(actual, (list, tuple)) and isinstance(expected, (list, tuple)):
        return len(actual) == len(expected) and all(values_equal(a, e, rel_tol) for a, e in zip(actual, expected))
    if isinstance(actual, dict) and isinstance(expected, dict):
        return actual.keys() == expected.keys() and all(values_equal(actual[k], expected[k], rel_tol) for k in actual)
    if isinstance(actual, Decimal) and isinstance(expected, Decimal):
        return actual == expected
    numeric = (int, float, Decimal)
    if isinstance(actual, numeric) and isinstance(expected, numeric) and not isinstance(actual, bool) and not isinstance(expected, bool):
        a, e = float(actual), float(expected)
        if math.isnan(a) or math.isnan(e):
            return math.isnan(a) and math.isnan(e)
        if math.isinf(a) or math.isinf(e):
            return a == e
        return math.isclose(a, e, rel_tol=rel_tol, abs_tol=FLOAT_ABS_TOL)
    return actual == expected


AGG_LIST_ARG = re.compile(r"^\((.*)\)\s*::\s*(.+)$", re.DOTALL)


def translate_aggregate(function_call: str) -> str:
    """fn((v1, v2)::T, extra...) -> SELECT fn(x, extra...) FROM (SELECT UNNEST([v1, v2]::T[]) AS x)"""
    m = re.match(r"^([a-zA-Z_]\w*)\s*\((.*)\)$", function_call.strip(), re.DOTALL)
    if not m:
        raise Untranslatable("not a function call")
    fn_name, args_str = m.group(1).lower(), m.group(2)
    args = Mapper.parse_args(args_str)
    if not args:
        raise Untranslatable("aggregate without arguments")
    list_m = AGG_LIST_ARG.match(args[0].strip())
    if not list_m:
        raise Untranslatable("first aggregate argument is not a typed value list")
    raw_values, raw_type = list_m.group(1), list_m.group(2).strip()
    sql_type = Mapper.clean_types("x::" + raw_type)[len("x::"):]
    if not re.fullmatch(r"[A-Za-z]+(\(\d+,\s*\d+\))?", sql_type):
        raise Untranslatable(f"unsupported aggregate element type {raw_type!r}")
    values = [Mapper.clean_types(v) for v in Mapper.parse_args(raw_values)]
    values = [SPECIAL_FLOATS.get(v.strip().lower(), v) for v in values]
    extra = [Mapper.map_expression(a) for a in args[1:]]
    call_args = ", ".join(["x"] + extra)
    if fn_name in AGG_TEMPLATES:
        call = AGG_TEMPLATES[fn_name].format(args=call_args)
    else:
        call = f"{Mapper.FN_RENAMES.get(fn_name, fn_name)}({call_args})"
    return f"SELECT {call} FROM (SELECT UNNEST([{', '.join(values)}]::{sql_type}[]) AS x)"


class DuckDBEngine:
    name = "DuckDB"
    dialect = fsql.DUCKDB
    # Option modes DuckDB is known to implement for every function that declares the
    # option (see run_upstream_case).
    implemented_modes = {
        "overflow": {"ERROR"},
        "division_type": {"TRUNCATE"},
    }

    def __init__(self):
        try:
            import duckdb
        except ImportError as exc:  # never fall back to simulated numbers
            raise SystemExit(f"❌ duckdb Python package is not installed: {exc}")
        self.version = duckdb.__version__
        self.con = duckdb.connect()
        self.con.execute("SET TimeZone = 'UTC'")
        # a test such as repeat('a', 2147483647) must fail, not exhaust the machine
        self.con.execute("SET memory_limit = '1GB'")
        self.extensions = {}
        for ext in ("spatial",):
            try:
                self.con.execute(f"INSTALL {ext}; LOAD {ext};")
                self.extensions[ext] = "loaded"
            except Exception as exc:
                self.extensions[ext] = f"unavailable: {str(exc).splitlines()[0][:120]}"
                print(f"⚠️  DuckDB {ext} extension unavailable; tests needing it will be skipped")

    def scalar(self, sql: str):
        return self.con.execute(f"SELECT {sql}").fetchone()[0]

    def query(self, sql: str):
        return self.con.execute(sql).fetchone()[0]

    # -- plan mode -----------------------------------------------------------
    def enable_plans(self):
        """Load the substrait extension (community repository, as the Java runner does)."""
        errors = []
        for statement in ("INSTALL substrait FROM community", "INSTALL substrait"):
            try:
                self.con.execute(statement)
                self.con.execute("LOAD substrait")
                self.extensions["substrait"] = "loaded"
                return
            except Exception as exc:  # noqa: BLE001 - try the next source
                errors.append(str(exc).splitlines()[0][:160])
        raise SystemExit("❌ DuckDB substrait extension unavailable: " + " | ".join(errors))

    def create_table(self, name: str, select_sql: str):
        self.con.execute(f"CREATE OR REPLACE TABLE {name} AS {select_sql}")

    def _version(self):
        return self.version

    def _extensions(self):
        return dict(self.extensions)

    def run_plan(self, plan_bytes: bytes):
        """All rows of the plan's (single) output column."""
        return [row[0] for row in self.con.execute("SELECT * FROM from_substrait(?)", [plan_bytes]).fetchall()]

    def error_kind(self, exc: Exception):
        """('parser'|'extension'|'missing'|'bind'|'runtime', detail)"""
        if _is_parser_error(exc):
            return "parser", None
        ext = _needs_extension(exc)
        if ext:
            return "extension", ext
        missing = _missing_function(exc)
        if missing:
            return "missing", missing
        if type(exc).__name__ == "BinderException":
            return "bind", None
        return "runtime", None

    def plan_error_kind(self, exc: Exception):
        """'rejected' when the substrait extension could not translate the plan,
        'runtime' when the translated plan raised while running."""
        name, msg = type(exc).__name__, str(exc)
        if name in ("NotImplementedException", "BinderException", "ParserException", "CatalogException") \
                or "not supported" in msg.lower() or "unsupported" in msg.lower() \
                or "could not find" in msg.lower():
            return "rejected", None
        return "runtime", None


class DataFusionEngine:
    """Apache DataFusion through its Python bindings (SQL)."""
    name = "DataFusion"
    dialect = fsql.DATAFUSION
    # DataFusion's integer arithmetic is not consistent about overflow (+ - * wrap
    # silently, abs/negate/divide raise), so no overflow mode is claimed; integer
    # division truncates.
    implemented_modes = {
        "division_type": {"TRUNCATE"},
    }
    extensions = {}

    def __init__(self):
        try:
            import datafusion
        except ImportError as exc:  # never fall back to simulated numbers
            raise SystemExit(f"❌ datafusion Python package is not installed: {exc}")
        self.version = datafusion.__version__
        self.ctx = datafusion.SessionContext()

    def query(self, sql: str):
        table = self.ctx.sql(sql).to_arrow_table()
        if table.num_rows == 0:
            return None
        return table.column(0)[0].as_py()

    def scalar(self, sql: str):
        return self.query(f"SELECT {sql}")

    # -- plan mode -----------------------------------------------------------
    def enable_plans(self):
        from datafusion import substrait  # noqa: F401 - fail early if missing
        self.extensions = {"substrait": "built in"}

    def create_table(self, name: str, select_sql: str):
        self.ctx.sql(f"CREATE OR REPLACE TABLE {name} AS {select_sql}").collect()

    def _version(self):
        return self.version

    def _extensions(self):
        return dict(self.extensions)

    def run_plan(self, plan_bytes: bytes):
        from datafusion import substrait as ss
        plan = ss.Serde.deserialize_bytes(plan_bytes)
        logical = ss.Consumer.from_substrait_plan(self.ctx, plan)
        table = self.ctx.create_dataframe_from_logical_plan(logical).to_arrow_table()
        return table.column(0).to_pylist()

    def error_kind(self, exc: Exception):
        msg = str(exc)
        if "ParserError" in msg or msg.startswith("SQL error"):
            return "parser", None
        m = re.search(r"Invalid function '(\w+)'", msg)
        if m:
            return "missing", m.group(1)
        if "Optimizer rule" in msg:  # constant folding hit a runtime error
            return "runtime", None
        if "Error during planning" in msg or "not implemented" in msg or "Schema error" in msg:
            return "bind", None
        return "runtime", None

    def plan_error_kind(self, exc: Exception):
        msg = str(exc)
        if "Optimizer rule" in msg or "Arrow error" in msg or "Execution error" in msg:
            return "runtime", None
        return "rejected", None


ENGINES = {"duckdb": DuckDBEngine, "datafusion": DataFusionEngine}


def suite_files_hash(suites_dir: Path):
    """Hash of the .test files run (scripts/submissions.py), recorded for submissions."""
    import submissions
    return submissions.hash_suite_path(suites_dir, "cases")


def resolve_engine(spec: str):
    """An engine class: a built-in name (duckdb, datafusion) or "package.module:ClassName" for an
    engine defined outside this repository (importable on PYTHONPATH)."""
    if spec in ENGINES:
        return ENGINES[spec]
    module, sep, cls_name = spec.partition(":")
    if not sep or not module or not cls_name:
        raise SystemExit(f"❌ unknown engine {spec!r}: use one of {', '.join(sorted(ENGINES))} "
                         "or package.module:ClassName")
    import importlib
    try:
        cls = getattr(importlib.import_module(module), cls_name)
    except (ImportError, AttributeError) as exc:
        raise SystemExit(f"❌ cannot load engine {spec!r}: {exc}")
    return cls


# ----------------------------------------------------------- crash isolation

class EngineCrashed(Exception):
    """The engine process died (e.g. a segmentation fault in a Substrait consumer)."""


def _remote_error(name: str, message: str) -> Exception:
    """An exception carrying the worker's exception class name and message, so the
    engine's error classification still sees the original type name."""
    return type(name, (Exception,), {})(message)


def _engine_worker(engine_name, conn):
    engine = resolve_engine(engine_name)()
    while True:
        try:
            method, args = conn.recv()
        except EOFError:
            return
        try:
            conn.send(("ok", getattr(engine, method)(*args)))
        except (Exception, SystemExit) as exc:  # noqa: BLE001 - shipped back to the parent
            conn.send(("error", (type(exc).__name__, str(exc))))


class IsolatedEngine:
    """Runs an engine in a child process, so a crash inside the engine (a consumer
    segfaulting on a plan) fails that one case instead of ending the run. After a
    crash a fresh process is started and the setup calls are replayed."""

    def __init__(self, engine_name: str):
        import multiprocessing as mp
        self._mp = mp.get_context("spawn")
        self._name = engine_name
        cls = resolve_engine(engine_name)
        self.name, self.dialect, self.implemented_modes = cls.name, cls.dialect, cls.implemented_modes
        self._setup = []        # calls replayed after a restart
        self.crashes = 0
        self._start()
        self.version = self._call("_version")
        self.extensions = {}

    def _start(self):
        self._conn, child = self._mp.Pipe()
        self._proc = self._mp.Process(target=_engine_worker, args=(self._name, child), daemon=True)
        self._proc.start()
        child.close()

    def _call(self, method, *args):
        try:
            self._conn.send((method, args))
            status, value = self._conn.recv()
        except (EOFError, BrokenPipeError, ConnectionResetError):
            self._proc.join(timeout=5)
            code = self._proc.exitcode
            self.crashes += 1
            self._start()
            for m, a in self._setup:
                self._call(m, *a)
            raise EngineCrashed(f"{self.name} crashed (exit code {code}) while running {method}")
        if status == "error":
            raise _remote_error(*value)
        return value

    def setup(self, method, *args):
        self._setup.append((method, args))
        return self._call(method, *args)

    def enable_plans(self):
        self.setup("enable_plans")
        self.extensions = self._call("_extensions")

    def create_table(self, name, select_sql):
        return self._call("create_table", name, select_sql)

    def run_plan(self, plan_bytes):
        return self._call("run_plan", plan_bytes)

    def query(self, sql):
        return self._call("query", sql)

    def plan_error_kind(self, exc):
        if isinstance(exc, EngineCrashed):
            return "crashed", None
        return resolve_engine(self._name).plan_error_kind(None, exc)

    def error_kind(self, exc):
        return resolve_engine(self._name).error_kind(None, exc)

    def close(self):
        try:
            self._conn.close()
        finally:
            self._proc.join(timeout=5)


def _classify_engine_error(engine, exc: Exception, sql: str):
    """(status, reason) for an exception that means the case could not be run at
    all (skipped), or None when it is the engine's real answer to the case."""
    kind, detail = engine.error_kind(exc)
    if kind == "parser":
        return SKIPPED, f"untranslatable: {sql}"
    if kind == "extension":
        return SKIPPED, f"requires {engine.name} {detail} extension (not available)"
    if kind == "missing":
        return SKIPPED, f"no SQL mapping for function: {detail}"
    return None


INTERVAL_BASES = {"iday", "iyear", "icompound"}


def compare_as_text(type_str: str) -> bool:
    base, params = fsql.split_type(type_str)
    if base == "list":
        return compare_as_text(params)
    if base in INTERVAL_BASES:
        return True
    return base in ("pts", "ptstz", "pt") and params.strip().isdigit() and int(params) > 6


def file_kind(path: Path) -> str:
    """'scalar', 'aggregate' or 'window' from the ### SUBSTRAIT_<KIND>_TEST header."""
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"###\s*SUBSTRAIT_(\w+?)_TEST", line)
            if m:
                return m.group(1).lower()
    return "scalar"


def frame_types_for(source_lines, line_no):
    """Column types of the window frame (DEFINE line) that precedes a case."""
    for i in range(line_no - 1, -1, -1):
        if source_lines[i].lstrip().startswith("DEFINE"):
            return fsql.frame_column_types(source_lines[i])
    return {}


def _values_sql(engine, rows, types, with_row_number=False) -> str:
    """SELECT for a data table: typed VALUES rows (or an empty typed SELECT)."""
    names = (["rn"] if with_row_number else []) + [f"col{i}" for i in range(len(types))]
    if not rows:
        cols = (["CAST(NULL AS BIGINT) AS rn"] if with_row_number else []) + \
               [f"CAST(NULL AS {fsql.sql_type(t, engine.dialect)}) AS col{i}" for i, t in enumerate(types)]
        return f"SELECT {', '.join(cols)} WHERE FALSE"
    tuples = []
    for r, row in enumerate(rows):
        if len(row) != len(types):
            raise fsql.Untranslatable("row width does not match the declared columns")
        vals = ([f"CAST({r + 1} AS BIGINT)"] if with_row_number else []) + \
               [fsql.literal_sql(v, t, engine.dialect) for v, t in zip(row, types)]
        tuples.append("(" + ", ".join(vals) + ")")
    return f"SELECT * FROM (VALUES {', '.join(tuples)}) AS t({', '.join(names)})"


def _column_types(args, frame_types=None):
    columns = dict(frame_types or {})
    for a in args:
        if getattr(a, "column_name", "") and re.fullmatch(r"col\d+", a.column_name) and a.column_type:
            columns[int(a.column_name[3:])] = a.column_type
    return [columns[i] for i in range(max(columns) + 1)] if columns else []


def _run_plan_case(engine, kind, tc, source_lines, test_file, registry):
    """Like _run_upstream_case, but the function runs from a Substrait plan through
    the engine's Substrait consumer. Only the expected literal and the input tables
    use SQL."""
    import function_plans as fp

    result = tc.result
    if isinstance(result, SubstraitError) and result.error == "undefined":
        return SKIPPED, "expected result is undefined"
    builder = fp.PlanBuilder(registry, test_file, kind)
    tr = fsql.Translator(tc.options, engine.dialect)
    try:
        if kind == "window":
            types = _column_types(tc.args, frame_types_for(source_lines, tc.line))
            rows = [r for r in (tc.rows or []) if r]
            engine.create_table(fp.DATA_TABLE, _values_sql(engine, rows, types, with_row_number=True))
            plan = builder.window_plan(tc, [("rn", "i64")] + [(f"col{i}", t) for i, t in enumerate(types)])
        elif tc.rows is not None or kind == "aggregate":
            if tc.rows is None:
                values = tc.args[0]
                if not isinstance(values, CaseLiteral) or not isinstance(values.value, list):
                    raise fsql.Untranslatable("aggregate input is not a value list")
                types, rows = [values.type], [[v] for v in values.value]
            else:
                types, rows = _column_types(tc.args), [r for r in tc.rows if r]
            engine.create_table(fp.DATA_TABLE, _values_sql(engine, rows, types))
            plan = builder.aggregate_plan(tc, [(f"col{i}", t) for i, t in enumerate(types)])
        else:
            plan = builder.scalar_plan(tc)
        expected_sql = None if isinstance(result, SubstraitError) else tr.expected(result)
    except (fp.Unbuildable, fsql.Untranslatable) as exc:
        return SKIPPED, f"plan not built: {exc}"

    try:
        rows_out = engine.run_plan(plan)
    except Exception as exc:  # noqa: BLE001 - the consumer's answer is the result
        msg = f"{type(exc).__name__}: {str(exc).splitlines()[0][:200]}"
        if engine.plan_error_kind(exc)[0] == "crashed":
            return FAILED, f"engine crashed on this plan: {msg}"
        if expected_sql is None:
            kind_, _ = engine.plan_error_kind(exc)
            if kind_ == "runtime":
                return PASSED, None
            return FAILED, f"expected a runtime error, but the consumer rejected the plan: {msg}"
        return FAILED, msg
    if expected_sql is None:
        return FAILED, f"expected an error, got {rows_out!r}"

    actual = rows_out if kind == "window" else (rows_out[0] if len(rows_out) == 1 else rows_out)
    if kind != "window" and len(rows_out) != 1:
        return FAILED, f"expected one result row, got {len(rows_out)}"
    try:
        expected_value = engine.query(expected_sql)
        if isinstance(result, CaseLiteral) and compare_as_text(result.type):
            # compare in the engine's text form, as the SQL mode does
            if kind == "window":
                raise fsql.Untranslatable("text comparison of window results")
            expected_value = engine.query(f"SELECT CAST(({expected_sql}) AS VARCHAR)")
            actual = engine.query(f"SELECT CAST({_python_literal_sql(engine, actual, result.type)} AS VARCHAR)")
    except Exception as exc:  # noqa: BLE001
        return SKIPPED, f"untranslatable expected value {expected_sql}: {type(exc).__name__}"
    if upstream_values_match(actual, expected_value, result.type):
        return PASSED, None
    return FAILED, f"got {actual!r}, expected {expected_value!r}"


def _python_literal_sql(engine, value, type_str):
    """A Python value returned by the engine, back as an SQL literal of type_str (for
    text comparisons of intervals and nanosecond timestamps)."""
    if value is None:
        return f"CAST(NULL AS {fsql.sql_type(type_str, engine.dialect)})"
    if isinstance(value, timedelta):
        micros = value // timedelta(microseconds=1)
        return f"CAST('{micros} microseconds' AS INTERVAL)"
    return f"CAST('{value}' AS {fsql.sql_type(type_str, engine.dialect)})"


def run_plan_case(engine, kind, tc, source_lines, test_file, registry):
    """Plan-mode counterpart of run_upstream_case (same option rules)."""
    for key, value in tc.options.items():
        modes = engine.implemented_modes.get(key)
        if modes is not None and str(value).upper() not in modes:
            return SKIPPED, f"unsupported option {key}:{value} (engine implements {key}:{'/'.join(sorted(modes))})"
    status, reason = _run_plan_case(engine, kind, tc, source_lines, test_file, registry)
    unknown = {k: v for k, v in tc.options.items() if k not in engine.implemented_modes}
    if status == FAILED and unknown:
        modes = ", ".join(f"{k}:{v}" for k, v in unknown.items())
        return SKIPPED, f"option mode not implemented ({modes}): {reason}"
    return status, reason


def run_upstream_case(engine, kind, tc, source_lines):
    """Run one case parsed from the upstream format (see the module docstring).

    Options: a case pinned to a mode in engine.implemented_modes is scored normally;
    a case pinned to another value of such an option is skipped up front. For any
    other option the runner cannot tell which mode the engine implements, so a
    failure is reported as "mode not implemented" (skipped) rather than as a failure."""
    for key, value in tc.options.items():
        modes = engine.implemented_modes.get(key)
        if modes is not None and str(value).upper() not in modes:
            return SKIPPED, f"unsupported option {key}:{value} (engine implements {key}:{'/'.join(sorted(modes))})"
    status, reason = _run_upstream_case(engine, kind, tc, source_lines)
    unknown = {k: v for k, v in tc.options.items() if k not in engine.implemented_modes}
    if status == FAILED and unknown:
        modes = ", ".join(f"{k}:{v}" for k, v in unknown.items())
        return SKIPPED, f"option mode not implemented ({modes}): {reason}"
    return status, reason


def _is_bool(v):
    return isinstance(v, bool)


def _exact_number(v):
    """Decimal for an int/Decimal/float value (floats via their shortest repr)."""
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, Decimal)):
        return Decimal(v)
    if isinstance(v, float) and math.isfinite(v):
        return Decimal(repr(v))
    return None


EXACT_BASES = {"i8", "i16", "i32", "i64", "u!u8", "u!u16", "u!u32", "u!u64", "dec", "decimal"}
FLOAT_BASES = {"fp32", "fp64"}


def upstream_values_match(actual, expected, type_str) -> bool:
    """Compare a result with the expected value, by the expected Substrait type:

    * integers and decimals exactly (by value; DuckDB may widen i64 to HUGEINT),
    * booleans only with booleans,
    * floats with a relative tolerance only (1e-9; 1e-6 for fp32) - no absolute
      slack, so 0.0 must be exactly 0 and 5e-324 is not 0,
    * lists element by element with the element type,
    * everything else (strings, temporal values) with ==, by value: a timestamp at
      midnight equals the date it represents, either way round (result types are
      not scored).
    """
    if actual is None or expected is None:
        return actual is None and expected is None
    base = fsql.base_type(type_str) if type_str else ""
    if isinstance(expected, (list, tuple)):
        elem = fsql.list_element_type(type_str) if base == "list" else type_str
        return isinstance(actual, (list, tuple)) and len(actual) == len(expected) and \
            all(upstream_values_match(a, e, elem) for a, e in zip(actual, expected))
    if _is_bool(expected) or _is_bool(actual):
        return _is_bool(expected) and _is_bool(actual) and actual == expected
    if base in EXACT_BASES:
        a, e = _exact_number(actual), _exact_number(expected)
        return a is not None and e is not None and a == e
    if base in FLOAT_BASES or isinstance(expected, float):
        if not isinstance(actual, (int, float, Decimal)):
            return False
        a, e = float(actual), float(expected)
        if math.isnan(a) or math.isnan(e):
            return math.isnan(a) and math.isnan(e)
        if math.isinf(a) or math.isinf(e):
            return a == e
        rel = FP32_REL_TOL if base == "fp32" else FLOAT_REL_TOL
        return math.isclose(a, e, rel_tol=rel, abs_tol=0.0)
    if type(expected).__name__ == "date" and isinstance(actual, datetime):
        return actual.date() == expected and actual.time() == datetime.min.time() and actual.tzinfo is None
    if type(actual).__name__ == "date" and isinstance(expected, datetime):
        return expected.date() == actual and expected.time() == datetime.min.time() and expected.tzinfo is None
    return actual == expected


def _run_upstream_case(engine, kind, tc, source_lines):
    result = tc.result
    if isinstance(result, SubstraitError) and result.error == "undefined":
        return SKIPPED, "expected result is undefined"

    tr = fsql.Translator(tc.options, engine.dialect)
    try:
        if kind == "window":
            actual_sql = tr.window_case(tc, frame_types_for(source_lines, tc.line))
        elif tc.rows is not None or kind == "aggregate":
            actual_sql = tr.aggregate_case(tc)
        else:
            actual_sql = tr.scalar_case(tc)
        expected_sql = None if isinstance(result, SubstraitError) else tr.expected(result)
    except fsql.Untranslatable as exc:
        return SKIPPED, f"untranslatable: {exc}"

    if isinstance(result, CaseLiteral) and compare_as_text(result.type):
        # DuckDB returns intervals as timedeltas that fold months into 30 days, and
        # nanosecond timestamps as microsecond datetimes; compare DuckDB's canonical
        # text form instead.
        actual_sql = f"SELECT CAST(({actual_sql}) AS VARCHAR)"
        expected_sql = f"SELECT CAST(({expected_sql}) AS VARCHAR)"

    if expected_sql is None:  # <!ERROR>
        try:
            value = engine.query(actual_sql)
        except Exception as exc:
            classified = _classify_engine_error(engine, exc, actual_sql)
            if classified:
                return classified
            if engine.error_kind(exc)[0] == "bind":
                # the engine could not even bind the call (e.g. no overload for these
                # types): that is not the runtime error the case expects
                return FAILED, f"expected a runtime error, got {type(exc).__name__}: " \
                               f"{str(exc).splitlines()[0][:160]} [{actual_sql}]"
            return PASSED, None
        return FAILED, f"expected an error, got {value!r} [{actual_sql}]"

    try:
        expected_value = engine.query(expected_sql)
    except Exception as exc:
        return SKIPPED, f"untranslatable expected value {expected_sql}: {type(exc).__name__}"
    try:
        actual_value = engine.query(actual_sql)
    except Exception as exc:
        classified = _classify_engine_error(engine, exc, actual_sql)
        if classified:
            return classified
        msg = str(exc).splitlines()[0][:200]
        return FAILED, f"{type(exc).__name__}: {msg} [{actual_sql}]"

    if upstream_values_match(actual_value, expected_value, result.type):
        return PASSED, None
    return FAILED, f"got {actual_value!r}, expected {expected_value!r} [{actual_sql}]"


def run_case(engine, suite_type, tc):
    """Return (status, reason). See the module docstring for the classification rules."""
    status, reason = _run_case(engine, suite_type, tc)
    if status == FAILED and tc.options:
        modes = ", ".join(f"{k}:{v}" for k, v in tc.options.items())
        return SKIPPED, f"option mode not implemented ({modes}): {reason}"
    return status, reason


def _run_case(engine, suite_type, tc):
    expected = tc.expected_result.strip()

    for key, value in tc.options.items():
        supported = DUCKDB_OPTIONS.get(key)
        if supported is not None and value.upper() != supported:
            return SKIPPED, f"unsupported option {key}:{value} (engine supports {key}:{supported})"

    if expected == "<!UNDEFINED>":
        return SKIPPED, "expected result is undefined"

    if suite_type == TestType.WINDOW:
        return SKIPPED, "window-function tests are not yet supported by this runner"

    try:
        if suite_type == TestType.AGGREGATE:
            actual_sql = translate_aggregate(tc.function_call)
            run_actual = lambda: engine.query(actual_sql)  # noqa: E731
        else:
            actual_sql = Mapper.map_expression(tc.function_call)
            run_actual = lambda: engine.scalar(actual_sql)  # noqa: E731
    except Untranslatable as exc:
        return SKIPPED, f"untranslatable: {exc}"

    if expected == "<!ERROR>":
        try:
            value = run_actual()
        except Exception as exc:
            if _is_parser_error(exc):
                return SKIPPED, f"untranslatable: {actual_sql}"
            ext = _needs_extension(exc)
            if ext:
                return SKIPPED, f"requires DuckDB {ext} extension (not available)"
            missing = _missing_function(exc)
            if missing:
                return SKIPPED, f"no SQL mapping for function: {missing}"
            return PASSED, None
        return FAILED, f"expected an error, got {value!r} from {actual_sql}"

    expected_sql = Mapper.clean_expected(expected)
    try:
        expected_value = engine.scalar(expected_sql)
    except Exception as exc:
        return SKIPPED, f"untranslatable expected value {expected_sql}: {type(exc).__name__}"

    try:
        actual_value = run_actual()
    except Exception as exc:
        if _is_parser_error(exc):
            return SKIPPED, f"untranslatable: {actual_sql}"
        ext = _needs_extension(exc)
        if ext:
            return SKIPPED, f"requires DuckDB {ext} extension (not available)"
        missing = _missing_function(exc)
        if missing:
            return SKIPPED, f"no SQL mapping for function: {missing}"
        msg = str(exc).splitlines()[0][:200]
        return FAILED, f"{type(exc).__name__}: {msg} [{actual_sql}]"

    tol = FP32_REL_TOL if "::fp32" in expected else FLOAT_REL_TOL
    if values_equal(actual_value, expected_value, tol):
        return PASSED, None
    return FAILED, f"got {actual_value!r}, expected {expected_value!r} [{actual_sql}]"


def display_path(path: Path) -> str:
    """Path relative to the repo when possible (works for relative --suites-dir too)."""
    resolved = path.resolve()
    try:
        return str(resolved.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def pass_rate(passed, failed):
    """Percentage of executed tests that passed; None when nothing was executed."""
    executed = passed + failed
    return round(passed * 100.0 / executed, 1) if executed else None


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--suites-dir", type=Path, default=REPO_ROOT / "test-suites" / "functions")
    ap.add_argument("--output", type=Path, required=True, help="JSON report to write")
    ap.add_argument("--suite-name", default="functions", help="suite name recorded in the report")
    ap.add_argument("--engine", default="duckdb",
                    help="engine to run on: duckdb, datafusion, or package.module:ClassName for your "
                         "own engine class (see submissions/README.md); legacy-format files: duckdb only")
    ap.add_argument("--mode", choices=("sql", "plan"), default="sql",
                    help="sql: translate each case to the engine's SQL (default); plan: build a "
                         "Substrait plan per case and run it through the engine's Substrait consumer "
                         "(current-format suites only)")
    args = ap.parse_args(argv)

    resolve_engine(args.engine)  # fail early on an unknown engine
    registry = None
    if args.mode == "plan":
        # plans run in a child process: a consumer crash fails one case, not the run
        engine = IsolatedEngine(args.engine)
        engine.enable_plans()
        engine.setup("create_table", "one_row", "SELECT CAST(1 AS INTEGER) AS dummy")  # scalar plans' input
        import validate_function_tests as vft
        m = re.search(r"substrait-(\d+\.\d+)", str(args.suites_dir.resolve()))
        registry, _ = vft.load_registry(m.group(1) if m else None)
    else:
        engine = resolve_engine(args.engine)()
    parser = FunctionTestParser()
    files = sorted(args.suites_dir.rglob("*.test"))
    if not files:
        raise SystemExit(f"❌ No .test files found under {args.suites_dir}")

    categories, results = {}, []
    parse_errors, fatal_parse_errors = [], []
    for path in files:
        if args.mode == "plan":
            print(f"   {display_path(path)}", flush=True)  # shows where a slow or crashing run is
        category = str(path.parent.relative_to(args.suites_dir))
        cat = categories.setdefault(category, {"total": 0, PASSED: 0, FAILED: 0, SKIPPED: 0})
        if is_upstream_format(path):
            try:
                test_file = parse_case_file(path)
            except Exception as exc:  # noqa: BLE001 - any parse failure
                parse_errors.append(f"{display_path(path)}: {exc}")
                fatal_parse_errors.append(display_path(path))
                continue
            source_lines = path.read_text(encoding="utf-8").splitlines()
            kind = file_kind(path)
            if args.mode == "plan":
                cases = [(tc, run_plan_case(engine, kind, tc, source_lines, test_file, registry),
                          tc.line, source_lines[tc.line - 1].strip()) for tc in test_file.testcases]
            else:
                cases = [(tc, run_upstream_case(engine, kind, tc, source_lines),
                          tc.line, source_lines[tc.line - 1].strip()) for tc in test_file.testcases]
        else:
            if args.engine != "duckdb" or args.mode == "plan":
                raise SystemExit(f"❌ {display_path(path)} is in the legacy format, which only the "
                                 f"duckdb engine supports; use a current-format suite")
            try:
                suite = parser.parse_file(path)
            except Exception as exc:
                parse_errors.append(f"{display_path(path)}: {exc}")
                continue
            cases = [(tc, run_case(engine, suite.test_type, tc), tc.line_number,
                      f"{tc.function_call} = {tc.expected_result}") for tc in suite.test_cases]
        for tc, (status, reason), line, text in cases:
            cat["total"] += 1
            cat[status] += 1
            if status != PASSED:
                call, _, expected = text.rpartition(" = ")
                results.append({
                    "file": display_path(path),
                    "line": line,
                    "test": call or text,
                    "expected": expected,
                    "status": status,
                    "reason": reason,
                })
    for err in parse_errors:
        print(f"⚠️  Could not parse {err}")

    for cat in categories.values():
        cat["passRate"] = pass_rate(cat[PASSED], cat[FAILED])

    totals = {k: sum(c[k] for c in categories.values()) for k in ("total", PASSED, FAILED, SKIPPED)}
    if totals[PASSED] + totals[FAILED] == 0:
        raise SystemExit("❌ No function test was executed; refusing to write an empty report")

    unmapped = sorted({r["reason"].split(": ", 1)[1] for r in results
                       if r["status"] == SKIPPED and r["reason"].startswith("no SQL mapping for function")})
    report = {
        "engineName": engine.name,
        "engineVersion": engine.version,
        "suite": args.suite_name,
        "mode": args.mode,
        "suiteHash": suite_files_hash(args.suites_dir),
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "totalTests": totals["total"],
        "passed": totals[PASSED],
        "failed": totals[FAILED],
        "skipped": totals[SKIPPED],
        "passRate": pass_rate(totals[PASSED], totals[FAILED]),
        "categories": categories,
        "unmappedFunctions": unmapped,
        "extensions": engine.extensions,
        "parseErrors": parse_errors,
        **({"engineCrashes": engine.crashes} if args.mode == "plan" else {}),
        "nonPassingResults": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2))

    width = max([12] + [len(n) for n in categories])
    print(f"{'Category':<{width}} {'Total':>6} {'Pass':>6} {'Fail':>6} {'Skip':>6} {'Rate':>7}")
    for name, c in sorted(categories.items()):
        rate = "n/a" if c["passRate"] is None else f"{c['passRate']:.1f}%"
        print(f"{name:<{width}} {c['total']:>6} {c[PASSED]:>6} {c[FAILED]:>6} {c[SKIPPED]:>6} {rate:>7}")
    print(f"{'TOTAL':<{width}} {totals['total']:>6} {totals[PASSED]:>6} {totals[FAILED]:>6} {totals[SKIPPED]:>6} {report['passRate']:>6.1f}%")
    print(f"{engine.name} {engine.version} — report written to {args.output}")
    if args.mode == "plan":
        if engine.crashes:
            print(f"⚠️  the engine crashed {engine.crashes} time(s); those cases are reported as failed")
        engine.close()
    if fatal_parse_errors:
        # a current-format file that does not parse would silently drop out of the totals
        print(f"❌ {len(fatal_parse_errors)} file(s) failed to parse: {', '.join(fatal_parse_errors)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
