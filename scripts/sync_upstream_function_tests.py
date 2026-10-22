#!/usr/bin/env python3
"""
Copy the Substrait project's own function test cases into this repository.

    scripts/sync_upstream_function_tests.py --upstream-dir ~/src/substrait \\
        --dest test-suites/functions-substrait-0.107/upstream

--upstream-dir is a checkout of https://github.com/substrait-io/substrait at the
release tag the suite targets (e.g. `git clone --depth 1 --branch v0.107.0 ...`).

What it does:
  1. replaces DEST with a copy of UPSTREAM/tests/cases/**/*.test (nothing else),
  2. applies FIXES below (corrected values, required arguments, options the
     function definitions do not declare): each one must
     match the upstream file exactly, so a fix that upstream has since made (or a
     case it changed) stops the sync instead of silently doing nothing,
  3. parses every copied file with the vendored upstream parser,
  4. writes DEST/UPSTREAM.md recording the tag, commit and corrections.

Report every fix upstream; once upstream fixes a case, delete its entry when
re-syncing.
"""

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "sdk" / "python"))

from substrait_compliance.testcase_parser import load_all_testcases  # noqa: E402

# Fixes applied on top of the upstream files. Each entry must match the upstream
# file exactly (count included), so a fix that upstream has made in the meantime
# stops the sync instead of silently doing nothing. Every entry should be reported
# upstream; delete it once upstream has fixed the case.
#
#   ("replace", file, exact upstream line, replacement line, reason)
#   ("exclude", file, exact upstream line, reason)                  # case removed
#   ("rewrite_option", option name, replacement "key:VALUE" or None to remove it,
#                      expected number of cases, reason)
#   ("swap_args", file, function, expected number of cases, reason) # swap args 1 and 2

YAML = "https://github.com/substrait-io/substrait/blob/v0.107.0/extensions/"
WRONG_STAT = "Miscomputed upstream; the corrected value is the exact {} of the listed rows."
EXTRACT_INDEXING = (f"[functions_datetime.yaml]({YAML}functions_datetime.yaml) requires the `indexing` "
                    "argument (ONE/ZERO) for this component; the expected value is for ONE.")

