"""Tests for Substrait Expression Mapper."""

import pytest
from substrait_compliance.expression_mapper import SubstraitExpressionMapper


def test_clean_types():
    assert "::VARCHAR" in SubstraitExpressionMapper.clean_types("'abc'::str")
    assert "::INTEGER" in SubstraitExpressionMapper.clean_types("42::i32")
    assert "::BIGINT" in SubstraitExpressionMapper.clean_types("100::i64")
    assert "::DOUBLE" in SubstraitExpressionMapper.clean_types("3.14::fp64")
    assert "::BOOLEAN" in SubstraitExpressionMapper.clean_types("true::bool")
    assert "::DECIMAL(38,0)" in SubstraitExpressionMapper.clean_types("10::dec<38, 0>")
    assert "'inf'::DOUBLE" in SubstraitExpressionMapper.clean_types("inf::fp64")
    assert "'-inf'::DOUBLE" in SubstraitExpressionMapper.clean_types("-inf::fp64")
    assert "'nan'::DOUBLE" in SubstraitExpressionMapper.clean_types("nan::fp64")


def test_binary_operators():
    res = SubstraitExpressionMapper.map_expression("equal(1::i8, 1::i8)")
    assert res == "(1::TINYINT = 1::TINYINT)"

    res = SubstraitExpressionMapper.map_expression("add(10::i32, 20::i32)")
    assert res == "(10::INTEGER + 20::INTEGER)"

    res = SubstraitExpressionMapper.map_expression("multiply(2::i32, 3::i32)")
    assert res == "(2::INTEGER * 3::INTEGER)"

    res = SubstraitExpressionMapper.map_expression("lt(5::i32, 10::i32)")
    assert res == "(5::INTEGER < 10::INTEGER)"


def test_nested_expressions():
    nested = "equal(add(1::i32, multiply(2::i32, 3::i32)), 7::i32)"
    res = SubstraitExpressionMapper.map_expression(nested)
    assert res == "((1::INTEGER + (2::INTEGER * 3::INTEGER)) = 7::INTEGER)"


def test_conditionals_and_ranges():
    between = "between(5::i32, 1::i32, 10::i32)"
    res = SubstraitExpressionMapper.map_expression(between)
    assert res == "(5::INTEGER BETWEEN 1::INTEGER AND 10::INTEGER)"

    cond = "case_when(true::bool, 1::i32, 0::i32)"
    res = SubstraitExpressionMapper.map_expression(cond)
    assert res == "(CASE WHEN true::BOOLEAN THEN 1::INTEGER ELSE 0::INTEGER END)"


def test_unary_and_predicates():
    res = SubstraitExpressionMapper.map_expression("negate(5::i32)")
    assert res == "(-(5::INTEGER))"

    res = SubstraitExpressionMapper.map_expression("not(false::bool)")
    assert res == "(NOT (false::BOOLEAN))"

    res = SubstraitExpressionMapper.map_expression("is_null(null::str)")
    assert res == "(null::VARCHAR IS NULL)"


def test_cast_and_struct():
    res = SubstraitExpressionMapper.map_expression("try_cast('42'::str, i32)")
    assert res == "TRY_CAST('42'::VARCHAR AS INTEGER)"

    struct_expr = "struct_construct('x', 10::i32, 'y', 20::i32)"
    res = SubstraitExpressionMapper.map_expression(struct_expr)
    assert 'struct_pack("x" := 10::INTEGER, "y" := 20::INTEGER)' in res


def test_array_operations():
    res = SubstraitExpressionMapper.map_expression("array_contains([1, 2, 3]::list<i32>, 2::i32)")
    assert "list_contains" in res
