"""Tests for scripts/duckdb_function_sql.py (Substrait test case -> DuckDB SQL).

These only check the generated SQL text, so they need neither DuckDB nor the
extension YAMLs."""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import duckdb_function_sql as fsql  # noqa: E402
from substrait_compliance.function_cases import parse_case_text  # noqa: E402


def case(line, kind="SCALAR", include="functions_arithmetic"):
    tf = parse_case_text(f"### SUBSTRAIT_{kind}_TEST: v1.0\n"
                         f"### SUBSTRAIT_INCLUDE: extension:io.substrait:{include}\n\n# g: g\n{line}\n")
    return tf.testcases[0]


def test_literals():
    assert fsql.literal_sql("-128", "i8") == "CAST(-128 AS TINYINT)"
    assert fsql.literal_sql("2.5", "fp32") == "CAST('2.5' AS FLOAT)"
    assert fsql.literal_sql("inf", "fp64") == "CAST('inf' AS DOUBLE)"
    assert fsql.literal_sql("1.75", "dec<38, 2>") == "CAST('1.75' AS DECIMAL(38,2))"
    assert fsql.literal_sql(None, "i32?") == "CAST(NULL AS INTEGER)"
    assert fsql.literal_sql("2016-12-31T13:30:15", "pts<6>") == "CAST('2016-12-31T13:30:15' AS TIMESTAMP)"
    assert fsql.literal_sql(["1", "Null"], "list<i32?>") == \
        "CAST([CAST(1 AS INTEGER), CAST(NULL AS INTEGER)] AS INTEGER[])"


def test_strings_use_only_the_defined_escapes():
    assert fsql.literal_sql(r"'it\'s'", "str") == "'it''s'"
    assert fsql.literal_sql(r"'a\\b'", "str") == r"'a\b'"
    assert fsql.literal_sql(r"'a\nb'", "str") == r"'a\nb'"  # \n is not an escape in the format


def test_intervals():
    assert fsql.interval_sql("P1DT10H") == "CAST('1 days 10 hours' AS INTERVAL)"
    assert fsql.interval_sql("P-5D") == "CAST('-5 days' AS INTERVAL)"
    assert fsql.interval_sql("P2W") == "CAST('14 days' AS INTERVAL)"


def test_decimal_needs_precision_and_scale():
    with pytest.raises(fsql.Untranslatable):
        fsql.sql_type("dec")


def test_integer_division_truncates():
    tc = case("divide(-7::i32, 2::i32) = -3::i32")
    assert fsql.Translator().scalar_case(tc) == "SELECT (CAST(-7 AS INTEGER) // CAST(2 AS INTEGER))"


def test_logb_takes_value_then_base():
    tc = case("logb(100000::i64, 10::i64) = 5.0::fp64", include="functions_logarithmic")
    assert fsql.Translator().scalar_case(tc) == "SELECT log(CAST(10 AS BIGINT), CAST(100000 AS BIGINT))"


def test_regexp_replace_occurrence_zero_replaces_all():
    tc = case("regexp_replace('aaa'::str, 'a'::str, 'b'::str) = 'bbb'::str", include="functions_string")
    assert fsql.Translator().scalar_case(tc).endswith(", 'g')")


def test_extract_requires_indexing_for_calendar_components():
    tr = fsql.Translator()
    ok = case("extract(MONTH::enum, ZERO::enum, 2020-12-31::date) = 11::i64", include="functions_datetime")
    assert tr.scalar_case(ok) == "SELECT CAST(month(CAST('2020-12-31' AS DATE)) - 1 AS BIGINT)"
    missing = case("extract(MONTH::enum, 2020-12-31::date) = 12::i64", include="functions_datetime")
    with pytest.raises(fsql.Untranslatable):
        tr.scalar_case(missing)


def test_lambda():
    tc = case("filter([1, 2, 3]::list<i32>, (x -> gt(x, 2::i32))::func<i32 -> bool>) = [3]::list<i32>",
              include="functions_list")
    sql = fsql.Translator().scalar_case(tc)
    assert "list_filter(" in sql and "lambda lp_x: (lp_x > CAST(2 AS INTEGER))" in sql


def test_aggregates():
    tr = fsql.Translator()
    listed = case("max((20, -3, 1)::i8) = 20::i8?", kind="AGGREGATE")
    assert tr.aggregate_case(listed) == (
        "SELECT max(x) FROM (SELECT UNNEST(CAST(CAST([CAST(20 AS TINYINT), CAST(-3 AS TINYINT), "
        "CAST(1 AS TINYINT)] AS TINYINT[]) AS TINYINT[])) AS x)")
    table = case("((1.0), (3.0)) variance(POPULATION::enum, col0::fp64) = 1.0::fp64?", kind="AGGREGATE")
    assert tr.aggregate_case(table) == (
        "SELECT var_pop(col0) FROM (VALUES (CAST('1.0' AS DOUBLE)), (CAST('3.0' AS DOUBLE))) AS t(col0)")
    empty = case("(()) variance(SAMPLE::enum, col0::fp64) = Null::fp64?", kind="AGGREGATE")
    assert "WHERE FALSE" in tr.aggregate_case(empty)


def test_window():
    tc = case("DEFINE f1(i32) = ((1998), (1999))\nlag(col0) OVER f1 = (Null, 1998)::i32?", kind="WINDOW")
    sql = fsql.Translator().window_case(tc, {0: "i32"})
    assert sql.startswith("SELECT coalesce(list(v ORDER BY rn), []) FROM (SELECT rn, lag(col0) OVER "
                          "(ORDER BY rn ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)")


def test_frame_column_types_keep_parameterised_types():
    assert fsql.frame_column_types("DEFINE f1(i32, dec<10, 2>) = ((1, 1.5))") == {0: "i32", 1: "dec<10, 2>"}


def test_pad_defaults_to_space_and_huge_results_are_refused():
    tr = fsql.Translator()
    two = case("lpad('ab'::str, 4::i32) = '  ab'::str", include="functions_string")
    assert tr.scalar_case(two) == "SELECT lpad('ab', CAST(4 AS INTEGER), ' ')"
    huge = case("repeat('a'::str, 2147483647::i64) = 'a'::str", include="functions_string")
    with pytest.raises(fsql.Untranslatable):
        tr.scalar_case(huge)
