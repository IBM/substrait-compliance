"""
An example command for command_plan_engine.py: runs a Substrait plan on DataFusion.
A real engine's command does the same in its own language.

    python datafusion_command.py --plan plan.bin --table LINEITEM=lineitem.arrow --output out.arrow
"""

import argparse
import sys


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--table", action="append", default=[], metavar="NAME=FILE")
    ap.add_argument("--output", required=True)
    args = ap.parse_args(argv)

    import datafusion
    import pyarrow as pa
    import pyarrow.ipc as ipc
    from datafusion import substrait as ss

    ctx = datafusion.SessionContext()
    for spec in args.table:
        name, _, path = spec.partition("=")
        with pa.memory_map(path) as source:
            table = ipc.open_file(source).read_all()
        ctx.register_record_batches(f'"{name}"', [table.to_batches()])
    try:
        plan = ss.Serde.deserialize_bytes(open(args.plan, "rb").read())
        logical = ss.Consumer.from_substrait_plan(ctx, plan)
        result = ctx.create_dataframe_from_logical_plan(logical).to_arrow_table()
    except Exception as exc:  # noqa: BLE001 - report the engine's error and fail
        print(str(exc).splitlines()[0] if str(exc) else type(exc).__name__, file=sys.stderr)
        return 1
    with ipc.new_file(args.output, result.schema) as writer:
        writer.write_table(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
