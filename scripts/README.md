# Scripts Documentation

Comprehensive guide to all automation scripts in the Substrait Compliance project.

## 📋 Table of Contents

- [Overview](#overview)
- [Quality Checking](#quality-checking)
- [Test Enhancement](#test-enhancement)
- [Report Generation](#report-generation)
- [SDK Tools](#sdk-tools)
- [Quick Start](#quick-start)

---

## Overview

This directory contains automation scripts for quality checking, test enhancement, report generation, and SDK verification.

### Prerequisites

```bash
# Python 3.8+
python3 --version

# Install dependencies
pip install -r requirements-report.txt
```

---

## Quality Checking

### Quality Checker (`quality_checker.py`)

Validates test files for completeness, edge cases, and best practices.

#### Features
- Format validation
- Edge case detection
- NULL handling verification
- Error case checking
- Boundary value analysis

#### Usage

**Single File:**
```bash
python scripts/quality_checker.py test-suites/functions/string/concat.test
```

**Multiple Files:**
```bash
python scripts/batch_quality_check.py test-suites/functions/string/
```

**With Configuration:**
```bash
python scripts/quality_checker.py \
  --config scripts/quality_config.yaml \
  test-suites/functions/string/concat.test
```

#### Output Example
```
Quality Report for concat.test
==============================
✓ Format: Valid
✓ Edge Cases: 8/10 covered
✗ NULL Handling: Missing NULL + NULL case
✓ Error Cases: Present
✓ Boundary Values: Covered

Overall Score: 85/100
Recommendations:
- Add NULL + NULL test case
- Consider empty string edge case
```

#### Configuration (`quality_config.yaml`)

```yaml
checks:
  format:
    enabled: true
    strict: true
  
  edge_cases:
    enabled: true
    required:
      - null_handling
      - empty_values
      - boundary_values
  
  error_cases:
    enabled: true
    min_count: 2

scoring:
  weights:
    format: 20
    edge_cases: 40
    error_cases: 20
    documentation: 20
```

---

## Test Enhancement

### Test Enhancer (`test_enhancer.py`)

Automatically adds missing test cases to improve coverage.

#### Features
- Edge case generation
- NULL combination testing
- Boundary value testing
- Error scenario generation
- Type variation testing

#### Usage

**Enhance Single File:**
```bash
python scripts/test_enhancer.py test-suites/functions/string/concat.test
```

**Enhance Category:**
```bash
bash scripts/enhance_all_categories.sh string
```

**Dry Run (Preview Changes):**
```bash
python scripts/test_enhancer.py --dry-run concat.test
```

#### Enhancement Types

1. **NULL Handling**
   ```
   # Original
   concat('hello', 'world') = 'helloworld'
   
   # Enhanced
   concat('hello', 'world') = 'helloworld'
   concat('hello', NULL) = NULL
   concat(NULL, 'world') = NULL
   concat(NULL, NULL) = NULL
   ```

2. **Boundary Values**
   ```
   # Original
   substring('hello', 1, 3) = 'hel'
   
   # Enhanced
   substring('hello', 1, 3) = 'hel'
   substring('hello', 0, 1) = ''      # Start at 0
   substring('hello', 1, 0) = ''      # Length 0
   substring('hello', 1, 100) = 'hello'  # Length > string
   substring('hello', -1, 1) = ERROR  # Negative start
   ```

3. **Type Variations**
   ```
   # Original
   add(1, 2) = 3
   
   # Enhanced
   add(1, 2) = 3                    # int + int
   add(1.5, 2.5) = 4.0             # float + float
   add(1, 2.5) = 3.5               # int + float
   add(9223372036854775807, 1) = ERROR  # Overflow
   ```

---

## Report Generation

### Technical Report Generator (`generate_technical_report.py`)

Creates comprehensive technical reports from test results.

#### Features
- Leaderboard generation
- Coverage analysis
- Performance metrics
- Trend analysis
- Export formats (Markdown, HTML, PDF)

#### Usage

**Generate Full Report:**
```bash
python scripts/generate_technical_report.py \
  --input quality_reports/ \
  --output docs/TECHNICAL_REPORT.md
```

**Generate Leaderboard:**
```bash
python scripts/generate_leaderboard.py \
  --input quality_reports/ \
  --output leaderboard.md
```

**Custom Report:**
```bash
python scripts/generate_technical_report.py \
  --config report_config.yaml \
  --format html \
  --output report.html
```

#### Report Configuration (`report_config.yaml`)

```yaml
report:
  title: "Substrait Compliance Report"
  date_format: "%Y-%m-%d"
  
sections:
  - leaderboard
  - coverage_analysis
  - performance_metrics
  - recommendations

leaderboard:
  top_n: 10
  sort_by: "pass_rate"
  
coverage:
  categories:
    - arithmetic
    - string
    - comparison
    - datetime
  
  thresholds:
    excellent: 95
    good: 85
    needs_improvement: 70
```

#### Example Report Output

```markdown
# Substrait Compliance Report
Generated: 2026-05-30

## Leaderboard

| Rank | Engine | Pass Rate | Tests Passed | Total Tests |
|------|--------|-----------|--------------|-------------|
| 1 | DuckDB | 98.5% | 837 | 850 |
| 2 | DataFusion | 95.2% | 809 | 850 |
| 3 | PostgreSQL | 92.8% | 789 | 850 |

## Coverage Analysis

### By Category
- Arithmetic: 95% (810/850)
- String: 92% (782/850)
- Comparison: 98% (833/850)
- Datetime: 88% (748/850)

### Recommendations
1. Improve datetime function coverage
2. Add more edge cases for string functions
3. Enhance error handling tests
```

---

## SDK Tools

### SDK Verification (`verify_sdk_builds.sh`)

Verifies all SDK implementations build and pass tests.

#### Usage

**Verify All SDKs:**
```bash
bash scripts/verify_sdk_builds.sh
```

**Verify Specific SDK:**
```bash
bash scripts/verify_sdk_builds.sh java
bash scripts/verify_sdk_builds.sh csharp
bash scripts/verify_sdk_builds.sh rust
```

#### Output Example
```
Verifying SDK Builds
====================

Java SDK:
  ✓ Build successful
  ✓ Tests passed (45/45)
  ✓ Coverage: 87%

C# SDK:
  ✓ Build successful
  ✓ Tests passed (38/38)
  ✓ Coverage: 82%

Rust SDK:
  ✓ Build successful
  ✓ Tests passed (42/42)
  ✓ Coverage: 90%

All SDKs verified successfully!
```

### SDK Installation (`install_sdk_tools.sh`)

Installs required tools for SDK development.

#### Usage
```bash
bash scripts/install_sdk_tools.sh
```

#### Installs
- Java 11+ (via SDKMAN)
- .NET 6.0+
- Rust toolchain
- Build tools (Gradle, Maven)

---

## Quick Start

### 1. Check Test Quality
```bash
# Check a single test file
python scripts/quality_checker.py test-suites/functions/string/concat.test

# Check all string functions
python scripts/batch_quality_check.py test-suites/functions/string/
```

### 2. Enhance Tests
```bash
# Enhance a single file
python scripts/test_enhancer.py test-suites/functions/string/concat.test

# Enhance all string functions
bash scripts/enhance_all_categories.sh string
```

### 3. Generate Reports
```bash
# Generate technical report
python scripts/generate_technical_report.py \
  --input quality_reports/ \
  --output docs/TECHNICAL_REPORT.md

# Generate leaderboard
python scripts/generate_leaderboard.py \
  --input quality_reports/ \
  --output leaderboard.md
```

### 4. Verify SDKs
```bash
# Verify all SDKs build
bash scripts/verify_sdk_builds.sh

# Install SDK tools if needed
bash scripts/install_sdk_tools.sh
```

---

## Advanced Usage

### Batch Processing

**Process Multiple Categories:**
```bash
for category in arithmetic string comparison datetime; do
  python scripts/batch_quality_check.py test-suites/functions/$category/
  bash scripts/enhance_all_categories.sh $category
done
```

### Custom Quality Checks

**Create Custom Checker:**
```python
from quality_checker import QualityChecker

checker = QualityChecker()
checker.add_check('custom_check', my_custom_function)
result = checker.check_file('test.test')
```

### Automated Reporting

**Cron Job for Daily Reports:**
```bash
# Add to crontab
0 2 * * * cd /path/to/repo && python scripts/generate_technical_report.py
```

---

## Troubleshooting

### Common Issues

**1. Import Errors**
```bash
# Solution: Install dependencies
pip install -r scripts/requirements-report.txt
```

**2. Permission Denied**
```bash
# Solution: Make scripts executable
chmod +x scripts/*.sh
```

**3. Python Version**
```bash
# Solution: Use Python 3.8+
python3 --version
# If needed, use python3 explicitly
python3 scripts/quality_checker.py test.test
```

---

## Additional Resources

- [Test Enhancement Guide](TEST_ENHANCEMENT_GUIDE.md) - Detailed enhancement strategies
- [Quality Checker README](QUALITY_CHECKER_README.md) - In-depth quality checking
- [Report Generator Config](REPORT_GENERATOR_CONFIG.md) - Report customization
- [Quick Start Guide](QUICKSTART_QUALITY_CHECK.md) - Getting started quickly

---

**Version:** 1.0  
**Last Updated:** 2026-05-30  
**Maintained by:** Substrait Compliance Team