FIXES = [
    ("replace", "arithmetic/shift_left.test",
     "shift_left(301989888::i64, 8::i32) = 19791209299968::i64",
     "shift_left(301989888::i64, 8::i32) = 77309411328::i64",
     "301989888 << 8 = 77309411328; the upstream value is 301989888 << 16."),
    # std_dev / variance: sample (n-1) and population (n) statistics recomputed exactly
    ("replace", "arithmetic/std_dev.test",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) std_dev(SAMPLE::enum, col0::fp32) = 3.8944404::fp32?",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) std_dev(SAMPLE::enum, col0::fp32) = 3.7416575::fp32?",
     WRONG_STAT.format("sample standard deviation (sqrt(70/5))")),
    ("replace", "arithmetic/std_dev.test",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) std_dev(SAMPLE::enum, col0::fp64) = 3.8944404818493075::fp64?",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) std_dev(SAMPLE::enum, col0::fp64) = 3.7416573867739413::fp64?",
     WRONG_STAT.format("sample standard deviation (sqrt(70/5))")),
    ("replace", "arithmetic/std_dev.test",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) std_dev(SAMPLE::enum, col0::fp32) [rounding:TIE_TO_EVEN] = 1.7406897::fp32?",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) std_dev(SAMPLE::enum, col0::fp32) [rounding:TIE_TO_EVEN] = 1.7392527::fp32?",
     WRONG_STAT.format("sample standard deviation (sqrt(12.1/4))")),
    ("replace", "arithmetic/std_dev.test",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) std_dev(SAMPLE::enum, col0::fp64) [rounding:TRUNCATE] = 1.7406897166664838::fp64?",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) std_dev(SAMPLE::enum, col0::fp64) [rounding:TRUNCATE] = 1.7392527130926085::fp64?",
     WRONG_STAT.format("sample standard deviation (sqrt(12.1/4))")),
    ("replace", "arithmetic/std_dev.test",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) std_dev(SAMPLE::enum, col0::fp32) = 41.010193::fp32?",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) std_dev(SAMPLE::enum, col0::fp32) = 39.52847::fp32?",
     WRONG_STAT.format("sample standard deviation (sqrt(6250/4))")),
    ("replace", "arithmetic/std_dev.test",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) std_dev(POPULATION::enum, col0::fp64) = 36.66060555964672::fp64?",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) std_dev(POPULATION::enum, col0::fp64) = 35.35533905932738::fp64?",
     WRONG_STAT.format("population standard deviation (sqrt(6250/5))")),
    ("replace", "arithmetic/variance.test",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) variance(SAMPLE::enum, col0::fp32) = 15.166667::fp32?",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) variance(SAMPLE::enum, col0::fp32) = 14.0::fp32?",
     WRONG_STAT.format("sample variance (70/5)")),
    ("replace", "arithmetic/variance.test",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) variance(SAMPLE::enum, col0::fp64) = 15.166666666666666::fp64?",
     "((-5.0), (-3.0), (-1.0), (1.0), (3.0), (5.0)) variance(SAMPLE::enum, col0::fp64) = 14.0::fp64?",
     WRONG_STAT.format("sample variance (70/5)")),
    ("replace", "arithmetic/variance.test",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) variance(SAMPLE::enum, col0::fp32) [rounding:TIE_TO_EVEN] = 3.03::fp32?",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) variance(SAMPLE::enum, col0::fp32) [rounding:TIE_TO_EVEN] = 3.025::fp32?",
     WRONG_STAT.format("sample variance (12.1/4)")),
    ("replace", "arithmetic/variance.test",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) variance(SAMPLE::enum, col0::fp64) [rounding:TRUNCATE] = 3.0299999999999994::fp64?",
     "((1.1), (2.2), (3.3), (4.4), (5.5)) variance(SAMPLE::enum, col0::fp64) [rounding:TRUNCATE] = 3.025::fp64?",
     WRONG_STAT.format("sample variance (12.1/4)")),
    ("replace", "arithmetic/variance.test",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) variance(SAMPLE::enum, col0::fp32) = 1681.25::fp32?",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) variance(SAMPLE::enum, col0::fp32) = 1562.5::fp32?",
     WRONG_STAT.format("sample variance (6250/4)")),
    ("replace", "arithmetic/variance.test",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) variance(POPULATION::enum, col0::fp64) = 1345.0::fp64?",
     "((0.0), (100.0), (50.0), (25.0), (75.0)) variance(POPULATION::enum, col0::fp64) = 1250.0::fp64?",
     WRONG_STAT.format("population variance (6250/5)")),
    # logb: the YAML defines logb(x, base) = log_base(x); the tests pass (base, x)
    ("swap_args", "logarithmic/logb.test", "logb", 10,
     f"[functions_logarithmic.yaml]({YAML}functions_logarithmic.yaml) defines `logb(x, base) = log_base(x)`; "
     "these cases pass the base first (e.g. `logb(10, 100000) = 5`)."),
    # on_log_zero:NAN means NaN, not null
    *[("replace", f"logarithmic/{f}.test", old, old.replace("= null::fp64?", "= nan::fp64"),
       "`on_log_zero:NAN` returns NaN; the upstream case expects null.")
      for f, old in (("ln", "ln(0.0::fp64) [on_log_zero:NAN] = null::fp64?"),
                     ("log10", "log10(0.0::fp64) [on_log_zero:NAN] = null::fp64?"),
                     ("log2", "log2(0.0::fp64) [on_log_zero:NAN] = null::fp64?"),
                     ("logb", "logb(2.0::fp64, 0.0::fp64) [on_log_zero:NAN] = null::fp64?"))],
    # on_domain_error has no NONE value; NULL is the declared spelling
    *[("replace", f"logarithmic/{f}.test", old, old.replace("on_domain_error:NONE", "on_domain_error:NULL"),
       "`on_domain_error` declares `NULL`, not `NONE`.")
      for f, old in (("ln", "ln(-inf::fp64) [on_domain_error:NONE] = null::fp64?"),
                     ("log10", "log10(-inf::fp64) [on_domain_error:NONE] = null::fp64?"),
                     ("log2", "log2(-inf::fp64) [on_domain_error:NONE] = null::fp64?"),
                     ("logb", "logb(10::fp64, -inf::fp64) [on_domain_error:NONE] = null::fp64?"))],
    ("exclude", "arithmetic/acosh.test", "acosh(0.5::fp32) [on_domain_error:NONE] = null::fp32?",
     "acosh declares `on_domain_error` values NAN and ERROR only."),
    ("exclude", "arithmetic/divide.test", "divide(5::i8, 0::i8) [on_division_by_zero:NAN] = null::i8?",
     "Integer divide declares `on_division_by_zero` values NULL and ERROR only."),
    # regexp_replace: occurrence 0 (also the default) replaces ALL matches
    *[("replace", "string/regexp_replace.test", old, new,
       "The YAML defines occurrence `0` (and the default) as replacing all matches; "
       "cases passing `0` but expecting only the first match now pass `1`.")
      for old, new in (
          ("regexp_replace('a bcdef'::str, '\\S'::str, ','::str, 1::i64, 0::i64) = ', bcdef'::str",
           "regexp_replace('a bcdef'::str, '\\S'::str, ','::str, 1::i64, 1::i64) = ', bcdef'::str"),
          ("regexp_replace(' abcdef'::str, '\\w'::str, '1'::str, 1::i64, 0::i64) = ' 1bcdef'::str",
           "regexp_replace(' abcdef'::str, '\\w'::str, '1'::str, 1::i64, 1::i64) = ' 1bcdef'::str"),
          ("regexp_replace('abcabcabc'::str, '[bc]'::str, 'dd'::str, 1::i64, 0::i64) = 'addcabcabc'::str",
           "regexp_replace('abcabcabc'::str, '[bc]'::str, 'dd'::str, 1::i64, 1::i64) = 'addcabcabc'::str"))],
    *[("replace", "string/regexp_replace.test", old, new,
       "Without position/occurrence every match is replaced (occurrence defaults to 0).")
      for old, new in (
          ("regexp_replace('a bcdef'::str, '\\S'::str, ','::str) = ', bcdef'::str",
           "regexp_replace('a bcdef'::str, '\\S'::str, ','::str) = ', ,,,,,'::str"),
          ("regexp_replace(' abcdef'::str, '\\w'::str, '1'::str) = ' 1bcdef'::str",
           "regexp_replace(' abcdef'::str, '\\w'::str, '1'::str) = ' 111111'::str"),
          ("regexp_replace('abcabcabc'::str, '[bc]'::str, 'dd'::str) = 'addcabcabc'::str",
           "regexp_replace('abcabcabc'::str, '[bc]'::str, 'dd'::str) = 'addddaddddadddd'::str"))],
    # extract: indexing argument, day-of-week numbering, sub-second components
    *[("replace", "datetime/extract.test", f"extract({c}::enum, {x}) = {v}::i64",
       f"extract({c}::enum, ONE::enum, {x}) = {nv}::i64", why)
      for c, x, v, nv, why in (
          ("QUARTER", "2016-12-31T13:30:15::pts<6>", 4, 4, EXTRACT_INDEXING),
          ("MONTH", "2016-12-31T13:30:15::pts<6>", 12, 12, EXTRACT_INDEXING),
          ("ISO_WEEK", "2016-12-31T13:30:15::pts<6>", 52, 52, EXTRACT_INDEXING),
          ("DAY", "2016-12-31T13:30:15::pts<6>", 31, 31, EXTRACT_INDEXING),
          ("DAY_OF_YEAR", "2016-12-25T13:30:15::pts<6>", 360, 360, EXTRACT_INDEXING),
          ("MONTH", "2020-12-31::date", 12, 12, EXTRACT_INDEXING),
          ("DAY", "2020-12-31::date", 31, 31, EXTRACT_INDEXING),
          ("SUNDAY_DAY_OF_WEEK", "2016-12-25T13:30:15::pts<6>", 7, 1,
           EXTRACT_INDEXING + " 2016-12-25 is a Sunday: day 1 when weeks start on Sunday."),
          ("MONDAY_DAY_OF_WEEK", "2016-12-25T13:30:15::pts<6>", 0, 7,
           EXTRACT_INDEXING + " 2016-12-25 is a Sunday: day 7 when weeks start on Monday."),
      )],
    *[("replace", "datetime/extract.test", old, new,
       "The YAML defines MILLISECOND as milliseconds since the last full second and MICROSECOND "
       "as microseconds since the last full millisecond; the upstream values count from the minute.")
      for old, new in (
          ("extract(MILLISECOND::enum, 2016-12-31T13:30:15::pts<6>) = 15000::i64",
           "extract(MILLISECOND::enum, 2016-12-31T13:30:15::pts<6>) = 0::i64"),
          ("extract(MICROSECOND::enum, 2016-12-31T13:30:15.220000::pts<6>) = 15220000::i64",
           "extract(MICROSECOND::enum, 2016-12-31T13:30:15.220000::pts<6>) = 0::i64"),
          ("extract(MILLISECOND::enum, 01:02:03.155::pt<6>) = 3155::i64",
           "extract(MILLISECOND::enum, 01:02:03.155::pt<6>) = 155::i64"),
          ("extract(MICROSECOND::enum, 01:02:03.45::pt<6>) = 3450000::i64",
           "extract(MICROSECOND::enum, 01:02:03.45::pt<6>) = 0::i64"))],
    # sqrt: on_domain_error:NAN means NaN, not null
    *[("replace", "arithmetic/sqrt.test", old, old.replace("= null::fp64?", "= nan::fp64"),
       "`on_domain_error:NAN` returns NaN; the upstream case expects null.")
      for old in ("sqrt(-1::i64) [on_domain_error:NAN] = null::fp64?",
                  "sqrt(-9223372036854775800::i64) [on_domain_error:NAN] = null::fp64?")],
    # rpad: the YAML truncates from the left
    ("replace", "string/rpad.test", "rpad('abcdef'::str, 4::i32, ' '::str) = 'abcd'::str",
     "rpad('abcdef'::str, 4::i32, ' '::str) = 'cdef'::str",
     f"[functions_string.yaml]({YAML}functions_string.yaml) says rpad removes characters from the "
     "*left* when the input is longer than `length` (lpad removes them from the right). If the YAML "
     "is the side in error, fix it upstream and drop this entry."),
    # decimal literals must declare precision and scale
    *[("replace", f"arithmetic_decimal/{f}.test", old, new,
       "`dec` without precision/scale is not a Substrait type; the smallest `dec<P, S>` holding the literal is used.")
      for f, old, new in (
          ("power", "power(-1::dec, 0.5::dec<38, 1>) [complex_number_result:NAN] = nan::fp64",
           "power(-1::dec<1, 0>, 0.5::dec<38, 1>) [complex_number_result:NAN] = nan::fp64"),
          ("power", "power(-1::dec, 0.5::dec<38, 1>) [complex_number_result:ERROR] = <!ERROR>",
           "power(-1::dec<1, 0>, 0.5::dec<38, 1>) [complex_number_result:ERROR] = <!ERROR>"),
          ("power_decimal", "power(8::dec, 2::dec<38, 0>) = 64::fp64",
           "power(8::dec<1, 0>, 2::dec<38, 0>) = 64::fp64"),
          ("power_decimal", "power(1.0::dec, -1.0::dec<38, 0>) = 1.0::fp64",
           "power(1.0::dec<2, 1>, -1::dec<38, 0>) = 1.0::fp64"),
          ("power_decimal", "power(-1::dec, 0.5::dec<38, 1>) [complex_number_result:NAN] = nan::fp64",
           "power(-1::dec<1, 0>, 0.5::dec<38, 1>) [complex_number_result:NAN] = nan::fp64"),
          ("power_decimal", "power(-1::dec, 0.5::dec<38, 1>) [complex_number_result:ERROR] = <!ERROR>",
           "power(-1::dec<1, 0>, 0.5::dec<38, 1>) [complex_number_result:ERROR] = <!ERROR>"))],
    # options the function definitions do not declare: the cases are valid without them
    ("rewrite_option", "lookaround", None, 28,
     "`lookaround` is not an option of any regexp function; the regexp functions follow ICU, which "
     "supports lookaround, so the option is removed and the cases kept."),
    ("rewrite_option", "spaces_only", None, 9,
     "`spaces_only` is not an option of trim/ltrim/rtrim; the cases (trimming the given "
     "characters) are valid without it, so the option is removed."),
    ("rewrite_option", "full_unicode", "char_set:UTF8", 2,
     "`full_unicode` is not an option of upper/lower; the declared equivalent is `char_set:UTF8`."),
]


