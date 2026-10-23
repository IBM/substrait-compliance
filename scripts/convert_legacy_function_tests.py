#!/usr/bin/env python3
"""
Convert the legacy function tests (test-suites/functions) into the current
upstream format, keeping only cases that are valid and verified.

    scripts/convert_legacy_function_tests.py \\
        --legacy test-suites/functions \\
        --suite  test-suites/functions-substrait-0.107

Writes SUITE/extended/<category>/<function>.test and SUITE/extended/CONVERSION.md.

A legacy case is KEPT only if all of these hold:
  1. its function is a Substrait standard function (legacy-only names such as
     json_extract, st_area or array_concat are not converted),
  2. it converts to the current syntax and parses with the upstream parser,
  3. it matches a signature in the extension YAMLs (scripts/validate_function_tests.py),
  4. it is not already in SUITE/upstream, and does not contradict an upstream case,
  5. its expected value is reproduced independently: by DuckDB (through the same
     translator the compliance runner uses) or, when DuckDB does not reproduce it, by
     the result the function definitions give (scripts/function_reference.py: exact
     integer and 50-digit floating-point arithmetic, IEEE 754 special values, the
     option modes, string semantics).

Cases failing 1-4 are dropped. A case whose expected value the definitions contradict
is dropped too, and listed in CONVERSION.md with the correct result. Cases the
definitions do not decide and DuckDB does not reproduce are QUARANTINED: listed in
CONVERSION.md with the reason. Because DuckDB is one of the two verifiers, cases
DuckDB gets right are favoured; the leaderboard therefore scores upstream/ and
extended/ as separate suites.
"""

from __future__ import annotations

import argparse
import collections
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "sdk" / "python"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import function_reference as fref  # noqa: E402

from substrait_compliance.function_cases import CaseLiteral, SubstraitError, parse_case_file, parse_case_text  # noqa: E402
import validate_function_tests as vft  # noqa: E402
import run_function_compliance as rfc  # noqa: E402

# legacy function name -> standard name (only exact equivalents)
RENAMES = {"mod": "modulus", "stddev": "std_dev"}
# legacy std_dev/variance tests were written for the population statistics (the
# current signatures take the distribution as their first argument)
DISTRIBUTION_FUNCTIONS = {"std_dev", "variance"}

# legacy extract component strings -> Substrait enum values (exact equivalents only)
EXTRACT_COMPONENTS = {
    "YEAR": "YEAR", "ISOYEAR": "ISO_YEAR", "ISO_YEAR": "ISO_YEAR", "QUARTER": "QUARTER",
    "MONTH": "MONTH", "DAY": "DAY", "DOY": "DAY_OF_YEAR", "DAY_OF_YEAR": "DAY_OF_YEAR",
    "HOUR": "HOUR", "MINUTE": "MINUTE", "SECOND": "SECOND",
}

TYPE_RENAMES = [
    (r"::timestamp_tz\b|::tstz\b", "::ptstz<6>"), (r"::timestamp\b|::ts\b", "::pts<6>"),
    (r"::time\b", "::pt<6>"), (r"::string\b", "::str"), (r"::boolean\b", "::bool"),
    (r"::varchar\b(?!<)", "::str"), (r"::vchar\b(?![<?])", "::str"), (r"::vchar\?(?!<)", "::str?"),
    (r"::varchar\b", "::vchar"), (r"::fixedchar\b", "::fchar"), (r"::decimal\b", "::dec"),
    (r"::interval_day\b", "::iday"), (r"::interval_year\b", "::iyear"),
]
QUOTED = r"'(?:[^'\\]|\\.)*'"


class Reject(Exception):
    pass


def strip_comment(line: str) -> str:
    out, quote = [], False
    for i, ch in enumerate(line):
        if ch == "'" and (i == 0 or line[i - 1] != "\\"):
            quote = not quote
        if ch == "#" and not quote:
            break
        out.append(ch)
    return "".join(out).rstrip()


