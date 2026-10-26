#!/usr/bin/env python3
"""
Generate the Substrait Compliance Leaderboard from per-engine entries.

Each entry is written by scripts/combine_engine_reports.py (schema 2): scored suites in
two categories - functions (function cases as Substrait plans) and queries (query plans
such as TPC-H) - and reference suites that are shown but not scored. Engines are ranked
by the overall score: the average of the two category pass rates.

The site has two kinds of page:

  leaderboard (index.html, leaderboard.md, leaderboard.json): the ranking, with links to
      how scoring works and to each engine's page
  engine pages (engines/<engine>.html and .json): what fails and why, grouped by cause
      (the entry's findings, scripts/engine_findings.py, with the explanations and upstream
      links in docs/engine-findings.yaml), then the detail tables: functions as plans and as
      SQL, each query suite and encoding, functions by extension, other results

The detail layout follows suite names: for the scored function suite F an engine page also
shows F-sql (the same cases as SQL), F-extended and F-extended-sql; for a scored query suite
Q it shows reference suites named Q-<variant> (e.g. tpch-substrait-0.81) next to it. Column
labels come from test-suites/registry.json ("label").

    generate_leaderboard.py --input compliance-reports --output docs/leaderboard.md --format all \\
        [--previous https://ibm.github.io/substrait-compliance/]

--format markdown|json|html|both|all ("both" = markdown + json; engine pages are written with
json and html). --previous is the published site (or a folder with engines/*.json) of the
last publish, used to show what changed since an engine's previous run.
"""

import argparse
import html
import json
import os
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parents[1]
SKIP_FILES = ('leaderboard.json', 'summary.json', 'function_tests_summary.json', 'analytics-report.json')
CURATED = REPO_ROOT / "docs" / "engine-findings.yaml"

QUERY_LABELS = {  # used when the registry gives no label
    "tpch": "TPC-H",
    "tpch-substrait-0.81": "TPC-H, 0.81 legacy encoding",
    "tpcds": "TPC-DS",
}
CASE_UNITS = {"functions": "cases", "queries": "queries"}


def repo_url() -> str:
    return f"https://github.com/{os.environ.get('GITHUB_REPOSITORY', 'IBM/substrait-compliance')}"


def site_url() -> str:
    owner, _, repo = os.environ.get('GITHUB_REPOSITORY', 'IBM/substrait-compliance').partition('/')
    return f"https://{owner.lower()}.github.io/{repo}/"


def doc_link(path: str, anchor: str = "") -> str:
    return f"{repo_url()}/blob/main/{path}" + (f"#{anchor}" if anchor else "")


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


def load_registry() -> Dict[str, dict]:
    try:
        return json.loads((REPO_ROOT / "test-suites" / "registry.json").read_text())["suites"]
    except (OSError, ValueError, KeyError):
        return {}


def suite_label(name: str, registry: Dict[str, dict]) -> str:
    return (registry.get(name) or {}).get("label") or QUERY_LABELS.get(name) or name


def load_curated(path: Optional[Path] = None) -> List[dict]:
    """The explanations and upstream links of docs/engine-findings.yaml ([] without PyYAML)."""
    path = Path(path or CURATED)
    if not path.exists():
        return []
    try:
        import yaml
    except ImportError:
        print(f"⚠️  PyYAML is not installed; {path.name} is not applied", file=sys.stderr)
        return []
    rules = (yaml.safe_load(path.read_text()) or {}).get("findings") or []
    for r in rules:
        re.compile(r["match"])  # fail early on a bad pattern
    return rules


# ---------------------------------------------------------------------------
# Formatting helpers. A cell is a str, (text, style) with style "strong" or "ref", or
# (text, "link", url).
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


def slug(entry: Dict[str, Any]) -> str:
    return re.sub(r"[^a-z0-9]+", "-", str(entry.get('engineName', 'engine')).lower()).strip("-") or "engine"


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
    text = "CI run" if source_of(entry) == 'ci' else "Submitted"
    return (text, "link", prov['runUrl']) if prov.get('runUrl') else text


# ---------------------------------------------------------------------------
# Model
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
    """Each scored query suite, then its variants (reference suites named <suite>-...)."""
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


