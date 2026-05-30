# Substrait Function Test Quality Checker

Automated quality checking for Substrait function tests using Claude AI via LiteLLM proxy.

## Overview

This quality checker uses Claude (via LiteLLM) to analyze your Substrait function test cases for:
- ✅ **Correctness**: Mathematical and logical accuracy
- ✅ **Coverage**: Edge cases, null handling, overflow scenarios
- ✅ **Completeness**: Missing test cases and gaps
- ✅ **Organization**: Test structure and categorization
- ✅ **Best Practices**: Testing standards compliance

## Prerequisites

### 1. Install Dependencies

```bash
# Install required Python packages
pip install -r sdk/python/requirements.txt

# Or install individually
pip install litellm openai pyyaml protobuf
```

### 2. LiteLLM Configuration

The quality checker is configured to use:
- **Base URL**: `https://ete-litellm.bx.cloud9.ibm.com`
- **API Key**: from the `LITELLM_API_KEY` environment variable
- **Model**: `claude-3-5-sonnet-20241022`

Configuration can be modified in `scripts/quality_config.yaml`.

## Usage

### Quick Start

Check a single category (e.g., arithmetic):

```bash
cd /path/to/substrait-compliance
python scripts/quality_checker.py arithmetic
```

### Check Specific Test File

```bash
python scripts/quality_checker.py --file test-suites/functions/arithmetic/add.test
```

### Batch Check All Categories

```bash
python scripts/batch_quality_check.py
```

### Check Specific Categories

```bash
python scripts/batch_quality_check.py --categories arithmetic boolean string
```

### Custom Output Directory

```bash
python scripts/quality_checker.py arithmetic --output my_reports
```

### Quiet Mode (Suppress Progress)

```bash
python scripts/batch_quality_check.py --quiet
```

## Output

### Generated Reports

All reports are saved to `quality_reports/` (or custom directory):

1. **`<category>_quality.json`** - Detailed analysis per category
2. **`summary.json`** - Comprehensive summary of all checks
3. **`QUALITY_REPORT.md`** - Human-readable markdown report

### Report Structure

Each quality report includes:

```json
{
  "suite_name": "add",
  "test_type": "SUBSTRAIT_SCALAR_TEST",
  "test_count": 27,
  "file_path": "test-suites/functions/arithmetic/add.test",
  "analysis": {
    "overall_score": 8.5,
    "correctness_score": 9.0,
    "coverage_score": 8.0,
    "completeness_score": 8.5,
    "issues": [
      "Missing test for negative number addition",
      "No boundary value test for i64 max"
    ],
    "missing_tests": [
      "add(-5::i32, -3::i32) = -8::i32",
      "add(9223372036854775807::i64, 0::i64) = 9223372036854775807::i64"
    ],
    "recommendations": [
      "Add more boundary value tests",
      "Include tests for all numeric types"
    ],
    "strengths": [
      "Good overflow handling coverage",
      "Clear test categorization"
    ]
  },
  "model": "claude-3-5-sonnet-20241022",
  "tokens_used": 1250,
  "timestamp": "2026-04-24T17:30:00.000Z"
}
```

## Examples

### Example 1: Check Arithmetic Functions

```bash
python scripts/quality_checker.py arithmetic
```

Output:
```
Initializing quality checker with Claude via LiteLLM...
Base URL: https://ete-litellm.bx.cloud9.ibm.com
Model: claude-3-5-sonnet-20241022

Checking 33 test file(s)...

Analyzing add.test...
  Analyzing add (27 tests)...
  ✓ 27 tests analyzed (1250 tokens)

Analyzing subtract.test...
  Analyzing subtract (24 tests)...
  ✓ 24 tests analyzed (1180 tokens)

...

============================================================
Quality Check Summary
============================================================
Files analyzed: 33
Total tests: 300
Total tokens used: 38,500
Report saved to: quality_reports/arithmetic_quality.json
============================================================
```

### Example 2: Batch Check with Custom Categories

```bash
python scripts/batch_quality_check.py --categories arithmetic string datetime
```

### Example 3: Review Generated Report

```bash
# View markdown report
cat quality_reports/QUALITY_REPORT.md

# View JSON summary
cat quality_reports/summary.json | python -m json.tool

# View specific category
cat quality_reports/arithmetic_quality.json | python -m json.tool
```

## Configuration

Edit `scripts/quality_config.yaml` to customize:

### LiteLLM Settings

```yaml
litellm:
  base_url: "https://ete-litellm.bx.cloud9.ibm.com"
  api_key: "${LITELLM_API_KEY}"
  model: "claude-3-5-sonnet-20241022"
  temperature: 0.3
  max_tokens: 4000
```

