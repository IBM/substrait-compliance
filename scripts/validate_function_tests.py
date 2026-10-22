#!/usr/bin/env python3
"""
Check Substrait function test cases against the function definitions they test.

    scripts/validate_function_tests.py test-suites/functions-substrait-0.107

Every case must:
  * parse with the upstream test-case parser,
  * name a function defined in the file's SUBSTRAIT_INCLUDE (or a dependency) extension,
    of the kind the file tests (scalar / aggregate / window), and only call defined
    functions in nested calls and lambda bodies,
  * match one of that function's signatures: argument count, argument types, and
    for enum arguments (e.g. extract's component, std_dev's distribution) a value
    the signature lists,
  * use only options the signature declares, with declared values,
  * expect a result of the signature's return type (base type; parameters and
    nullability are not compared),
  * not rely on a signature the YAML marks `deprecated`,
  * write decimal literals with precision and scale (dec<P, S>) that fit them,
    and integer literals within their type's range.

The definitions come from the `substrait-extensions` PyPI package, whose version
must match the suite (--substrait-version, default read from the suite directory
name, e.g. functions-substrait-0.107 -> 0.107.x).

This is stricter than upstream's coverage check (tests/coverage in substrait-io/
substrait), which matches argument count and base types only. Exit status is 1 if
any case is invalid.
"""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "sdk" / "python"))

from substrait_compliance.function_cases import (  # noqa: E402
    AggregateArgument, CaseLiteral, FuncCallArg, Identifier, Lambda, SubstraitError, parse_case_file,
)

# YAML type name -> test-case short type name
LONG_TO_SHORT = {
    "boolean": "bool", "i8": "i8", "i16": "i16", "i32": "i32", "i64": "i64",
    "fp32": "fp32", "fp64": "fp64", "string": "str", "varchar": "vchar", "fixedchar": "fchar",
    "binary": "vbin", "fixedbinary": "fbin", "decimal": "dec", "date": "date",
    "precision_time": "pt", "precision_timestamp": "pts", "precision_timestamp_tz": "ptstz",
    "interval_year": "iyear", "interval_day": "iday", "interval_compound": "icompound",
    "uuid": "uuid", "list": "list", "map": "map", "struct": "struct", "func": "func",
}
STRING_FAMILY = {"str", "vchar", "fchar"}


def short_type(type_str: str) -> str:
    """Base type of a YAML or test-case type string, in test-case short form."""
    t = str(type_str).strip().split("\n")[-1].strip()
    if t.startswith("u!"):
        return t.split("<")[0].rstrip("?").lower()
    base = re.split(r"[<?]", t, maxsplit=1)[0].strip().lower()
    if base in LONG_TO_SHORT:
        return LONG_TO_SHORT[base]
    if base in LONG_TO_SHORT.values():
        return base
    if re.fullmatch(r"any\d*|t\d*|[a-z]", base):
        return "any:" + base
    return base


def list_element(type_str: str) -> str:
    t = str(type_str).strip()
    return t[t.find("<") + 1: t.rfind(">")] if "<" in t else ""


@dataclass
class Signature:
    name: str
    kind: str  # scalar / aggregate / window
    args: List[dict]
    options: Dict[str, List[str]]
    returns: str
    variadic: Optional[dict]
    deprecated: bool = False


def load_registry(version_prefix: Optional[str]):
    try:
        dist = importlib.metadata.distribution("substrait-extensions")
    except importlib.metadata.PackageNotFoundError:
        raise SystemExit("❌ pip install substrait-extensions==<suite version> first")
    if version_prefix and not dist.version.startswith(version_prefix):
        raise SystemExit(f"❌ substrait-extensions {dist.version} installed; this suite needs {version_prefix}.x")
    registry: Dict[str, Dict[str, List[Signature]]] = {}
    for f in dist.files:
        if not str(f).startswith("substrait_extensions/extensions/") or not str(f).endswith(".yaml"):
            continue
        doc = yaml.safe_load(Path(dist.locate_file(f)).read_text())
        urn = doc.get("urn") if isinstance(doc, dict) else None
        if not urn:
            continue
        functions = registry.setdefault(urn, {})
        for kind in ("scalar", "aggregate", "window"):
            for fn in doc.get(f"{kind}_functions", []) or []:
                for impl in fn.get("impls", []):
                    options = {k: [str(v).upper() for v in o.get("values", [])]
                               for k, o in (impl.get("options") or {}).items()}
                    functions.setdefault(fn["name"], []).append(Signature(
                        fn["name"], kind, impl.get("args", []) or [], options,
                        str(impl.get("return", "")), impl.get("variadic"), bool(impl.get("deprecated"))))
    return registry, dist.version


def file_kind(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"###\s*SUBSTRAIT_(\w+?)_TEST", line)
        if m:
            return m.group(1).lower()
    return "scalar"


