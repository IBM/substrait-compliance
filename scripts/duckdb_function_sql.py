"""
Translate parsed Substrait function test cases (upstream ``.test`` format) into
DuckDB SQL.

Used by scripts/run_function_compliance.py for suites in the upstream format.
Each case becomes one query that returns the function result, plus one query that
evaluates the expected literal with the same DuckDB types, so both sides are
compared as Python values:

* scalar     ``fn(a, b)``                  -> ``SELECT fn_sql(a, b)``
* aggregate  ``fn((1, 2, 3)::i32)``        -> ``SELECT fn(x) FROM (SELECT UNNEST([...]) AS x)``
             ``((1), (2)) fn(col0::i32)``  -> ``SELECT fn(col0) FROM (VALUES ...) t(col0)``
* window     ``DEFINE f1(i32) = (...)``    -> ``SELECT list(v ORDER BY rn) FROM (SELECT rn, fn(col0)
             ``fn(col0) OVER f1``              OVER (ORDER BY rn ROWS BETWEEN UNBOUNDED PRECEDING AND
                                                UNBOUNDED FOLLOWING) AS v FROM (VALUES ...))``

Anything this module cannot express raises Untranslatable; the runner reports
those cases as skipped, never as passed or failed.
"""

from __future__ import annotations

import re
from typing import Callable, Dict, List, Optional

from substrait_compliance.function_cases import (
    AggregateArgument,
    CaseLiteral,
    FuncCallArg,
    Identifier,
    Lambda,
)


class Untranslatable(Exception):
    """The case uses something this translator (or DuckDB) cannot express."""


# --------------------------------------------------------------------------- types

INT_TYPES = {"i8": "TINYINT", "i16": "SMALLINT", "i32": "INTEGER", "i64": "BIGINT"}
UINT_TYPES = {"u!u8": "UTINYINT", "u!u16": "USMALLINT", "u!u32": "UINTEGER", "u!u64": "UBIGINT"}
SIMPLE_TYPES = {
    **INT_TYPES, **UINT_TYPES,
    "fp32": "FLOAT", "fp64": "DOUBLE", "bool": "BOOLEAN",
    "str": "VARCHAR", "string": "VARCHAR", "vchar": "VARCHAR", "varchar": "VARCHAR",
    "fchar": "VARCHAR", "fixedchar": "VARCHAR",
    "date": "DATE", "iday": "INTERVAL", "iyear": "INTERVAL", "icompound": "INTERVAL",
    "binary": "BLOB", "vbin": "BLOB", "fbin": "BLOB",
}
INTERVAL_TYPES = {"iday", "iyear", "icompound"}


def split_type(type_str: str):
    """'dec?<38,2>' -> ('dec', '38,2'); 'list<i32?>' -> ('list', 'i32?'); 'i32?' -> ('i32', '')"""
    t = type_str.strip()
    if "<" in t:
        head, params = t.split("<", 1)
        return head.rstrip("?").strip(), params[: params.rfind(">")].strip()
    return t.rstrip("?").strip(), ""


def base_type(type_str: str) -> str:
    return split_type(type_str)[0]


def sql_type(type_str: str) -> str:
    base, params = split_type(type_str)
    if base == "list":
        return sql_type(params) + "[]"
    if base in ("dec", "decimal"):
        try:
            p, s = (int(x) for x in params.split(","))
        except ValueError:
            raise Untranslatable(f"decimal without precision/scale: {type_str!r}") from None
        if p > 38:
            raise Untranslatable(f"DuckDB DECIMAL precision is at most 38 (got {p})")
        return f"DECIMAL({p},{s})"
    if base in ("pts", "precision_timestamp"):
        prec = int(params or 6)
        if prec <= 6:
            return "TIMESTAMP"
        if prec <= 9:
            return "TIMESTAMP_NS"
        raise Untranslatable(f"timestamp precision {prec} > 9")
    if base in ("ptstz", "precision_timestamp_tz"):
        if int(params or 6) > 6:
            raise Untranslatable("DuckDB TIMESTAMPTZ has microsecond precision")
        return "TIMESTAMPTZ"
    if base in ("pt", "precision_time"):
        if int(params or 6) > 6:
            raise Untranslatable("DuckDB TIME has microsecond precision")
        return "TIME"
    if base in SIMPLE_TYPES:
        return SIMPLE_TYPES[base]
    raise Untranslatable(f"type {type_str!r} has no DuckDB equivalent here")


