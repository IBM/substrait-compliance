# Technical Report Generator Configuration

## Overview

The technical report generator uses the **same LiteLLM configuration** as the quality checker (`quality_checker.py`) for consistency across the project.

## Configuration Details

### LiteLLM Proxy Setup

```python
LITELLM_BASE_URL = "https://ete-litellm.bx.cloud9.ibm.com"
LITELLM_API_KEY = os.getenv("LITELLM_API_KEY", "")
DEFAULT_MODEL = "aws/claude-sonnet-4-5"
```

### Why This Configuration?

1. **Consistency**: Same setup as the report-generation tooling
2. **Safer Secret Handling**: API keys come from environment variables, not source control
3. **Team Access**: Supports your team's LiteLLM proxy
4. **Cost Control**: Centralized billing and rate limiting

## Available Models

Your LiteLLM proxy provides access to multiple models:

### Claude Models (Anthropic via AWS)
- `aws/claude-sonnet-4-5` ⭐ **Default** - Best balance of speed and quality
- `aws/claude-opus-4-6` - Most capable, slower
- `aws/claude-opus-4-1` - High capability
- `aws/claude-haiku-4-5` - Fastest, most cost-effective
- `aws/claude-3-5-haiku` - Balanced performance

### Gemini Models (Google)
- `gemini-2.5-pro` - High capability
- `gemini-2.5-flash` - Fast and efficient

### Azure OpenAI
- `Azure/gpt-5-2025-08-07` - Latest GPT-5

## Usage Examples

### Basic Usage (Uses Defaults)

```bash
python scripts/generate_technical_report.py
```

This automatically uses:
- Base URL: `https://ete-litellm.bx.cloud9.ibm.com`
- API Key: from the `LITELLM_API_KEY` environment variable
- Model: `aws/claude-sonnet-4-5`

### Use Different Model

```bash
# Fastest model
python scripts/generate_technical_report.py --model aws/claude-haiku-4-5

# Most capable model
python scripts/generate_technical_report.py --model aws/claude-opus-4-6

# Google's model
python scripts/generate_technical_report.py --model gemini-2.5-pro
```

### Custom LiteLLM Instance

If you want to use a different LiteLLM instance:

```bash
python scripts/generate_technical_report.py \
  --base-url https://your-litellm.example.com \
  --api-key your_api_key \
  --model your_model
```

## Request Parameters

The generator uses these parameters (same as quality_checker.py):

```python
completion(
    model=self.model,
    api_key=self.api_key,
    api_base=self.base_url,
    custom_llm_provider="openai",  # Treat as OpenAI-compatible
    temperature=0.3,  # Lower for consistent output
    max_tokens=4000,
    timeout=120,
    force_timeout=120
)
```

### Parameter Explanation

- **temperature=0.3**: Lower temperature for more consistent, focused output
- **max_tokens=4000**: Sufficient for comprehensive reports
- **timeout=120**: 2-minute timeout to prevent hanging
- **custom_llm_provider="openai"**: Treats proxy as OpenAI-compatible API

## Comparison with Quality Checker

| Aspect | Quality Checker | Report Generator |
|--------|----------------|------------------|
| **Base URL** | `https://ete-litellm.bx.cloud9.ibm.com` | Same ✅ |
| **API Key** | `LITELLM_API_KEY` environment variable | Same approach ✅ |
| **Default Model** | `aws/claude-sonnet-4-5` | Same ✅ |
| **Temperature** | 0.3 | 0.3 ✅ |
| **Max Tokens** | 4000 | 4000 ✅ |
| **Timeout** | 60s | 120s (longer for reports) |
| **Provider** | `openai` | `openai` ✅ |

## Cost Considerations

Using the team's LiteLLM proxy:

- **Centralized Billing**: All costs tracked centrally
- **Rate Limiting**: Managed by proxy
- **No Embedded API Keys**: Credentials stay outside the repository

### Estimated Costs per Report

- **Input**: ~5,000-10,000 tokens (project analysis)
- **Output**: ~4,000 tokens (comprehensive report)
- **Total**: ~9,000-14,000 tokens per report

With Claude Sonnet 4.5:
- Approximate cost: **$0.05-$0.10 per report**

## Troubleshooting

### Connection Issues

```bash
# Test proxy connectivity
curl https://ete-litellm.bx.cloud9.ibm.com/health

# Check available models
curl https://ete-litellm.bx.cloud9.ibm.com/models \
  -H "Authorization: Bearer $LITELLM_API_KEY"
```

### Rate Limiting

If you hit rate limits:

1. **Wait**: Rate limits reset periodically
2. **Use Haiku**: Faster model with lower rate impact
   ```bash
   python scripts/generate_technical_report.py --model aws/claude-haiku-4-5
   ```
3. **Contact Admin**: If persistent issues

### Timeout Issues

If reports timeout:

1. **Reduce Scope**: Use `--analyze-only` first to check data size
2. **Increase Timeout**: Modify script if needed
3. **Use Faster Model**: Try Haiku for quicker responses

## Configuration Files

### Main Script
- **File**: `scripts/generate_technical_report.py`
- **Config Lines**: 34-36 (LITELLM_BASE_URL, LITELLM_API_KEY, DEFAULT_MODEL)

### Quality Checker (Reference)
- **File**: `scripts/quality_checker.py`
- **Config Lines**: 30-33 (same configuration)

### Quality Config YAML
- **File**: `scripts/quality_config.yaml`
- **Section**: `litellm:` (lines 5-22)

## Security Notes

⚠️ **API Key in Code**: The API key is hardcoded for team convenience. This is acceptable for:
- Internal team use
- Private repositories
- Controlled access

For public repositories, consider:
- Environment variables
- Secret management systems
- API key rotation

## Support

### Internal Support
- Check with team lead for proxy issues
- Review `quality_config.yaml` for latest settings
- See `quality_checker.py` for reference implementation

### LiteLLM Documentation
- Proxy docs: https://docs.litellm.ai/docs/proxy/quick_start
- Model support: https://docs.litellm.ai/docs/providers

---

**Last Updated**: 2026-04-26  
**Configuration Version**: 1.0  
**Maintained By**: Substrait Compliance Team