def split_top_level_args(text: str):
    """'a, f(b, c), 'x,y'' -> ['a', 'f(b, c)', "'x,y'"]"""
    parts, depth, quote, cur = [], 0, False, []
    i = 0
    while i < len(text):
        ch = text[i]
        if quote:
            cur.append(ch)
            if ch == "\\" and i + 1 < len(text):
                cur.append(text[i + 1])
                i += 1
            elif ch == "'":
                quote = False
        elif ch == "'":
            quote = True
            cur.append(ch)
        elif ch in "([{<":
            depth += 1
            cur.append(ch)
        elif ch in ")]}>" and not (ch == ">" and i > 0 and text[i - 1] == "-"):
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
        i += 1
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def swap_first_two_args(line: str, func: str) -> str:
    m = re.match(rf"^(\s*{re.escape(func)}\()(.*?)(\)\s*(\[[^\]]*\])?\s*=.*)$", line)
    if not m:
        raise ValueError(line)
    # find the call's closing parenthesis: the args end where depth returns to zero
    body, depth = line[len(m.group(1)):], 1
    for j, ch in enumerate(body):
        depth += ch == "("
        depth -= ch == ")"
        if depth == 0:
            break
    args = split_top_level_args(body[:j])
    args[0], args[1] = args[1], args[0]
    return m.group(1) + ", ".join(args) + body[j:]


