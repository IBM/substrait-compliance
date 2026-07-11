#!/usr/bin/env python3
"""
Substrait Function-Level Compliance Testing Demo

This demo showcases the function test framework by:
1. Parsing function test files
2. Generating simulated test results
3. Creating JSON output files
4. Displaying summary statistics
"""

import json
import sys
from pathlib import Path
from datetime import datetime

# Add SDK to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "sdk" / "python"))

try:
    from substrait_compliance.function_test_parser import FunctionTestParser
except ImportError:
    print("⚠️  SDK not found. Running in standalone mode...")
    FunctionTestParser = None


class FunctionTestDemo:
    """Demo runner for function tests."""
    
    def __init__(self):
        self.test_suites_dir = Path(__file__).parent.parent.parent / "test-suites" / "functions"
        self.output_dir = Path(__file__).parent.parent / "output"
        self.output_dir.mkdir(exist_ok=True)
        
    def run(self):
        """Run the demo."""
        print("╔════════════════════════════════════════════════════════════╗")
        print("║   Substrait Function-Level Compliance Testing Demo        ║")
        print("╚════════════════════════════════════════════════════════════╝")
        print()
        
        # Test each engine
        engines = ["MockDB", "FastDB", "CloudDB", "DuckDB", "PostgreSQL"]
        all_results = []
        
        for engine_name in engines:
            print(f"\n{'='*60}")
            print(f"Testing Engine: {engine_name}")
            print('='*60)
            
            results = self.test_engine(engine_name)
            all_results.append(results)
            
            # Save engine results
            self.save_engine_results(engine_name, results)
        
        # Generate summary
        self.generate_summary(all_results)
        
        print("\n" + "="*60)
        print("✅ Demo completed successfully!")
        print(f"📊 Results saved to: {self.output_dir}/")
        print("🌐 View results in the output directory")
        print("="*60)
    
    def scan_test_categories(self):
        """Scan test-suites/functions directory for all categories."""
        categories = {}
        
        # Default test counts per category
        default_counts = {
            "aggregate": {"files": 6, "avg_tests": 28},
            "window": {"files": 6, "avg_tests": 32},
            "cast": {"files": 2, "avg_tests": 72},
            "boolean": {"files": 4, "avg_tests": 50},
            "datetime": {"files": 4, "avg_tests": 40},
            "arithmetic": {"files": 11, "avg_tests": 30},
            "string": {"files": 20, "avg_tests": 35},
            "comparison": {"files": 15, "avg_tests": 25},
            "array": {"files": 6, "avg_tests": 25},
            "struct": {"files": 2, "avg_tests": 20},
            "map": {"files": 3, "avg_tests": 20},
            "json": {"files": 2, "avg_tests": 25},
            "conditional": {"files": 2, "avg_tests": 28},
            "set": {"files": 3, "avg_tests": 27},
            "geospatial": {"files": 4, "avg_tests": 20}
        }
        
        if self.test_suites_dir.exists():
            for category_dir in sorted(self.test_suites_dir.iterdir()):
                if category_dir.is_dir():
                    category_name = category_dir.name
                    # Use default if available, otherwise estimate
                    if category_name in default_counts:
                        categories[category_name] = default_counts[category_name]
                    else:
                        # Count actual test files
                        test_files = list(category_dir.glob("*.test"))
                        categories[category_name] = {
                            "files": len(test_files),
                            "avg_tests": 20  # default estimate
                        }
        else:
            # Fallback to defaults if directory doesn't exist
            categories = default_counts
        
        return categories
    
    def test_engine(self, engine_name):
        """Test an engine across all categories."""
        # Scan for all available categories
        categories = self.scan_test_categories()
        
        results = {
            "engine": engine_name,
            "timestamp": int(datetime.now().timestamp() * 1000),
            "categories": {},
            "totalTests": 0,
            "totalPassed": 0
        }
        
        for category, info in categories.items():
            print(f"\n📂 Category: {category}")
            
            # Count actual test files if parser available
            category_dir = self.test_suites_dir / category
            if category_dir.exists():
                test_files = list(category_dir.glob("*.test"))
                num_files = len(test_files)
                
                # Parse files to count tests if parser available
                if FunctionTestParser:
                    parser = FunctionTestParser()
                    total_tests = 0
                    for test_file in test_files:
                        try:
                            suite = parser.parse_file(test_file)
                            total_tests += len(suite.test_cases)
                        except Exception as e:
                            print(f"   ⚠️  Could not parse {test_file.name}: {e}")
                    
                    if total_tests == 0:
                        total_tests = num_files * info["avg_tests"]
                else:
                    total_tests = num_files * info["avg_tests"]
            else:
                total_tests = info["files"] * info["avg_tests"]
            
            # Simulate test execution with realistic pass rates
            pass_rate = self.get_pass_rate(engine_name, category)
            passed = int(total_tests * pass_rate)
            failed = total_tests - passed
            
            results["categories"][category] = {
                "total": total_tests,
                "passed": passed,
                "failed": failed,
                "passRate": round(pass_rate * 100, 1)
            }
            
            results["totalTests"] += total_tests
            results["totalPassed"] += passed
            
            print(f"   Tests: {total_tests} | Passed: {passed} | Failed: {failed} | Pass Rate: {pass_rate*100:.1f}%")
        
        results["overallPassRate"] = round((results["totalPassed"] / results["totalTests"]) * 100, 1) if results["totalTests"] > 0 else 0
        
        return results
    
    def get_pass_rate(self, engine_name, category):
        """Get simulated pass rate for engine/category."""
        rates = {
            "MockDB": {
                "aggregate": 0.75, "window": 0.65, "cast": 0.80, "boolean": 0.90, "datetime": 0.70,
                "arithmetic": 0.85, "string": 0.88, "comparison": 0.92,
                "array": 0.60, "struct": 0.55, "map": 0.58, "json": 0.50,
                "conditional": 0.82, "set": 0.65, "geospatial": 0.45
            },
            "FastDB": {
                "aggregate": 0.92, "window": 0.88, "cast": 0.95, "boolean": 0.98, "datetime": 0.85,
                "arithmetic": 0.96, "string": 0.94, "comparison": 0.98,
                "array": 0.85, "struct": 0.80, "map": 0.82, "json": 0.78,
                "conditional": 0.93, "set": 0.88, "geospatial": 0.72
            },
            "CloudDB": {
                "aggregate": 0.96, "window": 0.94, "cast": 0.98, "boolean": 1.00, "datetime": 0.92,
                "arithmetic": 0.99, "string": 0.97, "comparison": 1.00,
                "array": 0.92, "struct": 0.90, "map": 0.91, "json": 0.88,
                "conditional": 0.97, "set": 0.94, "geospatial": 0.85
            },
            "DuckDB": {
                "aggregate": 0.94, "window": 0.91, "cast": 0.96, "boolean": 0.99, "datetime": 0.89,
                "arithmetic": 0.97, "string": 0.95, "comparison": 0.99,
                "array": 0.88, "struct": 0.84, "map": 0.86, "json": 0.82,
                "conditional": 0.95, "set": 0.91, "geospatial": 0.78
            },
            "PostgreSQL": {
                "aggregate": 0.90, "window": 0.86, "cast": 0.92, "boolean": 0.96, "datetime": 0.85,
                "arithmetic": 0.94, "string": 0.91, "comparison": 0.97,
                "array": 0.82, "struct": 0.78, "map": 0.80, "json": 0.75,
                "conditional": 0.91, "set": 0.87, "geospatial": 0.70
            }
        }
        return rates.get(engine_name, {}).get(category, 0.80)
    
    def save_engine_results(self, engine_name, results):
        """Save engine results to JSON file."""
        filename = self.output_dir / f"{engine_name}_function_tests.json"
        with open(filename, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"\n💾 Saved: {filename}")
    
    def generate_summary(self, all_results):
        """Generate and save summary report."""
        print("\n" + "="*60)
        print("Summary Report")
        print("="*60)
        
        summary = {
            "timestamp": int(datetime.now().timestamp() * 1000),
            "testSuiteType": "function_tests",
            "engines": all_results
        }
        
        # Save summary
        filename = self.output_dir / "function_tests_summary.json"
        with open(filename, 'w') as f:
            json.dump(summary, f, indent=2)
        
        # Print table
        print("\n┌─────────────────┬────────┬─────────┬─────────┬───────────┐")
        print("│ Engine          │ Total  │ Passed  │ Failed  │ Pass Rate │")
        print("├─────────────────┼────────┼─────────┼─────────┼───────────┤")
        
        for result in all_results:
            engine = result["engine"]
            total = result["totalTests"]
            passed = result["totalPassed"]
            failed = total - passed
            pass_rate = result["overallPassRate"]
            
            print(f"│ {engine:15} │ {total:6} │ {passed:7} │ {failed:7} │ {pass_rate:8.1f}% │")
        
        print("└─────────────────┴────────┴─────────┴─────────┴───────────┘")
        
        print(f"\n💾 Saved: {filename}")
        self.generate_shared_summary(all_results)
    
    def generate_shared_summary(self, all_results):
        """Generate shared dashboard summary with function and TPC-H data."""
        demo_root = Path(__file__).parent.parent
        summary_path = demo_root / "dashboard" / "data" / "summary.json"
        leaderboard_path = demo_root / "output" / "leaderboard.json"
        
        existing_summary = {"engines": []}
        if summary_path.exists():
            with open(summary_path, 'r') as f:
                existing_summary = json.load(f)
        
        existing_by_engine = {
            engine["engineName"]: engine
            for engine in existing_summary.get("engines", [])
        }
        
        tpch_by_engine = {}
        if leaderboard_path.exists():
            with open(leaderboard_path, 'r') as f:
                leaderboard = json.load(f)
            for engine in leaderboard.get("engines", []):
                engine_name = engine.get("engineName")
                if engine_name:
                    tpch_by_engine[engine_name] = {
                        "passRate": engine.get("passRate", 0.0),
                        "totalTests": engine.get("totalTests", 0),
                        "passed": engine.get("passed", 0),
                        "failed": engine.get("failed", 0),
                        "skipped": engine.get("skipped", 0)
                    }
        
        merged_engines = []
        for result in all_results:
            engine_name = result["engine"]
            engine_summary = existing_by_engine.get(engine_name, {
                "engineName": engine_name,
                "engineVersion": "demo"
            })
            
            engine_summary["functions"] = {
                "passRate": result["overallPassRate"],
                "totalTests": result["totalTests"],
                "passed": result["totalPassed"],
                "failed": result["totalTests"] - result["totalPassed"]
            }
            
            engine_summary["tpch"] = tpch_by_engine.get(engine_name, engine_summary.get("tpch", {
                "passRate": 0.0,
                "totalTests": 0,
                "passed": 0,
                "failed": 0,
                "skipped": 0
            }))
            merged_engines.append(engine_summary)
        
        shared_summary = {
            "lastUpdated": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
            "totalEngines": len(merged_engines),
            "engines": merged_engines
        }
        
        with open(summary_path, 'w') as f:
            json.dump(shared_summary, f, indent=2)


def main():
    """Main entry point."""
    demo = FunctionTestDemo()
    demo.run()


if __name__ == "__main__":
    main()

