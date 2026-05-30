# Technical Report Generator

Automated technical report generation for the Substrait Compliance Framework using LiteLLM and Claude.

## Overview

This script uses LiteLLM to call Claude (or other LLMs) to automatically generate comprehensive technical reports about the Substrait Compliance Framework. It analyzes the project structure, gathers statistics, and uses AI to create professional documentation.

## Features

- 🤖 **AI-Powered**: Uses Claude via LiteLLM for intelligent report generation
- 📊 **Automatic Analysis**: Scans project structure and gathers metrics
- 📝 **Comprehensive Reports**: Generates detailed technical documentation
- 🔧 **Configurable**: Support for multiple LLM models
- 💾 **Fallback Mode**: Generates basic report if LLM unavailable

## Installation

### 1. Install Dependencies

```bash
pip install -r scripts/requirements-report.txt
```

Or install directly:

```bash
pip install litellm
```

### 2. Set Up API Key

The script requires an Anthropic API key to use Claude:

```bash
export ANTHROPIC_API_KEY=your_api_key_here
```

Or pass it as a command-line argument:

```bash
python scripts/generate_technical_report.py --api-key your_api_key_here
```

## Usage

### Basic Usage

Generate a technical report with default settings:

```bash
python scripts/generate_technical_report.py
```

This will:
- Analyze the project structure
- Call Claude to generate the report
- Save to `docs/TECHNICAL_REPORT.md`

### Custom Output Location

```bash
python scripts/generate_technical_report.py --output reports/my_report.md
```

### Use Different Model

```bash
# Use Claude 3 Opus
python scripts/generate_technical_report.py --model claude-3-opus-20240229

# Use Claude 3 Sonnet (default)
python scripts/generate_technical_report.py --model claude-3-5-sonnet-20241022

# Use GPT-4 (requires OpenAI API key)
export OPENAI_API_KEY=your_key
python scripts/generate_technical_report.py --model gpt-4-turbo-preview
```

### Analyze Project Only

To see what information will be gathered without generating a report:

```bash
python scripts/generate_technical_report.py --analyze-only
```

### Custom Project Root

If running from a different directory:

```bash
python scripts/generate_technical_report.py --project-root /path/to/substrait-compliance
```

## Command-Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `--output PATH` | Output file path | `docs/TECHNICAL_REPORT.md` |
| `--model MODEL` | LLM model to use | `claude-3-5-sonnet-20241022` |
| `--project-root PATH` | Project root directory | Current directory |
| `--api-key KEY` | API key for LLM | From `ANTHROPIC_API_KEY` env var |
| `--analyze-only` | Only analyze, don't generate | False |

## What Gets Analyzed

The script automatically analyzes:

### Project Structure
- Root directories and key files
- SDK implementations (Java, Python, Rust)
- Test suite organization

### Test Coverage
- TPC-H benchmark queries
- Function test categories and files
- Total test count

### Components
- REST API implementation
- Demo system components
- CI/CD workflows

### Documentation
- All Markdown files
- README files
- Implementation guides

### Statistics
- Total test files
- Total workflows
- Total documentation files
- Lines of code (estimated)

## Generated Report Sections

The AI-generated report includes:

1. **Executive Summary** - Overview and key achievements
2. **Architecture Overview** - System design and components
3. **SDK Implementation** - Multi-language SDK details
4. **Test Suite Coverage** - Comprehensive test information
5. **REST API Infrastructure** - API features and endpoints
6. **CI/CD Automation** - Workflow details
7. **Interactive Demo System** - Demo capabilities
8. **Project Statistics** - Metrics and numbers
9. **Benefits Analysis** - Value for stakeholders
10. **Technical Innovations** - Key innovations
11. **Future Roadmap** - Planned enhancements
12. **Getting Started** - Quick start guides
13. **Conclusion** - Summary and next steps

## Examples

### Example 1: Generate Report for Substrait Community

```bash
# Set API key
export ANTHROPIC_API_KEY=sk-ant-...

# Generate comprehensive report
python scripts/generate_technical_report.py \
  --output docs/SUBSTRAIT_COMMUNITY_REPORT.md \
  --model claude-3-5-sonnet-20241022

# Report saved to docs/SUBSTRAIT_COMMUNITY_REPORT.md
```

### Example 2: Quick Analysis

```bash
# See what will be included
python scripts/generate_technical_report.py --analyze-only

# Output shows JSON with all gathered information
```