def category_header(entries: List[Dict[str, Any]], cat: str, registry: Dict[str, dict]) -> str:
    """"Functions (1,315 cases)", "TPC-H (22 queries)": the scored suites' labels and size."""
    names: List[str] = []
    size = 0
    for e in entries:
        c = e['categories'].get(cat) or {}
        for n in c.get('suites') or []:
            if n not in names:
                names.append(n)
        size = max(size, sum(((e.get('suites') or {}).get(n) or {}).get('totalTests', 0) for n in c.get('suites') or []))
    label = " + ".join(suite_label(n, registry) for n in names) or cat.capitalize()
    return f"{label} ({size:,} {CASE_UNITS[cat]})" if size else label


def findings_count(entry: Dict[str, Any]) -> Optional[int]:
    """Number of causes found (consumer, query and engine findings), None without findings."""
    f = entry.get('findings')
    if not isinstance(f, dict):
        return None
    return sum((f.get('sections') or {}).get(s, {}).get('groups', 0) for s in ("consumer", "queries", "engine"))


def build_model(reports: List[Dict[str, Any]], registry: Optional[Dict[str, dict]] = None,
                page_base: str = "engines/") -> Dict[str, Any]:
    registry = load_registry() if registry is None else registry
    entries = sorted(one_per_engine([r for r in reports if is_scored_entry(r)]),
                     key=lambda r: (-(r.get('passRate') or 0.0), r.get('engineName', '')))
    unranked = [r for r in reports if not is_scored_entry(r)]

    def cat_cell(e, cat):
        c = e['categories'].get(cat) or {}
        return pct(c.get('passRate')) if c.get('suites') else ("not run", "ref")

    rankings = {
        "headers": ["Rank", "Engine", "Overall", category_header(entries, "functions", registry),
                    category_header(entries, "queries", registry), "Findings", "Results"],
        "align": ["r", "l", "r", "r", "r", "l", "l"],
        "rows": [],
    }
    medals = {1: "🥇", 2: "🥈", 3: "🥉"}
    for i, e in enumerate(entries, 1):
        page = f"{page_base}{slug(e)}.html"
        n = findings_count(e)
        rankings["rows"].append([
            medals.get(i, str(i)), (engine_label(e) + (" †" if older_suites(e) else ""), "link", page),
            (pct(e.get('passRate')), "strong"), cat_cell(e, 'functions'), cat_cell(e, 'queries'),
            (("details" if n is None else f"{n} finding{'s' if n != 1 else ''}"), "link", page),
            source_cell(e),
        ])
    older = [(engine_label(e), older_suites(e)) for e in entries if older_suites(e)]
    return {"entries": entries, "unranked": unranked, "older": older, "rankings": rankings,
            "qcols": query_columns(entries), "registry": registry}


def engine_tables(e: Dict[str, Any], qcols: List[Dict[str, Any]], registry: Dict[str, dict]) -> Dict[str, Any]:
    """The detail tables of one engine's page."""
    fs = function_suites(e)
    functions = {"headers": ["Run", "Upstream cases", "Extended cases"], "align": ["l", "r", "r"], "rows": [
        ["As Substrait plans", suite_cell(fs["plan"], "strong", with_counts=True), suite_cell(fs["ext"], with_counts=True)],
        ["As the engine's own SQL", suite_cell(fs["sql"], with_counts=True), suite_cell(fs["ext_sql"], with_counts=True)],
        ["Consumer gap (plans − SQL)", gap(fs["plan"], fs["sql"]), gap(fs["ext"], fs["ext_sql"])],
    ]} if fs["base"] else None
    queries = {"headers": ["Suite", "Passed", "Pass rate"], "align": ["l", "r", "r"], "rows": []}
    for c in qcols:
        s = (e.get('suites') or {}).get(c["name"])
        if s:
            label = suite_label(c["name"], registry) + (" (scored)" if c["scored"] else "")
            queries["rows"].append([label, counts(s), (pct(s.get('passRate')), "strong") if c["scored"] else pct(s.get('passRate'))])
    plan_ext = (fs["plan"] or {}).get('extensions') or {}
    sql_ext = (fs["sql"] or {}).get('extensions') or {}
    extensions = {"headers": ["Extension", "As plans", "As SQL"], "align": ["l", "r", "r"],
                  "rows": [[x, ext_pct(plan_ext.get(x)), ext_pct(sql_ext.get(x))] for x in sorted(set(plan_ext) | set(sql_ext))]}
    other = {"headers": ["Suite", "Pass rate", "Passed / executed", "Skipped"], "align": ["l", "r", "r", "r"], "rows": []}
    shown = shown_suites(e, qcols)
    for name, s in (e.get('suites') or {}).items():
        if name not in shown:
            other["rows"].append([f"`{name}`", pct(s.get('passRate')), counts(s), str(s.get('skipped', 0))])
    crashes = [(n, s['engineCrashes']) for n, s in (e.get('suites') or {}).items() if s.get('engineCrashes')]
    return {"functions": functions, "queries": queries, "extensions": extensions, "other": other, "crashes": crashes}


