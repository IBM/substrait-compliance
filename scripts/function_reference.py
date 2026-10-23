"""
Reference results for function test cases, computed from the function definitions alone.

Used by scripts/convert_legacy_function_tests.py to verify a converted legacy case that
DuckDB does not reproduce: reference(tc) returns the result the Substrait 0.107.0
definitions (the extension YAMLs) and IEEE 754 give for the case, or None when they do
not decide it. matches(ref, expected) then says whether the case's expected value agrees.

    Value(x)   a value: None (null), bool, int, float, str or a list of these
    ERROR      the definitions require an error (e.g. overflow:ERROR on an overflow)
    UNDEFINED  the definitions leave the result undefined (e.g. overflow:SILENT)

Covered, each only where the definitions are unambiguous:
  * integer add, subtract, multiply, divide, negate, abs, factorial, sum, modulus and
    shift_left, with the overflow / on_division_by_zero option modes;
  * floating-point math functions, exactly (50-digit mpmath, correctly rounded to the
    result type), with IEEE 754 special values (NaN propagates; sin/cos/tan of +/-inf is
    NaN) and the on_domain_error / on_log_zero option modes;
  * floating-point comparisons, which the YAMLs define as the IEEE operators
    (lt(x, y) := (x < y)), and sign;
  * floating-point sum, std_dev and variance, exactly;
  * string functions on UTF-8 bytes, and on characters for text without combining marks
    or format characters (where code points and user-perceived characters agree);
  * null propagation for functions that do not handle nulls themselves.
Left undecided (None): behaviour the definitions do not specify, such as shift_left
overflow, modulus(INT_MIN, -1), min/max with NaN, interval normalisation, a start or
position below 1 without an option, or splitting on an empty separator.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from fractions import Fraction
from typing import Any, Optional

INT_BITS = {"i8": 8, "i16": 16, "i32": 32, "i64": 64}
FLOAT_TYPES = ("fp32", "fp64")
STRING_TYPES = ("str", "string", "varchar", "vchar", "fchar", "fixedchar")
FP32_REL_TOL = 1e-6
FP64_REL_TOL = 1e-9
MATERIALISE_LIMIT = 10_000_000  # characters

# Functions that handle null arguments themselves (everything else returns null for a
# null argument).
NULL_HANDLING = {"is_null", "is_not_null", "coalesce", "nullif", "is_not_distinct_from",
                 "is_distinct_from", "is_true", "is_false", "is_not_true", "is_not_false",
                 "and", "or", "xor", "count", "any_value"}


@dataclass(frozen=True)
class Value:
    value: Any


class _Marker:
    def __init__(self, name):
        self.name = name

    def __repr__(self):
        return self.name


ERROR = _Marker("<!ERROR>")
UNDEFINED = _Marker("<!UNDEFINED>")


@dataclass(frozen=True)
class Repeated:
    """A string too long to build: `text` repeated `times` times."""
    text: str
    times: int


# ----------------------------------------------------------------------------- literals

def short_type(type_str: str) -> str:
    t = str(type_str).strip().lower()
    return re.split(r"[<?]", t, maxsplit=1)[0]


def _unescape(quoted: str) -> str:
    body, out, i = quoted[1:-1], [], 0
    while i < len(body):
        if body[i] == "\\" and i + 1 < len(body) and body[i + 1] in ("'", "\\"):
            out.append(body[i + 1])
            i += 2
        else:
            out.append(body[i])
            i += 1
    return "".join(out)


def _is_null(raw) -> bool:
    return raw is None or (isinstance(raw, str) and raw.lower() == "null")


def parse_value(raw, type_str: str):
    """A literal's raw value -> Python value of its type (None for null)."""
    if _is_null(raw):
        return None
    t = short_type(type_str)
    if isinstance(raw, list):
        inner = re.search(r"<(.*)>$", str(type_str).strip())
        elem = inner.group(1) if inner and t == "list" else type_str
        return [parse_value(v, elem) for v in raw]
    text = str(raw).strip()
    if t in INT_BITS:
        return int(text)
    if t in FLOAT_TYPES:
        x = float(text.replace("NaN", "nan"))
        if t == "fp32":
            r = _to_fp32(x)
            if math.isinf(r) and not math.isinf(x):
                raise ValueError(f"{text} is outside the fp32 range")
            return r
        return x
    if t in ("bool", "boolean"):
        return text.lower() == "true"
    if t in STRING_TYPES:
        return _unescape(text) if text.startswith("'") else text
    raise ValueError(f"no reference for literals of type {type_str}")


def _to_fp32(x: float) -> float:
    import numpy as np
    with np.errstate(over="ignore"):
        return float(np.float32(x))


