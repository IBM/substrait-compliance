"""
Build Substrait plans (protobuf, Substrait 0.107.0) for parsed function test cases.

Used by scripts/run_function_compliance.py --mode plan, which runs each plan through
the engine's own Substrait consumer (DuckDB: the `substrait` extension's
from_substrait(); DataFusion: datafusion.substrait), so the function call, its
literal arguments, options and enum arguments are all read from the plan.

Plan shapes (tables are created in the engine by the runner, from SQL VALUES, and
read here as NamedTables - so only data loading uses SQL, never the function):

* scalar     Root(Project(Read(one_row), [fn(literal, ...)]), names=[result])
* aggregate  Root(Aggregate(Read(t), measures=[fn(col0, literal, ...)]), names=[result])
* window     Root(Sort(Project(Read(t), [rn, fn(...) OVER (ORDER BY rn, ROWS unbounded)]),
                  by rn), emit the function column)

Functions are bound by URN and compound signature name (e.g. ``add:i8_i8``) from
the signature the case matches in the extension YAMLs (scripts/validate_function_tests.py).
Anything this module cannot express raises Unbuildable; the runner reports such
cases as skipped.
"""

from __future__ import annotations

import re
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
from typing import Dict, List, Optional, Tuple

from substrait import algebra_pb2 as alg
from substrait import plan_pb2
from substrait import type_pb2 as tp
from substrait.extensions import extensions_pb2 as ext

from substrait_compliance.function_cases import (
    AggregateArgument, CaseLiteral, FuncCallArg, Identifier, Lambda, SubstraitError,
)

import function_sql as fsql
import validate_function_tests as vft

SUBSTRAIT_VERSION = (0, 107, 0)
ONE_ROW_TABLE = "one_row"
DATA_TABLE = "t"

NULLABLE = tp.Type.NULLABILITY_NULLABLE
REQUIRED = tp.Type.NULLABILITY_REQUIRED


class Unbuildable(Exception):
    """The case uses something this builder cannot express as a plan."""


# ------------------------------------------------------------------------- types

def _nullability(type_str: str) -> int:
    base, _ = fsql.split_type(type_str)
    head = type_str.strip().split("<", 1)[0]
    return NULLABLE if head.endswith("?") else REQUIRED


def _params(type_str: str) -> List[str]:
    return [p for p in fsql.split_types(fsql.split_type(type_str)[1])] if "<" in type_str else []


def substrait_type(type_str: str, nullable: Optional[bool] = None) -> tp.Type:
    """Test-case type string (i32?, dec<38,2>, list<i32?>, pts<6>, ...) -> Type."""
    base = fsql.base_type(type_str)
    n = (NULLABLE if nullable else REQUIRED) if nullable is not None else _nullability(type_str)
    params = _params(type_str)
    simple = {"bool": "bool", "i8": "i8", "i16": "i16", "i32": "i32", "i64": "i64",
              "fp32": "fp32", "fp64": "fp64", "str": "string", "string": "string",
              "date": "date", "iyear": "interval_year", "vbin": "binary", "binary": "binary"}
    if base in simple:
        t = tp.Type()
        getattr(t, simple[base]).nullability = n
        return t
    if base in ("vchar", "varchar", "fchar", "fixedchar"):
        if not params:
            raise Unbuildable(f"{base} without a length")
        t = tp.Type()
        field = t.varchar if base in ("vchar", "varchar") else t.fixed_char
        field.length, field.nullability = int(params[0]), n
        return t
    if base in ("dec", "decimal"):
        if len(params) != 2:
            raise Unbuildable("decimal without precision/scale")
        t = tp.Type()
        t.decimal.precision, t.decimal.scale, t.decimal.nullability = int(params[0]), int(params[1]), n
        return t
    if base in ("pts", "ptstz", "pt", "iday"):
        precision = int(params[0]) if params else 6
        t = tp.Type()
        field = {"pts": t.precision_timestamp, "ptstz": t.precision_timestamp_tz,
                 "pt": t.precision_time, "iday": t.interval_day}[base]
        field.precision, field.nullability = precision, n
        return t
    if base == "list":
        t = tp.Type()
        t.list.type.CopyFrom(substrait_type(params[0]))
        t.list.nullability = n
        return t
    raise Unbuildable(f"type {type_str!r} is not built (yet)")