def list_element_type(type_str: str) -> str:
    base, params = split_type(type_str)
    if base != "list":
        raise Untranslatable(f"{type_str} is not a list type")
    return params


# ------------------------------------------------------------------------ literals

def unescape_string(quoted: str) -> str:
    """Upstream string literal ('it\\'s') -> Python string. Only \\' and \\\\ are
    defined escapes; any other backslash pair is kept literally."""
    if len(quoted) < 2 or quoted[0] != "'" or quoted[-1] != "'":
        raise Untranslatable(f"not a string literal: {quoted!r}")
    body, out, i = quoted[1:-1], [], 0
    while i < len(body):
        ch = body[i]
        if ch == "\\" and i + 1 < len(body) and body[i + 1] in ("'", "\\"):
            out.append(body[i + 1])
            i += 2
            continue
        if ch == "'" and i + 1 < len(body) and body[i + 1] == "'":
            out.append("'")
            i += 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


ISO_DURATION = re.compile(
    r"^(?P<sign>-)?P(?:(?P<y>-?\d+)Y)?(?:(?P<mo>-?\d+)M)?(?:(?P<w>-?\d+)W)?(?:(?P<d>-?\d+)D)?"
    r"(?:T(?:(?P<h>-?\d+)H)?(?:(?P<mi>-?\d+)M)?(?:(?P<s>-?\d+(?:\.\d+)?)S)?)?$")


def interval_sql(iso: str) -> str:
    m = ISO_DURATION.match(iso.strip())
    if not m or iso.strip() in ("P", "PT"):
        raise Untranslatable(f"unsupported interval literal {iso!r}")
    sign = -1 if m.group("sign") else 1
    parts = []
    for key, unit in (("y", "years"), ("mo", "months"), ("d", "days"), ("h", "hours"),
                      ("mi", "minutes"), ("s", "seconds")):
        if m.group(key) is not None:
            parts.append(f"{m.group(key)} {unit}")
    if m.group("w") is not None:
        parts.append(f"{int(m.group('w')) * 7} days")
    text = " ".join(parts) or "0 days"
    sql = f"CAST({sql_string(text)} AS INTERVAL)"
    return f"(-{sql})" if sign < 0 else sql


def is_null_value(value) -> bool:
    return value is None or (isinstance(value, str) and value.lower() == "null")


def literal_sql(value, type_str: str) -> str:
    """One literal value of the given Substrait type as a DuckDB SQL expression."""
    base = base_type(type_str)
    if base == "enum":
        raise Untranslatable("enum argument outside a function that takes it")
    target = sql_type(type_str)
    if is_null_value(value):
        return f"CAST(NULL AS {target})"
    if base == "list":
        if not isinstance(value, list):
            raise Untranslatable(f"list literal expected for {type_str}")
        elem = list_element_type(type_str)
        return f"CAST([{', '.join(literal_sql(v, elem) for v in value)}] AS {target})"
    if base in UINT_TYPES:  # user-defined type literal ('25')::u!u8 -> value ["'25'"]
        raw = value[0] if isinstance(value, list) and len(value) == 1 else value
        raw = unescape_string(raw) if isinstance(raw, str) and raw.startswith("'") else str(raw)
        return f"CAST({sql_string(raw)} AS {target})"
    if isinstance(value, (list, dict)):
        raise Untranslatable(f"compound literal for scalar type {type_str}")
    text = str(value).strip()
    if base in INT_TYPES:
        if not re.fullmatch(r"[+-]?\d+", text):
            raise Untranslatable(f"bad integer literal {text!r}")
        return f"CAST({int(text)} AS {target})"
    if base in ("fp32", "fp64", "dec", "decimal"):
        if base in ("dec", "decimal") and text.lower() in ("nan", "inf", "+inf", "-inf"):
            raise Untranslatable("decimal NaN/infinity")
        # cast from text so DuckDB parses the full precision (a bare numeric literal
        # is first read as DECIMAL and can lose digits)
        return f"CAST({sql_string(text)} AS {target})"
    if base == "bool":
        if text.lower() not in ("true", "false"):
            raise Untranslatable(f"bad boolean literal {text!r}")
        return text.upper()
    if base in ("str", "string", "vchar", "varchar", "fchar", "fixedchar"):
        return sql_string(unescape_string(text))
    if base in ("binary", "vbin", "fbin"):
        return f"CAST({sql_string(unescape_string(text))} AS BLOB)"
    if base in INTERVAL_TYPES:
        return interval_sql(text)
    if base in ("date", "pts", "precision_timestamp", "ptstz", "precision_timestamp_tz",
                "pt", "precision_time"):
        return f"CAST({sql_string(text)} AS {target})"
    raise Untranslatable(f"literal of type {type_str!r}")


