"""
Quality checker for function test cases using Claude via LiteLLM.
Validates test correctness, coverage, and edge cases.
"""

import os
import sys
from pathlib import Path
from typing import List, Dict, Any, Optional
import json
from datetime import datetime
import time

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

try:
    from litellm import completion  # type: ignore[reportMissingImports]
except ImportError:
    print("Error: litellm not installed. Run: pip install litellm openai")
    sys.exit(1)

from sdk.python.substrait_compliance.function_test_parser import (
    FunctionTestParser, 
    FunctionTestSuite,
    FunctionTestCase
)

# LiteLLM Configuration
LITELLM_BASE_URL = "https://ete-litellm.bx.cloud9.ibm.com"
LITELLM_API_KEY = os.getenv("LITELLM_API_KEY", "")
# Use exact model name from available models list
MODEL = "aws/claude-sonnet-4-5"


class TestQualityChecker:
    """Checks quality of function tests using Claude via LiteLLM."""
    
    def __init__(self, api_key: str, base_url: Optional[str] = None, model: str = MODEL):
        """
        Initialize the quality checker.
        
        Args:
            api_key: LiteLLM API key
            base_url: LiteLLM proxy base URL
            model: Model to use (default: Claude 3.5 Sonnet)
        """
        self.api_key = api_key
        self.base_url = base_url
        self.model = model
        
        # Set environment variables for litellm
        os.environ["LITELLM_API_KEY"] = api_key
        if base_url:
            os.environ["LITELLM_BASE_URL"] = base_url
    
    def check_test_suite(self, suite: FunctionTestSuite, verbose: bool = True) -> Dict[str, Any]:
        """
        Check quality of an entire test suite.
        
        Args:
            suite: FunctionTestSuite to analyze
            verbose: Print progress messages
            
        Returns:
            Quality report with scores and recommendations
        """
        if verbose:
            print(f"  Analyzing {suite.name} ({len(suite.test_cases)} tests)...")
        
        # Prepare test cases for analysis
        test_summary = self._prepare_test_summary(suite)
        
        # Create prompt for Claude
        prompt = self._create_quality_prompt(suite, test_summary)
        
        # Retry logic with exponential backoff
        max_retries = 3
        retry_delay = 2
        response = None
        
        for attempt in range(max_retries):
            try:
                # Call Claude via LiteLLM
                # For custom proxy, use custom_llm_provider="openai" to treat as OpenAI-compatible
                response = completion(
                    model=self.model,
                    messages=[
                        {
                            "role": "system",
                            "content": "You are an expert in database testing and Substrait compliance. "
                                       "Analyze test cases for correctness, completeness, and quality. "
                                       "Provide specific, actionable feedback."
                        },
                        {
                            "role": "user",
                            "content": prompt
                        }
                    ],
                    api_key=self.api_key,
                    api_base=self.base_url,
                    custom_llm_provider="openai",
                    temperature=0.3,  # Lower temperature for more consistent analysis
                    max_tokens=4000,
                    timeout=60,  # Add timeout to prevent hanging
                    force_timeout=60  # Force timeout for litellm
                )
                break  # Success, exit retry loop
                
            except Exception as e:
                if attempt < max_retries - 1:
                    if verbose:
                        print(f"  ⚠ Connection error (attempt {attempt + 1}/{max_retries}), retrying in {retry_delay}s...")
                    time.sleep(retry_delay)
                    retry_delay *= 2  # Exponential backoff
                else:
                    # Final attempt failed
                    return {
                        "suite_name": suite.name,
                        "test_count": len(suite.test_cases),
                        "error": f"Connection error after {max_retries} attempts: {str(e)}",
                        "timestamp": datetime.now().isoformat()
                    }
        
        if response is None:
            return {
                "suite_name": suite.name,
                "test_count": len(suite.test_cases),
                "error": "No response received from API",
                "timestamp": datetime.now().isoformat()
            }
        
        try:
            
            # Parse response
            analysis = response.choices[0].message.content
            
            # Try to extract JSON if present
            parsed_analysis = self._parse_analysis(analysis)
            
            return {
                "suite_name": suite.name,
                "test_type": suite.test_type.value,
                "test_count": len(suite.test_cases),
                "file_path": str(suite.file_path),
                "analysis": parsed_analysis,
                "raw_analysis": analysis,
                "model": self.model,
                "tokens_used": response.usage.total_tokens,
                "timestamp": datetime.now().isoformat()
            }
            
        except Exception as e:
            return {
                "suite_name": suite.name,
                "test_count": len(suite.test_cases),
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }
    
    def _prepare_test_summary(self, suite: FunctionTestSuite) -> str:
        """Prepare a summary of test cases for analysis."""
        summary_lines = [
            f"Function: {suite.name}",
            f"Test Type: {suite.test_type.value}",
            f"Total Tests: {len(suite.test_cases)}",
            f"Includes: {', '.join(suite.includes) if suite.includes else 'None'}",
            "",
            "Test Cases by Category:"
        ]
        
        # Group by category
        categories = {}
        for test in suite.test_cases:
            if test.category not in categories:
                categories[test.category] = []
            categories[test.category].append(test)
        
        for category, tests in sorted(categories.items()):
            summary_lines.append(f"\n## {category} ({len(tests)} tests)")
            
            # Show first 10 tests of each category
            for i, test in enumerate(tests[:10]):
                options_str = ""
                if test.options:
                    opts = ", ".join(f"{k}:{v}" for k, v in test.options.items())
                    options_str = f" [{opts}]"
                
                summary_lines.append(
                    f"  {i+1}. {test.function_call}{options_str} = {test.expected_result}"
                )
            
            if len(tests) > 10:
                summary_lines.append(f"  ... and {len(tests) - 10} more tests")
        
        return "\n".join(summary_lines)
    
    def _create_quality_prompt(self, suite: FunctionTestSuite, summary: str) -> str:
        """Create prompt for quality analysis."""
        return f"""Analyze the quality of these Substrait function tests:

{summary}

Please evaluate the following aspects:

1. **Correctness**: Are the test cases mathematically/logically correct?
   - Check calculations and expected results
   - Verify type compatibility
   - Validate error conditions

2. **Coverage**: Are important edge cases covered?
   - Null handling (null inputs, null propagation)
   - Overflow/underflow scenarios
   - Type boundaries (min/max values)
   - Error conditions
   - Special values (infinity, NaN for floating point)

3. **Completeness**: What test cases are missing?
   - Common use cases
   - Boundary values
   - Negative numbers
   - Zero values
   - Large values

4. **Organization**: Are test cases well-organized?
   - Clear categorization
   - Logical grouping
   - Consistent naming

5. **Best Practices**: Do tests follow testing best practices?
   - Clear expected behavior
   - Appropriate use of options
   - Good coverage of data types

Provide your analysis in the following JSON format:
```json
{{
  "overall_score": <1-10>,
  "correctness_score": <1-10>,
  "coverage_score": <1-10>,
  "completeness_score": <1-10>,
  "issues": [
    "Specific issue 1",
    "Specific issue 2"
  ],
  "missing_tests": [
    "Suggested test case 1",
    "Suggested test case 2"
  ],
  "recommendations": [
    "Recommendation 1",
    "Recommendation 2"
  ],
  "strengths": [
    "Strength 1",
    "Strength 2"
  ]
}}
```

After the JSON, provide a brief summary explanation."""
    
    def _parse_analysis(self, analysis: str) -> Dict[str, Any]:
        """Try to parse JSON from analysis response."""
        try:
            # Look for JSON block
            if "```json" in analysis:
                start = analysis.find("```json") + 7
                end = analysis.find("```", start)
                json_str = analysis[start:end].strip()
                return json.loads(json_str)
            elif "{" in analysis and "}" in analysis:
                # Try to extract JSON directly
                start = analysis.find("{")
                end = analysis.rfind("}") + 1
                json_str = analysis[start:end]
                return json.loads(json_str)
        except Exception:
            pass
        
        # Return raw text if JSON parsing fails
        return {"raw_text": analysis}
    
    def check_specific_test(self, test_case: str, expected: str, 
                           function_name: str) -> Dict[str, Any]:
        """
        Check a specific test case for correctness.
        
        Args:
            test_case: The function call (e.g., "add(5::i32, 3::i32)")
            expected: Expected result (e.g., "8::i32")
            function_name: Name of the function being tested
            
        Returns:
            Analysis of the specific test case
        """
        prompt = f"""Verify this test case for the {function_name} function:

Test: {test_case}
Expected: {expected}

Is this test case correct? Consider:
1. Mathematical/logical correctness
2. Type compatibility
3. Edge case handling
4. Expected behavior according to Substrait specification

Respond with JSON:
{{
  "correct": true/false,
  "confidence": <1-10>,
  "explanation": "Detailed explanation",
  "suggestion": "Improvement suggestion if needed"
}}"""

        try:
            response = completion(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": "You are a database testing expert. Verify test case correctness."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                api_key=self.api_key,
                api_base=self.base_url,
                custom_llm_provider="openai",
                temperature=0.2
            )
            
            content = response.choices[0].message.content
            return self._parse_analysis(content)
            
        except Exception as e:
            return {"error": str(e)}