def convert_line(line: str) -> str:
    """One legacy case line -> current syntax (raises Reject when unsure)."""
    s = strip_comment(line)
    for q in re.findall(QUOTED, s):
        if re.search(r"\\[^'\\]", q):
            raise Reject("string uses \\n/\\t-style escapes, which the format does not define")
    if " OVER " in s:
        raise Reject("legacy window syntax (OVER ... WITH) has no automatic conversion")
    if re.search(r"\b(json|map|struct|geometry|imonth|u8|u16|u32|u64)\b", s):
        raise Reject("type with no Substrait standard equivalent")
    for pat, rep in TYPE_RENAMES:
        s = re.sub(pat, rep, s)
    # temporal / interval literals are unquoted in the current format
    s = re.sub(r"'(\d{4}-\d{2}-\d{2})'::date\b", r"\1::date", s)
    s = re.sub(r"'(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2}(?:\.\d+)?)'::pts<6>", r"\1T\2::pts<6>", s)
    s = re.sub(r"'(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2}(?:\.\d+)?)(Z|[+-]\d{2}:\d{2})'::ptstz<6>",
               lambda m: f"{m.group(1)}T{m.group(2)}{'+00:00' if m.group(3) == 'Z' else m.group(3)}::ptstz<6>", s)
    s = re.sub(r"'(\d{2}:\d{2}:\d{2}(?:\.\d+)?)'::pt<6>", r"\1::pt<6>", s)
    s = re.sub(r"'(-?P[0-9YMWDTHS.-]+)'::(iday|iyear)\b", r"\1::\2", s)
    # a leading minus negates every component: -P1DT2H -> P-1DT-2H
    s = re.sub(r"(?<![\w.])-P([0-9YMWDTHS.]+)::(iday|iyear)\b",
               lambda m: "P" + re.sub(r"(\d+(?:\.\d+)?)", r"-\1", m.group(1)) + "::" + m.group(2), s)
    if re.search(r"'[^']*'::(date|pts|ptstz|pt|iday|iyear)\b", s):
        raise Reject("temporal literal in an unsupported form")
    # special float spellings
    s = re.sub(r"\bNaN::", "nan::", s)
    s = re.sub(r"(-?)\bInfinity::", r"\1inf::", s)
    # null literals are typed nullable
    s = re.sub(r"\b[Nn][Uu][Ll][Ll]::([a-z][a-z0-9]*)(<[^>]*>)?(?![?a-z0-9])",
               lambda m: f"null::{m.group(1)}?{m.group(2) or ''}", s)
    return s


def to_table_form(s: str) -> str:
    """fn((v1, v2)::T, extra...) -> ((v1), (v2)) fn(col0::T, extra...): the current format
    writes multi-argument aggregates over a table."""
    from sync_upstream_function_tests import split_top_level_args
    m = re.match(r"^(\w+)\((.*)$", s)
    if not m:
        return s
    fn, rest = m.group(1), m.group(2)
    depth, end = 1, None
    for i, ch in enumerate(rest):
        depth += ch == "("
        depth -= ch == ")"
        if depth == 0:
            end = i
            break
    if end is None:
        raise Reject("unbalanced parentheses")
    args = split_top_level_args(rest[:end])
    if len(args) < 2:
        return s
    lm = re.fullmatch(r"\((.*)\)::(\S+)", args[0].strip())
    if not lm:
        raise Reject("aggregate input is not a typed value list")
    values = split_top_level_args(lm.group(1))
    rows = ", ".join(f"({v})" for v in values)
    return f"({rows}) {fn}(col0::{lm.group(2)}, {', '.join(args[1:])}{rest[end:]}"


def with_population_distribution(s: str) -> str:
    """std_dev((1, 2)::fp64) -> ((1), (2)) std_dev(POPULATION::enum, col0::fp64)"""
    table = to_table_form(re.sub(r"^(\w+)\((.*?)\)(\s*(\[[^\]]*\])?\s*=)",
                                 r"\1(\2, __DIST__)\3", s, count=1))
    if "__DIST__" not in table:
        raise Reject("std_dev/variance case in an unexpected form")
    return table.replace("(col0::", "(POPULATION::enum, col0::", 1).replace(", __DIST__", "", 1)


def convert_extract(s: str) -> str:
    m = re.match(r"^extract\('(\w+)'::str,\s*(.*)$", s)
    if not m:
        raise Reject("extract without a literal component")
    comp = EXTRACT_COMPONENTS.get(m.group(1).upper())
    if comp is None:
        raise Reject("extract component with no exact Substrait equivalent (EPOCH, WEEK, DOW, ...)")
    from function_sql import INDEXED_COMPONENTS
    indexing = "ONE::enum, " if comp in INDEXED_COMPONENTS else ""
    return f"extract({comp}::enum, {indexing}{m.group(2)}"