### Quality Check Aspects

```yaml
quality_checks:
  aspects:
    null_handling: true
    overflow_handling: true
    type_boundaries: true
    error_conditions: true
    special_values: true
```

### Categories to Check

```yaml
categories:
  - arithmetic
  - boolean
  - comparison
  - string
  - datetime
  - aggregate
  - window
  - cast
```

## Understanding Scores

Quality scores range from 1-10:

| Score | Rating | Description |
|-------|--------|-------------|
| 9-10 | Excellent | Comprehensive, well-organized tests |
| 7-8 | Good | Solid coverage with minor gaps |
| 5-6 | Acceptable | Basic coverage, needs improvement |
| 3-4 | Needs Work | Significant gaps or issues |
| 1-2 | Poor | Major problems, requires attention |

## Common Issues and Recommendations

### Issue: Low Coverage Score

**Recommendation**: Add tests for:
- Null value handling
- Overflow/underflow scenarios
- Type boundary values (min/max)
- Error conditions

### Issue: Missing Edge Cases

**Recommendation**: Include tests for:
- Zero values
- Negative numbers
- Special floating-point values (infinity, NaN)
- Empty inputs (for aggregate functions)

### Issue: Poor Organization

**Recommendation**:
- Use clear category comments
- Group related tests together
- Follow consistent naming patterns

## Cost Estimation

Approximate token usage per test file:
- Small file (10-20 tests): ~800-1,200 tokens
- Medium file (20-50 tests): ~1,200-2,000 tokens
- Large file (50+ tests): ~2,000-4,000 tokens

**Example**: Checking all 111 test files (~1,635 tests) uses approximately 150,000-200,000 tokens.

## Troubleshooting

### Error: "litellm not installed"

```bash
pip install litellm openai
```

### Error: "Could not import quality_checker"

Make sure you're running from the project root:
```bash
cd /path/to/substrait-compliance
python scripts/quality_checker.py
```

### Error: API Connection Failed

Check:
1. LiteLLM proxy is accessible: `https://ete-litellm.bx.cloud9.ibm.com`
2. `LITELLM_API_KEY` is set and valid
3. Network connectivity

### Error: Rate Limit Exceeded

Add delays between requests in `quality_config.yaml`:
```yaml
batch:
  delay_between_requests: 2  # seconds
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Quality Check

on:
  pull_request:
    paths:
      - 'test-suites/functions/**/*.test'

jobs:
  quality-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.9'
      
      - name: Install dependencies
        run: pip install -r sdk/python/requirements.txt
      
      - name: Run quality check
        run: python scripts/batch_quality_check.py
      
      - name: Upload reports
        uses: actions/upload-artifact@v3
        with:
          name: quality-reports
          path: quality_reports/
```

## Advanced Usage

### Programmatic Usage

```python
from pathlib import Path
from quality_checker import TestQualityChecker
from sdk.python.substrait_compliance.function_test_parser import FunctionTestParser

# Initialize
checker = TestQualityChecker(
    api_key=os.environ["LITELLM_API_KEY"],
    base_url="https://ete-litellm.bx.cloud9.ibm.com"
)

parser = FunctionTestParser()

# Check a specific file
suite = parser.parse_file(Path("test-suites/functions/arithmetic/add.test"))
report = checker.check_test_suite(suite)

# Access results
print(f"Score: {report['analysis']['overall_score']}/10")
print(f"Issues: {report['analysis']['issues']}")
print(f"Recommendations: {report['analysis']['recommendations']}")
```

### Check Single Test Case

```python
result = checker.check_specific_test(
    test_case="add(5::i32, 3::i32)",
    expected="8::i32",
    function_name="add"
)

print(f"Correct: {result['correct']}")
print(f"Explanation: {result['explanation']}")
```

## Best Practices

1. **Run regularly**: Check quality after adding new tests
2. **Review recommendations**: Implement suggested improvements
3. **Track scores**: Monitor quality trends over time
4. **Focus on low scores**: Prioritize files with scores < 7
5. **Iterate**: Re-run after improvements to verify

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review `quality_config.yaml` settings
3. Examine generated reports for details
4. Verify LiteLLM proxy connectivity

## Related Documentation

- [Function Tests README](../test-suites/functions/README.md)
- [Python SDK Documentation](../sdk/python/README.md)
- [LiteLLM Documentation](https://docs.litellm.ai/)

---

**Made with ❤️ for the Substrait Community**