def main():
    """Main quality checking workflow for a single category."""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Check quality of Substrait function tests using Claude"
    )
    parser.add_argument(
        "category",
        nargs="?",
        default="arithmetic",
        help="Test category to check (default: arithmetic)"
    )
    parser.add_argument(
        "--file",
        help="Specific test file to check"
    )
    parser.add_argument(
        "--output",
        default="quality_reports",
        help="Output directory for reports (default: quality_reports)"
    )
    
    args = parser.parse_args()
    
    # Initialize checker
    print(f"Initializing quality checker with Claude via LiteLLM...")
    print(f"Base URL: {LITELLM_BASE_URL}")
    print(f"Model: {MODEL}\n")
    
    checker = TestQualityChecker(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL,
        model=MODEL
    )
    
    # Initialize parser
    parser_obj = FunctionTestParser()
    
    # Determine what to check
    if args.file:
        test_files = [Path(args.file)]
        output_name = Path(args.file).stem
    else:
        test_dir = Path("test-suites/functions") / args.category
        if not test_dir.exists():
            print(f"Error: Category directory not found: {test_dir}")
            sys.exit(1)
        test_files = sorted(test_dir.glob("*.test"))
        output_name = args.category
    
    print(f"Checking {len(test_files)} test file(s)...\n")
    
    # Check all test files
    results = []
    total_tests = 0
    total_tokens = 0
    
    for i, test_file in enumerate(test_files):
        print(f"Analyzing {test_file.name}...")
        
        try:
            # Parse test file
            suite = parser_obj.parse_file(test_file)
            total_tests += len(suite.test_cases)
            
            # Check quality
            quality_report = checker.check_test_suite(suite)
            results.append(quality_report)
            
            # Print summary
            if "error" not in quality_report:
                tokens = quality_report.get('tokens_used', 0)
                total_tokens += tokens
                print(f"  ✓ {quality_report['test_count']} tests analyzed ({tokens} tokens)\n")
            else:
                print(f"  ✗ Error: {quality_report['error']}\n")
            
            # Add delay between API calls to avoid connection issues
            # Skip delay after the last file
            if i < len(test_files) - 1:
                time.sleep(1)
                
        except Exception as e:
            print(f"  ✗ Failed to process: {e}\n")
            # Continue with next file even if one fails
            continue
    
    # Save results
    output_dir = Path(args.output)
    output_dir.mkdir(exist_ok=True)
    
    output_file = output_dir / f"{output_name}_quality.json"
    
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    # Print summary
    print("="*60)
    print("Quality Check Summary")
    print("="*60)
    print(f"Files analyzed: {len(results)}")
    print(f"Total tests: {total_tests}")
    print(f"Total tokens used: {total_tokens}")
    print(f"Report saved to: {output_file}")
    print("="*60)


if __name__ == "__main__":
    main()

