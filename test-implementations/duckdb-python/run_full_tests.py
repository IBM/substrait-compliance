"""
Full End-to-End Compliance Test Runner

This script runs the complete compliance test suite against DuckDB,
loading real test cases from the test-suites directory.
"""

import sys
import os
from pathlib import Path

from duckdb_engine import DuckDBComplianceEngine
from compliance_runner import (
    ComplianceRunner,
    YamlTestSuiteLoader
)


def find_test_suites_directory():
    """Find the test-suites directory relative to this script"""
    # Go up from test-implementations/duckdb-python to project root
    current_dir = Path(__file__).parent
    project_root = current_dir.parent.parent
    test_suites_dir = project_root / "test-suites"
    
    if test_suites_dir.exists():
        return str(test_suites_dir)
    
    # Alternative: look for test-suites in current directory
    if Path("../../test-suites").exists():
        return "../../test-suites"
    
    return None


def main():
    """Run full compliance tests"""
    print("=" * 70)
    print("DuckDB Full Compliance Test Suite Runner")
    print("=" * 70)
    
    # Find test suites directory
    test_suites_dir = find_test_suites_directory()
    
    if not test_suites_dir:
        print("\n❌ Error: Could not find test-suites directory")
        print("Expected location: ../../test-suites")
        print("\nPlease run this script from the test-implementations/duckdb-python directory")
        return 1
    
    print(f"\n📁 Test suites directory: {test_suites_dir}")
    
    # Create engine
    engine = DuckDBComplianceEngine()
    
    # Display engine info
    info = engine.get_info()
    print(f"\n🔧 Engine: {info.name} v{info.version}")
    print(f"   Vendor: {info.vendor}")
    
    # Create loader and runner
    loader = YamlTestSuiteLoader()
    runner = ComplianceRunner(engine, verbose=True)
    
    # Discover test suites
    print(f"\n🔍 Discovering test suites...")
    test_files = loader.discover_test_suites(test_suites_dir)
    
    if not test_files:
        print(f"\n⚠️  No test suite files found in {test_suites_dir}")
        print("   Looking for files: metadata.yaml, *.test")
        return 1
    
    print(f"   Found {len(test_files)} test suite file(s)")
    
    # Show discovered files
    print("\n📋 Test suite files:")
    for idx, test_file in enumerate(test_files[:10], 1):  # Show first 10
        rel_path = os.path.relpath(test_file, test_suites_dir)
        print(f"   {idx}. {rel_path}")
    if len(test_files) > 10:
        print(f"   ... and {len(test_files) - 10} more")
    
    # Ask user which suites to run
    print("\n" + "=" * 70)
    print("Select test suites to run:")
    print("  1. Run all test suites")
    print("  2. Run specific test suite (by number)")
    print("  3. Run arithmetic tests only")
    print("  4. Run comparison tests only")
    print("  0. Exit")
    
    try:
        choice = input("\nEnter choice (default: 1): ").strip() or "1"
    except (KeyboardInterrupt, EOFError):
        print("\n\nExiting...")
        return 0
    
    # Determine which files to run
    files_to_run = []
    
    if choice == "0":
        print("Exiting...")
        return 0
    elif choice == "1":
        files_to_run = test_files
    elif choice == "2":
        try:
            idx = int(input(f"Enter test suite number (1-{len(test_files)}): ")) - 1
            if 0 <= idx < len(test_files):
                files_to_run = [test_files[idx]]
            else:
                print("Invalid number")
                return 1
        except (ValueError, KeyboardInterrupt, EOFError):
            print("\nInvalid input")
            return 1
    elif choice == "3":
        files_to_run = [f for f in test_files if 'arithmetic' in f.lower()]
    elif choice == "4":
        files_to_run = [f for f in test_files if 'comparison' in f.lower()]
    else:
        print("Invalid choice")
        return 1
    
    if not files_to_run:
        print("No test suites selected")
        return 1
    
    # Run selected test suites
    print("\n" + "=" * 70)
    print(f"Running {len(files_to_run)} test suite(s)...")
    print("=" * 70)
    
    all_reports = []
    total_passed = 0
    total_tests = 0
    
    for test_file in files_to_run:
        try:
            # Load test suite
            suite = loader.load_from_file(test_file)
            
            # Run tests
            report = runner.run_test_suite(suite)
            all_reports.append(report)
            
            total_passed += report.get_passed_count()
            total_tests += report.get_total_count()
            
            # Print individual report
            runner.print_report(report)
            
        except Exception as e:
            print(f"\n❌ Error loading/running {test_file}:")
            print(f"   {str(e)}")
            continue
    
    # Print overall summary
    print("\n" + "=" * 70)
    print("OVERALL SUMMARY")
    print("=" * 70)
    print(f"Test Suites Run:  {len(all_reports)}")
    print(f"Total Tests:      {total_tests}")
    print(f"Total Passed:     {total_passed}")
    print(f"Total Failed:     {total_tests - total_passed}")
    if total_tests > 0:
        print(f"Overall Pass Rate: {(total_passed / total_tests * 100):.1f}%")
    print("=" * 70)
    
    # Cleanup
    engine.close()
    
    # Return exit code
    return 0 if total_passed == total_tests else 1


if __name__ == "__main__":
    sys.exit(main())

# Made with Bob
