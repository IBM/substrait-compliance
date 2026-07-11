#!/usr/bin/env python3
"""
Test Enhancer - Automatically improve test files based on Claude's quality analysis.

This script:
1. Runs quality checks on test files
2. Uses Claude to generate missing test cases
3. Saves enhanced tests to a separate directory
4. Preserves original tests for comparison
"""

import sys
import json
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime
import shutil

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

try:
    from litellm import completion
except ImportError:
    print("Error: litellm not installed. Run: pip install litellm openai")
    sys.exit(1)

from quality_checker import TestQualityChecker, LITELLM_API_KEY, LITELLM_BASE_URL, MODEL
from sdk.python.substrait_compliance.function_test_parser import FunctionTestParser


class TestEnhancer:
    """Enhances test files based on quality analysis."""
    
    def __init__(self, api_key: str, base_url: str = None, model: str = MODEL):
        """
        Initialize the test enhancer.
        
        Args:
            api_key: LiteLLM API key
            base_url: LiteLLM proxy base URL
            model: Model to use
        """
        self.api_key = api_key
        self.base_url = base_url
        self.model = model
        self.quality_checker = TestQualityChecker(api_key, base_url, model)
        self.parser = FunctionTestParser()
    
    def enhance_test_file(
        self,
        test_file: Path,
        output_dir: Path,
        verbose: bool = True,
        fix_errors: bool = False
    ) -> Dict[str, Any]:
        """
        Enhance a single test file.
        
        Args:
            test_file: Path to original test file
            output_dir: Directory to save enhanced test
            verbose: Print progress messages
            fix_errors: If True, correct errors in original tests
            
        Returns:
            Enhancement report with statistics
        """
        if verbose:
            print(f"\nEnhancing {test_file.name}...")
        
        # Step 1: Run quality check
        suite = self.parser.parse_file(test_file)
        quality_report = self.quality_checker.check_test_suite(suite, verbose=False)
        
        if "error" in quality_report:
            return {
                "file": str(test_file),
                "status": "error",
                "error": quality_report["error"]
            }
        
        # Step 2: Read original file content
        with open(test_file, 'r') as f:
            original_content = f.read()
        
        # Step 3: Generate enhancements
        if verbose:
            print(f"  Generating enhancements...")
        
        enhancements = self._generate_enhancements(
            suite,
            quality_report,
            original_content,
            fix_errors
        )
        
        if "error" in enhancements:
            return {
                "file": str(test_file),
                "status": "error",
                "error": enhancements["error"]
            }
        
        # Step 4: Create enhanced test file
        enhanced_content = self._create_enhanced_file(
            original_content,
            enhancements,
            fix_errors
        )
        
        # Step 5: Save enhanced file
        output_dir.mkdir(parents=True, exist_ok=True)
        enhanced_file = output_dir / test_file.name
        
        with open(enhanced_file, 'w') as f:
            f.write(enhanced_content)
        
        if verbose:
            print(f"  ✓ Enhanced file saved to {enhanced_file}")
            print(f"  Added {enhancements.get('tests_added', 0)} new test cases")
        
        return {
            "file": str(test_file),
            "enhanced_file": str(enhanced_file),
            "status": "success",
            "original_tests": len(suite.test_cases),
            "tests_added": enhancements.get('tests_added', 0),
            "quality_score_before": quality_report['analysis'].get('overall_score', 0),
            "enhancements": enhancements.get('summary', ''),
            "timestamp": datetime.now().isoformat()
        }
    
    def _generate_enhancements(
        self,
        suite,
        quality_report: Dict[str, Any],
        original_content: str,
        fix_errors: bool = False
    ) -> Dict[str, Any]:
        """Generate test enhancements using Claude."""
        
        analysis = quality_report.get('analysis', {})
        
        # Extract missing tests and recommendations
        missing_tests = analysis.get('missing_tests', [])
        issues = analysis.get('issues', [])
        recommendations = analysis.get('recommendations', [])
        
        if not missing_tests and not issues:
            return {
                "tests_added": 0,
                "new_tests": [],
                "summary": "No enhancements needed"
            }
        
        # Create prompt for Claude to generate new tests and optionally fix errors
        fix_instruction = ""
        if fix_errors and issues:
            fix_instruction = f"""
IMPORTANT: Some test cases in the original file have errors. Please also provide corrections:
- Identify which specific test cases are incorrect
- Provide the corrected version of each incorrect test
- Format corrections as: ORIGINAL_LINE -> CORRECTED_LINE
"""
        
        prompt = f"""Based on the quality analysis of this Substrait function test file, generate additional test cases to improve coverage{' and fix any errors' if fix_errors else ''}.

Original Test File:
```
{original_content[:2000]}  # First 2000 chars
...
```

Quality Analysis:
- Missing Tests: {len(missing_tests)}
- Issues Found: {len(issues)}

Missing Test Cases:
{chr(10).join(f"- {test}" for test in missing_tests[:10])}

Issues to Address:
{chr(10).join(f"- {issue}" for issue in issues[:5])}

Recommendations:
{chr(10).join(f"- {rec}" for rec in recommendations[:5])}

{fix_instruction}

Please generate NEW test cases in the exact same format as the original file. Include:
1. Test cases for the missing scenarios identified
2. Edge cases that address the issues
3. Follow the same format: function_call [options] = expected_result

{'If fixing errors, first list corrections in this format:' if fix_errors else ''}
{'CORRECTIONS:' if fix_errors else ''}
{'original_test_line -> corrected_test_line' if fix_errors else ''}
{'(one per line)' if fix_errors else ''}
{'---' if fix_errors else ''}

Then generate ONLY the new test cases (5-10 tests), with appropriate category comments.
Format each test case exactly like the original file format.

Example format:
# category_name: Description
function(arg1::type, arg2::type) = result::type
function(arg1::type, arg2::type) [option:value] = result::type
"""

        try:
            response = completion(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": "You are an expert in creating comprehensive test cases for database functions. "
                                   "Generate test cases in the exact Substrait test format."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                api_key=self.api_key,
                api_base=self.base_url,
                custom_llm_provider="openai",
                temperature=0.4,  # Slightly higher for creative test generation
                max_tokens=2000
            )
            
            new_tests_content = response.choices[0].message.content
            
            # Extract corrections if fix_errors is enabled
            corrections = {}
            if fix_errors:
                corrections = self._extract_corrections(new_tests_content)
            
            # Parse the generated tests
            new_tests = self._parse_generated_tests(new_tests_content)
            
            return {
                "tests_added": len(new_tests),
                "new_tests": new_tests,
                "corrections": corrections,
                "corrections_count": len(corrections),
                "raw_content": new_tests_content,
                "summary": f"Added {len(new_tests)} new test cases" +
                          (f", corrected {len(corrections)} errors" if corrections else "")
            }
            
        except Exception as e:
            return {"error": str(e)}
    
    def _extract_corrections(self, content: str) -> Dict[str, str]:
        """Extract corrections from generated content."""
        corrections = {}
        
        # Look for CORRECTIONS: section
        if 'CORRECTIONS:' in content:
            lines = content.split('\n')
            in_corrections = False
            
            for line in lines:
                line = line.strip()
                if 'CORRECTIONS:' in line:
                    in_corrections = True
                    continue
                if line == '---' or (in_corrections and not line):
                    break
                if in_corrections and '->' in line:
                    parts = line.split('->', 1)
                    if len(parts) == 2:
                        original = parts[0].strip()
                        corrected = parts[1].strip()
                        corrections[original] = corrected
        
        return corrections
    
    def _parse_generated_tests(self, content: str) -> List[str]:
        """Parse generated test cases from Claude's response."""
        tests = []
        lines = content.split('\n')
        in_corrections = False
        
        for line in lines:
            line = line.strip()
            
            # Skip corrections section
            if 'CORRECTIONS:' in line:
                in_corrections = True
                continue
            if in_corrections and (line == '---' or not line):
                in_corrections = False
                continue
            if in_corrections:
                continue
            
            # Skip empty lines, comments, and code blocks
            if not line or line.startswith('#') or line.startswith('```'):
                continue
            # Look for test case pattern: function(...) = result
            if '=' in line and '(' in line and ')' in line and '->' not in line:
                tests.append(line)
        
        return tests
    
    def _create_enhanced_file(
        self,
        original_content: str,
        enhancements: Dict[str, Any],
        fix_errors: bool = False
    ) -> str:
        """Create enhanced test file content with optional error corrections."""
        
        new_tests = enhancements.get('new_tests', [])
        corrections = enhancements.get('corrections', {})
        
        # Apply corrections to original content if fix_errors is enabled
        working_content = original_content
        if fix_errors and corrections:
            lines = working_content.split('\n')
            corrected_lines = []
            
            for line in lines:
                line_stripped = line.strip()
                corrected = False
                
                # Check if this line needs correction
                for original_line, corrected_line in corrections.items():
                    if line_stripped == original_line.strip():
                        # Add comment showing the correction
                        corrected_lines.append(f"{corrected_line}  # CORRECTED: was {original_line}")
                        corrected = True
                        break
                
                if not corrected:
                    corrected_lines.append(line)
            
            working_content = '\n'.join(corrected_lines)
        
        if not new_tests and not corrections:
            return original_content
        
        # Build corrections section if any corrections were made
        corrections_section = ""
        if corrections:
            corrections_section = "\n# CORRECTIONS APPLIED:\n"
            for i, (original, corrected) in enumerate(corrections.items(), 1):
                corrections_section += f"#   {i}. {original}\n"
                corrections_section += f"#      -> {corrected}\n"
        
        # Add enhancement header
        enhancement_header = f"""
# ============================================================================
# ENHANCED TEST CASES - Generated by Claude AI Quality Checker
# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
# Corrections Applied: {len(corrections)}
# Tests Added: {len(new_tests)}
# ============================================================================
{corrections_section}
# ai_generated: Test cases generated based on quality analysis
"""
        
        # Combine corrected content with new tests
        enhanced_content = working_content.rstrip() + "\n" + enhancement_header
        
        for test in new_tests:
            enhanced_content += test + "\n"
        
        enhanced_content += "\n# End of AI-generated tests\n"
        
        return enhanced_content
    
    def enhance_category(
        self,
        category: str,
        output_base_dir: Path = Path("test-suites-enhanced/functions"),
        verbose: bool = True,
        fix_errors: bool = False
    ) -> Dict[str, Any]:
        """
        Enhance all test files in a category.
        
        Args:
            category: Category name (e.g., 'arithmetic')
            output_base_dir: Base directory for enhanced tests
            verbose: Print progress messages
            fix_errors: If True, correct errors in original tests
            
        Returns:
            Summary of enhancements
        """
        category_dir = Path("test-suites/functions") / category
        
        if not category_dir.exists():
            return {
                "category": category,
                "status": "error",
                "error": f"Category directory not found: {category_dir}"
            }
        
        if verbose:
            print(f"\n{'='*60}")
            print(f"Enhancing Category: {category}")
            print('='*60)
        
        output_dir = output_base_dir / category
        results = []
        
        for test_file in sorted(category_dir.glob("*.test")):
            result = self.enhance_test_file(test_file, output_dir, verbose, fix_errors)
            results.append(result)
        
        # Calculate statistics
        total_files = len(results)
        successful = sum(1 for r in results if r.get('status') == 'success')
        total_tests_added = sum(r.get('tests_added', 0) for r in results)
        
        summary = {
            "category": category,
            "total_files": total_files,
            "successful": successful,
            "failed": total_files - successful,
            "total_tests_added": total_tests_added,
            "output_directory": str(output_dir),
            "results": results,
            "timestamp": datetime.now().isoformat()
        }
        
        if verbose:
            print(f"\nCategory Summary:")
            print(f"  Files processed: {total_files}")
            print(f"  Successful: {successful}")
            print(f"  Total tests added: {total_tests_added}")
            print(f"  Output: {output_dir}")
        
        return summary