def drop_empty_groups(lines):
    """Remove '# group: description' headers (and the blank line before them) whose
    group has no cases left; the grammar requires at least one case per group."""
    def is_case(line):
        return line.strip() and not line.lstrip().startswith("#")
    out = []
    for i, line in enumerate(lines):
        if line.startswith("# ") and not line.startswith("###"):
            rest = lines[i + 1:]
            nxt = next((j for j, l in enumerate(rest) if l.startswith("# ") and not l.startswith("###")), len(rest))
            if not any(is_case(l) for l in rest[:nxt]):
                while out and not out[-1].strip():
                    out.pop()
                continue
        out.append(line)
    return out


def apply_fixes(dest: Path):
    applied = []
    # argument swaps last, so line fixes can quote the upstream text unchanged
    for fix in sorted(FIXES, key=lambda f: f[0] == "swap_args"):
        kind = fix[0]
        if kind == "rewrite_option":
            _, option, replacement, expected, why = fix
            pattern = re.compile(rf"\s*\[([^\]]*)\]")
            hits = []
            for path in sorted(dest.rglob("*.test")):
                lines = path.read_text(encoding="utf-8").split("\n")
                for i, line in enumerate(lines):
                    m = pattern.search(line)
                    if not m or not re.search(rf"\b{option}:", m.group(1)):
                        continue
                    opts = [o.strip() for o in m.group(1).split(",")]
                    kept = [o for o in opts if not o.startswith(option + ":")]
                    if replacement:
                        kept.append(replacement)
                    new_opts = f" [{', '.join(kept)}]" if kept else ""
                    lines[i] = line[:m.start()] + new_opts + line[m.end():]
                    hits.append(path)
                path.write_text("\n".join(lines), encoding="utf-8")
            if len(hits) != expected:
                raise SystemExit(f"❌ option {option}: {len(hits)} cases (expected {expected}); review FIXES")
            action = f"`[{replacement}]`" if replacement else "option removed"
            applied.append((kind, f"{len(hits)} cases using `[{option}:...]`", "", action, why,
                            sorted({str(p.relative_to(dest)) for p in hits})))
            continue
        rel = fix[1]
        path = dest / rel
        lines = path.read_text(encoding="utf-8").split("\n")
        if kind == "swap_args":
            _, _, func, expected, why = fix
            idx = [i for i, line in enumerate(lines) if line.lstrip().startswith(func + "(")]
            if len(idx) != expected:
                raise SystemExit(f"❌ {rel}: {len(idx)} {func} cases (expected {expected}); review FIXES")
            for i in idx:
                lines[i] = swap_first_two_args(lines[i], func)
            applied.append((kind, rel, f"all {expected} `{func}` cases", "arguments 1 and 2 swapped", why, None))
        else:
            old = fix[2]
            hits = [i for i, line in enumerate(lines) if line.strip() == old]
            if len(hits) != 1:
                raise SystemExit(f"❌ {rel}: fix matched {len(hits)} lines (expected 1); upstream may have "
                                 f"changed it — review FIXES.\n   {old}")
            if kind == "replace":
                lines[hits[0]] = fix[3]
                applied.append((kind, rel, old, fix[3], fix[4], None))
            elif kind == "exclude":
                del lines[hits[0]]
                lines = drop_empty_groups(lines)
                applied.append((kind, rel, old, "(removed)", fix[3], None))
            else:
                raise SystemExit(f"unknown fix kind {kind}")
        path.write_text("\n".join(lines), encoding="utf-8")
    return applied