# ----------------------------------------------------------------------- functions

def infix(op: str) -> Callable:
    return lambda a, ctx: "(" + f" {op} ".join(a) + ")"


def postfix(test: str) -> Callable:
    return lambda a, ctx: f"({a[0]} {test})"


def call(name: str) -> Callable:
    return lambda a, ctx: f"{name}({', '.join(a)})"


def _arity(n_min: int, n_max: int, fn: Callable) -> Callable:
    def wrapped(a, ctx):
        if not n_min <= len(a) <= n_max:
            raise Untranslatable(f"{ctx.name}: {len(a)} argument(s) not supported")
        return fn(a, ctx)
    return wrapped


def _divide(a, ctx):
    if ctx.arg_base(0) in INT_TYPES or ctx.arg_base(0) in UINT_TYPES:
        return f"({a[0]} // {a[1]})"
    return f"({a[0]} / {a[1]})"


def _shift_right_unsigned(a, ctx):
    width = {"i8": 8, "i16": 16, "i32": 32, "i64": 64}.get(ctx.arg_base(0))
    if width is None:
        raise Untranslatable("shift_right_unsigned on a non-integer type")
    mask = (1 << width) - 1
    signed = INT_TYPES[ctx.arg_base(0)]
    # reinterpret the bits as unsigned, shift, then reinterpret back
    unsigned = f"(CAST({a[0]} AS HUGEINT) & {mask})"
    shifted = f"({unsigned} >> CAST({a[1]} AS INTEGER))"
    return (f"CAST(CASE WHEN {shifted} >= {1 << (width - 1)} THEN {shifted} - {1 << width} "
            f"ELSE {shifted} END AS {signed})")


def _xor_bool(a, ctx):
    return f"(({a[0]} OR {a[1]}) AND NOT ({a[0]} AND {a[1]}))"


def _xor(a, ctx):
    return _xor_bool(a, ctx) if ctx.arg_base(0) == "bool" else f"xor({a[0]}, {a[1]})"


def _concat(a, ctx):
    mode = ctx.options.get("null_handling", "").upper()
    if mode == "IGNORE_NULLS":
        return f"concat({', '.join(a)})"
    return "(" + " || ".join(a) + ")"


def _like(a, ctx):
    op = "ILIKE" if ctx.options.get("case_sensitivity", "").upper().startswith("CASE_INSENSITIVE") else "LIKE"
    return f"({a[0]} {op} {a[1]})"


def _case_aware(name):
    def fn(a, ctx):
        if ctx.options.get("case_sensitivity", "").upper().startswith("CASE_INSENSITIVE"):
            a = [f"lower({x})" for x in a]
        return f"{name}({', '.join(a)})"
    return fn


def _const_int(ctx, i, default=None):
    node = ctx.raw_args[i] if i < len(ctx.raw_args) else None
    if node is None:
        return default
    if isinstance(node, CaseLiteral) and base_type(node.type) in INT_TYPES and not is_null_value(node.value):
        return int(node.value)
    raise Untranslatable(f"{ctx.name}: argument {i + 1} must be an integer literal")


