#!/usr/bin/env python3
"""
Generate the Substrait Compliance Leaderboard from per-engine entries.

Each entry is written by scripts/combine_engine_reports.py (schema 2): scored suites in
two categories - functions (function cases as Substrait plans) and queries (query plans
such as TPC-H) - and reference suites that are shown but not scored. Engines are ranked
by the overall score: the average of the two category pass rates.

The layout follows suite names: for the scored function suite F the page also shows
F-sql (the same cases as SQL), F-extended and F-extended-sql; for a scored query suite Q
it shows reference suites named Q-<variant> (e.g. tpch-substrait-0.81, an older plan
encoding) next to it. Any other reference suite is listed under "Other results".

    generate_leaderboard.py --input compliance-reports --output docs/leaderboard.md --format all

--format markdown|json|html|both|all ("both" = markdown + json).
"""

import argparse
import html
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

SKIP_FILES = ('leaderboard.json', 'summary.json', 'function_tests_summary.json', 'analytics-report.json')

QUERY_LABELS = {
    "tpch": "TPC-H",
    "tpch-substrait-0.81": "TPC-H, 0.81 legacy encoding",
    "tpcds": "TPC-DS",
}


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------

def load_compliance_report(report_path: Path) -> Optional[Dict[str, Any]]:
    """Load one engine entry; None for files that are not engine entries."""
    if report_path.name in SKIP_FILES or report_path.name.startswith('leaderboard'):
        return None
    try:
        data = json.loads(report_path.read_text())
    except Exception as e:  # noqa: BLE001
        print(f"Error loading {report_path}: {e}", file=sys.stderr)
        return None
    if not isinstance(data, dict):
        return None
    if 'engineName' not in data and 'engine' in data:
        data['engineName'] = data['engine']
    if 'engineName' not in data:
        return None
    for new, old in (('passed', 'totalPassed'), ('passRate', 'overallPassRate'), ('failed', 'totalFailed')):
        if new not in data and old in data:
            data[new] = data[old]
    return data


def is_scored_entry(entry: Dict[str, Any]) -> bool:
    return entry.get('schemaVersion', 1) >= 2 and isinstance(entry.get('categories'), dict)


# ---------------------------------------------------------------------------
# Formatting helpers. A cell is a str, or (text, style) with style "strong" or "ref".
# ---------------------------------------------------------------------------

def pct(value: Optional[float]) -> str:
    return "n/a" if value is None else f"{value:.1f}%"


def counts(suite: Optional[Dict[str, Any]]) -> str:
    if not suite:
        return ""
    return f"{suite['passed']}/{suite['passed'] + suite['failed']}"


def suite_cell(suite: Optional[Dict[str, Any]], style: Optional[str] = None, with_counts: bool = False):
    if not suite:
        return "—"
    text = pct(suite.get('passRate'))
    if with_counts:
        text += f" ({counts(suite)})"
    return (text, style) if style else text


def ext_pct(c: Optional[Dict[str, Any]]) -> str:
    if not c:
        return "—"
    if c.get('passRate') is None:
        return "all skipped" if c.get('skipped') else "n/a"
    return pct(c['passRate'])


def gap(plan: Optional[Dict[str, Any]], sql: Optional[Dict[str, Any]]) -> str:
    if not plan or not sql or plan.get('passRate') is None or sql.get('passRate') is None:
        return "—"
    d = plan['passRate'] - sql['passRate']
    return f"{'−' if d < 0 else '+'}{abs(d):.1f} pts"


def older_suites(entry: Dict[str, Any]) -> List[str]:
    return list((entry.get('provenance') or {}).get('olderSuites') or [])


def engine_label(entry: Dict[str, Any]) -> str:
    return f"{entry.get('engineName', 'Unknown')} {entry.get('engineVersion', '')}".strip()


# ---------------------------------------------------------------------------
# Model: the tables every output format renders
# ---------------------------------------------------------------------------

def function_suites(entry: Dict[str, Any]) -> Dict[str, Optional[Dict[str, Any]]]:
    suites = entry.get('suites') or {}
    names = entry['categories'].get('functions', {}).get('suites') or []
    base = names[0] if names else None
    get = (lambda n: suites.get(n)) if base else (lambda n: None)
    return {
        "base": base,
        "plan": get(base) if base else None,
        "sql": get(f"{base}-sql") if base else None,
        "ext": get(f"{base}-extended") if base else None,
        "ext_sql": get(f"{base}-extended-sql") if base else None,
    }


