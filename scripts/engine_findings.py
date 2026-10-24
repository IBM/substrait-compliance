#!/usr/bin/env python3
"""
What an engine gets wrong, grouped by cause: the "findings" on its leaderboard page.

Built from the per-case results in an engine's suite reports (the ones
scripts/combine_engine_reports.py combines), so it covers engines run here and submitted
engines alike. Each failing or skipped case is put in a section, and cases whose messages
differ only in the function, value or number they name are grouped together:

  consumer  a function case that fails as a Substrait plan but not as the engine's own SQL
            (or was not run as SQL): a gap in the engine's Substrait consumer
  engine    a function case that fails as the engine's own SQL: the engine itself
  queries   a query plan (TPC-H, ...) that fails; no SQL run to compare with
  untested  a case this repository's tooling cannot run yet (a plan the plan builder cannot
            build, SQL the translator cannot write), so nothing is known about the engine
  options   an option mode the engine does not claim to implement (skipped, not counted)

Explanations and upstream issue links are added when the page is generated
(docs/engine-findings.yaml), so they can change without running the engine again.
"""

import json
import re
from collections import Counter
from pathlib import Path
from typing import Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parents[1]
SECTIONS = ("consumer", "engine", "queries", "untested", "options")
MAX_EXAMPLE = 300


def _registry() -> Dict[str, dict]:
    try:
        return json.loads((REPO_ROOT / "test-suites" / "registry.json").read_text())["suites"]
    except (OSError, ValueError, KeyError):
        return {}


def case_id(row: dict, suite_path: Optional[str]) -> str:
    """"<file relative to the suite folder>:<line>" for a function case, the test id for a query."""
    if row.get("testId") or row.get("id"):
        return str(row.get("testId") or row.get("id"))
    file = str(row.get("file") or "").replace("\\", "/")
    marker = (suite_path or "").rstrip("/") + "/"
    if suite_path and marker in file:
        file = file.split(marker, 1)[1]  # runners may prefix a checkout folder
    elif "test-suites/" in file:
        file = file.split("test-suites/", 1)[1]
    return f"{file}:{row.get('line')}"


def subject_of(row: dict) -> str:
    """The function a function case tests (abs, std_dev, ...), or the query id. Function test
    files are named after their function (arithmetic/std_dev.test)."""
    if row.get("testId") or row.get("id"):
        return str(row.get("testId") or row.get("id"))
    file = str(row.get("file") or "").replace("\\", "/").rsplit("/", 1)[-1]
    if file.endswith(".test"):
        return file[:-len(".test")]
    m = re.search(r"([A-Za-z_]\w*)\(", str(row.get("test") or ""))
    return m.group(1) if m else "?"


def message_of(row: dict) -> str:
    return str(row.get("reason") or row.get("message") or row.get("errorMessage") or "").strip()


def signature(message: str, subject: str = "") -> str:
    """The message with the parts that vary between cases of one cause replaced by "…" or N:
    the function or table it names, quoted values and numbers. The generated SQL a runner
    appends ("[SELECT ...]") and anything after the first line are dropped."""
    m = message.split("\n", 1)[0].strip()
    m = re.sub(r"\s*\[(SELECT|WITH|VALUES)\b.*$", "", m)
    if re.match(r"got .*, expected ", m):
        return "Wrong result (got …, expected …)"
    if m.startswith("expected an error, got") or re.match(r"expected a runtime error, got", m):
        return "Expected an error, got a result"
    if m.startswith("expected a runtime error, but the consumer rejected the plan"):
        return "Expected a runtime error; the consumer rejected the plan instead"
    if m.startswith("Output mismatch"):
        if re.search(r"expected \d+ rows", m):
            return "Output mismatch: wrong number of rows"
        if "column names" in m:
            return "Output mismatch: different column names"
        if "column types" in m or "columns" in m:
            return "Output mismatch: different columns"
        return "Output mismatch: wrong values"
    # The pattern or operator an error quotes after these varies by case, not by cause.
    m = re.sub(r"(did not compile|regex parse error|invalid perl operator):.*", r"\1: …", m)
    m = re.sub(r"(bitwise operation) .*", r"\1 …", m)
    if subject and subject != "?":
        m = re.sub(r"(?<![\w])" + re.escape(subject) + r"(:[\w?]+)?(?![\w])", "…", m)
    m = re.sub(r'"[^"]*"', '"…"', m)
    m = re.sub(r"'[^']*'", "'…'", m)
    m = re.sub(r"(Scalar Function with name|function name:?) [\w.]+", r"\1 …", m)
    m = re.sub(r"\d+", "N", m)
    m = re.sub(r"\s+", " ", m).strip()
    return m[:200] or "(no message)"