# ---------------------------------------------------------------------- literals

EPOCH = datetime(1970, 1, 1)


def _scaled(seconds_fraction: str, precision: int) -> int:
    digits = (seconds_fraction or "").ljust(precision, "0")[:precision]
    return int(digits or 0)


def _timestamp_value(text: str, precision: int, with_tz: bool) -> int:
    m = re.fullmatch(r"(\d{4}-\d{2}-\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})?", text)
    if not m:
        raise Unbuildable(f"timestamp literal {text!r}")
    d = date.fromisoformat(m.group(1))
    dt = datetime(d.year, d.month, d.day, int(m.group(2)), int(m.group(3)), int(m.group(4)))
    if m.group(6) and m.group(6) != "Z":
        sign = 1 if m.group(6)[0] == "+" else -1
        hh, mm = m.group(6)[1:].split(":")
        dt -= sign * timedelta(hours=int(hh), minutes=int(mm))  # to UTC
    elif m.group(6) is None and with_tz:
        raise Unbuildable("ptstz literal without an offset")
    whole = (dt - EPOCH) // timedelta(seconds=1)
    return whole * 10 ** precision + _scaled(m.group(5), precision)


def _time_value(text: str, precision: int) -> int:
    m = re.fullmatch(r"(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?", text)
    if not m:
        raise Unbuildable(f"time literal {text!r}")
    whole = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3))
    return whole * 10 ** precision + _scaled(m.group(4), precision)


def _interval(text: str):
    m = fsql.ISO_DURATION.match(text.strip())
    if not m:
        raise Unbuildable(f"interval literal {text!r}")
    sign = -1 if m.group("sign") else 1
    g = {k: m.group(k) for k in ("y", "mo", "w", "d", "h", "mi", "s")}
    return sign, g


def _decimal_bytes(text: str, scale: int) -> bytes:
    """Unscaled 128-bit little-endian two's complement. Exact integer arithmetic: the
    default Decimal context (28 digits) would round 38-digit values."""
    sign, digits, exponent = Decimal(text).as_tuple()
    unscaled = int("".join(map(str, digits)) or "0")
    shift = exponent + scale
    if shift >= 0:
        unscaled *= 10 ** shift
    else:
        unscaled, rest = divmod(unscaled, 10 ** -shift)
        if rest:
            raise Unbuildable(f"{text} has more than {scale} fractional digits")
    return (-unscaled if sign else unscaled).to_bytes(16, "little", signed=True)