# ---------------------------------------------------------------------------
# Findings: curation and changes since the previous run
# ---------------------------------------------------------------------------

SECTION_TEXT = {
    "consumer": ("Substrait consumer gaps",
                 "These cases fail when run as Substrait plans but pass as the engine's own SQL (or were not run "
                 "as SQL), so the gap is in the engine's Substrait consumer."),
    "queries": ("Failing queries",
                "Query plans that fail. There is no SQL run to compare with, so the message is the guide."),
    "engine": ("Engine results that differ from the Substrait definition",
               "These cases fail in the engine's own SQL as well, so they are about the engine itself rather than "
               "its Substrait consumer."),
    "untested": ("Not tested yet: this repository's tooling",
                 "Cases this repository cannot run yet. They are skipped and say nothing about the engine."),
    "options": ("Option modes the engine does not claim",
                "Cases for an option mode (overflow, rounding, ...) the engine does not implement. They are skipped: "
                "engines need to implement only one mode."),
}
UPSTREAM_SECTIONS = ("consumer", "queries", "engine")


def curate(entry: Dict[str, Any], rules: List[dict]) -> List[dict]:
    """The entry's finding groups with title, note and upstream added from the curated rules."""
    groups = [dict(g) for g in ((entry.get('findings') or {}).get('groups') or [])]
    engine = str(entry.get('engineName', '')).lower()
    for g in groups:
        for r in rules:
            if r.get('engine') and str(r['engine']).lower() != engine:
                continue
            if r.get('section') and r['section'] != g['section']:
                continue
            if re.search(r['match'], g['signature']) or re.search(r['match'], g.get('example', '')):
                g['title'] = r.get('title') or g['signature']
                if r.get('note'):
                    g['note'] = r['note']
                if r.get('upstream'):
                    g['upstream'] = r['upstream']
                break
    return groups


def load_previous(source: Optional[str], name: str) -> Optional[dict]:
    """engines/<name>.json of the previous publish: a site URL or a folder."""
    if not source:
        return None
    try:
        if re.match(r"https?://", source):
            url = source.rstrip("/") + f"/engines/{name}.json"
            with urllib.request.urlopen(url, timeout=20) as resp:  # noqa: S310 - the published site
                return json.loads(resp.read().decode())
        path = Path(source) / "engines" / f"{name}.json"
        return json.loads(path.read_text()) if path.exists() else None
    except Exception:  # noqa: BLE001 - no previous page is fine
        return None


def case_keys(groups: List[dict]) -> Dict[tuple, str]:
    return {(g['section'], c['suite'], c['id']): g.get('title') or g['signature']
            for g in groups if g['section'] in UPSTREAM_SECTIONS for c in g.get('list') or []}


def changes_since(entry: Dict[str, Any], groups: List[dict], previous: Optional[dict]) -> Optional[dict]:
    """Cases failing now that did not before, and the reverse, against the engine's previous
    run. A republish of the same run keeps the changes it had."""
    if not previous or not isinstance(previous.get('findings'), dict):
        return None
    if previous.get('timestamp') == entry.get('timestamp'):
        return previous.get('changes')
    now, before = case_keys(groups), case_keys(previous['findings'].get('groups') or [])
    as_list = lambda keys, src: [{"section": k[0], "suite": k[1], "id": k[2], "finding": src[k]}  # noqa: E731
                                 for k in sorted(keys)]
    return {"since": previous.get('timestamp'), "previousVersion": previous.get('engineVersion'),
            "new": as_list(set(now) - set(before), now), "fixed": as_list(set(before) - set(now), before)}


