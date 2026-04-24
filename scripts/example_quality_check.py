#!/usr/bin/env python3
"""
Example usage of the Substrait Function Test Quality Checker.

This script demonstrates various ways to use the quality checker
to analyze your function tests using Claude via LiteLLM.
"""

import sys
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from quality_checker import TestQualityChecker, LITELLM_API_KEY, LITELLM_BASE_URL, MODEL
from sdk.python.substrait_compliance.function_test_parser import FunctionTestParser


def example_1_check_single_file():
    """Example 1: Check a single test file."""
    print("="*60)
    print("Example 1: Check Single Test File")
    print("="*60)
    
    # Initialize checker
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL
    )
    
    # Initialize parser
    parser = FunctionTestParser()
    
    # Parse a test file
    test_file = Path("test-suites/functions/arithmetic/add.test")
    print(f"\nAnalyzing: {test_file}")
    
    suite = parser.parse_file(test_file)
    print(f"Found {len(suite.test_cases)} test cases")
    
    # Check quality
    report = checker.check_test_suite(suite)
    
    # Display results
    print(f"\nResults:")
    print(f"  Overall Score: {report['analysis'].get('overall_score', 'N/A')}/10")
    print(f"  Tokens Used: {report['tokens_used']}")
    
    if 'issues' in report['analysis']:
        print(f"\n  Issues Found: {len(report['analysis']['issues'])}")
        for i, issue in enumerate(report['analysis']['issues'][:3], 1):
            print(f"    {i}. {issue}")
    
    if 'recommendations' in report['analysis']:
        print(f"\n  Recommendations:")
        for i, rec in enumerate(report['analysis']['recommendations'][:3], 1):
            print(f"    {i}. {rec}")
    
    print("\n" + "="*60 + "\n")


def example_2_check_specific_test():
    """Example 2: Check a specific test case."""
    print("="*60)
    print("Example 2: Check Specific Test Case")
    print("="*60)
    
    # Initialize checker
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL
    )
    
    # Check a specific test case
    test_case = "add(5::i32, 3::i32)"
    expected = "8::i32"
    
    print(f"\nTest Case: {test_case}")
    print(f"Expected: {expected}")
    
    result = checker.check_specific_test(
        test_case=test_case,
        expected=expected,
        function_name="add"
    )
    
    # Display results
    print(f"\nResults:")
    print(f"  Correct: {result.get('correct', 'N/A')}")
    print(f"  Confidence: {result.get('confidence', 'N/A')}/10")
    print(f"  Explanation: {result.get('explanation', 'N/A')}")
    
    if 'suggestion' in result and result['suggestion']:
        print(f"  Suggestion: {result['suggestion']}")
    
    print("\n" + "="*60 + "\n")


def example_3_check_category():
    """Example 3: Check all tests in a category."""
    print("="*60)
    print("Example 3: Check Category (Boolean Functions)")
    print("="*60)
    
    # Initialize checker and parser
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL
    )
    parser = FunctionTestParser()
    
    # Check all boolean function tests
    category_dir = Path("test-suites/functions/boolean")
    
    if not category_dir.exists():
        print(f"\nCategory directory not found: {category_dir}")
        print("Skipping this example.\n")
        return
    
    print(f"\nAnalyzing category: boolean")
    
    results = []
    total_tests = 0
    total_tokens = 0
    
    for test_file in sorted(category_dir.glob("*.test")):
        print(f"\n  {test_file.name}...", end=" ")
        
        suite = parser.parse_file(test_file)
        report = checker.check_test_suite(suite, verbose=False)
        
        results.append(report)
        total_tests += report['test_count']
        total_tokens += report.get('tokens_used', 0)
        
        score = report['analysis'].get('overall_score', 'N/A')
        print(f"Score: {score}/10")
    
    # Summary
    print(f"\nCategory Summary:")
    print(f"  Files: {len(results)}")
    print(f"  Total Tests: {total_tests}")
    print(f"  Total Tokens: {total_tokens}")
    
    # Average score
    scores = [r['analysis'].get('overall_score', 0) for r in results 
              if isinstance(r['analysis'], dict) and 'overall_score' in r['analysis']]
    if scores:
        avg_score = sum(scores) / len(scores)
        print(f"  Average Score: {avg_score:.1f}/10")
    
    print("\n" + "="*60 + "\n")


