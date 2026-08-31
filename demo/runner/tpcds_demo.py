#!/usr/bin/env python3
"""
Substrait TPC-DS Compliance Testing Demo

Runs 99 TPC-DS queries against deterministic simulated demo engines,
generates per-engine JSON reports, and writes the leaderboard to
demo/dashboard/data/tpcds_leaderboard.json for the dashboard.
"""

import json
import random
from datetime import datetime, timezone
from pathlib import Path

# ---------------------------------------------------------------------------
# Deterministic pass-rate table (mirrors the Java TPCDSDemoRunner behaviour)
# Tier thresholds: VERIFIED ≥90, EDGE ≥70, BASIC ≥50, NONE <50
# ---------------------------------------------------------------------------
ENGINE_PASS_RATES = {
    "MockDB":     {"base": 0.82, "variance": 0.10},
    "FastDB":     {"base": 0.91, "variance": 0.06},
    "CloudDB":    {"base": 0.76, "variance": 0.12},
    "DuckDB":     {"base": 0.68, "variance": 0.14},
    "PostgreSQL": {"base": 0.72, "variance": 0.10},
}

ENGINE_VERSIONS = {
    "MockDB": "1.0.0",
    "FastDB": "2.5.0",
    "CloudDB": "3.1.0",
    "DuckDB": "0.10.0",
    "PostgreSQL": "16.0",
}

# Complexity band for each of the 99 TPC-DS queries (1-indexed).
# Bands: SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX
def _query_complexity(n: int) -> str:
    if n in {3, 7, 19, 26, 42, 52, 55, 63, 65, 68, 73, 79, 89, 96, 98}:
        return "SIMPLE"
    if n in {1, 2, 6, 8, 10, 12, 13, 15, 20, 27, 32, 34, 37, 43,
             46, 48, 50, 53, 54, 57, 59, 61, 69, 76, 80, 82, 84,
             85, 86, 87, 88, 90, 91, 92, 93, 94, 97, 99}:
        return "MEDIUM"
    if n in {4, 5, 9, 11, 14, 16, 17, 18, 21, 22, 23, 24, 25, 28,
             29, 30, 31, 33, 35, 36, 38, 39, 40, 41, 44, 45, 47,
             49, 51, 56, 58, 60, 62, 64, 66, 67, 70, 71, 72, 74,
             75, 77, 78, 81, 83}:
        return "COMPLEX"
    return "VERY_COMPLEX"


def _status_label(pass_rate: float) -> str:
    if pass_rate >= 90:
        return "🟢 VERIFIED"
    if pass_rate >= 70:
        return "🔵 EDGE"
    if pass_rate >= 50:
        return "🟡 BASIC"
    return "🔴 NONE"