def case_arguments(tc):
    """[(kind, value)] where kind is 'type' (a type string), 'enum' (enum value) or 'any'."""
    out = []
    for i, a in enumerate(tc.args):
        if isinstance(a, AggregateArgument):
            if a.column_name:
                out.append(("type", a.column_type))
            elif a.scalar_value is not None:
                sv = a.scalar_value
                out.append(("enum", sv.value) if sv.type == "enum" else ("type", sv.type))
            continue
        if isinstance(a, CaseLiteral):
            if a.type == "enum":
                out.append(("enum", a.value))
            elif tc.rows is None and i == 0 and isinstance(a.value, list) and getattr(tc, "_aggregate", False):
                out.append(("type", a.type))  # fn((1, 2)::i32): element type is the arg type
            else:
                out.append(("type", a.type))
        elif isinstance(a, Lambda):
            out.append(("type", a.type))
        else:  # nested call or identifier: type not checked
            out.append(("any", None))
    return out


def types_compatible(declared: str, actual: str, bindings: dict) -> bool:
    d, a = short_type(declared), short_type(actual)
    if d.startswith("any:"):
        bound = bindings.setdefault(d, a)
        return bound == a or (bound in STRING_FAMILY and a in STRING_FAMILY)
    if d == a:
        if d == "list":
            return types_compatible(list_element(declared), list_element(actual), bindings) \
                if list_element(declared) and list_element(actual) else True
        return True
    return False


def match_signature(sig: Signature, args, options) -> Optional[str]:
    """None if the case matches sig, else why not."""
    declared = list(sig.args)
    if sig.variadic:
        lo = sig.variadic.get("min", 1)
        hi = sig.variadic.get("max")
        n_fixed = len(declared) - 1
        if len(args) < n_fixed + lo or (hi is not None and len(args) > n_fixed + hi):
            return f"{len(args)} argument(s)"
        declared = declared[:-1] + [declared[-1]] * (len(args) - n_fixed)
    elif len(args) != len(declared):
        return f"{len(args)} argument(s), signature has {len(declared)}"
    bindings = {}
    for i, ((kind, value), decl) in enumerate(zip(args, declared)):
        if "options" in decl:  # enum argument
            if kind != "enum":
                return f"argument {i + 1} must be one of {decl['options']}"
            if str(value).upper() not in [str(o).upper() for o in decl["options"]]:
                return f"argument {i + 1}: {value} is not one of {decl['options']}"
        elif kind == "enum":
            return f"argument {i + 1} is an enum but the signature expects {decl.get('value')}"
        elif kind == "type" and not types_compatible(decl.get("value", ""), value, bindings):
            return f"argument {i + 1}: {value} does not match {decl.get('value')}"
    for key, value in options.items():
        if key not in sig.options:
            return f"option {key} is not declared"
        if str(value).upper() not in sig.options[key]:
            return f"option {key}:{value} is not one of {sig.options[key]}"
    return None


def literal_types(node):
    """Every type string used by a case's literals (arguments, results, nested calls)."""
    if isinstance(node, CaseLiteral):
        yield node.type
    elif isinstance(node, AggregateArgument):
        if node.column_type:
            yield node.column_type
        if node.scalar_value is not None:
            yield node.scalar_value.type
    elif isinstance(node, FuncCallArg):
        for a in node.args:
            yield from literal_types(a)
    elif isinstance(node, Lambda):
        yield from literal_types(node.body)


INT_RANGES = {"i8": 8, "i16": 16, "i32": 32, "i64": 64}


def literal_values(node):
    """(value, type) for every scalar literal value in a case (list elements included)."""
    if isinstance(node, CaseLiteral):
        values = node.value if isinstance(node.value, list) else [node.value]
        base = short_type(node.type)
        elem_type = list_element(node.type) if base == "list" else node.type
        for v in values:
            if not isinstance(v, (list, dict)):
                yield v, elem_type
    elif isinstance(node, AggregateArgument) and node.scalar_value is not None:
        yield from literal_values(node.scalar_value)
    elif isinstance(node, FuncCallArg):
        for a in node.args:
            yield from literal_values(a)


def check_types_well_formed(tc) -> Optional[str]:
    for node in list(tc.args) + [tc.result]:
        for t in literal_types(node):
            if re.fullmatch(r"(dec|decimal)\??", str(t).strip()):
                return f"decimal type without precision/scale: {t}"
        for value, t in literal_values(node):
            if short_type(t) == "dec" and value is not None and re.fullmatch(r"[+-]?\d+(\.\d+)?", str(value)):
                m = re.search(r"<\s*(\d+)\s*,\s*(\d+)\s*>", str(t))
                if m:
                    p, sc = int(m.group(1)), int(m.group(2))
                    whole, _, frac = str(value).lstrip("+-").partition(".")
                    frac = frac.rstrip("0")
                    if len(frac) > sc or len(whole.lstrip("0")) > p - sc:
                        return f"{value} does not fit {t}"
            bits = INT_RANGES.get(short_type(t))
            if bits and value is not None and re.fullmatch(r"[+-]?\d+", str(value)):
                if not -(1 << (bits - 1)) <= int(value) < (1 << (bits - 1)):
                    return f"{value} is out of range for {t}"
    # table rows of compact aggregate cases
    col_types = {}
    for a in tc.args:
        if isinstance(a, AggregateArgument) and a.column_name and a.column_type:
            m = re.fullmatch(r"col(\d+)", a.column_name)
            if m:
                col_types[int(m.group(1))] = a.column_type
    for row in tc.rows or []:
        for i, value in enumerate(row):
            bits = INT_RANGES.get(short_type(col_types.get(i, "")))
            if bits and re.fullmatch(r"[+-]?\d+", str(value)) and \
                    not -(1 << (bits - 1)) <= int(value) < (1 << (bits - 1)):
                return f"{value} is out of range for {col_types[i]}"
    return None