### Example 3: Use with Different LLM

```bash
# Use OpenAI GPT-4
export OPENAI_API_KEY=sk-...
python scripts/generate_technical_report.py --model gpt-4-turbo-preview

# Use Anthropic Claude Opus
export ANTHROPIC_API_KEY=sk-ant-...
python scripts/generate_technical_report.py --model claude-3-opus-20240229
```

## Supported Models

LiteLLM supports many models. Common options:

### Anthropic Claude
- `claude-3-5-sonnet-20241022` (recommended, default)
- `claude-3-opus-20240229` (most capable)
- `claude-3-sonnet-20240229`
- `claude-3-haiku-20240307` (fastest)

### OpenAI
- `gpt-4-turbo-preview`
- `gpt-4`
- `gpt-3.5-turbo`

### Others
- `gemini/gemini-pro` (Google)
- `command-nightly` (Cohere)
- Many more via LiteLLM

See [LiteLLM documentation](https://docs.litellm.ai/docs/providers) for full list.

## Output Format

The generated report is in Markdown format with:

- Clear section hierarchies (H1, H2, H3)
- Tables for structured data
- Code blocks with syntax highlighting
- Emojis for visual appeal
- Statistics and metrics
- Professional formatting

## Troubleshooting

### Error: "litellm is not installed"

```bash
pip install litellm
```

### Error: "No API key found"

Set the appropriate environment variable:

```bash
# For Claude
export ANTHROPIC_API_KEY=your_key

# For OpenAI
export OPENAI_API_KEY=your_key
```

### Error: "Rate limit exceeded"

Wait a moment and try again, or use a different model:

```bash
python scripts/generate_technical_report.py --model claude-3-haiku-20240307
```

### Fallback Report Generated

If the LLM call fails, a basic fallback report is generated. Check:
- API key is correct
- Internet connection is working
- Model name is valid
- API quota is not exceeded

## Cost Considerations

Using Claude or other LLMs incurs API costs:

- **Claude 3.5 Sonnet**: ~$3 per million input tokens, ~$15 per million output tokens
- **Claude 3 Opus**: ~$15 per million input tokens, ~$75 per million output tokens
- **GPT-4 Turbo**: ~$10 per million input tokens, ~$30 per million output tokens

A typical report generation uses:
- Input: ~5,000-10,000 tokens (project info + prompt)
- Output: ~10,000-15,000 tokens (comprehensive report)
- **Estimated cost per report**: $0.10-$0.50

## Integration with CI/CD

You can integrate this into GitHub Actions:

```yaml
name: Generate Technical Report

on:
  schedule:
    - cron: '0 0 1 * *'  # Monthly
  workflow_dispatch:

jobs:
  generate-report:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      
      - name: Install dependencies
        run: pip install litellm
      
      - name: Generate report
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
        run: |
          python scripts/generate_technical_report.py \
            --output docs/TECHNICAL_REPORT.md
      
      - name: Commit report
        run: |
          git config user.name "GitHub Actions"
          git config user.email "actions@github.com"
          git add docs/TECHNICAL_REPORT.md
          git commit -m "Update technical report" || true
          git push
```

## Advanced Usage

### Custom Prompt

To customize the report content, edit the `_create_prompt()` method in the script:

```python
def _create_prompt(self, project_info: Dict[str, Any]) -> str:
    prompt = f"""Create a technical report focusing on...
    
    {json.dumps(project_info, indent=2)}
    
    Include these specific sections:
    - Custom section 1
    - Custom section 2
    """
    return prompt
```

### Multiple Reports

Generate different types of reports:

```bash
# Executive summary for stakeholders
python scripts/generate_technical_report.py \
  --output reports/executive_summary.md

# Technical deep-dive for developers
python scripts/generate_technical_report.py \
  --output reports/technical_deep_dive.md

# Quick reference guide
python scripts/generate_technical_report.py \
  --output reports/quick_reference.md
```

## Contributing

To improve the report generator:

1. Enhance project analysis in `ProjectAnalyzer` class
2. Improve prompt engineering in `_create_prompt()` method
3. Add new report sections or formats
4. Support additional LLM providers
5. Add caching for repeated generations

## License

Apache License 2.0 - Same as the Substrait Compliance Framework

## Support

- **Issues**: Open a GitHub issue
- **Questions**: See main project documentation
- **LiteLLM Docs**: https://docs.litellm.ai/

---

**Made with ❤️ for the Substrait Community**