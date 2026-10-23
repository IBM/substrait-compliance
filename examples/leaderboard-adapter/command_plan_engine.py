"""
A leaderboard adapter for an engine that is not reachable from Python: it runs each query
plan through a command you provide (for example a small Rust or C++ program), so
scripts/run_plan_compliance.py can score the engine like any other.

Subclass it, then pass the subclass to the runner:

    # my_engine.py, on PYTHONPATH
    from command_plan_engine import CommandPlanEngine

    class MyEngine(CommandPlanEngine):
        name = "MyEngine"
        version = "1.4.0"
        command = ["my-engine-substrait"]

    python scripts/run_plan_compliance.py --engine my_engine:MyEngine \\
        --test-suite test-suites/tpch/metadata.yaml --output suite-reports/tpch.json

The command is called once per plan as

    <command> --plan <plan.bin> --table <NAME>=<NAME.arrow> [--table ...] --output <result.arrow>

* plan.bin is the binary Substrait plan (substrait.proto.Plan);
* each input table is an Arrow IPC file (pyarrow.ipc file format), named as the plan
  reads it;
* the command writes the result as an Arrow IPC file, with the plan's column names,
  and exits 0. On failure it exits non-zero; the last line of its stderr becomes the
  case's failure reason.

datafusion_command.py in this folder is such a command, written in Python with DataFusion,
so the adapter can be tried without building anything.
"""

import subprocess
import tempfile
from pathlib import Path
from typing import Dict, List


class CommandPlanEngine:
    name = "CommandEngine"
    version = "0"
    command: List[str] = []
    timeout_seconds = 300

    def execute(self, plan_bytes: bytes, tables: Dict[str, object]):
        import pyarrow as pa
        import pyarrow.ipc as ipc

        if not self.command:
            raise RuntimeError(f"{type(self).__name__}.command is not set")
        with tempfile.TemporaryDirectory(prefix="plan-") as tmp:
            work = Path(tmp)
            (work / "plan.bin").write_bytes(plan_bytes)
            args = [*self.command, "--plan", str(work / "plan.bin")]
            for i, (name, table) in enumerate(tables.items()):
                path = work / f"table{i}.arrow"
                with ipc.new_file(str(path), table.schema) as writer:
                    writer.write_table(table)
                args += ["--table", f"{name}={path}"]
            out = work / "result.arrow"
            args += ["--output", str(out)]
            done = subprocess.run(args, capture_output=True, text=True, timeout=self.timeout_seconds)
            if done.returncode != 0:
                lines = [ln for ln in done.stderr.splitlines() if ln.strip()]
                raise RuntimeError(lines[-1] if lines else f"command exited with {done.returncode}")
            with pa.memory_map(str(out)) as source:
                result = ipc.open_file(source).read_all()
        from run_plan_compliance import arrow_type_name  # same type names as the built-in engines
        names = result.schema.names
        types = [arrow_type_name(f.type) for f in result.schema]
        rows = [list(r.values()) for r in result.to_pylist()]
        return names, types, rows