def _short(message: str) -> str:
    """A case's own message for the list of cases: its first line, without the SQL a runner
    appends (the group's example shows one in full)."""
    m = re.sub(r"\s*\[(SELECT|WITH|VALUES)\b.*$", "", message.split("\n", 1)[0].strip())
    return m[:160]


def _skip_section(reason: str) -> Optional[str]:
    r = reason.lower()
    if "result is undefined" in r:
        return None  # the suite defines no result; nothing to report
    if r.startswith("unsupported option") or r.startswith("option mode not implemented"):
        return "options"
    return "untested"


def _skip_where(reason: str, mode: str) -> str:
    r = reason.lower()
    if r.startswith("plan not built"):
        return "plan builder"
    if r.startswith("untranslatable") or mode == "sql":
        return "SQL translator"
    return "runner"


def compute_findings(suites: Dict[str, dict], registry: Optional[Dict[str, dict]] = None) -> dict:
    """suites: {suite name: {"report": <suite report>, "category": "functions"|"queries"|None}}.

    Returns {"sections": {section: {"groups": n, "cases": n}}, "groups": [...]}, groups sorted
    by section, then by number of cases."""
    registry = _registry() if registry is None else registry
    groups: Dict[tuple, dict] = {}

    def add(section: str, suite: str, row: dict, cid: str, where: Optional[str] = None):
        msg = message_of(row)
        subject = subject_of(row)
        key = (section, signature(msg, subject), where or "")
        g = groups.setdefault(key, {"section": section, "signature": key[1], "example": msg[:MAX_EXAMPLE],
                                    "cases": 0, "suites": Counter(), "subjects": Counter(), "list": []})
        if where:
            g["where"] = where
        g["cases"] += 1
        g["suites"][suite] += 1
        g["subjects"][subject] += 1
        case = {"suite": suite, "id": cid}
        if row.get("test"):
            case["test"] = str(row["test"])[:200]
        if row.get("expected") is not None:
            case["expected"] = str(row["expected"])[:120]
        short = _short(msg)
        if short != _short(g["example"]):
            case["message"] = short
        g["list"].append(case)

    def rows_of(report: dict) -> List[dict]:
        return list(report.get("nonPassingResults") or report.get("testResults") or [])

    def path_of(name: str) -> Optional[str]:
        return (registry.get(name) or {}).get("path")

    # Function suites: the plan run of each case, compared with its SQL run (<name>-sql).
    for name, s in suites.items():
        report = s["report"]
        if report.get("mode") != "plan":
            continue
        sql = (suites.get(f"{name}-sql") or {}).get("report")
        sql_status = {case_id(r, path_of(f"{name}-sql")): str(r.get("status", "")).lower()
                      for r in rows_of(sql)} if sql else {}
        for row in rows_of(report):
            status, cid = str(row.get("status", "")).lower(), case_id(row, path_of(name))
            if status in ("failed", "error"):
                if sql_status.get(cid) in ("failed", "error"):
                    continue  # fails as SQL too: listed under "engine" from the SQL run
                add("consumer", name, row, cid)
            elif status == "skipped":
                section = _skip_section(message_of(row))
                if section:
                    add(section, name, row, cid, _skip_where(message_of(row), "plan") if section == "untested" else None)

    # The SQL run of a plan suite (<plan suite>-sql). Other SQL-only suites, such as the
    # legacy function suite, are not findings.
    for name, s in suites.items():
        report = s["report"]
        if report.get("mode") != "sql" or not name.endswith("-sql") \
                or (suites.get(name[:-len("-sql")]) or {}).get("report", {}).get("mode") != "plan":
            continue
        for row in rows_of(report):
            status, cid = str(row.get("status", "")).lower(), case_id(row, path_of(name))
            if status in ("failed", "error"):
                add("engine", name, row, cid)
            elif status == "skipped" and _skip_section(message_of(row)) == "untested":
                add("untested", name, row, cid, _skip_where(message_of(row), "sql"))

    # Scored query suites (TPC-H): every failing query.
    for name, s in suites.items():
        if s.get("category") != "queries":
            continue
        for row in rows_of(s["report"]):
            if str(row.get("status", "")).upper() in ("FAILED", "ERROR"):
                add("queries", name, row, case_id(row, None))

    out = []
    for g in groups.values():
        g["suites"] = dict(g["suites"].most_common())
        g["subjects"] = dict(g["subjects"].most_common())
        out.append(g)
    out.sort(key=lambda g: (SECTIONS.index(g["section"]), -g["cases"], g["signature"]))
    for i, g in enumerate(out, 1):
        g["id"] = f"{g['section']}-{i}"
    sections = {sec: {"groups": sum(1 for g in out if g["section"] == sec),
                      "cases": sum(g["cases"] for g in out if g["section"] == sec)} for sec in SECTIONS}
    return {"sections": sections, "groups": out}