def main():
    """Main entry point for test enhancement."""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Enhance Substrait function tests based on quality analysis"
    )
    parser.add_argument(
        "category",
        nargs="?",
        help="Test category to enhance (e.g., arithmetic)"
    )
    parser.add_argument(
        "--file",
        help="Specific test file to enhance"
    )
    parser.add_argument(
        "--output",
        default="test-suites-enhanced/functions",
        help="Output directory for enhanced tests (default: test-suites-enhanced/functions)"
    )
    parser.add_argument(
        "--fix-errors",
        action="store_true",
        help="Fix errors in original test cases (default: only add new tests)"
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress progress messages"
    )
    
    args = parser.parse_args()
    
    # Initialize enhancer
    print("="*60)
    print("Substrait Test Enhancer")
    print("="*60)
    print(f"Model: {MODEL}")
    print(f"Output: {args.output}")
    print("="*60)
    
    enhancer = TestEnhancer(
        api_key=LITELLM_API_KEY,
        base_url=LITELLM_BASE_URL,
        model=MODEL
    )
    
    # Enhance specific file or category
    if args.file:
        test_file = Path(args.file)
        if not test_file.exists():
            print(f"Error: File not found: {test_file}")
            sys.exit(1)
        
        # Determine output directory
        output_dir = Path(args.output)
        if test_file.parent.name in ['arithmetic', 'boolean', 'string', 'datetime']:
            output_dir = output_dir / test_file.parent.name
        
        result = enhancer.enhance_test_file(
            test_file,
            output_dir,
            verbose=not args.quiet,
            fix_errors=args.fix_errors
        )
        
        # Save result
        result_file = Path("quality_reports") / "enhancement_result.json"
        result_file.parent.mkdir(exist_ok=True)
        with open(result_file, 'w') as f:
            json.dump(result, f, indent=2)
        
        print(f"\nResult saved to: {result_file}")
        
    elif args.category:
        summary = enhancer.enhance_category(
            args.category,
            Path(args.output),
            verbose=not args.quiet
        )
        
        # Save summary
        summary_file = Path("quality_reports") / f"{args.category}_enhancement.json"
        summary_file.parent.mkdir(exist_ok=True)
        with open(summary_file, 'w') as f:
            json.dump(summary, f, indent=2)
        
        print(f"\nSummary saved to: {summary_file}")
    
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()

