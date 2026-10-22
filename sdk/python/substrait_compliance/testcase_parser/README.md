# Vendored Substrait function test-case parser

This package is a copy of the parser the Substrait project uses for its own
function test cases (`tests/cases/**/*.test`), so that this repository reads those
files exactly the way upstream does.

| | |
|---|---|
| Source | https://github.com/substrait-io/substrait/tree/v0.107.0/tests/parser |
| Version | Substrait v0.107.0 (commit `edb8d48`) |
| License | Apache-2.0 (same as this repository); upstream copyright headers are kept |
| Runtime | `antlr4-python3-runtime==4.13.2` (the ANTLR version the parser was generated with) |

Grammar: [`FuncTestCaseLexer.g4` / `FuncTestCaseParser.g4`](https://github.com/substrait-io/substrait/tree/v0.107.0/grammar).
Format reference: [tests/README.md](https://github.com/substrait-io/substrait/blob/v0.107.0/tests/README.md).

## Local changes

Only these, so a re-sync stays a copy:

1. Imports changed from `tests.parser...` to package-relative imports.
2. `nodes.py`: `from __future__ import annotations`, so the `X | None` annotations
   also load on Python 3.9 (the SDK's minimum version).
3. `antlr_parser/__init__.py` added (empty) so the generated code is a package.

## Updating

Copy `tests/parser/{__init__,case_file_parser,nodes,visitor}.py` and
`tests/parser/antlr_parser/*.py` from the new upstream tag, re-apply the three
changes above, update the version in this file, and match the ANTLR runtime pin to
the `# Generated ... by ANTLR x.y.z` header of `antlr_parser/FuncTestCaseParser.py`.

Extensions this repository needs (source line numbers, lambda bodies) live in
`substrait_compliance/function_cases.py`, which subclasses the visitor, so this
copy does not have to change.