def example_4_compare_functions():
    """Example 4: Compare quality across multiple functions."""
    print("="*60)
    print("Example 4: Compare Function Quality")
    print("="*60)
    
    # Initialize checker and parser
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL
    )
    parser = FunctionTestParser()
    
    # Functions to compare
    functions = [
        "test-suites/functions/arithmetic/add.test",
        "test-suites/functions/arithmetic/subtract.test",
        "test-suites/functions/arithmetic/multiply.test"
    ]
    
    print("\nComparing functions:")
    comparison = []
    
    for func_path in functions:
        test_file = Path(func_path)
        if not test_file.exists():
            print(f"  ⚠ {test_file.name} not found, skipping")
            continue
        
        print(f"\n  Analyzing {test_file.name}...", end=" ")
        
        suite = parser.parse_file(test_file)
        report = checker.check_test_suite(suite, verbose=False)
        
        score = report['analysis'].get('overall_score', 0)
        print(f"Score: {score}/10")
        
        comparison.append({
            'name': test_file.stem,
            'score': score,
            'tests': report['test_count'],
            'tokens': report.get('tokens_used', 0)
        })
    
    # Display comparison table
    print("\n  Comparison Table:")
    print("  " + "-"*56)
    print(f"  {'Function':<15} {'Score':<10} {'Tests':<10} {'Tokens':<10}")
    print("  " + "-"*56)
    
    for item in sorted(comparison, key=lambda x: x['score'], reverse=True):
        print(f"  {item['name']:<15} {item['score']:<10.1f} {item['tests']:<10} {item['tokens']:<10}")
    
    print("  " + "-"*56)
    print("\n" + "="*60 + "\n")


def example_5_programmatic_analysis():
    """Example 5: Programmatic analysis with custom logic."""
    print("="*60)
    print("Example 5: Programmatic Analysis")
    print("="*60)
    
    # Initialize checker and parser
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL
    )
    parser = FunctionTestParser()
    
    # Analyze and filter
    test_file = Path("test-suites/functions/comparison/equal.test")
    
    if not test_file.exists():
        print(f"\nTest file not found: {test_file}")
        print("Skipping this example.\n")
        return
    
    print(f"\nAnalyzing: {test_file.name}")
    
    suite = parser.parse_file(test_file)
    report = checker.check_test_suite(suite, verbose=False)
    
    # Extract and analyze
    analysis = report['analysis']
    
    print(f"\nDetailed Analysis:")
    print(f"  Test Count: {report['test_count']}")
    
    if isinstance(analysis, dict):
        print(f"  Overall Score: {analysis.get('overall_score', 'N/A')}/10")
        print(f"  Correctness: {analysis.get('correctness_score', 'N/A')}/10")
        print(f"  Coverage: {analysis.get('coverage_score', 'N/A')}/10")
        print(f"  Completeness: {analysis.get('completeness_score', 'N/A')}/10")
        
        # Check if needs improvement
        overall = analysis.get('overall_score', 10)
        if overall < 7:
            print(f"\n  ⚠ WARNING: Score below 7, needs improvement!")
            print(f"  Priority: HIGH")
        elif overall < 8:
            print(f"\n  ℹ INFO: Score below 8, consider improvements")
            print(f"  Priority: MEDIUM")
        else:
            print(f"\n  ✓ GOOD: Score is acceptable")
            print(f"  Priority: LOW")
        
        # Show actionable items
        if 'missing_tests' in analysis and analysis['missing_tests']:
            print(f"\n  Missing Tests ({len(analysis['missing_tests'])}):")
            for i, test in enumerate(analysis['missing_tests'][:5], 1):
                print(f"    {i}. {test}")
    
    print("\n" + "="*60 + "\n")


def main():
    """Run all examples."""
    print("\n" + "="*60)
    print("Substrait Quality Checker - Usage Examples")
    print("="*60)
    print(f"\nUsing:")
    print(f"  Base URL: {LITELLM_BASE_URL}")
    print(f"  Model: {MODEL}")
    print(f"  API Key: {LITELLM_API_KEY[:20]}...")
    print("\n" + "="*60 + "\n")
    
    try:
        # Run examples
        example_1_check_single_file()
        
        example_2_check_specific_test()
        
        example_3_check_category()
        
        example_4_compare_functions()
        
        example_5_programmatic_analysis()
        
        print("="*60)
        print("All Examples Completed!")
        print("="*60)
        print("\nNext Steps:")
        print("  1. Review the generated reports")
        print("  2. Implement recommended improvements")
        print("  3. Run batch_quality_check.py for all categories")
        print("  4. Integrate into your CI/CD pipeline")
        print("="*60)
        
    except Exception as e:
        print(f"\n❌ Error running examples: {e}")
        print("\nTroubleshooting:")
        print("  1. Ensure dependencies are installed: pip install litellm openai")
        print("  2. Check LiteLLM proxy is accessible")
        print("  3. Verify API key is valid")
        print("  4. Ensure test files exist in test-suites/functions/")
        sys.exit(1)


if __name__ == "__main__":
    main()

# Made with Bob
