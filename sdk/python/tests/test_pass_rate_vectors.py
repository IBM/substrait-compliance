"""Pass rate: the vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv)."""

import csv
from pathlib import Path

import pytest

from substrait_compliance.result import ComplianceReport, ComplianceResult, TestStatus

VECTORS = Path(__file__).resolve().parents[2] / "conformance" / "pass-rate-vectors.csv"


def vectors():
    rows = [ln for ln in VECTORS.read_text().splitlines() if ln and not ln.startswith("#")]
    return [{k: float(v) if k == "expected_percent" else int(v) for k, v in r.items()}
            for r in csv.DictReader(rows)]


@pytest.mark.parametrize("v", vectors(), ids=lambda v: "{passed}p-{failed}f-{skipped}s-{error}e-{unsupported}u".format(**v))
def test_pass_rate(v):
    if v["unsupported"]:
        pytest.skip("no UNSUPPORTED status in the Python SDK")
    report = ComplianceReport(suite_name="s", engine_name="e")
    for status, n in ((TestStatus.PASSED, v["passed"]), (TestStatus.FAILED, v["failed"]),
                      (TestStatus.SKIPPED, v["skipped"]), (TestStatus.ERROR, v["error"])):
        report.results += [ComplianceResult(test_id=f"t{len(report.results) + i}", status=status) for i in range(n)]
    assert report.get_pass_rate() == pytest.approx(v["expected_percent"], abs=1e-9)
