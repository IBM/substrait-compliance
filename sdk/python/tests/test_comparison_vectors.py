"""Query-output comparison: the vectors shared with the Java runner
(sdk/conformance/comparison-vectors.json), checked against scripts/run_plan_compliance.py."""

import json
import sys
from decimal import Decimal
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))
pytest.importorskip("yaml")

import run_plan_compliance  # noqa: E402

VECTORS = json.loads((REPO / "sdk" / "conformance" / "comparison-vectors.json").read_text())["vectors"]


def decode(v):
    if isinstance(v, dict):
        return float(v["double"]) if "double" in v else Decimal(v["decimal"])
    return v


def table(t):
    return t["names"], t["types"], [[decode(v) for v in row] for row in t["rows"]]


@pytest.mark.parametrize("v", VECTORS, ids=[v["name"] for v in VECTORS])
def test_comparison(v):
    mismatch = run_plan_compliance.describe_mismatch(table(v["expected"]), table(v["actual"]),
                                                    v.get("declaredScales"))
    assert (mismatch is None) == v["match"], mismatch
