# DataFusion Python Compliance Example

Example implementation showing how to integrate Apache DataFusion with the
Substrait compliance framework using Python.

## Prerequisites

- Python 3.8 or higher
- The Substrait Compliance Python SDK (`sdk/python`)

## Quick Start

```bash
# 1. Install the SDK (no pip package needed — injected via sys.path)
cd ../../sdk/python && pip install -e . && cd -

# 2. Install DataFusion dependencies
pip install -r requirements.txt

# 3. Run the example
python datafusion_compliance.py
```

## Structure

```
datafusion-python/
├── datafusion_compliance.py   # ComplianceEngine implementation & runner
├── requirements.txt           # DataFusion runtime dependencies
└── README.md                  # This file
```

## Implementing the `ComplianceEngine` Interface

The Python SDK's
[`ComplianceEngine`](../../sdk/python/substrait_compliance/engine.py)
abstract base class has four methods to override plus two optional hooks:

```python
class ComplianceEngine(ABC):

    # Required ────────────────────────────────────────────────────────────────

    @abstractmethod
    def get_info(self) -> EngineInfo: ...

    @abstractmethod
    def get_capabilities(self) -> EngineCapabilities: ...

    @abstractmethod
    def execute_plan(self, plan_bytes: bytes,
                     input_data: Dict[str, TableData]) -> ComplianceResult: ...

    @abstractmethod
    def validate_plan(self, plan_bytes: bytes) -> ComplianceResult: ...

    # Optional hooks ──────────────────────────────────────────────────────────

    def initialize(self) -> None: ...   # called once before the suite runs
    def cleanup(self) -> None: ...      # called once after the suite finishes
```

### Execution model

- `initialize()` is called **once** before any test case runs.
- `execute_plan()` is called **once per test case**, sequentially.  
  The runner is single-threaded; engines may keep session state across calls.
- `cleanup()` is called **once** after all test cases finish (including on error).
- The runner is synchronous — no `async/await` required in the engine
  (contrast with the TypeScript SDK, whose interface is `async`).

### `DataFusionComplianceEngine` — how it works

[`DataFusionComplianceEngine`](datafusion_compliance.py) holds a single
`datafusion.SessionContext` and implements all four required methods:

| Method | What it does |
|--------|-------------|
| `get_info()` | Returns engine name, version, vendor |
| `get_capabilities()` | Declares supported relations and functions |
| `execute_plan(plan_bytes, input_data)` | Registers `input_data` as Arrow tables, deserialises the plan with `datafusion_substrait`, executes it, and converts the result to `TableData` |
| `validate_plan(plan_bytes)` | Returns `PASSED` if `plan_bytes` is non-empty |

The engine degrades gracefully: if `datafusion-substrait` is not installed,
`execute_plan` returns `TestStatus.ERROR` with a descriptive message rather
than raising an `ImportError` at construction time.

## Dependencies

| Package | Minimum version | Notes |
|---------|----------------|-------|
| `datafusion` | 34.0 | Python bindings for Apache DataFusion |
| `datafusion-substrait` | 0.10 | Substrait consumer for DataFusion |
| `pyarrow` | 14.0 | Arrow interchange format |

Install with:

```bash
pip install -r requirements.txt
```

## Notes

- The Python SDK is consumed directly from source (`sys.path.insert`) — no
  `pip install substrait-compliance` step is needed.
- `datafusion-substrait` >= 0.10 exposes
  `serde.deserialize_bytes` / `consumer.from_substrait_plan` as async
  functions; the example wraps them with `asyncio.run()`.
- If expected outputs are not present in the test suite, tests pass when
  execution does not raise — a limitation of the test-suite data, not the
  engine or SDK.

## License

Apache License 2.0
