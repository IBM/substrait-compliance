"""Tests for substrait_compliance.function_cases (upstream .test format reader)."""

from pathlib import Path

import pytest

from substrait_compliance.function_cases import (
    AggregateArgument,
    CaseLiteral,
    FuncCallArg,
    Identifier,
    Lambda,
    ParseError,
    SubstraitError,
    is_upstream_format,
    parse_case_file,
    parse_case_text,
)

REPO_ROOT = Path(__file__).resolve().parents[3]
SUITE = REPO_ROOT / "test-suites" / "functions-substrait-0.107"

SCALAR = """### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: extension:io.substrait:functions_arithmetic

# basic: Basic examples
add(120::i8, 5::i8) = 125::i8
add(null::i8?, 5::i8) = null::i8?

# overflow: Overflow
add(120::i8, 10::i8) [overflow:ERROR] = <!ERROR>
"""


def test_scalar_cases_have_values_types_options_and_lines():
    tf = parse_case_text(SCALAR)
    assert tf.include == "extension:io.substrait:functions_arithmetic"
    first, with_null, overflow = tf.testcases
    assert first.func_name == "add"
    assert first.args == [CaseLiteral("120", "i8"), CaseLiteral("5", "i8")]
    assert first.result == CaseLiteral("125", "i8")
    assert (first.line, with_null.line, overflow.line) == (5, 6, 9)
    assert with_null.args[0].nullable
    assert overflow.options == {"overflow": "ERROR"}
    assert isinstance(overflow.result, SubstraitError)


def test_lambda_arguments_keep_parameters_and_body():
    tf = parse_case_text("""### SUBSTRAIT_SCALAR_TEST: v1.0
### SUBSTRAIT_INCLUDE: extension:io.substrait:functions_list

# basic: lambdas
filter([1, 2, 3]::list<i32>, (x -> gt(x, 2::i32))::func<i32 -> bool>) = [3]::list<i32>
""")
    lam = tf.testcases[0].args[1]
    assert isinstance(lam, Lambda)
    assert lam.params == ["x"]
    assert lam.body == FuncCallArg("gt", [Identifier("x"), CaseLiteral("2", "i32")])
    assert lam.type == "func<i32->bool>"


def test_aggregate_table_form():
    tf = parse_case_text("""### SUBSTRAIT_AGGREGATE_TEST: v1.0
### SUBSTRAIT_INCLUDE: extension:io.substrait:functions_arithmetic

# basic: compact table
((1.0), (2.0), (3.0)) variance(SAMPLE::enum, col0::fp64) = 1.0::fp64?
""")
    tc = tf.testcases[0]
    assert tc.rows == [["1.0"], ["2.0"], ["3.0"]]
    assert tc.args[0].scalar_value == CaseLiteral("SAMPLE", "enum")
    assert tc.args[1] == AggregateArgument("col0", "fp64", "", None)
    assert tc.line == 5


def test_window_case_line_points_at_its_frame_definition():
    tf = parse_case_text("""### SUBSTRAIT_WINDOW_TEST: v1.0
### SUBSTRAIT_INCLUDE: extension:io.substrait:functions_arithmetic

# basic: lag
DEFINE f1(i32) = ((1), (2), (3))
lag(col0) OVER f1 = (Null, 1, 2)::i32?
""")
    tc = tf.testcases[0]
    assert tc.func_name == "lag"
    assert tc.rows == [["1"], ["2"], ["3"]]
    assert tc.line == 5


def test_syntax_errors_raise():
    with pytest.raises(ParseError):
        parse_case_text(SCALAR + "add(1::i8 = 2::i8\n")


def test_format_detection(tmp_path):
    current = tmp_path / "current.test"
    current.write_text(SCALAR)
    legacy = tmp_path / "legacy.test"
    legacy.write_text(SCALAR.replace("extension:io.substrait:functions_arithmetic",
                                     "'/extensions/functions_arithmetic.yaml'"))
    assert is_upstream_format(current)
    assert not is_upstream_format(legacy)


@pytest.mark.skipif(not SUITE.is_dir(), reason="function suite not present")
def test_every_file_in_the_current_suite_parses():
    files = sorted(SUITE.rglob("*.test"))
    assert files
    n_cases = 0
    for path in files:
        tf = parse_case_file(path)
        assert tf.testcases, path
        n_cases += len(tf.testcases)
    assert n_cases > 3000
