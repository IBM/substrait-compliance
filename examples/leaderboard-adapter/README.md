# Leaderboard adapter for engines without Python bindings

The leaderboard's runners are Python ([submissions/README.md](../../submissions/README.md)).
An engine reachable from Python plugs in directly; any other engine can be run through a
command. [`command_plan_engine.py`](command_plan_engine.py) is that adapter for the
**TPC-H** score: it calls your command once per plan and scores the result like
the built-in engines.

## The command

```
<command> --plan <plan.bin> --table <NAME>=<file.arrow> [--table ...] --output <result.arrow>
```

- `--plan`: the binary Substrait plan (`substrait.proto.Plan`).
- `--table`: each input table the plan reads, by the name the plan uses, as an Arrow IPC
  file (the Arrow file format, as written by `pyarrow.ipc.new_file`).
- `--output`: write the result here as an Arrow IPC file, with the plan's column names.
- Exit 0 on success. On failure exit non-zero; the last line of stderr becomes the case's
  failure reason in the report.

In Rust this is a short program with the `arrow` crate's IPC reader and writer around
your engine's Substrait consumer; in C++, Arrow's `arrow::ipc` does the same.

## Use it

```python
# my_engine.py, on PYTHONPATH with this folder
from command_plan_engine import CommandPlanEngine

class MyEngine(CommandPlanEngine):
    name = "MyEngine"
    version = "1.4.0"
    command = ["/path/to/my-engine-substrait"]
```

```bash
export PYTHONPATH=examples/leaderboard-adapter:/path/to/my_engine_dir:$PYTHONPATH
python scripts/run_plan_compliance.py --engine my_engine:MyEngine \
    --test-suite test-suites/tpch/metadata.yaml --output suite-reports/tpch.json
```

## Try it without building anything

[`datafusion_command.py`](datafusion_command.py) is such a command written with DataFusion.
Through the adapter it gives exactly the built-in DataFusion engine's results
(`sdk/python/tests/test_command_plan_engine.py` checks this):

```python
import sys
from command_plan_engine import CommandPlanEngine

class DataFusionViaCommand(CommandPlanEngine):
    name, version = "DataFusion", "55.0.0"
    command = [sys.executable, "examples/leaderboard-adapter/datafusion_command.py"]
```

## Function cases

The **Functions** score needs more than plans: the function runner also creates input
tables and evaluates expected values with the engine's SQL (see "Your engine class" in
[submissions/README.md](../../submissions/README.md)). A command-based function adapter is
possible on the same pattern, but start the engine once and keep it running: the suite runs
about 1,300 plans.