def _regexp_replace(a, ctx):
    position, occurrence = _const_int(ctx, 3, 1), _const_int(ctx, 4, 0)
    if position != 1:
        raise Untranslatable("regexp_replace with position != 1")
    if occurrence == 0:
        return f"regexp_replace({a[0]}, {a[1]}, {a[2]}, 'g')"
    if occurrence == 1:
        return f"regexp_replace({a[0]}, {a[1]}, {a[2]})"
    raise Untranslatable("regexp_replace with occurrence > 1")


def _regexp_count(a, ctx):
    position = _const_int(ctx, 2, 1)
    subject = a[0] if position == 1 else f"substring({a[0]}, {position})"
    return f"len(regexp_extract_all({subject}, {a[1]}))"


def _regexp_match(a, ctx):
    position, occurrence, group = _const_int(ctx, 2, 1), _const_int(ctx, 3, 1), _const_int(ctx, 4, 0)
    subject = a[0] if position == 1 else f"substring({a[0]}, {position})"
    return f"regexp_extract_all({subject}, {a[1]}, {group})[{occurrence}]"


EXTRACT = {
    "YEAR": "year({x})", "ISO_YEAR": "isoyear({x})", "QUARTER": "quarter({x})",
    "MONTH": "month({x})", "DAY": "day({x})", "DAY_OF_YEAR": "dayofyear({x})",
    "MONDAY_DAY_OF_WEEK": "isodow({x})", "SUNDAY_DAY_OF_WEEK": "(dayofweek({x}) + 1)",
    "ISO_WEEK": "week({x})", "HOUR": "hour({x})", "MINUTE": "minute({x})", "SECOND": "second({x})",
    # Substrait: milliseconds since the last full second, microseconds since the last
    # full millisecond; DuckDB's millisecond()/microsecond() count from the minute.
    "MILLISECOND": "(millisecond({x}) % 1000)", "MICROSECOND": "(microsecond({x}) % 1000)",
    "SUBSECOND": "(microsecond({x}) % 1000000)",
    "UNIX_TIME": "CAST(floor(epoch({x})) AS BIGINT)",
}


INDEXED_COMPONENTS = {"QUARTER", "MONTH", "DAY", "DAY_OF_YEAR", "MONDAY_DAY_OF_WEEK",
                      "SUNDAY_DAY_OF_WEEK", "MONDAY_WEEK", "SUNDAY_WEEK", "ISO_WEEK", "US_WEEK"}


def _enum_value(node):
    if isinstance(node, CaseLiteral) and node.type == "enum":
        return str(node.value).upper()
    return None


def _extract(a, ctx):
    """extract(component, [indexing,] x [, timezone]) - indexing is required for the
    calendar components (QUARTER, MONTH, DAY, ...) and not allowed for the others."""
    comp = _enum_value(ctx.raw_args[0])
    if comp is None:
        raise Untranslatable("extract without an enum component")
    template = EXTRACT.get(comp)
    if template is None:
        raise Untranslatable(f"extract component {comp}")
    rest, sql_rest = ctx.raw_args[1:], a[1:]
    offset = ""
    if comp in INDEXED_COMPONENTS:
        indexing = _enum_value(rest[0]) if rest else None
        if indexing not in ("ONE", "ZERO"):
            raise Untranslatable(f"extract({comp}) needs an indexing argument (ONE/ZERO)")
        offset = " - 1" if indexing == "ZERO" else ""
        rest, sql_rest = rest[1:], sql_rest[1:]
    if len(rest) != 1:
        raise Untranslatable("extract with a timezone argument")
    return f"CAST({template.format(x=sql_rest[0])}{offset} AS BIGINT)"


def _sort(a, ctx):
    direction = ctx.options.get("direction", "ASC_NULLS_LAST").upper()
    m = re.fullmatch(r"(ASC|DESC)_NULLS_(FIRST|LAST)", direction)
    if not m:
        raise Untranslatable(f"sort direction {direction}")
    return f"list_sort({a[0]}, '{m.group(1)}', 'NULLS {m.group(2)}')"


