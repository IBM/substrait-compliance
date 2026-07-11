"""
Batch quality checker for all function test categories.
Processes multiple test categories and generates comprehensive reports.
"""

import json
import sys
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Any

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

try:
    from quality_checker import TestQualityChecker, LITELLM_API_KEY, LITELLM_BASE_URL, MODEL
except ImportError:
    print("Error: Could not import quality_checker. Make sure it's in the same directory.")
    sys.exit(1)

from sdk.python.substrait_compliance.function_test_parser import FunctionTestParser


def check_all_categories(
    categories: List[str] = None,
    output_dir: str = "quality_reports",
    verbose: bool = True
) -> Dict[str, Any]:
    """
    Check quality of all test categories.
    
    Args:
        categories: List of category names to check (None = all)
        output_dir: Directory to save reports
        verbose: Print progress messages
        
    Returns:
        Summary of all quality checks
    """
    
    # Initialize checker
    if verbose:
        print("="*60)
        print("Substrait Function Test Quality Checker")
        print("="*60)
        print(f"LiteLLM Base URL: {LITELLM_BASE_URL}")
        print(f"Model: {MODEL}")
        print("="*60)
        print()
    
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL,
        model=MODEL
    )
    
    parser = FunctionTestParser()
    base_dir = Path("test-suites/functions")
    
    # Default categories if none specified
    if categories is None:
        categories = [
            "arithmetic",
            "boolean", 
            "comparison",
            "string",
            "datetime",
            "aggregate",
            "window",
            "cast",
            "array",
            "conditional",
            "geospatial",
            "json",
            "map",
            "set",
            "struct"
        ]
    
    all_results = {}
    summary_stats = {
        "total_categories": 0,
        "total_files": 0,
        "total_tests": 0,
        "total_tokens": 0,
        "categories_processed": [],
        "categories_failed": [],
        "start_time": datetime.now().isoformat()
    }
    
    # Process each category
    for category in categories:
        if verbose:
            print(f"\n{'='*60}")
            print(f"Category: {category}")
            print('='*60)
        
        category_dir = base_dir / category
        if not category_dir.exists():
            if verbose:
                print(f"  ⚠ Skipping (directory not found)")
            summary_stats["categories_failed"].append(category)
            continue
        
        category_results = []
        category_stats = {
            "files": 0,
            "tests": 0,
            "tokens": 0,
            "errors": 0
        }
        
        # Process each test file in category
        test_files = sorted(category_dir.glob("*.test"))
        
        for test_file in test_files:
            if verbose:
                print(f"\n  {test_file.name}")
            
            try:
                # Parse test file
                suite = parser.parse_file(test_file)
                
                # Check quality
                report = checker.check_test_suite(suite, verbose=verbose)
                category_results.append(report)
                
                # Update stats
                category_stats["files"] += 1
                category_stats["tests"] += report.get("test_count", 0)
                category_stats["tokens"] += report.get("tokens_used", 0)
                
                if "error" in report:
                    category_stats["errors"] += 1
                    if verbose:
                        print(f"    ✗ Error: {report['error']}")
                elif verbose:
                    print(f"    ✓ Analyzed ({report.get('tokens_used', 0)} tokens)")
                    
            except Exception as e:
                category_stats["errors"] += 1
                if verbose:
                    print(f"    ✗ Failed: {e}")
        
        # Save category results
        all_results[category] = {
            "results": category_results,
            "stats": category_stats
        }
        
        # Update summary stats
        summary_stats["total_categories"] += 1
        summary_stats["total_files"] += category_stats["files"]
        summary_stats["total_tests"] += category_stats["tests"]
        summary_stats["total_tokens"] += category_stats["tokens"]
        summary_stats["categories_processed"].append(category)
        
        if verbose:
            print(f"\n  Category Summary:")
            print(f"    Files: {category_stats['files']}")
            print(f"    Tests: {category_stats['tests']}")
            print(f"    Tokens: {category_stats['tokens']}")
            if category_stats['errors'] > 0:
                print(f"    Errors: {category_stats['errors']}")
        
        # Save individual category report
        output_path = Path(output_dir)
        output_path.mkdir(exist_ok=True)
        
        category_file = output_path / f"{category}_quality.json"
        with open(category_file, 'w') as f:
            json.dump(category_results, f, indent=2)
        
        if verbose:
            print(f"    Report: {category_file}")
    
    # Finalize summary
    summary_stats["end_time"] = datetime.now().isoformat()
    
    # Save comprehensive summary
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)
    
    summary_file = output_path / "summary.json"
    with open(summary_file, 'w') as f:
        json.dump({
            "summary": summary_stats,
            "categories": all_results
        }, f, indent=2)
    
    # Generate markdown report
    markdown_report = generate_markdown_report(summary_stats, all_results)
    markdown_file = output_path / "QUALITY_REPORT.md"
    with open(markdown_file, 'w') as f:
        f.write(markdown_report)
    
    # Print final summary
    if verbose:
        print(f"\n{'='*60}")
        print("Quality Check Complete!")
        print('='*60)
        print(f"Categories processed: {summary_stats['total_categories']}")
        print(f"Files analyzed: {summary_stats['total_files']}")
        print(f"Total tests: {summary_stats['total_tests']}")
        print(f"Total tokens used: {summary_stats['total_tokens']}")
        print(f"\nReports saved to: {output_dir}/")
        print(f"  - summary.json")
        print(f"  - QUALITY_REPORT.md")
        print(f"  - <category>_quality.json (per category)")
        print('='*60)
    
    return {
        "summary": summary_stats,
        "categories": all_results
    }