def literal(value, type_str: str) -> alg.Expression:
    """One test-case literal -> Expression(literal=...)."""
    lit = alg.Expression.Literal()
    base = fsql.base_type(type_str)
    nullable = _nullability(type_str) == NULLABLE
    if fsql.is_null_value(value):
        lit.null.CopyFrom(substrait_type(type_str, nullable=True))
        return alg.Expression(literal=lit)
    lit.nullable = nullable
    params = _params(type_str)
    text = value if not isinstance(value, str) else value.strip()
    if base == "list":
        elem = params[0]
        if not value:
            lit.empty_list.CopyFrom(substrait_type(type_str).list)
        else:
            for v in value:
                lit.list.values.append(literal(v, elem).literal)
        return alg.Expression(literal=lit)
    if isinstance(value, (list, dict)):
        raise Unbuildable(f"compound literal for {type_str}")
    if base in ("i8", "i16", "i32", "i64"):
        setattr(lit, base, int(text))
    elif base in ("fp32", "fp64"):
        setattr(lit, base, float(text))
    elif base == "bool":
        lit.boolean = text.lower() == "true"
    elif base in ("str", "string"):
        lit.string = fsql.unescape_string(text)
    elif base in ("vchar", "varchar"):
        lit.var_char.value, lit.var_char.length = fsql.unescape_string(text), int(params[0])
    elif base in ("fchar", "fixedchar"):
        lit.fixed_char = fsql.unescape_string(text)
    elif base in ("dec", "decimal"):
        p, s = int(params[0]), int(params[1])
        lit.decimal.value, lit.decimal.precision, lit.decimal.scale = _decimal_bytes(text, s), p, s
    elif base == "date":
        lit.date = (date.fromisoformat(text) - date(1970, 1, 1)).days
    elif base in ("pts", "ptstz"):
        precision = int(params[0]) if params else 6
        field = lit.precision_timestamp if base == "pts" else lit.precision_timestamp_tz
        field.precision, field.value = precision, _timestamp_value(text, precision, base == "ptstz")
    elif base == "pt":
        precision = int(params[0]) if params else 6
        lit.precision_time.precision, lit.precision_time.value = precision, _time_value(text, precision)
    elif base == "iday":
        precision = int(params[0]) if params else 6
        sign, g = _interval(text)
        if g["y"] or g["mo"]:
            raise Unbuildable("interval_day literal with years/months")
        days = int(g["d"] or 0) + 7 * int(g["w"] or 0)
        secs = Decimal(g["h"] or 0) * 3600 + Decimal(g["mi"] or 0) * 60 + Decimal(g["s"] or 0)
        whole = int(secs)
        frac = int((secs - whole) * (10 ** precision))
        lit.interval_day_to_second.days = sign * days
        lit.interval_day_to_second.seconds = sign * whole
        lit.interval_day_to_second.subseconds = sign * frac
        lit.interval_day_to_second.precision = precision
    elif base == "iyear":
        sign, g = _interval(text)
        if any(g[k] for k in ("w", "d", "h", "mi", "s")):
            raise Unbuildable("interval_year literal with days/time")
        lit.interval_year_to_month.years = sign * int(g["y"] or 0)
        lit.interval_year_to_month.months = sign * int(g["mo"] or 0)
    else:
        raise Unbuildable(f"literal of type {type_str!r} is not built (yet)")
    return alg.Expression(literal=lit)


def field_ref(index: int) -> alg.Expression:
    e = alg.Expression()
    e.selection.direct_reference.struct_field.field = index
    e.selection.root_reference.SetInParent()
    return e


# ------------------------------------------------------------ functions & plans

# YAML type name -> compound-signature short name (Substrait "Function Signature
# Compound Names")
SHORT = {"boolean": "bool", "i8": "i8", "i16": "i16", "i32": "i32", "i64": "i64", "fp32": "fp32",
         "fp64": "fp64", "string": "str", "binary": "vbin", "timestamp": "ts", "timestamp_tz": "tstz",
         "date": "date", "time": "time", "interval_year": "iyear", "interval_day": "iday",
         "interval_compound": "icompound", "uuid": "uuid", "fixedchar": "fchar", "varchar": "vchar",
         "fixedbinary": "fbin", "decimal": "dec", "precision_time": "pt", "precision_timestamp": "pts",
         "precision_timestamp_tz": "ptstz", "struct": "struct", "list": "list", "map": "map",
         "func": "func"}


def compound_name(sig: vft.Signature) -> str:
    parts = []
    for arg in sig.args:
        if "options" in arg:
            parts.append("req")
            continue
        value = str(arg.get("value", "")).strip()
        if value.startswith("u!"):
            parts.append(value.split("<")[0].rstrip("?"))
            continue
        base = re.split(r"[<?]", value, maxsplit=1)[0].strip().lower()
        parts.append(SHORT.get(base, base))
    return f"{sig.name}:{'_'.join(parts)}" if parts else sig.name