def _round_to(x, t: str) -> float:
    """An mpmath/Fraction/float value correctly rounded to fp64, then to fp32."""
    if isinstance(x, Fraction):
        try:
            d = x.numerator / x.denominator  # correctly rounded
        except OverflowError:
            d = math.inf if x > 0 else -math.inf
    else:
        import mpmath as mp
        if mp.isnan(x):
            return math.nan
        if mp.isinf(x):
            return math.inf if x > 0 else -math.inf
        try:
            d = float(x)
        except OverflowError:
            d = math.inf if x > 0 else -math.inf
    return _to_fp32(d) if t == "fp32" else d


# ----------------------------------------------------------------------------- integers

def _int_range(t: str):
    b = INT_BITS[t]
    return -(1 << (b - 1)), (1 << (b - 1)) - 1


def _overflow(r: int, t: str, options: dict):
    lo, hi = _int_range(t)
    if lo <= r <= hi:
        return Value(r)
    mode = str(options.get("overflow", "")).upper()
    if mode == "ERROR":
        return ERROR
    if mode == "SATURATE":
        return Value(max(lo, min(hi, r)))
    if mode == "SILENT":
        return UNDEFINED
    return None  # overflow without a declared mode: not decided


def _trunc_div(x: int, y: int) -> int:
    q = abs(x) // abs(y)
    return q if (x >= 0) == (y >= 0) else -q


