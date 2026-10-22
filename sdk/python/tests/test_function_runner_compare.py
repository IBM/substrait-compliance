"""Tests for the result comparison of the current-format path in
scripts/run_function_compliance.py (no engine needed)."""

import sys
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

from run_function_compliance import upstream_values_match as match  # noqa: E402


def test_floats_have_no_absolute_slack():
    assert not match(3e-100, 1e-100, "fp64")
    assert not match(5e-324, 0.0, "fp64")
    assert match(0.30000000000000004, 0.3, "fp64")
    assert match(1.0000001, 1.0, "fp32")
    assert not match(1.001, 1.0, "fp32")


def test_integers_and_decimals_are_exact():
    assert not match(9223372036854775806, 9223372036854775807, "i64")
    assert match(9223372046854775810, 9223372046854775810, "i64")  # HUGEINT result
    assert match(64.0, 64, "i64")                                   # DOUBLE result, same value
    assert match(Decimal("2.50"), Decimal("2.5"), "dec<38,2>")
    assert not match(Decimal("2.51"), Decimal("2.5"), "dec<38,2>")


def test_booleans_only_match_booleans():
    assert not match(True, 1, "i64")
    assert not match(0, False, "bool")
    assert match(False, False, "bool")


def test_values_not_types_for_temporal_results():
    assert match(datetime(2025, 12, 31), date(2025, 12, 31), "date")
    assert not match(datetime(2025, 12, 31, 1), date(2025, 12, 31), "date")


def test_lists_and_nulls():
    assert match([None, 1, 2], [None, 1, 2], "list<i32?>")
    assert not match([1, 2], [1, 2, 3], "list<i32>")
    assert match(None, None, "i32?")
    assert not match(0, None, "i32?")