class PlanBuilder:
    def __init__(self, registry, test_file, kind: str):
        self.registry, self.test_file, self.kind = registry, test_file, kind
        self.urns: Dict[str, int] = {}
        self.functions: Dict[Tuple[str, str], int] = {}

    # -- extensions ----------------------------------------------------------
    def _anchor(self, urn: str, name: str) -> int:
        self.urns.setdefault(urn, len(self.urns) + 1)
        key = (urn, name)
        if key not in self.functions:
            self.functions[key] = len(self.functions) + 1
        return self.functions[key]

    def _signature(self, func_name: str, args, options, nested=False):
        """(urn, Signature) the call binds to."""
        urns = [self.test_file.include] + list(self.test_file.dependencies)
        kinds = {"scalar"} if nested or self.kind == "scalar" else vft.KINDS_FOR_FILE.get(self.kind, {self.kind})
        probe = type("Probe", (), {})()
        probe.args, probe.options, probe.rows, probe._aggregate = args, options, None, self.kind == "aggregate"
        typed = vft.case_arguments(probe)
        for urn in urns:
            for sig in self.registry.get(urn, {}).get(func_name, []):
                if sig.deprecated or sig.kind not in kinds:
                    continue
                if vft.match_signature(sig, typed, options) is None:
                    return urn, sig
        raise Unbuildable(f"no signature of {func_name} matches")

    # -- expressions ---------------------------------------------------------
    def _output_type(self, sig, args, result) -> tp.Type:
        if isinstance(result, CaseLiteral):
            t = result.type
            if isinstance(result.value, list) and fsql.base_type(t) != "list":
                return substrait_type(t)  # window results: one value per row
            return substrait_type(t)
        # <!ERROR>/<!UNDEFINED> or nested call: derive from the declared return
        declared = sig.returns.strip().splitlines()[-1].strip()
        short = vft.short_type(declared)
        if not short.startswith("any:"):
            simple = {"bool": "bool", "str": "str", "date": "date", "i8": "i8", "i16": "i16", "i32": "i32",
                      "i64": "i64", "fp32": "fp32", "fp64": "fp64"}
            if short in simple:
                return substrait_type(simple[short] + ("?" if "?" in declared else ""))
        for a in args:  # any1 / parameterised: same as the first typed argument
            t = fsql.node_type(a)
            if t and t != "enum":
                return substrait_type(t)
        raise Unbuildable(f"cannot derive the output type of {sig.name}")

    def _argument(self, node) -> alg.FunctionArgument:
        if isinstance(node, CaseLiteral) and node.type == "enum":
            return alg.FunctionArgument(enum=str(node.value))
        return alg.FunctionArgument(value=self.expression(node))

    def _options(self, options) -> List[alg.FunctionOption]:
        return [alg.FunctionOption(name=k, preference=[str(v)]) for k, v in options.items()]

    def scalar_call(self, func_name, args, options, result=None, nested=False) -> alg.Expression:
        urn, sig = self._signature(func_name, args, options, nested=nested)
        e = alg.Expression()
        f = e.scalar_function
        f.function_reference = self._anchor(urn, compound_name(sig))
        f.arguments.extend(self._argument(a) for a in args)
        f.options.extend(self._options(options))
        f.output_type.CopyFrom(self._output_type(sig, args, result))
        return e

    def expression(self, node) -> alg.Expression:
        if isinstance(node, CaseLiteral):
            if fsql.base_type(node.type).startswith("u!"):
                raise Unbuildable("user-defined type literals (u!...) are not built")
            return literal(node.value, node.type)
        if isinstance(node, FuncCallArg):
            return self.scalar_call(node.func_name, node.args, {}, nested=True)
        if isinstance(node, (Lambda, Identifier)):
            raise Unbuildable("lambda arguments are not built")
        raise Unbuildable(f"argument {type(node).__name__}")

    # -- relations -----------------------------------------------------------
    @staticmethod
    def _read(table: str, columns: List[Tuple[str, str]]) -> alg.Rel:
        rel = alg.Rel()
        r = rel.read
        r.named_table.names.append(table)
        r.base_schema.names.extend(name for name, _ in columns)
        r.base_schema.struct.nullability = REQUIRED
        for _, t in columns:
            r.base_schema.struct.types.append(substrait_type(t, nullable=True))
        return rel

    def _plan(self, root_rel: alg.Rel) -> bytes:
        plan = plan_pb2.Plan()
        plan.version.major_number, plan.version.minor_number, plan.version.patch_number = SUBSTRAIT_VERSION
        plan.version.producer = "substrait-compliance function plans"
        for urn, anchor in self.urns.items():
            plan.extension_urns.append(ext.SimpleExtensionURN(extension_urn_anchor=anchor, urn=urn))
        for (urn, name), anchor in self.functions.items():
            decl = ext.SimpleExtensionDeclaration()
            decl.extension_function.extension_urn_reference = self.urns[urn]
            decl.extension_function.function_anchor = anchor
            decl.extension_function.name = name
            plan.extensions.append(decl)
        rel = plan.relations.add()
        rel.root.input.CopyFrom(root_rel)
        rel.root.names.append("result")
        return plan.SerializeToString()

    def scalar_plan(self, tc) -> bytes:
        call = self.scalar_call(tc.func_name, tc.args, tc.options, tc.result)
        rel = alg.Rel()
        rel.project.input.CopyFrom(self._read(ONE_ROW_TABLE, [("dummy", "i32")]))
        rel.project.expressions.append(call)
        rel.project.common.emit.output_mapping.append(1)  # only the function column
        return self._plan(rel)

    def aggregate_plan(self, tc, columns: List[Tuple[str, str]]) -> bytes:
        """columns: the data table's (name, type); col0 holds the list values."""
        if tc.rows is None:
            values = tc.args[0]
            args = [AggregateArgument("col0", values.type, "", None)] + list(tc.args[1:])
        else:
            args = list(tc.args)
        urn, sig = self._signature(tc.func_name, args, tc.options)
        measure = alg.AggregateFunction()
        measure.function_reference = self._anchor(urn, compound_name(sig))
        for a in args:
            if isinstance(a, AggregateArgument) and a.column_name:
                measure.arguments.append(alg.FunctionArgument(value=field_ref(int(a.column_name[3:]))))
            else:
                node = a.scalar_value if isinstance(a, AggregateArgument) else a
                measure.arguments.append(self._argument(node))
        measure.options.extend(self._options(tc.options))
        measure.output_type.CopyFrom(self._output_type(sig, args, tc.result))
        measure.phase = alg.AGGREGATION_PHASE_INITIAL_TO_RESULT
        measure.invocation = alg.AggregateFunction.AGGREGATION_INVOCATION_ALL
        rel = alg.Rel()
        rel.aggregate.input.CopyFrom(self._read(DATA_TABLE, columns))
        rel.aggregate.measures.add().measure.CopyFrom(measure)
        return self._plan(rel)

    def window_plan(self, tc, columns: List[Tuple[str, str]]) -> bytes:
        """columns: (rn, i64) then the frame columns."""
        urn, sig = self._signature(tc.func_name, tc.args, tc.options)
        e = alg.Expression()
        w = e.window_function
        w.function_reference = self._anchor(urn, compound_name(sig))
        for a in tc.args:
            if isinstance(a, AggregateArgument) and a.column_name:
                w.arguments.append(alg.FunctionArgument(value=field_ref(int(a.column_name[3:]) + 1)))
            else:
                node = a.scalar_value if isinstance(a, AggregateArgument) else a
                w.arguments.append(self._argument(node))
        w.options.extend(self._options(tc.options))
        w.output_type.CopyFrom(self._output_type(sig, tc.args, tc.result))
        w.phase = alg.AGGREGATION_PHASE_INITIAL_TO_RESULT
        w.invocation = alg.AggregateFunction.AGGREGATION_INVOCATION_ALL
        sort = w.sorts.add()
        sort.expr.CopyFrom(field_ref(0))
        sort.direction = alg.SortField.SORT_DIRECTION_ASC_NULLS_LAST
        w.bounds_type = alg.Expression.WindowFunction.BOUNDS_TYPE_ROWS
        w.lower_bound.unbounded.SetInParent()
        w.upper_bound.unbounded.SetInParent()
        project = alg.Rel()
        project.project.input.CopyFrom(self._read(DATA_TABLE, columns))
        project.project.expressions.append(e)
        n_cols = len(columns)
        project.project.common.emit.output_mapping.extend([0, n_cols])  # rn, result
        sort_rel = alg.Rel()
        sort_rel.sort.input.CopyFrom(project)
        s = sort_rel.sort.sorts.add()
        s.expr.CopyFrom(field_ref(0))
        s.direction = alg.SortField.SORT_DIRECTION_ASC_NULLS_LAST
        sort_rel.sort.common.emit.output_mapping.append(1)
        return self._plan(sort_rel)
