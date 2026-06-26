import os
from pathlib import Path

from compliance_runner import ComplianceRunner, YamlTestSuiteLoader
from duckdb_engine_real import RealDuckDBComplianceEngine, TestStatus


def main():
    script_dir = Path(__file__).parent
    metadata_path = script_dir.parent.parent / "test-suites" / "tpcds" / "metadata.yaml"

    loader = YamlTestSuiteLoader()
    suite = loader.load_from_file(str(metadata_path))

    print(f"Loaded suite: {suite.name}")
    print(f"Discovered tests: {suite.get_test_count()}")

    q01 = next((test for test in suite.test_cases if test.test_id == "q01"), None)
    q06 = next((test for test in suite.test_cases if test.test_id == "q06"), None)

    if q01 is None or q06 is None:
        raise RuntimeError("Expected q01 and q06 to be present in the TPC-DS suite")

    print(f"q01 input tables: {len(q01.input_data or [])}")
    print(f"q06 input tables: {len(q06.input_data or [])}")

    q01_files = [table.get("file") for table in (q01.input_data or [])]
    q06_files = [table.get("file") for table in (q06.input_data or [])]

    print(f"q01 files sample: {q01_files[:3]}")
    print(f"q06 files sample: {q06_files[:3]}")

    missing_q01 = [
        table_file for table_file in q01_files
        if table_file and not os.path.exists(os.path.join(os.path.dirname(q01.plan_path), table_file))
    ]
    missing_q06 = [
        table_file for table_file in q06_files
        if table_file and not os.path.exists(os.path.join(os.path.dirname(q06.plan_path), table_file))
    ]

    print(f"q01 missing files: {missing_q01}")
    print(f"q06 missing files count: {len(missing_q06)}")

    engine = RealDuckDBComplianceEngine()
    runner = ComplianceRunner(engine=engine, verbose=False)

    q01_result = runner.run_test_case(q01)
    q06_result = runner.run_test_case(q06)

    print(f"q01 status: {q01_result.status}")
    print(f"q01 error: {q01_result.error_message}")
    print(f"q06 status: {q06_result.status}")
    print(f"q06 error: {q06_result.error_message}")

    if q01_result.status == TestStatus.SKIPPED and "No input data provided" in (q01_result.error_message or ""):
        raise AssertionError("q01 should no longer be skipped due to missing input data")

    if q06_result.status == TestStatus.SKIPPED and "No input data provided" in (q06_result.error_message or ""):
        raise AssertionError("q06 should no longer be skipped due to missing input data")

    print("TPC-DS input loading validation passed")


if __name__ == "__main__":
    main()

# Made with Bob
