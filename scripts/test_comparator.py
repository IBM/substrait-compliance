#!/usr/bin/env python3
"""
Test Comparator - Compare original and enhanced test files.

This script provides detailed comparison between original and enhanced test files:
- Side-by-side diff view
- Statistics on added/modified tests
- Quality score improvements
- Visual comparison reports
"""

import sys
import json
from pathlib import Path
from typing import Dict, List, Any, Tuple
from datetime import datetime
import difflib

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from sdk.python.substrait_compliance.function_test_parser import FunctionTestParser


class TestComparator:
    """Compare original and enhanced test files."""
    
    def __init__(self):
        """Initialize the test comparator."""
        self.parser = FunctionTestParser()
    
    def compare_files(
        self,
        original_file: Path,
        enhanced_file: Path,
        verbose: bool = True
    ) -> Dict[str, Any]:
        """
        Compare two test files.
        
        Args:
            original_file: Path to original test file
            enhanced_file: Path to enhanced test file
            verbose: Print detailed comparison
            
        Returns:
            Comparison report
        """
        if verbose:
            print(f"\nComparing: {original_file.name}")
            print("="*60)
        
        # Read file contents
        with open(original_file, 'r') as f:
            original_content = f.read()
        
        with open(enhanced_file, 'r') as f:
            enhanced_content = f.read()
        
        # Parse test suites
        try:
            original_suite = self.parser.parse_file(original_file)
            enhanced_suite = self.parser.parse_file(enhanced_file)
        except Exception as e:
            return {
                "status": "error",
                "error": f"Failed to parse files: {e}"
            }
        
        # Calculate statistics
        original_count = len(original_suite.test_cases)
        enhanced_count = len(enhanced_suite.test_cases)
        tests_added = enhanced_count - original_count
        
        # Generate diff
        diff = self._generate_diff(original_content, enhanced_content)
        
        # Analyze changes
        changes = self._analyze_changes(original_suite, enhanced_suite)
        
        report = {
            "original_file": str(original_file),
            "enhanced_file": str(enhanced_file),
            "original_test_count": original_count,
            "enhanced_test_count": enhanced_count,
            "tests_added": tests_added,
            "percentage_increase": round((tests_added / original_count * 100), 2) if original_count > 0 else 0,
            "changes": changes,
            "diff_lines": len(diff),
            "timestamp": datetime.now().isoformat()
        }
        
        if verbose:
            self._print_comparison(report, diff[:50])  # Show first 50 diff lines
        
        return report
    
    def _generate_diff(
        self,
        original_content: str,
        enhanced_content: str
    ) -> List[str]:
        """Generate unified diff between two files."""
        original_lines = original_content.splitlines(keepends=True)
        enhanced_lines = enhanced_content.splitlines(keepends=True)
        
        diff = list(difflib.unified_diff(
            original_lines,
            enhanced_lines,
            fromfile='original',
            tofile='enhanced',
            lineterm=''
        ))
        
        return diff
    
    def _analyze_changes(self, original_suite, enhanced_suite) -> Dict[str, Any]:
        """Analyze what changed between test suites."""
        
        # Group tests by category
        original_categories = {}
        for test in original_suite.test_cases:
            if test.category not in original_categories:
                original_categories[test.category] = []
            original_categories[test.category].append(test)
        
        enhanced_categories = {}
        for test in enhanced_suite.test_cases:
            if test.category not in enhanced_categories:
                enhanced_categories[test.category] = []
            enhanced_categories[test.category].append(test)
        
        # Find new categories
        new_categories = set(enhanced_categories.keys()) - set(original_categories.keys())
        
        # Count tests per category
        category_changes = {}
        for category in enhanced_categories:
            original_count = len(original_categories.get(category, []))
            enhanced_count = len(enhanced_categories[category])
            if enhanced_count > original_count:
                category_changes[category] = {
                    "original": original_count,
                    "enhanced": enhanced_count,
                    "added": enhanced_count - original_count
                }
        
        return {
            "new_categories": list(new_categories),
            "category_changes": category_changes,
            "total_categories_original": len(original_categories),
            "total_categories_enhanced": len(enhanced_categories)
        }
    
    def _print_comparison(self, report: Dict[str, Any], diff_preview: List[str]):
        """Print comparison report to console."""
        
        print(f"\nOriginal Tests: {report['original_test_count']}")
        print(f"Enhanced Tests: {report['enhanced_test_count']}")
        print(f"Tests Added: {report['tests_added']} (+{report['percentage_increase']}%)")
        
        changes = report['changes']
        if changes['new_categories']:
            print(f"\nNew Categories: {', '.join(changes['new_categories'])}")
        
        if changes['category_changes']:
            print(f"\nCategory Changes:")
            for category, stats in changes['category_changes'].items():
                print(f"  {category}: {stats['original']} → {stats['enhanced']} (+{stats['added']})")
        
        if diff_preview:
            print(f"\nDiff Preview (first 50 lines):")
            print("-"*60)
            for line in diff_preview[:50]:
                if line.startswith('+') and not line.startswith('+++'):
                    print(f"\033[92m{line}\033[0m")  # Green for additions
                elif line.startswith('-') and not line.startswith('---'):
                    print(f"\033[91m{line}\033[0m")  # Red for deletions
                else:
                    print(line)
            print("-"*60)
    
    def compare_category(
        self,
        category: str,
        original_base: Path = Path("test-suites/functions"),
        enhanced_base: Path = Path("test-suites-enhanced/functions"),
        verbose: bool = True
    ) -> Dict[str, Any]:
        """
        Compare all test files in a category.
        
        Args:
            category: Category name
            original_base: Base directory for original tests
            enhanced_base: Base directory for enhanced tests
            verbose: Print progress
            
        Returns:
            Category comparison summary
        """
        original_dir = original_base / category
        enhanced_dir = enhanced_base / category
        
        if not original_dir.exists():
            return {
                "category": category,
                "status": "error",
                "error": f"Original directory not found: {original_dir}"
            }
        
        if not enhanced_dir.exists():
            return {
                "category": category,
                "status": "error",
                "error": f"Enhanced directory not found: {enhanced_dir}"
            }
        
        if verbose:
            print(f"\n{'='*60}")
            print(f"Comparing Category: {category}")
            print('='*60)
        
        results = []
        
        for original_file in sorted(original_dir.glob("*.test")):
            enhanced_file = enhanced_dir / original_file.name
            
            if not enhanced_file.exists():
                if verbose:
                    print(f"\n⚠ Skipping {original_file.name} (no enhanced version)")
                continue
            
            result = self.compare_files(original_file, enhanced_file, verbose=False)
            results.append(result)
            
            if verbose:
                print(f"\n{original_file.name}:")
                print(f"  Original: {result['original_test_count']} tests")
                print(f"  Enhanced: {result['enhanced_test_count']} tests")
                print(f"  Added: {result['tests_added']} (+{result['percentage_increase']}%)")
        
        # Calculate category statistics
        total_original = sum(r['original_test_count'] for r in results)
        total_enhanced = sum(r['enhanced_test_count'] for r in results)
        total_added = sum(r['tests_added'] for r in results)
        
        summary = {
            "category": category,
            "files_compared": len(results),
            "total_original_tests": total_original,
            "total_enhanced_tests": total_enhanced,
            "total_tests_added": total_added,
            "percentage_increase": round((total_added / total_original * 100), 2) if total_original > 0 else 0,
            "file_comparisons": results,
            "timestamp": datetime.now().isoformat()
        }
        
        if verbose:
            print(f"\n{'='*60}")
            print(f"Category Summary:")
            print(f"  Files: {summary['files_compared']}")
            print(f"  Original Tests: {summary['total_original_tests']}")
            print(f"  Enhanced Tests: {summary['total_enhanced_tests']}")
            print(f"  Tests Added: {summary['total_tests_added']} (+{summary['percentage_increase']}%)")
            print('='*60)
        
        return summary
    
    def generate_html_report(
        self,
        comparison_data: Dict[str, Any],
        output_file: Path
    ):
        """Generate HTML comparison report."""
        
        html = f"""<!DOCTYPE html>
<html>
<head>
    <title>Test Comparison Report - {comparison_data.get('category', 'Unknown')}</title>
    <style>
        body {{
            font-family: Arial, sans-serif;
            margin: 20px;
            background-color: #f5f5f5;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background-color: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }}
        h1 {{
            color: #333;
            border-bottom: 3px solid #4CAF50;
            padding-bottom: 10px;
        }}
        h2 {{
            color: #555;
            margin-top: 30px;
        }}
        .stats {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }}
        .stat-card {{
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
        }}
        .stat-card.green {{
            background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
        }}
        .stat-card h3 {{
            margin: 0;
            font-size: 2em;
        }}
        .stat-card p {{
            margin: 5px 0 0 0;
            opacity: 0.9;
        }}
        table {{
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
        }}
        th, td {{
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }}
        th {{
            background-color: #4CAF50;
            color: white;
        }}
        tr:hover {{
            background-color: #f5f5f5;
        }}
        .added {{
            color: #4CAF50;
            font-weight: bold;
        }}
        .timestamp {{
            color: #888;
            font-size: 0.9em;
            margin-top: 30px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>Test Comparison Report: {comparison_data.get('category', 'Unknown')}</h1>
        
        <div class="stats">
            <div class="stat-card">
                <h3>{comparison_data.get('files_compared', 0)}</h3>
                <p>Files Compared</p>
            </div>
            <div class="stat-card">
                <h3>{comparison_data.get('total_original_tests', 0)}</h3>
                <p>Original Tests</p>
            </div>
            <div class="stat-card green">
                <h3>{comparison_data.get('total_enhanced_tests', 0)}</h3>
                <p>Enhanced Tests</p>
            </div>
            <div class="stat-card green">
                <h3>+{comparison_data.get('total_tests_added', 0)}</h3>
                <p>Tests Added ({comparison_data.get('percentage_increase', 0)}%)</p>
            </div>
        </div>
        
        <h2>File-by-File Comparison</h2>
        <table>
            <thead>
                <tr>
                    <th>File</th>
                    <th>Original Tests</th>
                    <th>Enhanced Tests</th>
                    <th>Tests Added</th>
                    <th>Increase %</th>
                </tr>
            </thead>
            <tbody>
"""
        
        for file_comp in comparison_data.get('file_comparisons', []):
            filename = Path(file_comp['original_file']).name
            html += f"""
                <tr>
                    <td>{filename}</td>
                    <td>{file_comp['original_test_count']}</td>
                    <td>{file_comp['enhanced_test_count']}</td>
                    <td class="added">+{file_comp['tests_added']}</td>
                    <td>{file_comp['percentage_increase']}%</td>
                </tr>
"""
        
        html += f"""
            </tbody>
        </table>
        
        <p class="timestamp">Generated: {comparison_data.get('timestamp', 'Unknown')}</p>
    </div>
</body>
</html>
"""
        
        output_file.parent.mkdir(parents=True, exist_ok=True)
        with open(output_file, 'w') as f:
            f.write(html)


