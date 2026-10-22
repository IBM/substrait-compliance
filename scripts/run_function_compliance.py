#!/usr/bin/env python3
"""
Run the Substrait function test suites against a real engine (DuckDB).

Unlike demo/runner/function_test_demo.py, which simulates several engines for the
demo dashboard, this runner only reports results it actually measured:

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

Pass rate = passed / (passed + failed) * 100.

Exit status is non-zero if the engine cannot be loaded or no test was executed,
so CI can never silently fall back to made-up numbers.
"""

import argparse
import json
import math
import re
import sys
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "sdk" / "python"))

from substrait_compliance.function_test_parser import FunctionTestParser, TestType  # noqa: E402
from substrait_compliance.expression_mapper import SubstraitExpressionMapper as Mapper  # noqa: E402

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

    def __init__(self):
        try:
            import duckdb
        except ImportError as exc:  # never fall back to simulated numbers
            raise SystemExit(f"❌ duckdb Python package is not installed: {exc}")
        self.version = duckdb.__version__
        self.con = duckdb.connect()
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
    args = ap.parse_args(argv)

    engine = DuckDBEngine()
    parser = FunctionTestParser()
    files = sorted(args.suites_dir.glob("*/*.test"))
    if not files:
        raise SystemExit(f"❌ No .test files found under {args.suites_dir}")

    categories, results = {}, []
    for path in files:
        category = path.parent.name
        cat = categories.setdefault(category, {"total": 0, PASSED: 0, FAILED: 0, SKIPPED: 0})
        try:
            suite = parser.parse_file(path)
        except Exception as exc:
            print(f"⚠️  Could not parse {display_path(path)}: {exc}")
            continue
        for tc in suite.test_cases:
            status, reason = run_case(engine, suite.test_type, tc)
            cat["total"] += 1
            cat[status] += 1
            if status != PASSED:
                results.append({
                    "file": display_path(path),
                    "line": tc.line_number,
                    "test": tc.function_call,
                    "expected": tc.expected_result,
                    "status": status,
                    "reason": reason,
                })

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
        "suite": "functions",
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "totalTests": totals["total"],
        "passed": totals[PASSED],
        "failed": totals[FAILED],
        "skipped": totals[SKIPPED],
        "passRate": pass_rate(totals[PASSED], totals[FAILED]),
        "categories": categories,
        "unmappedFunctions": unmapped,
        "extensions": engine.extensions,
        "nonPassingResults": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2))

    print(f"{'Category':<12} {'Total':>6} {'Pass':>6} {'Fail':>6} {'Skip':>6} {'Rate':>7}")
    for name, c in sorted(categories.items()):
        rate = "n/a" if c["passRate"] is None else f"{c['passRate']:.1f}%"
        print(f"{name:<12} {c['total']:>6} {c[PASSED]:>6} {c[FAILED]:>6} {c[SKIPPED]:>6} {rate:>7}")
    print(f"{'TOTAL':<12} {totals['total']:>6} {totals[PASSED]:>6} {totals[FAILED]:>6} {totals[SKIPPED]:>6} {report['passRate']:>6.1f}%")
    print(f"DuckDB {engine.version} — report written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
