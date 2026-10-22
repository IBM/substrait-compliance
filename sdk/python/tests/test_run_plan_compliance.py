"""Tests for scripts/run_plan_compliance.py: expected-output loading and result
comparison must match the Java runner (YamlTestSuiteLoader / ComplianceRunner)."""

import sys
from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "scripts"))

import run_plan_compliance as rpc  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[3]


def test_expected_csv_typed_header(tmp_path):
    f = tmp_path / "e.csv"
    f.write_text("name:string|n:integer|x:double\n  a |3|1.5\nnull|| 2\n")
    names, types, rows = rpc.load_expected(f)
    assert names == ["name", "n", "x"] and types == ["string", "integer", "double"]
    assert rows == [["a", 3, 1.5], [None, None, 2.0]]


def test_comparison_follows_the_java_runner():
    exp = (["L_RETURNFLAG", "total"], ["string", "double"], [["A", 1.0], ["B", 2.0]])
    ok = (["l_returnflag", "TOTAL"], ["varchar", "decimal"], [["A", Decimal("1.0000000000001")], ["B", 2]])
    assert rpc.describe_mismatch(exp, ok) is None
    off = (["l_returnflag", "total"], ["varchar", "decimal"], [["A", Decimal("1.000001")], ["B", 2]])
    assert "row 0, column total" in rpc.describe_mismatch(exp, off)
    renamed = (["l_returnflag", "sum(x)"], ["varchar", "double"], [["A", 1.0], ["B", 2.0]])
    assert "column names" in rpc.describe_mismatch(exp, renamed)
    reordered = (["l_returnflag", "total"], ["varchar", "double"], [["B", 2.0], ["A", 1.0]])
    assert rpc.describe_mismatch(exp, reordered) is not None  # row order matters, as in Java


def test_dates_compare_as_text_and_types_by_family():
    exp = (["d"], ["string"], [["1995-03-15"]])
    assert rpc.describe_mismatch(exp, (["d"], ["date"], [[date(1995, 3, 15)]])) is None
    assert "column types" in rpc.describe_mismatch((["n"], ["integer"], [[1]]), (["n"], ["varchar"], [["1"]]))


@pytest.mark.skipif(not (REPO_ROOT / "test-suites/tpch/plans/q03.json").exists(), reason="TPC-H suite absent")
def test_plan_schemas_come_from_the_read_relations():
    tables = rpc.named_table_schemas(rpc.read_plan_json(REPO_ROOT / "test-suites/tpch/plans/q03.json"))
    assert set(tables) == {"CUSTOMER", "ORDERS", "LINEITEM"}
    columns = dict(tables["ORDERS"])
    assert "decimal" in columns["O_TOTALPRICE"] and "date" in columns["O_ORDERDATE"]