def _list_match(kind):
    def fn(a, ctx):
        mapped = f"list_transform({a[0]}, {a[1]})"
        if kind == "any":
            return (f"(CASE WHEN {a[0]} IS NULL THEN NULL "
                    f"WHEN len(list_filter({mapped}, lambda b: b)) > 0 THEN TRUE "
                    f"WHEN len(list_filter({mapped}, lambda b: b IS NULL)) > 0 THEN NULL ELSE FALSE END)")
        return (f"(CASE WHEN {a[0]} IS NULL THEN NULL "
                f"WHEN len(list_filter({mapped}, lambda b: NOT b)) > 0 THEN FALSE "
                f"WHEN len(list_filter({mapped}, lambda b: b IS NULL)) > 0 THEN NULL ELSE TRUE END)")
    return fn


MAX_RESULT_CHARS = 10_000_000


def _guard_length(ctx, index, what):
    """Refuse cases whose result would be too large to build (e.g. repeat('a', 2147483647))."""
    n = _const_int(ctx, index, None) if index < len(ctx.raw_args) and \
        isinstance(ctx.raw_args[index], CaseLiteral) and not is_null_value(ctx.raw_args[index].value) else None
    if n is not None and n > MAX_RESULT_CHARS:
        raise Untranslatable(f"{what} of {n} characters is too large to materialise")


def _pad(name):
    def fn(a, ctx):
        _guard_length(ctx, 1, f"{name} result")
        if len(a) == 2:  # 'characters' defaults to a single space
            a = a + ["' '"]
        return f"{name}({', '.join(a)})"
    return fn


def _repeat(a, ctx):
    _guard_length(ctx, 1, "repeat result")
    return f"repeat({', '.join(a)})"


def _logb(a, ctx):
    return f"log({a[1]}, {a[0]})"


def _factorial(a, ctx):
    return f"factorial(CAST({a[0]} AS INTEGER))"


SCALAR: Dict[str, Callable] = {
    # arithmetic
    "add": infix("+"), "subtract": infix("-"), "multiply": infix("*"),
    "divide": _arity(2, 2, _divide), "modulus": infix("%"),
    "negate": lambda a, ctx: f"(-{a[0]})", "abs": call("abs"), "sign": call("sign"),
    "sqrt": call("sqrt"), "exp": call("exp"), "power": call("pow"),
    "factorial": _factorial,
    "sin": call("sin"), "cos": call("cos"), "tan": call("tan"),
    "asin": call("asin"), "acos": call("acos"), "atan": call("atan"), "atan2": call("atan2"),
    "sinh": call("sinh"), "cosh": call("cosh"), "tanh": call("tanh"),
    "asinh": call("asinh"), "acosh": call("acosh"), "atanh": call("atanh"),
    "radians": call("radians"), "degrees": call("degrees"),
    "ln": call("ln"), "log10": call("log10"), "log2": call("log2"), "logb": _arity(2, 2, _logb),
    "round": _arity(2, 2, call("round")), "ceil": call("ceil"), "floor": call("floor"),
    "bitwise_and": infix("&"), "bitwise_or": infix("|"), "bitwise_xor": call("xor"),
    "bitwise_not": lambda a, ctx: f"(~{a[0]})",
    "shift_left": infix("<<"), "shift_right": infix(">>"),
    "shift_right_unsigned": _shift_right_unsigned,
    # comparison
    "equal": infix("="), "not_equal": infix("<>"),
    "lt": infix("<"), "gt": infix(">"), "lte": infix("<="), "gte": infix(">="),
    "between": lambda a, ctx: f"({a[0]} BETWEEN {a[1]} AND {a[2]})",
    "is_null": postfix("IS NULL"), "is_not_null": postfix("IS NOT NULL"),
    "is_true": postfix("IS TRUE"), "is_false": postfix("IS FALSE"),
    "is_not_true": postfix("IS NOT TRUE"), "is_not_false": postfix("IS NOT FALSE"),
    "is_not_distinct_from": lambda a, ctx: f"({a[0]} IS NOT DISTINCT FROM {a[1]})",
    "is_nan": call("isnan"), "is_finite": call("isfinite"), "is_infinite": call("isinf"),
    "coalesce": call("coalesce"), "nullif": call("nullif"),
    # boolean
    "and": infix("AND"), "or": infix("OR"), "not": lambda a, ctx: f"(NOT {a[0]})",
    "xor": _xor, "and_not": lambda a, ctx: f"({a[0]} AND NOT {a[1]})",
    # string
    "concat": _concat, "concat_ws": call("concat_ws"),
    "upper": call("upper"), "lower": call("lower"), "reverse": call("reverse"),
    "char_length": call("length"), "octet_length": call("strlen"), "bit_length": call("bit_length"),
    "substring": _arity(2, 3, call("substring")), "left": call("left"), "right": call("right"),
    "lpad": _arity(2, 3, _pad("lpad")), "rpad": _arity(2, 3, _pad("rpad")), "repeat": _repeat,
    "replace": call("replace"),
    "trim": call("trim"), "ltrim": call("ltrim"), "rtrim": call("rtrim"),
    "starts_with": _case_aware("starts_with"), "ends_with": _case_aware("ends_with"),
    "contains": _case_aware("contains"), "like": _like,
    "regexp_replace": _arity(3, 5, _regexp_replace),
    "regexp_count_substring": _arity(2, 3, _regexp_count),
    "regexp_match_substring": _arity(2, 5, _regexp_match),
    "regexp_string_split": _arity(2, 2, call("regexp_split_to_array")),
    "string_split": _arity(2, 2, call("string_split")),
    # datetime
    "extract": _extract, "add_intervals": infix("+"),
    # list
    "cardinality": call("len"), "sort": _sort,
    "filter": call("list_filter"), "transform": call("list_transform"),
    "any_match": _list_match("any"), "all_match": _list_match("all"),
}