class TPCDSDemo:
    """Demo runner for TPC-DS compliance tests."""

    QUERY_COUNT = 99

    def __init__(self):
        self.demo_root = Path(__file__).parent.parent
        self.output_dir = self.demo_root / "output"
        self.output_dir.mkdir(exist_ok=True)
        self.dashboard_data_dir = self.demo_root / "dashboard" / "data"
        self.dashboard_data_dir.mkdir(parents=True, exist_ok=True)

    # ------------------------------------------------------------------
    # Public entry point
    # ------------------------------------------------------------------

    def run(self):
        print()
        all_reports = []
        engines = list(ENGINE_PASS_RATES.keys())

        for engine_name in engines:
            print(f"{'─'*60}")
            report = self._test_engine(engine_name)
            all_reports.append(report)
            self._save_engine_report(engine_name, report)

        print(f"{'='*60}")
        self._print_summary(all_reports)
        self._write_leaderboard(all_reports)
        self._update_cross_suite_summary(all_reports)

        print()
        print("✅ TPC-DS demo completed successfully!")
        print(f"📊 Results saved to: {self.output_dir}/")
        print(f"📈 Dashboard data:   {self.dashboard_data_dir}/tpcds_leaderboard.json")
        print()
        print("🌐 View in dashboard:")
        print("   cd demo/dashboard && python3 -m http.server 8080")
        print("   Open: http://localhost:8080/tpcds-tests.html")

    # ------------------------------------------------------------------
    # Per-engine test simulation
    # ------------------------------------------------------------------

    def _test_engine(self, engine_name: str) -> dict:
        cfg = ENGINE_PASS_RATES[engine_name]
        version = ENGINE_VERSIONS[engine_name]
        timestamp_ms = int(datetime.now(timezone.utc).timestamp() * 1000)

        print(f"\n🔧 Testing: {engine_name} v{version}")

        # Use a stable seed per engine so results are deterministic
        rng = random.Random(hash(engine_name) & 0xFFFFFFFF)

        test_results = []
        passed = failed = skipped = 0

        for q in range(1, self.QUERY_COUNT + 1):
            query_id = f"Q{q:02d}"
            complexity = _query_complexity(q)

            # Complexity penalty keeps very-complex queries harder
            complexity_penalty = {
                "SIMPLE": 0.05,
                "MEDIUM": 0.0,
                "COMPLEX": -0.05,
                "VERY_COMPLEX": -0.12,
            }[complexity]

            roll = rng.random()
            effective_rate = cfg["base"] + complexity_penalty
            exec_ms = rng.randint(50, 800)

            if roll < effective_rate:
                status = "PASSED"
                passed += 1
                entry = {"testId": query_id, "complexity": complexity,
                         "status": status, "executionTimeMs": exec_ms}
            elif roll < effective_rate + 0.06:
                status = "SKIPPED"
                skipped += 1
                entry = {"testId": query_id, "complexity": complexity,
                         "status": status, "executionTimeMs": 0}
            else:
                status = "FAILED"
                failed += 1
                entry = {"testId": query_id, "complexity": complexity,
                         "status": status, "executionTimeMs": exec_ms,
                         "errorMessage": f"Query execution failed: simulated failure for {query_id}"}

            test_results.append(entry)

        total = passed + failed + skipped
        pass_rate = round((passed / total) * 100, 1) if total > 0 else 0.0

        print(f"   Total Tests: {total}  ✅ Passed: {passed}  "
              f"❌ Failed: {failed}   ⏭️  Skipped: {skipped}")
        print(f"   📊 Pass Rate: {pass_rate}%")

        return {
            "engineName": engine_name,
            "engineVersion": version,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "testSuiteName": "tpcds",
            "totalTests": total,
            "passed": passed,
            "failed": failed,
            "skipped": skipped,
            "passRate": pass_rate,
            "testResults": test_results,
        }

    # ------------------------------------------------------------------
    # Output helpers
    # ------------------------------------------------------------------

    def _save_engine_report(self, engine_name: str, report: dict):
        filename = self.output_dir / f"{engine_name.lower()}-tpcds-report.json"
        with open(filename, "w") as f:
            json.dump(report, f, indent=2)
        print(f"   💾 Report saved: {filename}")

    def _print_summary(self, reports: list):
        sorted_reports = sorted(reports, key=lambda r: r["passRate"], reverse=True)
        print()
        print("📊 TPC-DS Demo Summary")
        print("Note: results are from deterministic simulated demo engines.")
        print()
        print(f"{'':2} {'Engine':<12} {'Version':<10} {'Pass Rate':>10}  Status")
        print("─" * 55)
        medals = ["🥇", "🥈", "🥉"]
        for i, r in enumerate(sorted_reports):
            medal = medals[i] if i < 3 else "  "
            label = _status_label(r["passRate"])
            print(f"{medal} {r['engineName']:<12} {r['engineVersion']:<10} "
                  f"{r['passRate']:>8.1f}%  {label}")

    def _write_leaderboard(self, reports: list):
        sorted_reports = sorted(reports, key=lambda r: r["passRate"], reverse=True)
        avg = round(sum(r["passRate"] for r in sorted_reports) / len(sorted_reports), 1)

        engines = []
        for i, r in enumerate(sorted_reports):
            engines.append({
                "rank": i + 1,
                "name": r["engineName"],
                "engineName": r["engineName"],
                "version": r["engineVersion"],
                "engineVersion": r["engineVersion"],
                "passRate": r["passRate"],
                "passed": r["passed"],
                "failed": r["failed"],
                "skipped": r["skipped"],
                "totalTests": r["totalTests"],
                "timestamp": r["timestamp"],
            })

        leaderboard = {
            "lastUpdated": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "totalEngines": len(engines),
            "testSuite": "TPC-DS",
            "queryCount": self.QUERY_COUNT,
            "averagePassRate": avg,
            "engines": engines,
        }

        # Write to output/ and dashboard/data/
        for dest in [
            self.output_dir / "leaderboard-tpcds.json",
            self.dashboard_data_dir / "tpcds_leaderboard.json",
        ]:
            with open(dest, "w") as f:
                json.dump(leaderboard, f, indent=2)
            print(f"   💾 Leaderboard saved: {dest}")

    def _update_cross_suite_summary(self, reports: list):
        """Merge TPC-DS results into the shared dashboard summary.json."""
        summary_path = self.dashboard_data_dir / "summary.json"

        existing = {"engines": []}
        if summary_path.exists():
            with open(summary_path) as f:
                existing = json.load(f)

        by_engine = {e["engineName"]: e for e in existing.get("engines", [])}

        tpcds_by_engine = {r["engineName"]: r for r in reports}

        for engine_name, r in tpcds_by_engine.items():
            entry = by_engine.get(engine_name, {"engineName": engine_name,
                                                 "engineVersion": r["engineVersion"]})
            entry["tpcds"] = {
                "passRate": r["passRate"],
                "totalTests": r["totalTests"],
                "passed": r["passed"],
                "failed": r["failed"],
                "skipped": r["skipped"],
            }
            by_engine[engine_name] = entry

        existing["lastUpdated"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        existing["totalEngines"] = len(by_engine)
        existing["engines"] = list(by_engine.values())

        with open(summary_path, "w") as f:
            json.dump(existing, f, indent=2)
        print(f"   💾 Cross-suite summary updated: {summary_path}")


def main():
    demo = TPCDSDemo()
    demo.run()


if __name__ == "__main__":
    main()