def nested_calls(node):
    """Function calls nested in arguments and lambda bodies."""
    if isinstance(node, FuncCallArg):
        yield node
        for a in node.args:
            yield from nested_calls(a)
    elif isinstance(node, Lambda):
        yield from nested_calls(node.body)


# which function kinds a file kind may call at its top level
KINDS_FOR_FILE = {"scalar": {"scalar"}, "aggregate": {"aggregate"}, "window": {"window", "aggregate"}}


def check_case(tc, test_file, kind, registry) -> Optional[str]:
    malformed = check_types_well_formed(tc)
    if malformed:
        return malformed
    urns = [test_file.include] + list(test_file.dependencies)
    for urn in urns:
        if urn not in registry:
            return f"unknown extension {urn}"
    for node in list(tc.args) + [tc.result]:
        for call in nested_calls(node):
            if not any(registry[u].get(call.func_name) for u in urns):
                return f"nested call {call.func_name} is not defined in {', '.join(urns)}"
    all_sigs = [s for urn in urns for s in registry[urn].get(tc.func_name, [])]
    sigs = [s for s in all_sigs if s.kind in KINDS_FOR_FILE.get(kind, {kind})]
    if all_sigs and not sigs:
        def a(word):
            return ("an " if word[0] in "aeiou" else "a ") + word
        return f"{tc.func_name} is {a(all_sigs[0].kind)} function, but this is {a(kind)} test file"
    if not sigs:
        return f"{tc.func_name} is not defined in {', '.join(urns)}"
    tc._aggregate = kind == "aggregate"
    args = case_arguments(tc)
    reasons = []
    for sig in sigs:
        why = match_signature(sig, args, tc.options)
        if why is None and sig.deprecated:
            why = "matches only a deprecated signature"
        if why:
            reasons.append(why)
            continue
        result = tc.result
        if isinstance(result, SubstraitError) or not isinstance(result, CaseLiteral):
            return None
        declared = short_type(sig.returns)
        if declared.startswith("any:") or declared == short_type(result.type):
            return None
        reasons.append(f"returns {sig.returns.strip().splitlines()[-1]}, test expects {result.type}")
    # report the most specific reason (later checks are more specific)
    best = sorted(set(reasons), key=lambda r: ("returns" not in r, "option" not in r, r))[0]
    return f"no matching signature for {tc.func_name}: {best}"


def validate(suite_dir: Path, registry):
    problems, n_cases = [], 0
    for path in sorted(suite_dir.rglob("*.test")):
        rel = str(path.relative_to(suite_dir))
        try:
            test_file = parse_case_file(path)
        except Exception as exc:  # noqa: BLE001 - report any parse failure
            problems.append({"file": rel, "line": 0, "problem": f"parse error: {exc}"})
            continue
        kind = file_kind(path)
        lines = path.read_text(encoding="utf-8").splitlines()
        n_cases += len(test_file.testcases)
        for tc in test_file.testcases:
            why = check_case(tc, test_file, kind, registry)
            if why:
                problems.append({"file": rel, "line": tc.line, "case": lines[tc.line - 1].strip(), "problem": why})
    return problems, n_cases


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("suite_dir", type=Path)
    ap.add_argument("--substrait-version", help="expected substrait-extensions version prefix, e.g. 0.107")
    ap.add_argument("--json", type=Path, help="also write the problems as JSON")
    args = ap.parse_args(argv)

    version = args.substrait_version
    if version is None:
        m = re.search(r"substrait-(\d+\.\d+)", str(args.suite_dir.resolve()))
        version = m.group(1) if m else None
    registry, installed = load_registry(version)
    problems, n_cases = validate(args.suite_dir, registry)
    if args.json:
        args.json.write_text(json.dumps(problems, indent=2, ensure_ascii=False))
    for p in problems:
        print(f"{p['file']}:{p['line']}: {p['problem']}\n    {p.get('case', '')}")
    print(f"{'❌' if problems else '✅'} {len(problems)} invalid of {n_cases} cases "
          f"(substrait-extensions {installed})")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
