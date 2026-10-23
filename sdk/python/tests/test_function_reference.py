"""Tests for scripts/function_reference.py: results from the function definitions alone."""

import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))
pytest.importorskip("mpmath")
pytest.importorskip("numpy")

import function_reference as fr  # noqa: E402
from substrait_compliance.function_cases import parse_case_text  # noqa: E402


def case(line, urn="functions_arithmetic", kind="SCALAR"):
    text = (f"### SUBSTRAIT_{kind}_TEST: v1.0\n### SUBSTRAIT_INCLUDE: extension:io.substrait:{urn}\n\n"
            f"# g\n{line}\n")
    return parse_case_text(text).testcases[0]


# (case, extension, kind, expected verdict): True = the definitions confirm the expected
# value, False = they contradict it, None = they do not decide it.
CASES = [
    ("abs(-32768::i16) [overflow:SATURATE] = 32767::i16", "functions_arithmetic", "SCALAR", True),
    ("abs(-32768::i16) [overflow:SILENT] = <!UNDEFINED>", "functions_arithmetic", "SCALAR", True),
    ("factorial(13::i32) [overflow:ERROR] = <!ERROR>", "functions_arithmetic", "SCALAR", True),
    ("modulus(1000000::i32, 7::i32) = 6::i32", "functions_arithmetic", "SCALAR", False),
    ("modulus(-2147483648::i32, -1::i32) = 0::i32", "functions_arithmetic", "SCALAR", None),
    ("shift_left(5::i32, 30::i32) = -2147483648::i32", "functions_arithmetic", "SCALAR", None),
    ("cos(inf::fp64) = nan::fp64", "functions_arithmetic", "SCALAR", True),
    ("cosh(89.0::fp32) = inf::fp32", "functions_arithmetic", "SCALAR", False),
    ("atan(inf::fp64) = 1.5707963633974483::fp64", "functions_arithmetic", "SCALAR", False),
    ("acos(inf::fp64) [on_domain_error:NAN] = nan::fp64", "functions_arithmetic", "SCALAR", True),
    ("acosh(-1.0::fp64) [on_domain_error:ERROR] = <!ERROR>", "functions_arithmetic", "SCALAR", True),
    ("power(2.0::fp64, -1074.0::fp64) = 0.0::fp64", "functions_arithmetic", "SCALAR", False),
    ("sign(nan::fp64) = nan::fp64", "functions_arithmetic", "SCALAR", True),
    ("divide(100::i32, 0::i32) [on_division_by_zero:ERROR] = <!ERROR>", "functions_arithmetic", "SCALAR", True),
    ("gt(nan::fp64, 5.0::fp64) = false::bool", "functions_comparison", "SCALAR", True),
    ("not_equal(nan::fp32, nan::fp32) = true::bool", "functions_comparison", "SCALAR", True),
    ("between(-inf::fp64, -100.0::fp64, 100.0::fp64) = true::bool", "functions_comparison", "SCALAR", False),
    ("octet_length('test🎉string'::str) = 15::i64", "functions_string", "SCALAR", False),
    ("right('mixed😄text'::str, 4::i32) = '😄text'::str", "functions_string", "SCALAR", False),
    ("right('ab'::str, 5::i32) = 'ab'::str", "functions_string", "SCALAR", True),
    ("right('a̐é̲'::str, 1::i32) = 'é̲'::str", "functions_string", "SCALAR", None),
    ("like('abc%def'::str, 'abc%def'::str) = false::bool", "functions_string", "SCALAR", False),
    ("regexp_replace('abcabc'::str, 'abc'::str, 'X'::str, 1::i64, 4::i64) = 'abcX'::str", "functions_string", "SCALAR", False),
    ("regexp_replace('abcabc'::str, 'abc'::str, 'X'::str, 1::i64, 2::i64) = 'abcX'::str", "functions_string", "SCALAR", True),
    ("regexp_replace('hello'::str, 'l'::str, null::str?, 0::i64, 0::i64) = null::str?", "functions_string", "SCALAR", None),
    ("regexp_replace('hello'::str, 'l'::str, null::str?, 1::i64, 0::i64) = null::str?", "functions_string", "SCALAR", True),
    ("substring('abcdefg'::str, -10::i32, 5::i32) [negative_start:LEFT_OF_BEGINNING] = ''::str", "functions_string", "SCALAR", True),
    ("substring('abcdefg'::str, 0::i32, 3::i32) = 'abc'::str", "functions_string", "SCALAR", None),
    ("repeat(''::str, 9223372036854775807::i64) = ''::str", "functions_string", "SCALAR", True),
    ("repeat('a'::str, 2147483647::i64) = 'aaa...'::str", "functions_string", "SCALAR", False),
    ("string_split('abc'::str, null::str?) = null::list?<str>", "functions_string", "SCALAR", True),
    ("string_split(''::str, ' '::str) = []::list<str>", "functions_string", "SCALAR", None),
    ("regexp_string_split('AbCdEf'::str, 'b'::str) = ['AbCdEf']::list<str>", "functions_string", "SCALAR", False),
    ("atanh(-1.0::fp64) = -inf::fp64", "functions_arithmetic", "SCALAR", True),
    ("degrees(3.4028235e+38::fp32) = 1.9498558e+40::fp32", "functions_arithmetic", "SCALAR", False),
    ("degrees(1.401298464324817e-45::fp32) = 8.029052e-44::fp32", "functions_arithmetic", "SCALAR", True),
    ("sum((-9223372036854775808, -1)::i64) [overflow:ERROR] = <!ERROR>", "functions_arithmetic", "AGGREGATE", True),
    ("((1.0), (NaN), (3.0)) std_dev(POPULATION::enum, col0::fp64) = nan::fp64", "functions_arithmetic", "AGGREGATE", True),
    ("sum((3.14159)::fp32) = 3.14159::fp64", "functions_arithmetic", "AGGREGATE", False),
    ("((2.0), (4.0), (4.0), (4.0), (5.0), (5.0), (7.0), (9.0)) std_dev(POPULATION::enum, col0::fp64) = 2.0::fp64",
     "functions_arithmetic", "AGGREGATE", True),
]


@pytest.mark.parametrize("line, urn, kind, verdict", CASES, ids=[c[0][:60] for c in CASES])
def test_reference_verdicts(line, urn, kind, verdict):
    tc = case(line, urn, kind)
    ref = fr.reference(tc)
    assert (None if ref is None else fr.matches(ref, tc.result)) is verdict, fr.describe(ref) if ref else None


def test_describe_long_results():
    tc = case("repeat('a'::str, 2147483647::i64) = 'aaa...'::str", "functions_string")
    assert fr.describe(fr.reference(tc)) == "'a' repeated 2147483647 times"