def query_columns(entries: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Columns of the Queries table: each scored query suite, then its variants."""
    scored, variants = [], []
    for e in entries:
        for q in e['categories'].get('queries', {}).get('suites') or []:
            if q not in scored:
                scored.append(q)
    for e in entries:
        for name, s in (e.get('suites') or {}).items():
            if s.get('informational') and any(name.startswith(q + "-") for q in scored) and name not in variants:
                variants.append(name)
    return [{"name": n, "scored": True} for n in scored] + [{"name": n, "scored": False} for n in sorted(variants)]


def shown_suites(entry: Dict[str, Any], qcols: List[Dict[str, Any]]) -> set:
    fs = function_suites(entry)
    shown = {n for n in entry.get('categories', {}).get('functions', {}).get('suites') or []}
    if fs["base"]:
        shown |= {f"{fs['base']}-sql", f"{fs['base']}-extended", f"{fs['base']}-extended-sql"}
    shown |= {c["name"] for c in qcols}
    return shown


def source_of(entry: Dict[str, Any]) -> str:
    return (entry.get('provenance') or {}).get('source', 'ci')


def one_per_engine(entries: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """One entry per engine name: a run in this repository's CI wins over a submission;
    otherwise the latest."""
    best: Dict[str, Dict[str, Any]] = {}
    for e in entries:
        key = str(e.get('engineName', '')).lower()
        rank = (source_of(e) == 'ci', str(e.get('timestamp', '')))
        if key not in best or rank > (source_of(best[key]) == 'ci', str(best[key].get('timestamp', ''))):
            best[key] = e
    return list(best.values())


def source_cell(entry: Dict[str, Any]):
    prov = entry.get('provenance') or {}
    if source_of(entry) == 'ci':
        return ("Run here", "link", prov['runUrl']) if prov.get('runUrl') else "Run here"
    return ("Submitted", "link", prov['runUrl']) if prov.get('runUrl') else "Submitted"


def build_model(reports: List[Dict[str, Any]]) -> Dict[str, Any]:
    entries = sorted(one_per_engine([r for r in reports if is_scored_entry(r)]),
                     key=lambda r: (-(r.get('passRate') or 0.0), r.get('engineName', '')))
    unranked = [r for r in reports if not is_scored_entry(r)]
    qcols = query_columns(entries)

    def cat_cell(e, cat):
        c = e['categories'].get(cat) or {}
        if not c.get('suites'):
            return ("not run", "ref")
        txt = pct(c.get('passRate'))
        return txt

    rankings = {
        "headers": ["Rank", "Engine", "Overall", "Functions", "Queries",
                    ("Functions via SQL (reference)", "ref"), "Substrait consumer", "Source"],
        "align": ["r", "l", "r", "r", "r", "r", "l", "l"],
        "rows": [],
    }
    medals = {1: "🥇", 2: "🥈", 3: "🥉"}
    for i, e in enumerate(entries, 1):
        fs = function_suites(e)
        rankings["rows"].append([
            medals.get(i, str(i)), engine_label(e) + (" †" if older_suites(e) else ""),
            (pct(e.get('passRate')), "strong"), cat_cell(e, 'functions'), cat_cell(e, 'queries'),
            suite_cell(fs["sql"], "ref"), e.get('consumer') or "—", source_cell(e),
        ])

    functions = {
        "headers": ["Engine", "Upstream: plans (scored)", "Upstream: SQL", "Consumer gap",
                    "Extended: plans", "Extended: SQL"],
        "align": ["l", "r", "r", "r", "r", "r"],
        "rows": [],
    }
    for e in entries:
        fs = function_suites(e)
        functions["rows"].append([
            engine_label(e), suite_cell(fs["plan"], "strong", with_counts=True), suite_cell(fs["sql"]),
            gap(fs["plan"], fs["sql"]), suite_cell(fs["ext"]), suite_cell(fs["ext_sql"]),
        ])

    queries = {
        "headers": ["Engine"] + [QUERY_LABELS.get(c["name"], c["name"]) + (" (scored)" if c["scored"] else "")
                                 for c in qcols],
        "align": ["l"] + ["r"] * len(qcols),
        "rows": [],
    }
    for e in entries:
        row = [engine_label(e)]
        for c in qcols:
            s = (e.get('suites') or {}).get(c["name"])
            if not s:
                row.append("—")
                continue
            txt = f"{counts(s)} ({pct(s.get('passRate'))})"
            row.append((txt, "strong") if c["scored"] else txt)
        queries["rows"].append(row)

    # Per-extension matrix: one row per extension, one column per engine, "plan / SQL".
    ext_names = sorted({x for e in entries for x in ((function_suites(e)["plan"] or {}).get('extensions') or {})})
    extensions = {
        "headers": ["Extension"] + [engine_label(e) for e in entries],
        "align": ["l"] + ["r"] * len(entries),
        "rows": [],
    }
    for x in ext_names:
        row = [x]
        for e in entries:
            fs = function_suites(e)
            p = ((fs["plan"] or {}).get('extensions') or {}).get(x)
            s = ((fs["sql"] or {}).get('extensions') or {}).get(x)
            if not p and not s:
                row.append("—")
                continue
            row.append(f"{ext_pct(p)} / {ext_pct(s)}")
        extensions["rows"].append(row)

    other = {"headers": ["Engine", "Suite", "Pass rate", "Passed / executed", "Skipped"],
             "align": ["l", "l", "r", "r", "r"], "rows": []}
    for e in entries:
        shown = shown_suites(e, qcols)
        for name, s in (e.get('suites') or {}).items():
            if name not in shown:
                other["rows"].append([engine_label(e), f"`{name}`", pct(s.get('passRate')), counts(s),
                                      str(s.get('skipped', 0))])

    crashes = [(engine_label(e), n, s['engineCrashes']) for e in entries
               for n, s in (e.get('suites') or {}).items() if s.get('engineCrashes')]

    older = [(engine_label(e), older_suites(e)) for e in entries if older_suites(e)]
    return {"entries": entries, "unranked": unranked, "older": older, "rankings": rankings, "functions": functions,
            "queries": queries, "extensions": extensions, "other": other, "crashes": crashes}


# ---------------------------------------------------------------------------
# Text shared by the Markdown and HTML pages
# ---------------------------------------------------------------------------

INTRO = ("How completely each engine's Substrait consumer runs Substrait plans. "
         "Every result is measured against the real engine: in this repository's CI, or in the "
         "engine team's own CI and submitted by pull request (see `submissions/README.md`).")

RANKING_NOTE = ("**Overall** is the average of the Functions and Queries pass rates, so both "
                "count equally however many cases each has; a category an engine does not run "
                "counts as 0%. *Functions via SQL* is shown for reference and does not affect the ranking. "
                "**Source**: *Run here* = run by this repository's CI; *Submitted* = results an engine "
                "team ran in its own CI and submitted by pull request, scored here with the same rules "
                "(the link goes to their run).")


def older_note(older) -> str:
    return "† Run on an earlier revision of " + "; ".join(
        f"{', '.join(f'`{n}`' for n in names)} ({e})" for e, names in older) + "."


FUNCTIONS_NOTE = [
    "**Upstream** cases are the Substrait project's own function tests (`tests/cases` at v0.107.0); "
    "they are the scored set.",
    "**Extended** cases are converted from the legacy suite. Most were verified on DuckDB when "
    "converted (the rest against the function definitions), so that part favours DuckDB; it is "
    "shown for detail and not scored.",
    "**Plans** run each case as a Substrait plan through the engine's consumer. **SQL** runs the "
    "same case as the engine's own SQL. **Consumer gap** = plans − SQL: function support the "
    "engine has in SQL but loses through its Substrait consumer.",
]

QUERIES_NOTE = ("Only the current plan encoding is scored. Other encodings show whether plans "
                "from older producers still run.")

EXTENSIONS_NOTE = ("Upstream cases by extension, as `plans / SQL`. A low plan rate next to a high "
                   "SQL rate points at the consumer rather than the engine.")

SCORING = [
    "A function case **passes** when the engine's result matches the expected value, compared by the "
    "expected type: integers and decimals exactly, floats with a relative tolerance (1e-9; 1e-6 "
    "for `fp32`), other values by value. Result types are not scored.",
    "A plan the consumer rejects is a **failure**. A plan that crashes the engine fails that case; "
    "the engine runs in a child process, so the run continues.",
    "**Skipped** cases are not counted either way: option modes the engine does not claim to "
    "implement, `<!UNDEFINED>` results, and cases the plan builder cannot express yet. "
    "Pass rate = passed ÷ (passed + failed).",
    "A **query** passes when its result has the expected column names (case-insensitive), number "
    "and type family of columns, and the expected rows in order. Numbers match within 1e-9 "
    "(relative), and a column the plan declares as `decimal(P,S)` within one unit in its last "
    "declared place (10^-S), since the answer is only defined to that scale.",
    "Per-case reasons for every failure and skip are in each engine's CI report artifact.",
]


# ---------------------------------------------------------------------------
# Markdown
# ---------------------------------------------------------------------------

def md_escape(text: str) -> str:
    """Plain text for a Markdown table cell: no line breaks, raw HTML, links or column breaks."""
    text = re.sub(r"[\r\n\t]+", " ", str(text))
    for ch, rep in (("\\", "\\\\"), ("|", "\\|"), ("[", "\\["), ("]", "\\]"), ("<", "&lt;"), (">", "&gt;")):
        text = text.replace(ch, rep)
    return text


def md_cell(cell) -> str:
    if isinstance(cell, tuple) and len(cell) == 3:  # (text, "link", url)
        url = str(cell[2])
        if not re.fullmatch(r"https://[^\s()<>\"'`\[\]|]+", url):
            return md_escape(cell[0])
        return f"[{md_escape(cell[0])}]({url})"
    if isinstance(cell, tuple):
        text, style = cell
        return f"**{md_escape(text)}**" if style == "strong" else f"*{md_escape(text)}*"
    text = str(cell)
    if re.fullmatch(r"`[A-Za-z0-9._-]+`", text):  # a suite name in code style
        return text
    return md_escape(text)


def md_table(t: Dict[str, Any]) -> List[str]:
    sep = {"l": "---", "r": "---:"}
    lines = ["| " + " | ".join(md_cell(h) for h in t["headers"]) + " |",
             "|" + "|".join(sep[a] for a in t["align"]) + "|"]
    lines += ["| " + " | ".join(md_cell(c) for c in row) + " |" for row in t["rows"]]
    return lines


def generate_leaderboard_markdown(reports: List[Dict[str, Any]], output_path: Path):
    m = build_model(reports)
    now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    lines = ["# Substrait Consumer Compliance", "", INTRO, "", f"Last updated: {now}", ""]
    if m["entries"]:
        lines += ["## Rankings", ""] + md_table(m["rankings"]) + ["", RANKING_NOTE, ""]
        if m["older"]:
            lines += [older_note(m["older"]), ""]
        lines += ["## Functions", ""] + md_table(m["functions"]) + [""] + [f"- {n}" for n in FUNCTIONS_NOTE] + [""]
        if m["queries"]["headers"][1:]:
            lines += ["## Queries", ""] + md_table(m["queries"]) + ["", QUERIES_NOTE, ""]
        if m["extensions"]["rows"]:
            lines += ["## Functions by extension", "", EXTENSIONS_NOTE, ""] + md_table(m["extensions"]) + [""]
        if m["other"]["rows"]:
            lines += ["## Other results (not scored)", ""] + md_table(m["other"]) + [""]
        if m["crashes"]:
            lines += ["Engine crashes during plan runs (each fails one case): "
                      + "; ".join(f"{e} `{n}`: {c}" for e, n, c in m["crashes"]) + ".", ""]
    if m["unranked"]:
        lines += ["## Not ranked", "",
                  "Entries in an older report format, without scoring categories:", ""]
        lines += [f"- {engine_label(r)}: {pct(r.get('passRate'))}" for r in m["unranked"]] + [""]
    lines += ["## How scoring works", ""] + [f"- {s}" for s in SCORING] + ["", "---", "",
              "*Generated by `scripts/generate_leaderboard.py` from the engines' CI results.*", ""]
    output_path.write_text("\n".join(lines))
    print(f"✅ Leaderboard generated: {output_path}")


# ---------------------------------------------------------------------------
# HTML (standalone page for GitHub Pages)
# ---------------------------------------------------------------------------

def _inline(text: str) -> str:
    """Escape, then render the few inline Markdown marks the notes use."""
    out = html.escape(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"\*(.+?)\*", r"<em>\1</em>", out)
    out = re.sub(r"`(.+?)`", r"<code>\1</code>", out)
    return out


def html_cell(cell, tag: str, align: str) -> str:
    cls = ' class="num"' if align == "r" else ""
    if isinstance(cell, tuple) and len(cell) == 3:  # (text, "link", url)
        url = cell[2] if str(cell[2]).startswith("https://") else "#"
        return f'<{tag}{cls}><a href="{html.escape(url)}">{html.escape(cell[0])}</a></{tag}>'
    if isinstance(cell, tuple):
        text, style = cell
        inner = f"<strong>{_inline(text)}</strong>" if style == "strong" else f'<span class="ref">{_inline(text)}</span>'
    else:
        inner = _inline(str(cell))
    return f"<{tag}{cls}>{inner}</{tag}>"


def html_table(t: Dict[str, Any]) -> str:
    head = "".join(html_cell(h, "th", a) for h, a in zip(t["headers"], t["align"]))
    body = "".join("<tr>" + "".join(html_cell(c, "td", a) for c, a in zip(row, t["align"])) + "</tr>"
                   for row in t["rows"])
    return f'<div class="scroll"><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div>'


HTML_STYLE = """
:root { --bg:#f6f7f9; --card:#fff; --fg:#1d2330; --muted:#5d6675; --line:#e2e5ea; --accent:#2f6fde; --head:#f0f2f5; }
@media (prefers-color-scheme: dark) { :root { --bg:#12151b; --card:#1a1e26; --fg:#e6e9ef; --muted:#9aa3b2; --line:#2c323d; --accent:#7aa7ff; --head:#222833; } }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
main { max-width:1100px; margin:0 auto; padding:32px 16px 48px; }
h1 { font-size:28px; margin:0 0 6px; }
h2 { font-size:19px; margin:36px 0 10px; }
p, li { color:var(--muted); }
.updated { font-size:13px; }
.scroll { overflow-x:auto; background:var(--card); border:1px solid var(--line); border-radius:8px; }
table { border-collapse:collapse; width:100%; }
th, td { padding:9px 12px; border-bottom:1px solid var(--line); text-align:left; white-space:nowrap; }
th { background:var(--head); font-size:13px; font-weight:600; }
tr:last-child td { border-bottom:none; }
td.num, th.num { text-align:right; font-variant-numeric:tabular-nums; }
.ref { color:var(--muted); font-style:italic; }
code { font-size:13px; }
a { color:var(--accent); }
footer { margin-top:40px; font-size:13px; color:var(--muted); }
"""


def generate_leaderboard_html(reports: List[Dict[str, Any]], output_path: Path, json_name: str = "leaderboard.json"):
    m = build_model(reports)
    now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    parts = [f"<h1>Substrait Consumer Compliance</h1><p>{_inline(INTRO)}</p>"
             f'<p class="updated">Last updated: {now}</p>']
    if m["entries"]:
        parts.append("<h2>Rankings</h2>" + html_table(m["rankings"]) + f"<p>{_inline(RANKING_NOTE)}</p>"
                     + (f"<p>{_inline(older_note(m['older']))}</p>" if m["older"] else ""))
        parts.append("<h2>Functions</h2>" + html_table(m["functions"])
                     + "<ul>" + "".join(f"<li>{_inline(n)}</li>" for n in FUNCTIONS_NOTE) + "</ul>")
        if m["queries"]["headers"][1:]:
            parts.append("<h2>Queries</h2>" + html_table(m["queries"]) + f"<p>{_inline(QUERIES_NOTE)}</p>")
        if m["extensions"]["rows"]:
            parts.append("<h2>Functions by extension</h2>" + f"<p>{_inline(EXTENSIONS_NOTE)}</p>"
                         + html_table(m["extensions"]))
        if m["other"]["rows"]:
            parts.append("<h2>Other results (not scored)</h2>" + html_table(m["other"]))
        if m["crashes"]:
            parts.append("<p>Engine crashes during plan runs (each fails one case): "
                         + "; ".join(f"{html.escape(e)} <code>{html.escape(n)}</code>: {c}"
                                     for e, n, c in m["crashes"]) + ".</p>")
    if m["unranked"]:
        parts.append("<h2>Not ranked</h2><p>Entries in an older report format, without scoring categories:</p><ul>"
                     + "".join(f"<li>{html.escape(engine_label(r))}: {pct(r.get('passRate'))}</li>"
                               for r in m["unranked"]) + "</ul>")
    parts.append("<h2>How scoring works</h2><ul>" + "".join(f"<li>{_inline(s)}</li>" for s in SCORING) + "</ul>")
    parts.append(f'<footer>Data: <a href="{html.escape(json_name)}">{html.escape(json_name)}</a> · '
                 "generated by <code>scripts/generate_leaderboard.py</code></footer>")
    page = ('<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width, initial-scale=1">'
            f"<title>Substrait Compliance Leaderboard</title><style>{HTML_STYLE}</style></head>"
            f"<body><main>{''.join(parts)}</main></body></html>\n")
    output_path.write_text(page)
    print(f"✅ Leaderboard HTML generated: {output_path}")


# ---------------------------------------------------------------------------
# JSON
# ---------------------------------------------------------------------------

def generate_leaderboard_json(reports: List[Dict[str, Any]], output_path: Path):
    m = build_model(reports)
    entries = m["entries"]
    leaderboard = {
        "schemaVersion": 2,
        "lastUpdated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "scoring": "overall = average of the functions and queries category pass rates",
        "totalEngines": len(entries),
        "averagePassRate": round(sum(e.get('passRate') or 0 for e in entries) / len(entries), 1) if entries else 0,
        "engines": [],
        "unranked": [{"engineName": r.get('engineName'), "engineVersion": r.get('engineVersion'),
                      "passRate": r.get('passRate')} for r in m["unranked"]],
    }
    for i, e in enumerate(entries, 1):
        leaderboard["engines"].append({
            "rank": i,
            "schemaVersion": e.get('schemaVersion', 2),  # usable as a regression baseline
            "engineName": e.get('engineName', 'Unknown'),
            "engineVersion": e.get('engineVersion', 'Unknown'),
            "consumer": e.get('consumer'),
            "provenance": e.get('provenance') or {"source": "ci"},
            "passRate": e.get('passRate'),
            "categories": e.get('categories'),
            "passed": e.get('passed', 0), "failed": e.get('failed', 0),
            "skipped": e.get('skipped', 0), "totalTests": e.get('totalTests', 0),
            "timestamp": e.get('timestamp', 'Unknown'),
            "suites": e.get('suites') or {},
        })
    output_path.write_text(json.dumps(leaderboard, indent=2))
    print(f"✅ Leaderboard JSON generated: {output_path}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--input', '-i', type=Path, required=True,
                        help='Directory containing engine entries (combine_engine_reports.py output)')
    parser.add_argument('--output', '-o', type=Path, required=True,
                        help='Output path; the extension is replaced per format')
    parser.add_argument('--format', '-f', choices=['markdown', 'json', 'html', 'both', 'all'], default='markdown',
                        help='both = markdown + json; all = markdown + json + html')
    parser.add_argument('--include-demo', action='store_true',
                        help='Include reports marked "demo": true (excluded by default)')
    args = parser.parse_args(argv)

    if not args.input.is_dir():
        print(f"Error: {args.input} is not a directory", file=sys.stderr)
        sys.exit(1)
    reports = []
    for report_file in sorted(args.input.glob('*.json')):
        report = load_compliance_report(report_file)
        if report and report.get('demo') and not args.include_demo:
            print(f"Skipping demo report {report_file.name} (use --include-demo to keep it)")
        elif report:
            reports.append(report)
    if not reports:
        print("Error: No valid compliance reports found", file=sys.stderr)
        sys.exit(1)
    print(f"📊 Loaded {len(reports)} engine entries")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    fmt = {'both': {'markdown', 'json'}, 'all': {'markdown', 'json', 'html'}}.get(args.format, {args.format})
    if 'markdown' in fmt:
        generate_leaderboard_markdown(reports, args.output.with_suffix('.md'))
    if 'json' in fmt:
        generate_leaderboard_json(reports, args.output.with_suffix('.json'))
    if 'html' in fmt:
        generate_leaderboard_html(reports, args.output.with_suffix('.html'), args.output.with_suffix('.json').name)
    print("✅ Leaderboard generation complete")


if __name__ == '__main__':
    main()