# ------------------------------------------------------------- arithmetic oracle

def pin_implied_option(text, tc, tf, kind, registry):
    """Pin the option a special-value expectation depends on, as upstream does.

    With no option given, Substrait leaves e.g. asin(1.1) to the engine (NaN or an
    error are both compliant), so a legacy case expecting NaN only tests something
    once [on_domain_error:NAN] is pinned; likewise ln(0) = -inf needs
    [on_log_zero:MINUS_INFINITY]. Returns the (possibly) updated text."""
    if tc.options or not isinstance(tc.result, CaseLiteral):
        return text
    if any(isinstance(a, CaseLiteral) and str(a.value).lower() == "nan" for a in tc.args):
        return text  # NaN in, NaN out: not a domain error
    value = str(tc.result.value).lower()
    sigs = [sg for urn in [tf.include] + list(tf.dependencies)
            for sg in registry.get(urn, {}).get(tc.func_name, [])
            if not sg.deprecated and vft.match_signature(sg, vft.case_arguments(tc), {}) is None]
    declared = {k: v for sg in sigs for k, v in sg.options.items()}
    option = None
    first = tc.args[0] if tc.args and isinstance(tc.args[0], CaseLiteral) else None
    is_zero = first is not None and re.fullmatch(r"-?0(\.0*)?", str(first.value) or "") is not None
    if value == "nan" and "NAN" in declared.get("on_domain_error", []):
        option = "on_domain_error:NAN"
    elif value == "-inf" and is_zero and "MINUS_INFINITY" in declared.get("on_log_zero", []):
        option = "on_log_zero:MINUS_INFINITY"
    elif value == "nan" and is_zero and "NAN" in declared.get("on_log_zero", []):
        option = "on_log_zero:NAN"
    if option is None:
        return text
    i = text.rfind(" = ")
    return f"{text[:i]} [{option}]{text[i:]}"


def spec_conflict(tc):
    """Reason to drop a case whose expectation contradicts the function definition text
    in a way the signature check cannot see, else None."""
    if tc.func_name == "rpad" and len(tc.args) >= 2 and all(isinstance(a, CaseLiteral) for a in tc.args[:2]):
        s, n = tc.args[0], tc.args[1]
        if s.value not in (None, "null") and n.value not in (None, "null") and str(n.value).lstrip("-").isdigit():
            if 0 < int(n.value) < len(str(s.value)) - 2:  # value includes its quotes
                return "rpad truncation: functions_string.yaml removes characters from the left"
    return None


# --------------------------------------------------------------------- pipeline

def norm(text: str) -> str:
    """Comparison key for a case line: whitespace, case and nullability markers outside
    string literals are ignored; string contents are compared exactly."""
    out, quote, i = [], False, 0
    while i < len(text):
        ch = text[i]
        if quote:
            out.append(ch)
            if ch == "\\" and i + 1 < len(text):
                out.append(text[i + 1])
                i += 1
            elif ch == "'":
                quote = False
        elif ch == "'":
            quote = True
            out.append(ch)
        elif not ch.isspace() and ch != "?":
            out.append(ch.lower())  # Null/null, NaN/nan: case matters only inside strings
        i += 1
    return "".join(out)


def call_part(text: str) -> str:
    i = text.rfind(" = ")
    return text[:i] if i > 0 else text