AGGREGATE: Dict[str, Callable] = {
    "sum": call("sum"), "avg": call("avg"), "min": call("min"), "max": call("max"),
    "count": lambda a, ctx: f"count({', '.join(a) or '*'})",
    "approx_count_distinct": call("approx_count_distinct"),
    "bool_and": call("bool_and"), "bool_or": call("bool_or"),
    "string_agg": call("string_agg"),
}
DISTRIBUTION_AGGREGATES = {
    "std_dev": {"SAMPLE": "stddev_samp", "POPULATION": "stddev_pop"},
    "variance": {"SAMPLE": "var_samp", "POPULATION": "var_pop"},
}
WINDOW: Dict[str, Callable] = {
    "row_number": lambda a, ctx: "row_number()",
    "rank": lambda a, ctx: "rank()", "dense_rank": lambda a, ctx: "dense_rank()",
    "percent_rank": lambda a, ctx: "percent_rank()", "cume_dist": lambda a, ctx: "cume_dist()",
    "ntile": call("ntile"), "lag": call("lag"), "lead": call("lead"),
    "first_value": call("first_value"), "last_value": call("last_value"), "nth_value": call("nth_value"),
}


class _Ctx:
    def __init__(self, name, raw_args, options, lambda_types=None):
        self.name, self.raw_args, self.options = name, raw_args, options
        self.lambda_types = lambda_types or {}

    def arg_base(self, i):
        return base_type(node_type(self.raw_args[i], self.lambda_types)) if i < len(self.raw_args) else None


def node_type(node, lambda_types=None) -> str:
    """Best-effort Substrait type of an argument node."""
    if isinstance(node, CaseLiteral):
        return node.type
    if isinstance(node, Identifier):
        return (lambda_types or {}).get(node.name, "")
    if isinstance(node, FuncCallArg) and node.args:
        return node_type(node.args[0], lambda_types)
    if isinstance(node, AggregateArgument):
        return node.column_type or (node.scalar_value.type if node.scalar_value else "")
    return ""


def lambda_param_types(func_type: str) -> List[str]:
    """'func<i32->bool>' -> ['i32']; 'func<(i32,i64)->bool>' -> ['i32', 'i64']"""
    inner = split_type(func_type)[1]
    params = inner.rsplit("->", 1)[0].strip()
    if params.startswith("(") and params.endswith(")"):
        params = params[1:-1]
    return [p.strip() for p in params.split(",") if p.strip()]


