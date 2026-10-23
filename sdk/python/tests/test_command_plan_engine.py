"""examples/leaderboard-adapter: an engine run through a command scores like a built-in one."""

import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))
sys.path.insert(0, str(REPO / "examples" / "leaderboard-adapter"))
pytest.importorskip("yaml")
pytest.importorskip("pyarrow")
pytest.importorskip("datafusion")

import run_plan_compliance as rpc  # noqa: E402
from command_plan_engine import CommandPlanEngine  # noqa: E402

SUITE = REPO / "test-suites" / "tpch"


class ViaCommand(CommandPlanEngine):
    name, version = "DataFusion-via-command", "test"
    command = [sys.executable, str(REPO / "examples" / "leaderboard-adapter" / "datafusion_command.py")]


def run(engine, query):
    case = {"q06": 5, "q03": 2}[query]
    meta = rpc.yaml.safe_load((SUITE / "metadata.yaml").read_text())
    tc = meta["testCases"][case]
    assert tc["id"] == query
    plan = rpc.read_plan_json(SUITE / tc["planJson"])
    files = {t["name"].lower(): SUITE / t["file"] for t in tc["inputTables"]}
    tables = {n: rpc.load_input_table(files[n.lower()], cols) for n, cols in rpc.named_table_schemas(plan).items()}
    return engine.execute((SUITE / tc["planBinary"]).read_bytes(), tables), tc


def test_command_result_matches_the_built_in_engine():
    via, tc = run(ViaCommand(), "q06")
    built_in, _ = run(rpc.DataFusionPlanEngine(), "q06")
    assert via == built_in
    expected = rpc.load_expected(SUITE / tc["expectedOutput"])
    assert rpc.describe_mismatch(expected, via, rpc.declared_scales(rpc.read_plan_json(SUITE / tc["planJson"]))) is None


def test_command_failure_becomes_the_error_message():
    with pytest.raises(RuntimeError, match="Unsupported literal_type"):
        run(ViaCommand(), "q03")


def test_unset_command_is_an_error():
    with pytest.raises(RuntimeError, match="command is not set"):
        CommandPlanEngine().execute(b"", {})