def main():
    """Main entry point for test comparison."""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Compare original and enhanced Substrait function tests"
    )
    parser.add_argument(
        "category",
        nargs="?",
        help="Test category to compare (e.g., arithmetic)"
    )
    parser.add_argument(
        "--file",
        help="Specific test file to compare (provide original file path)"
    )
    parser.add_argument(
        "--original-dir",
        default="test-suites/functions",
        help="Directory containing original tests"
    )
    parser.add_argument(
        "--enhanced-dir",
        default="test-suites-enhanced/functions",
        help="Directory containing enhanced tests"
    )
    parser.add_argument(
        "--output",
        default="quality_reports",
        help="Output directory for comparison reports"
    )
    parser.add_argument(
        "--html",
        action="store_true",
        help="Generate HTML report"
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress progress messages"
    )
    
    args = parser.parse_args()
    
    comparator = TestComparator()
    
    print("="*60)
    print("Substrait Test Comparator")
    print("="*60)
    
    if args.file:
        # Compare specific file
        original_file = Path(args.file)
        if not original_file.exists():
            print(f"Error: File not found: {original_file}")
            sys.exit(1)
        
        # Determine enhanced file path
        rel_path = original_file.relative_to(args.original_dir)
        enhanced_file = Path(args.enhanced_dir) / rel_path
        
        if not enhanced_file.exists():
            print(f"Error: Enhanced file not found: {enhanced_file}")
            sys.exit(1)
        
        result = comparator.compare_files(
            original_file,
            enhanced_file,
            verbose=not args.quiet
        )
        
        # Save result
        output_file = Path(args.output) / "comparison_result.json"
        output_file.parent.mkdir(exist_ok=True)
        with open(output_file, 'w') as f:
            json.dump(result, f, indent=2)
        
        print(f"\nComparison saved to: {output_file}")
        
    elif args.category:
        # Compare category
        summary = comparator.compare_category(
            args.category,
            Path(args.original_dir),
            Path(args.enhanced_dir),
            verbose=not args.quiet
        )
        
        # Save JSON summary
        json_file = Path(args.output) / f"{args.category}_comparison.json"
        json_file.parent.mkdir(exist_ok=True)
        with open(json_file, 'w') as f:
            json.dump(summary, f, indent=2)
        
        print(f"\nComparison saved to: {json_file}")
        
        # Generate HTML report if requested
        if args.html:
            html_file = Path(args.output) / f"{args.category}_comparison.html"
            comparator.generate_html_report(summary, html_file)
            print(f"HTML report saved to: {html_file}")
    
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()

# Made with Bob