def engine_document(entry: Dict[str, Any], rules: List[dict], previous_source: Optional[str],
                    qcols: List[Dict[str, Any]], registry: Dict[str, dict]) -> Dict[str, Any]:
    groups = curate(entry, rules)
    findings = entry.get('findings') if isinstance(entry.get('findings'), dict) else None
    doc = {
        "engineName": entry.get('engineName'), "engineVersion": entry.get('engineVersion'),
        "consumer": entry.get('consumer'), "timestamp": entry.get('timestamp'),
        "provenance": entry.get('provenance') or {"source": "ci"},
        "passRate": entry.get('passRate'), "categories": entry.get('categories'),
        "suites": entry.get('suites') or {},
        "findings": {"sections": findings['sections'], "groups": groups} if findings else None,
    }
    doc["changes"] = changes_since(entry, groups, load_previous(previous_source, slug(entry))) if findings else None
    doc["_tables"] = engine_tables(entry, qcols, registry)
    return doc


# ---------------------------------------------------------------------------
# Text
# ---------------------------------------------------------------------------

INTRO = ("How completely each engine's Substrait consumer runs Substrait plans, measured against the "
         "real engine: in this repository's CI, or in the engine team's own CI and submitted by pull request.")

RANKING_NOTE = ("**Overall** is the average of the two scored columns. Each engine's page lists what fails and "
                "why, with the detail behind its scores.")


def older_note(older) -> str:
    return "† Run on an earlier revision of " + "; ".join(
        f"{', '.join(f'`{n}`' for n in names)} ({e})" for e, names in older) + "."


def links() -> List[tuple]:
    return [
        ("How scoring works", doc_link("docs/COMPLIANCE_LEADERBOARD.md", "how-engines-are-scored")),
        ("How the pass rate is calculated", doc_link("docs/COMPLIANCE_LEADERBOARD.md", "how-the-pass-rate-is-calculated")),
        ("Suites at a glance", doc_link("test-suites/README.md", "suites-at-a-glance")),
        ("Add your engine", doc_link("submissions/README.md")),
    ]


FUNCTIONS_NOTE = ("**Upstream** cases are the Substrait project's own function tests and are scored; "
                  "**extended** cases are converted from the legacy suite and shown for detail. **Consumer gap** = "
                  "plans − SQL: what the engine can do in SQL but not through its Substrait consumer.")
EXTENSIONS_NOTE = "Upstream cases by extension. A low plan rate next to a high SQL rate points at the consumer."


# ---------------------------------------------------------------------------
# Markdown
# ---------------------------------------------------------------------------

def md_escape(text: str) -> str:
    """Plain text for a Markdown table cell: no line breaks, raw HTML, links or column breaks."""
    text = re.sub(r"[\r\n\t]+", " ", str(text))
    for ch, rep in (("\\", "\\\\"), ("|", "\\|"), ("[", "\\["), ("]", "\\]"), ("<", "&lt;"), (">", "&gt;")):
        text = text.replace(ch, rep)
    return text


def md_cell(cell, base: str = "") -> str:
    if isinstance(cell, tuple) and len(cell) == 3:  # (text, "link", url)
        url = str(cell[2])
        if not re.match(r"https?://", url):
            url = base + url
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


def md_table(t: Dict[str, Any], base: str = "") -> List[str]:
    sep = {"l": "---", "r": "---:"}
    lines = ["| " + " | ".join(md_cell(h, base) for h in t["headers"]) + " |",
             "|" + "|".join(sep[a] for a in t["align"]) + "|"]
    lines += ["| " + " | ".join(md_cell(c, base) for c in row) + " |" for row in t["rows"]]
    return lines


