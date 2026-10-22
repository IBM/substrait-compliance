"""Tests for scripts/function_plans.py (test case -> Substrait plan protobuf)."""

import sys
from pathlib import Path

import pytest

pytest.importorskip("substrait.plan_pb2", reason="substrait-protobuf not installed")
pytest.importorskip("substrait_extensions", reason="substrait-extensions not installed")

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import function_plans as fp  # noqa: E402
import validate_function_tests as vft  # noqa: E402
from substrait import plan_pb2  # noqa: E402
from substrait_compliance.function_cases import parse_case_text  # noqa: E402

REGISTRY, _ = vft.load_registry(None)


def case(line, kind="SCALAR", include="functions_arithmetic"):
    tf = parse_case_text(f"### SUBSTRAIT_{kind}_TEST: v1.0\n"
                         f"### SUBSTRAIT_INCLUDE: extension:io.substrait:{include}\n\n# g: g\n{line}\n")
    return tf, tf.testcases[0]


def test_literal_encodings():
    assert fp.literal("2020-01-02", "date").literal.date == 18263
    ts = fp.literal("1970-01-01T00:00:01.5", "pts<6>").literal.precision_timestamp
    assert (ts.precision, ts.value) == (6, 1_500_000)
    tz = fp.literal("1970-01-01T01:00:00+01:00", "ptstz<3>").literal.precision_timestamp_tz
    assert tz.value == 0
    iday = fp.literal("P1DT2H", "iday").literal.interval_day_to_second
    assert (iday.days, iday.seconds) == (1, 7200)
    big = fp.literal("99999999999999999999999999999999999999", "dec<38, 0>").literal.decimal
    assert int.from_bytes(big.value, "little", signed=True) == 10 ** 38 - 1
    null = fp.literal(None, "i32?").literal
    assert null.HasField("null") and null.null.i32.nullability == fp.NULLABLE
    assert list(fp.literal(["1", "Null"], "list<i32?>").literal.list.values)[1].HasField("null")


def test_scalar_plan_binds_by_urn_and_compound_name():
    tf, tc = case("add(120::i8, 5::i8) [overflow:ERROR] = 125::i8")
    plan = plan_pb2.Plan.FromString(fp.PlanBuilder(REGISTRY, tf, "scalar").scalar_plan(tc))
    assert plan.version.minor_number == 107
    assert plan.extension_urns[0].urn == "extension:io.substrait:functions_arithmetic"
    assert plan.extensions[0].extension_function.name == "add:i8_i8"
    project = plan.relations[0].root.input.project
    assert project.input.read.named_table.names == [fp.ONE_ROW_TABLE]
    call = project.expressions[0].scalar_function
    assert [a.value.literal.i8 for a in call.arguments] == [120, 5]
    assert call.options[0].name == "overflow" and list(call.options[0].preference) == ["ERROR"]
    assert list(project.common.emit.output_mapping) == [1]


def test_enum_arguments_and_indexing():
    tf, tc = case("extract(MONTH::enum, ONE::enum, 2020-12-31::date) = 12::i64", include="functions_datetime")
    plan = plan_pb2.Plan.FromString(fp.PlanBuilder(REGISTRY, tf, "scalar").scalar_plan(tc))
    assert plan.extensions[0].extension_function.name == "extract:req_req_date"
    args = plan.relations[0].root.input.project.expressions[0].scalar_function.arguments
    assert (args[0].enum, args[1].enum) == ("MONTH", "ONE")


def test_aggregate_and_window_plans():
    tf, tc = case("((1.0), (3.0)) variance(POPULATION::enum, col0::fp64) = 1.0::fp64?", kind="AGGREGATE")
    plan = plan_pb2.Plan.FromString(
        fp.PlanBuilder(REGISTRY, tf, "aggregate").aggregate_plan(tc, [("col0", "fp64")]))
    measure = plan.relations[0].root.input.aggregate.measures[0].measure
    assert plan.extensions[0].extension_function.name == "variance:req_fp64"
    assert measure.arguments[0].enum == "POPULATION"
    assert measure.arguments[1].value.selection.direct_reference.struct_field.field == 0

    tf, tc = case("DEFINE f1(i32) = ((1), (2))\nlag(col0) OVER f1 = (Null, 1)::i32?", kind="WINDOW")
    plan = plan_pb2.Plan.FromString(
        fp.PlanBuilder(REGISTRY, tf, "window").window_plan(tc, [("rn", "i64"), ("col0", "i32")]))
    sort = plan.relations[0].root.input.sort
    w = sort.input.project.expressions[0].window_function
    assert w.bounds_type == w.BOUNDS_TYPE_ROWS
    assert w.lower_bound.HasField("unbounded") and w.upper_bound.HasField("unbounded")
    assert list(sort.common.emit.output_mapping) == [1]


def test_unbuildable_cases_are_reported():
    tf, tc = case("add(('1')::u!u8, ('2')::u!u8) = ('3')::u!u8", include="unsigned_integers")
    with pytest.raises(fp.Unbuildable):
        fp.PlanBuilder(REGISTRY, tf, "scalar").scalar_plan(tc)