def legacy_cases(legacy_dir: Path):
    """(file, line_no, kind, group, text) for every legacy case line."""
    for path in sorted(legacy_dir.glob("*/*.test")):
        kind, group = "scalar", ""
        for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            line = raw.strip()
            m = re.match(r"###\s*SUBSTRAIT_(\w+?)_TEST", line)
            if m:
                kind = m.group(1).lower()
                continue
            if line.startswith("#"):
                if re.match(r"#\s*\w[\w ]*:", line):
                    group = line
                continue
            if line:
                yield path, n, kind, group, line


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--legacy", type=Path, required=True)
    ap.add_argument("--suite", type=Path, required=True, help="suite dir containing upstream/")
    args = ap.parse_args(argv)

    m = re.search(r"substrait-(\d+\.\d+)", str(args.suite.resolve()))
    registry, ext_version = vft.load_registry(m.group(1) if m else None)
    name_to_urns = collections.defaultdict(list)  # a name can be defined in several extensions
    for urn, fns in sorted(registry.items()):
        for name in fns:
            name_to_urns[name].append(urn)

    upstream_dir = args.suite / "upstream"
    upstream_full, upstream_calls, fn_category = set(), {}, {}
    for path in upstream_dir.rglob("*.test"):
        lines = path.read_text(encoding="utf-8").splitlines()
        tf = parse_case_file(path)
        for tc in tf.testcases:
            text = lines[tc.line - 1].strip()
            upstream_full.add(norm(text))
            upstream_calls[norm(call_part(text))] = text
            fn_category.setdefault((tc.func_name, tf.include), path.parent.name)

    engine = rfc.DuckDBEngine()
    kept = collections.defaultdict(list)      # (category, fn, kind, urn) -> [(group, text)]
    dropped = collections.Counter()
    dropped_examples = collections.defaultdict(list)
    quarantined = []
    contradicted = []  # (where, text, correct result, what DuckDB said)
    verified_by = collections.Counter()
    seen = set()

    def drop(reason, where, text):
        dropped[reason] += 1
        if len(dropped_examples[reason]) < 3:
            dropped_examples[reason].append(f"`{where}`: `{text[:110]}`")

    for path, n, kind, group, line in legacy_cases(args.legacy):
        where = f"{path.relative_to(args.legacy)}:{n}"
        fname = re.match(r"^(?:\(\(.*?\)\)\s*)?(\w+)\s*\(", line)
        if not fname:
            drop("not a function-call case", where, line)
            continue
        legacy_name = fname.group(1)
        fn = RENAMES.get(legacy_name, legacy_name)
        if kind == "window":
            drop("legacy window syntax (OVER ... WITH) has no automatic conversion", where, line)
            continue
        urns = name_to_urns.get(fn)
        if not urns:
            drop("not a Substrait standard function", where, line)
            continue
        try:
            text = convert_line(line)
            if legacy_name != fn:
                text = re.sub(rf"^{legacy_name}\(", f"{fn}(", text)
            if fn == "extract":
                text = convert_extract(text)
            if fn in DISTRIBUTION_FUNCTIONS:
                text = with_population_distribution(text)
            elif kind == "aggregate":
                text = to_table_form(text)
        except Reject as exc:
            drop(str(exc), where, line)
            continue

        # validate against each extension that defines the name; keep the first that matches
        problem, tc, tf, urn, header = "does not parse", None, None, None, None
        for candidate in urns:
            header = f"### SUBSTRAIT_{kind.upper()}_TEST: v1.0\n### SUBSTRAIT_INCLUDE: {candidate}\n\n# converted: case\n"
            try:
                tf = parse_case_text(header + text + "\n", where)
                tc = tf.testcases[0]
            except Exception:  # noqa: BLE001 - any parse failure rejects the case
                problem = "does not parse"
                break
            problem = vft.check_case(tc, tf, kind, registry)
            if not problem:
                pinned = pin_implied_option(text, tc, tf, kind, registry)
                if pinned != text:
                    tf = parse_case_text(header + pinned + "\n", where)
                    tc = tf.testcases[0]
                    problem = vft.check_case(tc, tf, kind, registry)
                    if not problem:
                        text = pinned
            if not problem:
                urn = candidate
                break
        if problem == "does not parse":
            drop("does not parse in the current format after conversion", where, text)
            continue
        if problem:
            drop("no matching signature in the " + ext_version + " YAMLs", where, f"{text}  — {problem}")
            continue
        conflict = spec_conflict(tc)
        if conflict:
            drop(conflict, where, text)
            continue
        key = norm(text)
        if key in upstream_full:
            drop("already covered by an identical upstream case", where, text)
            continue
        if norm(call_part(text)) in upstream_calls:
            drop("contradicts the upstream case for the same call", where,
                 f"{text}  vs upstream `{upstream_calls[norm(call_part(text))]}`")
            continue
        if key in seen:
            drop("duplicate of another legacy case", where, text)
            continue
        seen.add(key)

        status, reason = rfc.run_upstream_case(engine, kind, tc, [text] * (tc.line + 1))
        if status == rfc.PASSED:
            verified_by["DuckDB"] += 1
        else:
            ref = fref.reference(tc)
            if ref is not None and fref.matches(ref, tc.result):
                verified_by["the function definitions (exact computation)"] += 1
            elif ref is not None:
                drop("expected value contradicted by the function definitions", where, text)
                contradicted.append((where, text, fref.describe(ref), reason or ""))
                continue
            else:
                quarantined.append((where, text, reason or "not verified"))
                continue
        category = fn_category.get((fn, urn)) or urn.rsplit(":", 1)[1].replace("functions_", "")
        kept[(category, fn, kind, urn)].append((group or "# legacy: converted cases", text))

    out_dir = args.suite / "extended"
    if out_dir.exists():
        for p in out_dir.rglob("*.test"):
            p.unlink()
    n_kept = 0
    for (category, fn, kind, urn), cases in sorted(kept.items()):
        path = out_dir / category / f"{fn}.test"
        path.parent.mkdir(parents=True, exist_ok=True)
        body = [f"### SUBSTRAIT_{kind.upper()}_TEST: v1.0", f"### SUBSTRAIT_INCLUDE: {urn}"]
        current = None
        for group, text in cases:
            if group != current:
                body += ["", group]
                current = group
            body.append(text)
            n_kept += 1
        path.write_text("\n".join(body) + "\n", encoding="utf-8")
        parse_case_file(path)  # the written file must parse as a whole

    total = sum(dropped.values()) + len(quarantined) + n_kept
    lines = [
        "# Extended function test cases",
        "",
        "Cases from the legacy suite (`test-suites/functions`) for Substrait standard functions,",
        "converted to the current test-case format by `scripts/convert_legacy_function_tests.py`.",
        "Do not edit by hand; fix the legacy case or the converter and re-run it.",
        "",
        f"Function definitions: `substrait-extensions` {ext_version}.",
        "",
        "## Result",
        "",
        "| | Cases |",
        "|---|---:|",
        f"| Legacy cases read | {total} |",
        f"| **Kept** (valid and verified) | **{n_kept}** |",
        *[f"| &nbsp;&nbsp;verified by {k} | {v} |" for k, v in verified_by.most_common()],
        f"| Quarantined (valid, not verified — see below) | {len(quarantined)} |",
        *[f"| Dropped: {k} | {v} |" for k, v in dropped.most_common()],
        "",
        "A kept case's expected value was reproduced by DuckDB (through the compliance runner's",
        "translator) or, where DuckDB differs or cannot run the case, by the result the function",
        "definitions give (`scripts/function_reference.py`: exact arithmetic, IEEE 754 special values,",
        "the option modes, string semantics). DuckDB's agreement is one of the two checks, so these",
        "cases lean towards behaviour DuckDB shares; the leaderboard therefore does not score them.",
        "",
        "## Examples of dropped cases",
        "",
    ]
    for reason, examples in sorted(dropped_examples.items(), key=lambda kv: -dropped[kv[0]]):
        lines += [f"**{reason}** ({dropped[reason]})", ""] + [f"- {e}" for e in examples] + [""]
    cell = lambda t: t.replace("|", "\\|")  # noqa: E731
    lines += [
        "## Legacy values contradicted by the function definitions",
        "",
        "Valid cases whose expected value is wrong: the definitions give a different result, shown",
        "here. Dropped; to keep one, correct the legacy case.",
        "",
        "| Legacy case | Converted | Correct result | DuckDB |",
        "|---|---|---|---|",
    ]
    for where, text, correct, duck in contradicted:
        lines.append(f"| `{where}` | `{cell(text[:160])}` | `{cell(correct)}` | {cell(duck[:120])} |")
    lines += [
        "",
        "## Quarantined cases",
        "",
        "Valid against the function definitions, but DuckDB does not reproduce the expected value and",
        "the definitions do not decide it (for example shift_left overflow, modulus(INT_MIN, -1),",
        "min/max with NaN, interval normalisation, a start or position below 1 without an option).",
        "To keep one, extend `scripts/function_reference.py` where a definition settles it.",
        "",
        "| Legacy case | Converted | Why not verified |",
        "|---|---|---|",
    ]
    for where, text, why in quarantined:
        lines.append(f"| `{where}` | `{cell(text)}` | {cell(why[:220])} |")
    (out_dir / "CONVERSION.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"✅ {n_kept} cases kept in {out_dir} ({dict(verified_by)}); "
          f"{len(quarantined)} quarantined; {sum(dropped.values())} dropped of {total}")
    for k, v in dropped.most_common():
        print(f"   dropped {v:5}  {k}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