def generate_leaderboard_markdown(reports: List[Dict[str, Any]], output_path: Path):
    m = build_model(reports)
    now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    base = site_url()  # engine pages are linked absolutely: this file is also a CI job summary
    lines = ["# Substrait Consumer Compliance", "", INTRO, "", f"Last updated: {now}", ""]
    if m["entries"]:
        lines += ["## Rankings", ""] + md_table(m["rankings"], base) + ["", RANKING_NOTE, ""]
        if m["older"]:
            lines += [older_note(m["older"]), ""]
    if m["unranked"]:
        lines += ["## Not ranked", "",
                  "Entries in an older report format, without scoring categories:", ""]
        lines += [f"- {engine_label(r)}: {pct(r.get('passRate'))}" for r in m["unranked"]] + [""]
    lines += [" · ".join(f"[{t}]({u})" for t, u in links()) + f" · [Data]({base}leaderboard.json)", ""]
    output_path.write_text("\n".join(lines))
    print(f"✅ Leaderboard generated: {output_path}")


# ---------------------------------------------------------------------------
# HTML
# ---------------------------------------------------------------------------

def _inline(text: str) -> str:
    """Escape, then render the few inline Markdown marks the notes use."""
    out = html.escape(text)
    out = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"\*(.+?)\*", r"<em>\1</em>", out)
    out = re.sub(r"`(.+?)`", r"<code>\1</code>", out)
    return out


def _href(url: str) -> str:
    url = str(url)
    ok = url.startswith("https://") or re.fullmatch(r"[A-Za-z0-9._/-]+\.(html|json)(#[\w-]+)?", url)
    return html.escape(url if ok else "#")