def git_describe(upstream: Path):
    def run(*args):
        out = subprocess.run(["git", "-C", str(upstream), *args], capture_output=True, text=True)
        return out.stdout.strip() if out.returncode == 0 else None
    return run("describe", "--tags", "--exact-match") or run("describe", "--tags"), run("rev-parse", "--short", "HEAD")


def write_provenance(dest: Path, tag, commit, n_files, n_cases, applied):
    def cell(text):
        return str(text).replace("|", "\\|")
    rows = []
    for kind, rel, old, new, why, files in applied:
        if kind == "rewrite_option":
            rows.append(f"| {cell(', '.join(files))} | {cell(rel)} | {cell(new)} | {cell(why)} |")
        elif kind == "swap_args":
            rows.append(f"| `{rel}` | {cell(old)} | {cell(new)} | {cell(why)} |")
        else:
            rows.append(f"| `{rel}` | `{cell(old)}` | `{cell(new)}` | {cell(why)} |" if kind == "replace"
                        else f"| `{rel}` | `{cell(old)}` | removed | {cell(why)} |")
    (dest / "UPSTREAM.md").write_text(f"""# Upstream Substrait function test cases

These files are a copy of
[substrait-io/substrait `tests/cases`](https://github.com/substrait-io/substrait/tree/{tag}/tests/cases)
at **{tag}** (commit `{commit}`), with the fixes listed below: {n_files} files, {n_cases} test cases.
Do not edit them by hand — change `FIXES` in `scripts/sync_upstream_function_tests.py` and re-run it.
The test-case format is described in
[tests/README.md](https://github.com/substrait-io/substrait/blob/{tag}/tests/README.md).

Licensed under the Apache License 2.0 by the Substrait project.

## Fixes applied to the upstream cases

Upstream cases that contradict the function definitions in the
[{tag} extension YAMLs](https://github.com/substrait-io/substrait/tree/{tag}/extensions), or whose
expected value is arithmetically wrong. Each should be reported upstream.

| File | Upstream | Here | Why |
|---|---|---|---|
""" + "\n".join(rows) + "\n", encoding="utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--upstream-dir", type=Path, required=True, help="checkout of substrait-io/substrait")
    ap.add_argument("--dest", type=Path, required=True, help="directory to (re)create")
    args = ap.parse_args(argv)

    cases_dir = args.upstream_dir / "tests" / "cases"
    if not cases_dir.is_dir():
        raise SystemExit(f"❌ {cases_dir} not found; --upstream-dir must be a substrait checkout")
    tag, commit = git_describe(args.upstream_dir)
    if not tag:
        raise SystemExit("❌ cannot tell which upstream version this is; check out a release tag")

    sources = sorted(cases_dir.rglob("*.test"))
    if args.dest.exists():
        shutil.rmtree(args.dest)
    for src in sources:
        target = args.dest / src.relative_to(cases_dir)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    applied = apply_fixes(args.dest)

    test_files = load_all_testcases(str(args.dest))  # raises on any syntax error
    n_cases = sum(len(f.testcases) for f in test_files)
    write_provenance(args.dest, tag, commit, len(test_files), n_cases, applied)
    print(f"✅ {len(test_files)} files / {n_cases} cases from {tag} ({commit}) -> {args.dest}; "
          f"{len(applied)} fix(es) applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
