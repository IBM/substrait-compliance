"""
Read Substrait function test-case files (the upstream ``.test`` format).

This wraps the vendored upstream parser (``substrait_compliance.testcase_parser``)
and adds what a runner needs that the upstream visitor drops:

* ``TestCase.line`` - the 1-based line of the case in its file;
* lambda arguments as :class:`Lambda` (parameters + body) instead of the string
  ``"lambda"``;
* bare identifiers inside lambda bodies as :class:`Identifier`.

The upstream format is documented in
https://github.com/substrait-io/substrait/blob/main/tests/README.md
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Union

from antlr4 import CommonTokenStream, FileStream, InputStream

from .testcase_parser.antlr_parser.FuncTestCaseLexer import FuncTestCaseLexer
from .testcase_parser.antlr_parser.FuncTestCaseParser import FuncTestCaseParser
from .testcase_parser.case_file_parser import ParseErrorListener
from .testcase_parser.nodes import (  # re-exported for callers
    AggregateArgument,
    CaseLiteral,
    FuncCallArg,
    SubstraitError,
    TestCase,
    TestFile,
)
from .testcase_parser.visitor import ParseError, TestCaseVisitor

__all__ = [
    "AggregateArgument", "CaseLiteral", "FuncCallArg", "Identifier", "Lambda",
    "ParseError", "SubstraitError", "TestCase", "TestFile",
    "is_upstream_format", "parse_case_file", "parse_case_text",
]


@dataclass
class Identifier:
    """A bare name, e.g. a lambda parameter used inside the lambda body."""
    name: str


@dataclass
class Lambda:
    """``(x -> gt(x, 2::i32))::func<i32 -> bool>``"""
    params: List[str]
    body: FuncCallArg
    type: str
    nullable: bool = False
    value: str = field(default="lambda")  # what the upstream visitor reports


class _Visitor(TestCaseVisitor):
    def _with_line(self, testcase, ctx):
        testcase.line = ctx.start.line
        return testcase

    def visitTestCase(self, ctx):
        return self._with_line(super().visitTestCase(ctx), ctx)

    def visitAggFuncTestCase(self, ctx):
        return self._with_line(super().visitAggFuncTestCase(ctx), ctx)

    def visitWindowFuncTestCase(self, ctx):
        return self._with_line(super().visitWindowFuncTestCase(ctx), ctx)

    def visitArgument(self, ctx):
        if ctx.Identifier() is not None:
            return Identifier(ctx.Identifier().getText())
        return super().visitArgument(ctx)

    def visitLambdaArg(self, ctx):
        lam = ctx.literalLambda()
        params_ctx = lam.lambdaParameters()
        params = [p.getText() for p in params_ctx.Identifier()] if hasattr(params_ctx.Identifier(), "__iter__") \
            else [params_ctx.Identifier().getText()]
        body_ctx = lam.lambdaBody()
        body = FuncCallArg(func_name=body_ctx.identifier().getText(),
                           args=self.visitArguments(body_ctx.arguments()))
        return Lambda(params=params, body=body, type=ctx.funcType().getText(),
                      nullable=ctx.funcType().isnull is not None)


def _parse(stream, file_path) -> TestFile:
    parser = FuncTestCaseParser(CommonTokenStream(FuncTestCaseLexer(stream)))
    errors = ParseErrorListener()
    parser.removeErrorListeners()
    parser.addErrorListener(errors)
    tree = parser.doc()
    if parser.getNumberOfSyntaxErrors() > 0:
        raise ParseError(f"{file_path}: syntax errors: {errors.errors}")
    return _Visitor(str(file_path)).visit(tree)


def parse_case_file(path: Union[str, Path]) -> TestFile:
    """Parse one ``.test`` file; raises ParseError on any syntax error."""
    return _parse(FileStream(str(path), "UTF-8"), path)


def parse_case_text(text: str, file_path: str = "<text>") -> TestFile:
    return _parse(InputStream(text), file_path)


def is_upstream_format(path: Union[str, Path]) -> bool:
    """True for files whose include header uses the current URN form
    (``### SUBSTRAIT_INCLUDE: extension:...``); older files use a quoted path."""
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("### SUBSTRAIT_INCLUDE:"):
                return line.split(":", 1)[1].strip().startswith("extension:")
    return False
