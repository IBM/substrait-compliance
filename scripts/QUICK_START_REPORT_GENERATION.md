# Quick Start: Generate Technical Report

Generate a comprehensive technical report for the Substrait Compliance Framework using AI.

## 🚀 Quick Start (2 Steps)

### 1. Install Dependencies

```bash
pip install litellm
```

### 2. Generate Report

```bash
python scripts/generate_technical_report.py
```

That's it! Your report will be saved to `docs/TECHNICAL_REPORT.md`

**Note:** The script uses the same pre-configured LiteLLM proxy as the quality checker:
- **Base URL:** `https://ete-litellm.bx.cloud9.ibm.com`
- **Model:** `aws/claude-sonnet-4-5`
- **API Key:** Pre-configured (same as quality_checker.py)

## 📋 What You Get

A comprehensive technical report including:

- ✅ Executive Summary
- ✅ Architecture Overview  
- ✅ SDK Implementation Details
- ✅ Test Suite Coverage
- ✅ REST API Documentation
- ✅ CI/CD Automation
- ✅ Demo System Features
- ✅ Project Statistics
- ✅ Benefits Analysis
- ✅ Future Roadmap
- ✅ Getting Started Guide

## 🎯 Common Use Cases

### Generate Report for Community

```bash
python scripts/generate_technical_report.py \
  --output docs/SUBSTRAIT_COMMUNITY_REPORT.md
```

### Use Different Model

```bash
# Claude Opus (most capable)
python scripts/generate_technical_report.py --model aws/claude-opus-4-6

# Claude Haiku (fastest, cheapest)
python scripts/generate_technical_report.py --model aws/claude-haiku-4-5

# Gemini Pro (Google)
python scripts/generate_technical_report.py --model gemini-2.5-pro
```

### Analyze Project First

```bash
# See what will be included
python scripts/generate_technical_report.py --analyze-only
```

### Use Example Script

```bash
# Interactive script with checks
./scripts/example_generate_report.sh
```

## 💰 Cost

Typical cost per report: **$0.10 - $0.50**

- Input: ~5,000-10,000 tokens (project analysis)
- Output: ~10,000-15,000 tokens (comprehensive report)

## 🔧 Troubleshooting

### "litellm is not installed"

```bash
pip install litellm
```

### Connection Issues

The script uses a pre-configured LiteLLM proxy. If you have connection issues:

```bash
# Check if the proxy is accessible
curl https://ete-litellm.bx.cloud9.ibm.com/health

# Try with a different model
python scripts/generate_technical_report.py --model aws/claude-haiku-4-5
```

### Want to use a different LiteLLM instance?

```bash
python scripts/generate_technical_report.py \
  --base-url https://your-litellm-instance.com \
  --api-key your_api_key
```

## 📚 Full Documentation

See [TECHNICAL_REPORT_GENERATOR.md](TECHNICAL_REPORT_GENERATOR.md) for:
- Detailed usage instructions
- All command-line options
- Supported models
- Advanced configuration
- CI/CD integration
- Cost considerations

## 🎬 Example Output

The generated report is a professional Markdown document with:

```markdown
# Substrait Compliance Framework - Technical Report

**Version:** 1.0.0
**Date:** 2026-04-26

## Executive Summary

The Substrait Compliance Framework represents a paradigm shift...

## Architecture Overview

### Decentralized Compliance Model
...

[Tables, code examples, statistics, and more]
```

## ⚡ Pro Tips

1. **Run analysis first** to see what will be included:
   ```bash
   python scripts/generate_technical_report.py --analyze-only
   ```

2. **Use faster model** for quick iterations:
   ```bash
   python scripts/generate_technical_report.py --model claude-3-haiku-20240307
   ```

3. **Generate multiple reports** for different audiences:
   ```bash
   # Technical deep-dive
   python scripts/generate_technical_report.py --output reports/technical.md
   
   # Executive summary
   python scripts/generate_technical_report.py --output reports/executive.md
   ```

4. **Check the output** before sharing:
   ```bash
   cat docs/TECHNICAL_REPORT.md | head -50
   ```

## 🤝 Need Help?

- **Script Issues**: Check [TECHNICAL_REPORT_GENERATOR.md](TECHNICAL_REPORT_GENERATOR.md)
- **LiteLLM Issues**: https://docs.litellm.ai/
- **API Issues**: https://docs.anthropic.com/

---

**Ready to generate your report?**

```bash
# Just run it - uses pre-configured LiteLLM proxy
python scripts/generate_technical_report.py
```

🎉 **Done!** Check `docs/TECHNICAL_REPORT.md`

---

**Configuration Details:**
- Uses same LiteLLM setup as `quality_checker.py`
- Pre-configured API key and base URL
- Model: `aws/claude-sonnet-4-5` (Claude 3.5 Sonnet)
- No additional setup required!