def generate_markdown_report(summary: Dict[str, Any], results: Dict[str, Any]) -> str:
    """Generate a markdown quality report."""
    
    lines = [
        "# Substrait Function Test Quality Report",
        "",
        f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"**Model:** {MODEL}",
        "",
        "## Summary",
        "",
        f"- **Categories Processed:** {summary['total_categories']}",
        f"- **Test Files Analyzed:** {summary['total_files']}",
        f"- **Total Test Cases:** {summary['total_tests']}",
        f"- **Total Tokens Used:** {summary['total_tokens']:,}",
        "",
        "## Category Breakdown",
        "",
        "| Category | Files | Tests | Tokens | Status |",
        "|----------|-------|-------|--------|--------|"
    ]
    
    for category, data in sorted(results.items()):
        stats = data.get("stats", {})
        files = stats.get("files", 0)
        tests = stats.get("tests", 0)
        tokens = stats.get("tokens", 0)
        errors = stats.get("errors", 0)
        
        status = "✅ Complete" if errors == 0 else f"⚠️ {errors} errors"
        
        lines.append(f"| {category} | {files} | {tests} | {tokens:,} | {status} |")
    
    lines.extend([
        "",
        "## Quality Scores by Category",
        "",
        "Average quality scores extracted from Claude's analysis:",
        ""
    ])
    
    # Extract scores from results
    for category, data in sorted(results.items()):
        category_results = data.get("results", [])
        if not category_results:
            continue
        
        lines.append(f"### {category.title()}")
        lines.append("")
        
        for result in category_results:
            suite_name = result.get("suite_name", "Unknown")
            analysis = result.get("analysis", {})
            
            if isinstance(analysis, dict) and "overall_score" in analysis:
                score = analysis.get("overall_score", "N/A")
                lines.append(f"- **{suite_name}**: {score}/10")
                
                # Add key issues if present
                issues = analysis.get("issues", [])
                if issues and len(issues) > 0:
                    lines.append(f"  - Issues: {len(issues)}")
                    for issue in issues[:3]:  # Show first 3 issues
                        lines.append(f"    - {issue}")
        
        lines.append("")
    
    lines.extend([
        "## Recommendations",
        "",
        "Based on the quality analysis, consider:",
        "",
        "1. Review test files with scores below 7/10",
        "2. Add missing edge case tests identified by Claude",
        "3. Improve test organization and categorization",
        "4. Ensure comprehensive null handling coverage",
        "5. Add overflow/underflow tests where missing",
        "",
        "## Next Steps",
        "",
        "1. Review individual category reports in `<category>_quality.json`",
        "2. Implement recommended test cases",
        "3. Re-run quality check to verify improvements",
        "4. Integrate quality checking into CI/CD pipeline",
        "",
        "---",
        "",
        "*Generated by Substrait Compliance Quality Checker*"
    ])
    
    return "\n".join(lines)


def main():
    """Main entry point for batch quality checking."""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Batch quality check for Substrait function tests"
    )
    parser.add_argument(
        "--categories",
        nargs="+",
        help="Specific categories to check (default: all)"
    )
    parser.add_argument(
        "--output",
        default="quality_reports",
        help="Output directory for reports (default: quality_reports)"
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress progress messages"
    )
    
    args = parser.parse_args()
    
    # Run batch quality check
    check_all_categories(
        categories=args.categories,
        output_dir=args.output,
        verbose=not args.quiet
    )


if __name__ == "__main__":
    main()

