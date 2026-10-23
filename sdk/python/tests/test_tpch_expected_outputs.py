"""The TPC-H expected outputs agree with the plans: same column names (engines return the
plan's names) and the same type family per column as the plan declares. A mismatch here
fails the query on every engine, whatever it returns (as q18 and q11 once did)."""

import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))
yaml = pytest.importorskip("yaml")

import run_plan_compliance as rpc  # noqa: E402

SUITE = REPO / "test-suites" / "tpch"
FAMILY = {"bool": "boolean", "i8": "numeric", "i16": "numeric", "i32": "numeric", "i64": "numeric",
          "fp32": "numeric", "fp64": "numeric", "decimal": "numeric", "string": "string",
          "varchar": "string", "fixedChar": "string", "date": "string", "timestamp": "string"}
CASES = [(meta, case) for meta in ("metadata.yaml", "metadata-substrait-0.81.yaml")
         for case in yaml.safe_load((SUITE / meta).read_text())["testCases"]]


@pytest.mark.parametrize("meta,case", CASES, ids=[f"{m}:{c['id']}" for m, c in CASES])
def test_expected_output_matches_plan(meta, case):
    plan = rpc.read_plan_json(SUITE / case["planJson"])
    root = next(r["root"] for r in plan["relations"] if "root" in r)
    names, types, _ = rpc.load_expected(SUITE / case["expectedOutput"])
    assert [n.lower() for n in names] == [n.lower() for n in root["names"]]
    declared = rpc.output_types(plan)
    assert len(declared) == len(names)
    for name, t, e in zip(names, declared, types):
        assert FAMILY[next(iter(t))] == rpc.normalize_type(e), f"{name}: plan {t}, expected {e}"


def test_declared_scales_of_q01():
    plan = rpc.read_plan_json(SUITE / "plans" / "q01.json")
    assert rpc.declared_scales(plan) == [None, None, 2, 2, 4, 6, 2, 2, 2, None]


def test_unknown_relation_gives_no_scales():
    assert rpc.declared_scales({"relations": [{"root": {"input": {"exchange": {}}}}]}) is None