class Translator:
    """Builds DuckDB SQL for one parsed test case."""

    def __init__(self, options: Optional[dict] = None):
        self.options = {k: str(v) for k, v in (options or {}).items()}

    # -- expressions -------------------------------------------------------
    def expr(self, node, lambda_types=None) -> str:
        lambda_types = lambda_types or {}
        if isinstance(node, CaseLiteral):
            return literal_sql(node.value, node.type)
        if isinstance(node, Identifier):
            if node.name not in lambda_types:
                raise Untranslatable(f"unbound identifier {node.name}")
            return f"lp_{node.name}"
        if isinstance(node, Lambda):
            types = lambda_param_types(node.type)
            if len(types) != len(node.params):
                raise Untranslatable("lambda parameter/type count mismatch")
            scope = {**lambda_types, **dict(zip(node.params, types))}
            params = ", ".join(f"lp_{p}" for p in node.params)
            return f"lambda {params}: {self.expr(node.body, scope)}"
        if isinstance(node, FuncCallArg):
            return self.scalar_call(node.func_name, node.args, {}, lambda_types)
        raise Untranslatable(f"argument {type(node).__name__}")

    def scalar_call(self, name, args, options, lambda_types=None) -> str:
        fn = SCALAR.get(name)
        if fn is None:
            raise Untranslatable(f"no DuckDB mapping for scalar function {name}")
        ctx = _Ctx(name, args, options, lambda_types)
        if name == "extract":
            sql_args = ["" if isinstance(a, CaseLiteral) and a.type == "enum" else self.expr(a, lambda_types)
                        for a in args]
        else:
            sql_args = [self.expr(a, lambda_types) for a in args]
        return fn(sql_args, ctx)

    # -- whole cases -------------------------------------------------------
    def scalar_case(self, tc) -> str:
        return "SELECT " + self.scalar_call(tc.func_name, tc.args, tc.options)

    def aggregate_case(self, tc) -> str:
        if tc.rows is None:
            return self._aggregate_over_list(tc)
        return self._aggregate_over_table(tc)

    def _aggregate_fn(self, tc, sql_args, raw_args):
        name = tc.func_name
        if name in DISTRIBUTION_AGGREGATES:
            enum = raw_args[0] if raw_args else None
            value = getattr(enum, "value", None) if isinstance(enum, CaseLiteral) and enum.type == "enum" else None
            sql_name = DISTRIBUTION_AGGREGATES[name].get(str(value).upper())
            if sql_name is None:
                raise Untranslatable(f"{name} needs a SAMPLE/POPULATION distribution argument")
            return f"{sql_name}({', '.join(sql_args[1:])})"
        fn = AGGREGATE.get(name)
        if fn is None:
            raise Untranslatable(f"no DuckDB mapping for aggregate function {name}")
        return fn(sql_args, _Ctx(name, raw_args, tc.options))

    def _aggregate_over_list(self, tc) -> str:
        if not tc.args or not isinstance(tc.args[0], CaseLiteral) or not isinstance(tc.args[0].value, list):
            raise Untranslatable("aggregate input is not a value list")
        values = tc.args[0]
        column = f"CAST({literal_sql(values.value, 'list<' + values.type + '>')} AS {sql_type(values.type)}[])"
        extra = [self.expr(a) for a in tc.args[1:]]
        call_sql = self._aggregate_fn(tc, ["x"] + extra, tc.args)
        return f"SELECT {call_sql} FROM (SELECT UNNEST({column}) AS x)"

    def _table(self, rows, columns, with_row_number=False):
        """(VALUES ...) AS t(col0, ...) for the given rows and {index: type}."""
        rows = [r for r in (rows or []) if r]  # '(())' is an empty table
        if not columns:
            columns = {}
        n_cols = max(columns) + 1 if columns else (len(rows[0]) if rows else 0)
        types = [columns.get(i) for i in range(n_cols)]
        if any(t is None for t in types) and rows:
            raise Untranslatable("table column without a declared type")
        names = (["rn"] if with_row_number else []) + [f"col{i}" for i in range(n_cols)]
        if not rows:
            cols = ([f"CAST(NULL AS BIGINT) AS rn"] if with_row_number else []) + \
                   [f"CAST(NULL AS {sql_type(t)}) AS col{i}" for i, t in enumerate(types)]
            return f"(SELECT {', '.join(cols) or '1 AS dummy'} WHERE FALSE) AS t"
        tuples = []
        for r, row in enumerate(rows):
            if len(row) != n_cols:
                raise Untranslatable("row width does not match the declared columns")
            vals = ([str(r + 1)] if with_row_number else []) + \
                   [literal_sql(v, types[i]) for i, v in enumerate(row)]
            tuples.append("(" + ", ".join(vals) + ")")
        return f"(VALUES {', '.join(tuples)}) AS t({', '.join(names)})"

    def _column_args(self, args):
        columns, sql_args = {}, []
        for a in args:
            if not isinstance(a, AggregateArgument):
                raise Untranslatable("unexpected argument in a table-based case")
            if a.column_name:
                m = re.fullmatch(r"col(\d+)", a.column_name)
                if not m:
                    raise Untranslatable(f"column {a.column_name}")
                idx = int(m.group(1))
                if a.column_type:
                    columns[idx] = a.column_type
                sql_args.append(f"col{idx}")
            elif a.scalar_value is not None:
                sv = a.scalar_value
                sql_args.append("" if sv.type == "enum" else literal_sql(sv.value, sv.type))
            else:
                raise Untranslatable("empty aggregate argument")
        return columns, sql_args

    def _aggregate_over_table(self, tc) -> str:
        columns, sql_args = self._column_args(tc.args)
        raw = [a.scalar_value if a.scalar_value is not None else a for a in tc.args]
        call_sql = self._aggregate_fn(tc, sql_args, raw)
        return f"SELECT {call_sql} FROM {self._table(tc.rows, columns)}"

    def window_case(self, tc, frame_types: Optional[Dict[int, str]] = None) -> str:
        columns, sql_args = self._column_args(tc.args)
        columns = {**(frame_types or {}), **columns}
        fn = WINDOW.get(tc.func_name)
        if fn is None:
            raise Untranslatable(f"no DuckDB mapping for window function {tc.func_name}")
        call_sql = fn(sql_args, _Ctx(tc.func_name, tc.args, tc.options))
        # tests/README.md: the frame's rows give the order, and the window frame spans
        # the whole partition
        inner = (f"SELECT rn, {call_sql} OVER (ORDER BY rn ROWS BETWEEN UNBOUNDED PRECEDING AND "
                 f"UNBOUNDED FOLLOWING) AS v FROM {self._table(tc.rows, columns, True)}")
        return f"SELECT coalesce(list(v ORDER BY rn), []) FROM ({inner})"

    # -- expected values ---------------------------------------------------
    @staticmethod
    def expected(result) -> str:
        if isinstance(result, FuncCallArg):
            raise Untranslatable("expected value is an expression")
        if not isinstance(result, CaseLiteral):
            raise Untranslatable(f"expected value {result!r}")
        if isinstance(result.value, list) and base_type(result.type) not in ("list",) + tuple(UINT_TYPES):
            # window results: one value per row, written as (v1, v2, ...)::T
            return "SELECT " + literal_sql(result.value, f"list<{result.type}>")
        return "SELECT " + literal_sql(result.value, result.type)


def split_types(text: str) -> List[str]:
    """'i32, dec<10, 2>, str' -> ['i32', 'dec<10, 2>', 'str'] (commas inside <> kept)."""
    parts, depth, cur = [], 0, []
    for ch in text:
        depth += ch == "<"
        depth -= ch == ">"
        if ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def frame_column_types(source_line_text: str) -> Dict[int, str]:
    """Column types from a window frame definition line ``DEFINE f1(i32, str) = ...``."""
    m = re.match(r"\s*DEFINE\s+\w+\s*\((.*?)\)\s*=", source_line_text)
    if not m:
        return {}
    return dict(enumerate(split_types(m.group(1))))
