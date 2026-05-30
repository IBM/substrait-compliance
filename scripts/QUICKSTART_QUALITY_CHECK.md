# Quick Start: Quality Checking with LiteLLM

Get started with quality checking your Substrait function tests in 5 minutes!

## 1. Install Dependencies

```bash
pip install litellm openai pyyaml protobuf
```

Or use the requirements file:

```bash
pip install -r sdk/python/requirements.txt
```

## 2. Verify Setup

Check that everything is configured correctly:

```bash
python -c "from litellm import completion; print('✓ LiteLLM installed successfully')"
```

## 3. Run Your First Quality Check

### Option A: Check a Single Category

```bash
cd /path/to/substrait-compliance-private
python scripts/quality_checker.py arithmetic
```

### Option B: Run Examples

```bash
python scripts/example_quality_check.py
```

### Option C: Batch Check All Categories

```bash
python scripts/batch_quality_check.py
```

## 4. View Results

Results are saved to `quality_reports/`:

```bash
# View markdown report
cat quality_reports/QUALITY_REPORT.md

# View JSON summary
cat quality_reports/summary.json

# View specific category
cat quality_reports/arithmetic_quality.json
```

## Configuration

The quality checker is configured with:

- **LiteLLM Proxy**: `https://ete-litellm.bx.cloud9.ibm.com`
- **API Key**: set via the `LITELLM_API_KEY` environment variable
- **Model**: `claude-3-5-sonnet-20241022`

To modify settings, edit `scripts/quality_config.yaml`.

## What Gets Checked?

✅ **Correctness**: Are test cases mathematically/logically correct?  
✅ **Coverage**: Are edge cases covered (null, overflow, boundaries)?  
✅ **Completeness**: What test cases are missing?  
✅ **Organization**: Are tests well-structured?  
✅ **Best Practices**: Do tests follow standards?

## Understanding Scores

- **9-10**: Excellent - Comprehensive coverage
- **7-8**: Good - Solid with minor gaps
- **5-6**: Acceptable - Needs improvement
- **3-4**: Needs Work - Significant gaps
- **1-2**: Poor - Major issues

## Common Commands

```bash
# Check specific category
python scripts/quality_checker.py string

# Check specific file
python scripts/quality_checker.py --file test-suites/functions/arithmetic/add.test

# Check multiple categories
python scripts/batch_quality_check.py --categories arithmetic boolean string

# Custom output directory
python scripts/quality_checker.py arithmetic --output my_reports

# Quiet mode
python scripts/batch_quality_check.py --quiet
```

## Troubleshooting

### "litellm not installed"
```bash
pip install litellm openai
```

### "Could not import quality_checker"
Make sure you're in the project root directory.

### API Connection Issues
Verify the LiteLLM proxy is accessible:
```bash
curl https://ete-litellm.bx.cloud9.ibm.com
```

## Next Steps

1. ✅ Review generated reports in `quality_reports/`
2. ✅ Implement recommended test improvements
3. ✅ Re-run quality check to verify improvements
4. ✅ Integrate into CI/CD pipeline

## Full Documentation

For detailed documentation, see [QUALITY_CHECKER_README.md](QUALITY_CHECKER_README.md)

---

**Ready to improve your test quality? Start checking now!** 🚀