def html_cell(cell, tag: str, align: str) -> str:
    cls = ' class="num"' if align == "r" else ""
    if isinstance(cell, tuple) and len(cell) == 3:  # (text, "link", url)
        return f'<{tag}{cls}><a href="{_href(cell[2])}">{html.escape(cell[0])}</a></{tag}>'
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
:root { --bg:#f6f7f9; --card:#fff; --fg:#1d2330; --muted:#5d6675; --line:#e2e5ea; --accent:#2f6fde; --head:#f0f2f5;
        --tag:#eef2fb; --tagfg:#2a4a8a; --warn:#fff4e5; --warnfg:#8a4b00; --code:#f3f4f6; }
@media (prefers-color-scheme: dark) { :root { --bg:#12151b; --card:#1a1e26; --fg:#e6e9ef; --muted:#9aa3b2; --line:#2c323d;
        --accent:#7aa7ff; --head:#222833; --tag:#22304a; --tagfg:#b9cdf5; --warn:#3a2a12; --warnfg:#f3c78a; --code:#11141a; } }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
main { max-width:1000px; margin:0 auto; padding:32px 16px 48px; }
h1 { font-size:28px; margin:0 0 6px; }
h2 { font-size:19px; margin:36px 0 8px; }
h3 { font-size:16px; margin:0 0 6px; }
p, li { color:var(--muted); }
.updated { font-size:13px; }
.links { font-size:14px; line-height:2; }
.links a { margin-right:14px; }
.scroll { overflow-x:auto; background:var(--card); border:1px solid var(--line); border-radius:8px; }
table { border-collapse:collapse; width:100%; }
th, td { padding:9px 12px; border-bottom:1px solid var(--line); text-align:left; white-space:nowrap; }
th { background:var(--head); font-size:13px; font-weight:600; white-space:normal; }
@media (max-width: 600px) { th, td { padding:8px 8px; } main { padding:24px 16px 40px; } h1 { font-size:24px; } }
tr:last-child td { border-bottom:none; }
td.num, th.num { text-align:right; font-variant-numeric:tabular-nums; }
.ref { color:var(--muted); font-style:italic; }
code, pre { font:13px/1.45 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace; }
pre { background:var(--code); border:1px solid var(--line); border-radius:6px; padding:8px 10px; margin:8px 0;
      white-space:pre-wrap; word-break:break-word; color:var(--fg); }
a { color:var(--accent); }
.back { font-size:14px; }
.scores { display:flex; flex-wrap:wrap; gap:12px; margin:16px 0; }
.score { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:10px 14px; min-width:140px; }
.score b { display:block; font-size:22px; }
.score span { font-size:13px; color:var(--muted); }
.finding { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:14px 16px; margin:10px 0; }
.finding p { margin:6px 0; }
.meta { font-size:13px; color:var(--muted); }
.tag { display:inline-block; font-size:12px; padding:1px 8px; border-radius:10px; background:var(--tag); color:var(--tagfg); margin-right:6px; }
.tag.todo { background:var(--warn); color:var(--warnfg); }
details { margin-top:6px; }
summary { cursor:pointer; color:var(--accent); font-size:14px; }
details .scroll { margin-top:8px; }
details td { white-space:normal; font-size:13px; vertical-align:top; }
details td:first-child { white-space:nowrap; }
details td:last-child { min-width:260px; }
footer { margin-top:40px; font-size:13px; color:var(--muted); }
"""


def html_page(title: str, body: str) -> str:
    return ('<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width, initial-scale=1">'
            f"<title>{html.escape(title)}</title><style>{HTML_STYLE}</style></head>"
            f"<body><main>{body}</main></body></html>\n")


def links_html(json_href: str) -> str:
    items = " ".join(f'<a href="{_href(u)}">{html.escape(t)}</a>' for t, u in links())
    return f'<p class="links">{items} <a href="{_href(json_href)}">Data (JSON)</a></p>'


def generate_leaderboard_html(reports: List[Dict[str, Any]], output_path: Path, json_name: str = "leaderboard.json"):
    m = build_model(reports)
    now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')
    parts = [f"<h1>Substrait Consumer Compliance</h1><p>{_inline(INTRO)}</p>"
             f'<p class="updated">Last updated: {now}</p>']
    if m["entries"]:
        parts.append(html_table(m["rankings"]) + f"<p>{_inline(RANKING_NOTE)}</p>"
                     + (f"<p>{_inline(older_note(m['older']))}</p>" if m["older"] else ""))
    if m["unranked"]:
        parts.append("<h2>Not ranked</h2><p>Entries in an older report format, without scoring categories:</p><ul>"
                     + "".join(f"<li>{html.escape(engine_label(r))}: {pct(r.get('passRate'))}</li>"
                               for r in m["unranked"]) + "</ul>")
    parts.append(links_html(json_name))
    parts.append("<footer>Generated by <code>scripts/generate_leaderboard.py</code> from the engines' results.</footer>")
    output_path.write_text(html_page("Substrait Compliance Leaderboard", "".join(parts)))
    print(f"✅ Leaderboard HTML generated: {output_path}")


def _when(ts: Optional[str]) -> str:
    return str(ts or "")[:16].replace("T", " ") + (" UTC" if ts else "")


def finding_html(g: dict, suite_labels: Dict[str, str]) -> str:
    title = g.get('title')
    head = f"<h3>{html.escape(title)}</h3>" if title else f"<h3><code>{html.escape(g['signature'])}</code></h3>"
    subjects = g.get('subjects') or {}
    kind = "Queries" if g['section'] == "queries" else "Functions"
    subj = ", ".join(f"{html.escape(k)}" + (f" ({v})" if v > 1 else "") for k, v in list(subjects.items())[:25])
    if len(subjects) > 25:
        subj += f", and {len(subjects) - 25} more"
    suites = ", ".join(f"{html.escape(suite_labels.get(k, k))} {v}" for k, v in (g.get('suites') or {}).items())
    meta = f'<p class="meta">{g["cases"]} case{"s" if g["cases"] != 1 else ""} · {suites}'
    if g.get('where'):
        meta += f" · {html.escape(g['where'])}"
    meta += "</p>"
    tags = ""
    if g['section'] in UPSTREAM_SECTIONS:
        up = g.get('upstream')
        if up and str(up.get('url', '')).startswith("https://"):
            tags = (f'<span class="tag">Reported upstream{": " + html.escape(up["status"]) if up.get("status") else ""}'
                    f'</span><a href="{html.escape(up["url"])}">{html.escape(up["url"])}</a>')
        else:
            tags = '<span class="tag todo">Not yet reported upstream</span>'
    body = [head, meta]
    if tags:
        body.append(f"<p>{tags}</p>")
    if g.get('note'):
        body.append(f"<p>{_inline(str(g['note']))}</p>")
    body.append(f"<p><strong>{kind}:</strong> {subj}</p>")
    body.append(f"<pre>{html.escape(g.get('example') or g['signature'])}</pre>")
    cases = g.get('list') or []
    cols = [("id", "Case")] + [(k, h) for k, h in (("test", "Test"), ("expected", "Expected"),
                                                    ("message", "Message, where it differs"))
                               if any(c.get(k) for c in cases)]
    rows = "".join("<tr>" + "".join(f"<td>{html.escape(str(c.get(k, '')))}</td>" for k, _ in cols) + "</tr>"
                   for c in cases)
    head = "".join(f"<th>{h}</th>" for _, h in cols)
    label = "The case" if g["cases"] == 1 else f'All {g["cases"]} cases'
    body.append(f'<details><summary>{label}</summary><div class="scroll"><table><thead><tr>'
                f"{head}</tr></thead><tbody>{rows}</tbody></table></div></details>")
    return f'<div class="finding" id="{html.escape(g.get("id", ""))}">' + "".join(body) + "</div>"


def changes_html(ch: Optional[dict]) -> str:
    if not ch:
        return ""
    new, fixed = ch.get('new') or [], ch.get('fixed') or []
    since = f"the previous run ({_when(ch.get('since'))}{', ' + html.escape(str(ch['previousVersion'])) if ch.get('previousVersion') else ''})"
    if not new and not fixed:
        return f"<p>No change in failing cases since {since}.</p>"

    def lst(items):
        return "<ul>" + "".join(f"<li><code>{html.escape(i['id'])}</code> ({html.escape(i['suite'])}): "
                                f"{html.escape(i['finding'])}</li>" for i in items[:200]) + "</ul>"
    out = f"<p>Since {since}: <strong>{len(new)}</strong> newly failing, <strong>{len(fixed)}</strong> no longer failing.</p>"
    if new:
        out += f"<details><summary>Newly failing ({len(new)})</summary>{lst(new)}</details>"
    if fixed:
        out += f"<details><summary>No longer failing ({len(fixed)})</summary>{lst(fixed)}</details>"
    return out


def engine_page_html(doc: Dict[str, Any], registry: Dict[str, dict], slug_name: str) -> str:
    label = f"{doc.get('engineName')} {doc.get('engineVersion') or ''}".strip()
    prov = doc.get('provenance') or {}
    run = (f'<a href="{_href(prov["runUrl"])}">{"the CI run" if prov.get("source", "ci") == "ci" else "the team&#39;s run"}</a>'
           if prov.get('runUrl') else "")
    src = "Run in this repository's CI" if prov.get('source', 'ci') == 'ci' else "Submitted by the engine team"
    parts = ['<p class="back"><a href="../index.html">← Leaderboard</a></p>', f"<h1>{html.escape(label)}</h1>"]
    if doc.get('consumer'):
        parts.append(f"<p>Substrait consumer: {html.escape(str(doc['consumer']))}</p>")
    parts.append(f'<p class="updated">{src}, {_when(doc.get("timestamp"))}{" · " + run if run else ""}</p>')
    cats = doc.get('categories') or {}
    cards = [("Overall", doc.get('passRate'))] + [
        (" + ".join(suite_label(n, registry) for n in (cats.get(c) or {}).get('suites') or []) or c.capitalize(),
         (cats.get(c) or {}).get('passRate')) for c in ("functions", "queries")]
    parts.append('<div class="scores">' + "".join(
        f'<div class="score"><b>{pct(v)}</b><span>{html.escape(t)}</span></div>' for t, v in cards) + "</div>")

    findings = doc.get('findings')
    suite_labels = {n: suite_label(n, registry) for n in (doc.get('suites') or {})}
    parts.append("<h2>Findings</h2>")
    if not findings:
        parts.append("<p>Findings appear after the engine's next run: this result was recorded before the leaderboard "
                     "grouped failures by cause. The detail below and the run's reports have the per-case results.</p>")
    else:
        parts.append("<p>What fails and why, grouped by cause. Each finding lists every case it affects. "
                     "Upstream issue links are maintained in "
                     f'<a href="{_href(doc_link("docs/engine-findings.yaml"))}"><code>docs/engine-findings.yaml</code></a>.</p>')
        parts.append(changes_html(doc.get('changes')))
        groups = findings.get('groups') or []
        nav = []
        for sec in ("consumer", "queries", "engine", "untested", "options"):
            sg = [g for g in groups if g['section'] == sec]
            if sg:
                nav.append(f'<a href="#{sec}">{html.escape(SECTION_TEXT[sec][0])} ({sum(g["cases"] for g in sg)})</a>')
        parts.append(f'<p class="links">{" ".join(nav)}</p>')
        for sec in ("consumer", "queries", "engine", "untested", "options"):
            sg = [g for g in groups if g['section'] == sec]
            if not sg:
                continue
            title, text = SECTION_TEXT[sec]
            parts.append(f'<h2 id="{sec}">{html.escape(title)}</h2><p>{html.escape(text)}</p>')
            if sec == "options":
                parts.append("<details><summary>Show the "
                             f"{sum(g['cases'] for g in sg)} cases</summary>"
                             + "".join(finding_html(g, suite_labels) for g in sg) + "</details>")
            else:
                parts.extend(finding_html(g, suite_labels) for g in sg)

    t = doc.get('_tables') or {}
    parts.append("<h2>Detail</h2>")
    if t.get("functions"):
        parts.append("<h3>Functions</h3>" + html_table(t["functions"]) + f"<p>{_inline(FUNCTIONS_NOTE)}</p>")
    if t.get("queries") and t["queries"]["rows"]:
        parts.append("<h3>TPC-H</h3>" + html_table(t["queries"])
                     + "<p>Only the current plan encoding is scored; other encodings show whether plans from older "
                     "producers still run.</p>")
    if t.get("extensions") and t["extensions"]["rows"]:
        parts.append("<h3>Functions by extension</h3>" + f"<p>{_inline(EXTENSIONS_NOTE)}</p>" + html_table(t["extensions"]))
    if t.get("other") and t["other"]["rows"]:
        parts.append("<h3>Other results (not scored)</h3>" + html_table(t["other"]))
    if t.get("crashes"):
        parts.append("<p>Engine crashes during plan runs (each fails one case): "
                     + "; ".join(f"<code>{html.escape(n)}</code>: {c}" for n, c in t["crashes"]) + ".</p>")
    reports = (f'the reports of <a href="{_href(prov["runUrl"])}">its run</a> (GitHub keeps them for a limited time)'
               if prov.get('runUrl') else "the engine's run reports")
    parts.append("<h2>Reports</h2><p>Every case's result is in " + reports
                 + f'. This page\'s data: <a href="{html.escape(slug_name)}.json">{html.escape(slug_name)}.json</a>.</p>')
    parts.append(links_html("../leaderboard.json"))
    return html_page(f"{label}: Substrait compliance", "".join(parts))


def generate_engine_pages(reports: List[Dict[str, Any]], out_dir: Path, previous: Optional[str] = None,
                          write_html: bool = True) -> List[str]:
    m = build_model(reports)
    rules = load_curated()
    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for e in m["entries"]:
        doc = engine_document(e, rules, previous, m["qcols"], m["registry"])
        name = slug(e)
        data = {k: v for k, v in doc.items() if not k.startswith("_")}
        (out_dir / f"{name}.json").write_text(json.dumps(data, indent=1))
        if write_html:
            (out_dir / f"{name}.html").write_text(engine_page_html(doc, m["registry"], name))
        written.append(name)
    print(f"✅ Engine pages generated: {', '.join(written)} -> {out_dir}")
    return written


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
            "page": f"engines/{slug(e)}.html",
            "findings": (e.get('findings') or {}).get('sections') if isinstance(e.get('findings'), dict) else None,
        })
    output_path.write_text(json.dumps(leaderboard, indent=2))
    print(f"✅ Leaderboard JSON generated: {output_path}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--input', '-i', type=Path, required=True,
                        help='Directory containing engine entries (combine_engine_reports.py output)')
    parser.add_argument('--output', '-o', type=Path, required=True,
                        help='Output path; the extension is replaced per format. Engine pages go to engines/ beside it')
    parser.add_argument('--format', '-f', choices=['markdown', 'json', 'html', 'both', 'all'], default='markdown',
                        help='both = markdown + json; all = markdown + json + html')
    parser.add_argument('--previous', help='the previous publish (site URL or folder), for changes since the last run')
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
    if fmt & {'json', 'html'}:
        generate_engine_pages(reports, args.output.parent / "engines", args.previous, write_html='html' in fmt)
    print("✅ Leaderboard generation complete")


if __name__ == '__main__':
    main()