def _integer_scalar(fn, xs, t, options):
    if fn == "add":
        return _overflow(xs[0] + xs[1], t, options)
    if fn == "subtract":
        return _overflow(xs[0] - xs[1], t, options)
    if fn == "multiply":
        return _overflow(xs[0] * xs[1], t, options)
    if fn == "negate":
        return _overflow(-xs[0], t, options)
    if fn == "abs":
        return _overflow(abs(xs[0]), t, options)
    if fn == "factorial":
        if xs[0] < 0:
            return ERROR  # "Negative inputs will raise an error."
        if xs[0] > 1000:
            return _overflow(1 << 200, t, options)  # certainly out of range
        return _overflow(math.factorial(xs[0]), t, options)
    if fn == "divide":
        x, y = xs
        if y == 0:
            mode = str(options.get("on_division_by_zero", "")).upper()
            return {"ERROR": ERROR, "NULL": Value(None)}.get(mode)
        return _overflow(_trunc_div(x, y), t, options)
    if fn == "modulus":
        x, y = xs
        lo, _ = _int_range(t)
        if y == 0 or (x == lo and y == -1):
            return None  # domain error / quotient overflow: not decided here
        mode = str(options.get("division_type", "")).upper()
        if mode == "FLOOR":
            return Value(x - y * (x // y))
        if mode == "TRUNCATE" or (x >= 0) == (y > 0) or x % y == 0:
            return Value(x - y * _trunc_div(x, y))  # TRUNCATE (FLOOR agrees here)
        return None  # sign-dependent and no division_type given
    if fn == "shift_left":
        base, shift = xs
        if shift < 0 or shift >= INT_BITS[t]:
            return None
        r = base << shift
        lo, hi = _int_range(t)
        return Value(r) if lo <= r <= hi else None  # bits shifted out: not specified
    return None


# ----------------------------------------------------------------------------- floats

def _domain(options: dict, option: str = "on_domain_error"):
    mode = str(options.get(option, "")).upper()
    return {"NAN": Value(math.nan), "ERROR": ERROR, "NULL": Value(None),
            "MINUS_INFINITY": Value(-math.inf)}.get(mode)


def _float_scalar(fn, xs, t, options):
    import mpmath as mp
    mp.mp.dps = 50
    if fn in ("gt", "gte", "lt", "lte", "equal", "not_equal"):
        a, b = xs
        return Value({"gt": a > b, "gte": a >= b, "lt": a < b, "lte": a <= b,
                      "equal": a == b, "not_equal": a != b}[fn])
    if fn == "between":
        x, low, high = xs
        return Value(low <= x <= high)
    if fn == "is_nan":
        return Value(math.isnan(xs[0]))
    if fn == "is_finite":
        return Value(math.isfinite(xs[0]))
    if fn == "is_infinite":
        return Value(math.isinf(xs[0]))
    if any(math.isnan(x) for x in xs):
        return Value(math.nan)  # IEEE 754: NaN propagates
    if fn == "sign":
        x = xs[0]
        return Value(1.0 if x > 0 else -1.0 if x < 0 else x)
    x = xs[0]
    if fn in ("sin", "cos", "tan") and math.isinf(x):
        return Value(math.nan)  # IEEE 754: invalid operation
    domain_error = (
        (fn in ("asin", "acos") and abs(x) > 1)
        or (fn == "acosh" and x < 1)
        or (fn == "atanh" and abs(x) > 1)
        or (fn == "sqrt" and x < 0)
        or (fn in ("ln", "log10", "log2") and x < 0)
    )
    if domain_error:
        return _domain(options)
    if fn in ("ln", "log10", "log2") and x == 0:
        return _domain(options, "on_log_zero")
    if fn == "atanh" and abs(x) == 1:
        return Value(math.copysign(math.inf, x))  # IEEE 754: atanh(+/-1) = +/-inf
    neutral = {("on_domain_error", "NAN"), ("on_log_zero", "MINUS_INFINITY"), ("rounding", "TIE_TO_EVEN")}
    if any((k, str(v).upper()) not in neutral for k, v in options.items()):
        return None
    unary = {"sin": mp.sin, "cos": mp.cos, "tan": mp.tan, "asin": mp.asin, "acos": mp.acos,
             "atan": mp.atan, "sinh": mp.sinh, "cosh": mp.cosh, "tanh": mp.tanh,
             "asinh": mp.asinh, "acosh": mp.acosh, "atanh": mp.atanh, "exp": mp.exp,
             "sqrt": mp.sqrt, "ln": mp.ln, "log10": mp.log10, "log2": lambda v: mp.log(v, 2),
             "radians": lambda v: v * mp.pi / 180, "degrees": lambda v: v * 180 / mp.pi}
    if fn in unary and len(xs) == 1:
        r = unary[fn](mp.mpf(x))
    elif fn == "atan2" and len(xs) == 2:
        if any(math.isinf(v) for v in xs):
            return None
        r = mp.atan2(mp.mpf(xs[0]), mp.mpf(xs[1]))
    elif fn == "power" and len(xs) == 2:
        if any(math.isinf(v) for v in xs) or (xs[0] < 0 and not float(xs[1]).is_integer()):
            return None
        if xs[0] == 0 and xs[1] < 0:
            return None
        r = mp.power(mp.mpf(xs[0]), mp.mpf(xs[1]))
    else:
        return None
    if mp.im(r) != 0:
        return None
    return Value(_round_to(mp.re(r), t))


def _float_aggregate(fn, values, t, distribution):
    if any(v is None for v in values):
        values = [v for v in values if v is not None]  # aggregates skip nulls
    if not values:
        return None
    if any(math.isnan(v) for v in values):
        return Value(math.nan)
    if any(math.isinf(v) for v in values):
        return None
    exact = [Fraction(v) for v in values]
    if fn == "sum":
        return Value(_round_to(sum(exact, Fraction(0)), t))
    if fn in ("std_dev", "variance"):
        n = len(exact)
        dist = str(distribution or "").upper()
        if dist not in ("SAMPLE", "POPULATION") or (dist == "SAMPLE" and n < 2):
            return None
        mean = sum(exact, Fraction(0)) / n
        ss = sum(((v - mean) ** 2 for v in exact), Fraction(0))
        var = ss / (n - 1 if dist == "SAMPLE" else n)
        if fn == "variance":
            return Value(_round_to(var, t))
        import mpmath as mp
        mp.mp.dps = 50
        return Value(_round_to(mp.sqrt(mp.mpf(var.numerator) / var.denominator), t))
    return None


# ----------------------------------------------------------------------------- strings

def _like_regex(pattern: str) -> str:
    return "".join(".*" if c == "%" else "." if c == "_" else re.escape(c) for c in pattern)


def _matches_empty(pattern: str) -> bool:
    try:
        return re.fullmatch(pattern, "") is not None
    except re.error:
        return True


def _plain_text(s: str) -> bool:
    """No combining marks or format characters (ZWJ, tags, variation selectors): every
    code point is a character either way one counts characters."""
    import unicodedata
    return all(unicodedata.category(c) not in ("Mn", "Me", "Cf") and not 0xFE00 <= ord(c) <= 0xFE0F
               for c in s)


CHARACTER_COUNTING = {"char_length", "left", "right", "substring", "lpad", "rpad"}


def _string_scalar(fn, xs, options):
    if options:
        return None  # case_sensitivity, multiline, ... not modelled here
    if fn in CHARACTER_COUNTING and not all(_plain_text(x) for x in xs if isinstance(x, str)):
        return None  # code points vs. user-perceived characters: not specified
    if fn == "octet_length":
        return Value(len(xs[0].encode("utf-8")))
    if fn == "bit_length":
        return Value(8 * len(xs[0].encode("utf-8")))
    if fn == "char_length":
        return Value(len(xs[0]))
    if fn == "left" and len(xs) == 2:
        return Value(xs[0][:xs[1]]) if xs[1] >= 0 else None
    if fn == "right" and len(xs) == 2:
        return Value(xs[0][max(len(xs[0]) - xs[1], 0):] if xs[1] > 0 else "") if xs[1] >= 0 else None
    if fn == "like" and len(xs) == 2:
        return Value(re.fullmatch(_like_regex(xs[1]), xs[0], re.DOTALL) is not None)
    if fn == "replace" and len(xs) == 3:
        return Value(xs[0].replace(xs[1], xs[2])) if xs[1] else None
    if fn == "repeat" and len(xs) == 2:
        s, n = xs
        if n < 0:
            return None
        if s == "" or n == 0:
            return Value("")
        return Value(s * n) if len(s) * n <= MATERIALISE_LIMIT else Value(Repeated(s, n))
    if fn in ("lpad", "rpad") and len(xs) in (2, 3):
        s, length = xs[0], xs[1]
        chars = xs[2] if len(xs) == 3 else " "
        if length < 0 or chars == "":
            return None
        if len(s) >= length:
            return Value(s[:length])  # lpad: "remove characters from the right-side"
        if fn == "rpad":
            return None  # rpad truncation side differs in the YAML; not modelled
        need = length - len(s)
        return Value((chars * (need // len(chars) + 1))[:need] + s)
    if fn == "regexp_replace" and len(xs) in (3, 5):
        s, pattern, repl = xs[:3]
        position, occurrence = (xs[3], xs[4]) if len(xs) == 5 else (1, 0)
        if position < 1 or occurrence < 0 or "\\" in repl or "$" in repl or _matches_empty(pattern):
            return None
        head, rest = s[:position - 1], s[position - 1:]
        matches = list(re.finditer(pattern, rest))
        if occurrence == 0:
            return Value(head + re.sub(pattern, repl, rest))
        if occurrence > len(matches):
            return Value(s)
        m = matches[occurrence - 1]
        return Value(head + rest[:m.start()] + repl + rest[m.end():])
    if fn == "regexp_count_substring" and len(xs) in (2, 3):
        position = xs[2] if len(xs) == 3 else 1
        if position < 1 or _matches_empty(xs[1]):
            return None
        return Value(len(re.findall(xs[1], xs[0][position - 1:])))
    if fn == "regexp_string_split" and len(xs) == 2:
        if xs[1] == "" or _matches_empty(xs[1]) or "(" in xs[1]:
            return None
        return Value(re.split(xs[1], xs[0]))
    if fn == "string_split" and len(xs) == 2:
        if xs[0] == "" or xs[1] == "":
            return None  # not specified
        return Value(xs[0].split(xs[1]))
    if fn == "substring" and len(xs) == 3:
        s, start, length = xs
        if length < 0:
            return None
        if start < 1:
            return None  # needs negative_start, handled below
        return Value(s[start - 1:start - 1 + length])
    return None


def _substring_with_option(xs, options):
    s, start, length = xs
    if not _plain_text(s):
        return None
    mode = str(options.get("negative_start", "")).upper()
    if length < 0 or mode != "LEFT_OF_BEGINNING":
        return None
    # start 1 is the first character; 0 is one left of it, -1 two left of it, ...
    first, last = start, start + length - 1
    first, last = max(first, 1), min(last, len(s))
    return Value(s[first - 1:last] if last >= first else "")


# ----------------------------------------------------------------------------- entry

def reference(tc):
    """Reference result for a parsed case (substrait_compliance.function_cases.TestCase),
    or None when the definitions do not decide it."""
    try:
        return _reference(tc)
    except (ValueError, TypeError, OverflowError, ZeroDivisionError, re.error, AttributeError):
        return None


def _reference(tc):
    from substrait_compliance.function_cases import AggregateArgument, CaseLiteral
    fn, options = tc.func_name, {k: str(v) for k, v in (tc.options or {}).items()}
    args = tc.args or []

    # aggregates: a value list fn((1, 2)::T) or a table ((1), (2)) fn(col0::T)
    if tc.rows is not None or any(isinstance(a, AggregateArgument) for a in args):
        enums = [a.scalar_value.value for a in args
                 if isinstance(a, AggregateArgument) and a.scalar_value is not None
                 and a.scalar_value.type == "enum"]
        cols = [a for a in args if isinstance(a, AggregateArgument) and a.column_name]
        if len(cols) != 1 or not tc.rows:
            return None
        t = short_type(cols[0].column_type)
        idx = int(cols[0].column_name[3:])
        values = [parse_value(row[idx], t) for row in tc.rows]
        return _aggregate(fn, values, t, options, enums[0] if enums else None)
    if len(args) >= 1 and isinstance(args[0], CaseLiteral) and isinstance(args[0].value, list) \
            and short_type(args[0].type) != "list":
        t = short_type(args[0].type)
        enums = [a.value for a in args[1:] if isinstance(a, CaseLiteral) and a.type == "enum"]
        values = parse_value(args[0].value, args[0].type)
        return _aggregate(fn, values, t, options, enums[0] if enums else None)

    if not all(isinstance(a, CaseLiteral) for a in args) or not args:
        return None
    if any(a.type == "enum" for a in args):
        return None
    types = [short_type(a.type) for a in args]
    xs = [parse_value(a.value, a.type) for a in args]
    if fn in ("is_null", "is_not_null") and len(xs) == 1:
        return Value((xs[0] is None) == (fn == "is_null"))
    if any(x is None for x in xs):
        if fn in NULL_HANDLING:
            return None
        position_arg = {"regexp_replace": 3, "regexp_count_substring": 2, "regexp_match_substring": 2}.get(fn)
        if position_arg is not None and len(xs) > position_arg and \
                isinstance(xs[position_arg], int) and xs[position_arg] < 1:
            return None  # an invalid position with a null: which wins is not specified
        return Value(None)
    if all(t in INT_BITS for t in types):
        if fn in ("bitwise_and", "bitwise_or", "bitwise_xor", "bitwise_not"):
            lo, hi = _int_range(types[0])
            r = {"bitwise_and": lambda: xs[0] & xs[1], "bitwise_or": lambda: xs[0] | xs[1],
                 "bitwise_xor": lambda: xs[0] ^ xs[1], "bitwise_not": lambda: ~xs[0]}[fn]()
            return Value(r) if lo <= r <= hi else None
        return _integer_scalar(fn, xs, types[0], options)
    if all(t in FLOAT_TYPES for t in types):
        return _float_scalar(fn, xs, types[0], options)
    if types[0] in STRING_TYPES:
        if fn == "substring" and options:
            return _substring_with_option(xs, options)
        return _string_scalar(fn, xs, options)
    return None


def _aggregate(fn, values, t, options, enum):
    if t in INT_BITS and fn == "sum":
        present = [v for v in values if v is not None]
        return _overflow(sum(present), t, options) if present else Value(None)
    if t in FLOAT_TYPES and fn in ("sum", "std_dev", "variance"):
        if fn == "sum" and options:
            return None
        return _float_aggregate(fn, values, "fp64" if fn == "sum" else t, enum)
    return None


# ----------------------------------------------------------------------------- matching

def matches(ref, result) -> bool:
    """Whether a case's expected result (CaseLiteral or SubstraitError) equals ref."""
    from substrait_compliance.function_cases import SubstraitError
    if ref is ERROR or ref is UNDEFINED:
        return isinstance(result, SubstraitError) and \
            result.error == ("error" if ref is ERROR else "undefined")
    if isinstance(result, SubstraitError):
        return False
    try:
        expected = parse_value(result.value, result.type)
    except ValueError:
        return False
    return _equal(ref.value, expected, short_type(result.type))


def _equal(ref, expected, t) -> bool:
    if ref is None or expected is None:
        return ref is None and expected is None
    if isinstance(ref, Repeated):
        return isinstance(expected, str) and len(expected) == len(ref.text) * ref.times \
            and expected == ref.text * ref.times
    if isinstance(ref, list):
        return isinstance(expected, list) and len(ref) == len(expected) and \
            all(_equal(a, b, t) for a, b in zip(ref, expected))
    if isinstance(ref, bool) or isinstance(expected, bool):
        return isinstance(ref, bool) and isinstance(expected, bool) and ref == expected
    if isinstance(ref, float) or isinstance(expected, float):
        a, b = float(ref), float(expected)
        if math.isnan(a) or math.isnan(b):
            return math.isnan(a) and math.isnan(b)
        if math.isinf(a) or math.isinf(b):
            return a == b
        tol = FP32_REL_TOL if t == "fp32" else FP64_REL_TOL
        return math.isclose(a, b, rel_tol=tol, abs_tol=0.0)
    return ref == expected


def describe(ref) -> str:
    if ref is ERROR or ref is UNDEFINED:
        return repr(ref)
    v = ref.value
    if isinstance(v, Repeated):
        return f"{v.text!r} repeated {v.times} times"
    if isinstance(v, str) and len(v) > 60:
        return repr(v[:57] + "...") + f" ({len(v)} characters)"
    return repr(v)
