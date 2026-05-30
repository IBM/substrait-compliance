#!/bin/bash
# Example script to generate technical report using LiteLLM and Claude

set -e

echo "================================================"
echo "Substrait Compliance Technical Report Generator"
echo "================================================"
echo ""

# Check if litellm is installed
if ! python3 -c "import litellm" 2>/dev/null; then
    echo "❌ Error: litellm is not installed"
    echo ""
    echo "Install it with:"
    echo "  pip install litellm"
    echo ""
    exit 1
fi

# Note: Using pre-configured LiteLLM proxy (same as quality checker)
echo "ℹ️  Using LiteLLM proxy: https://ete-litellm.bx.cloud9.ibm.com"
echo "   Model: aws/claude-sonnet-4-5"
echo ""

echo "📊 Step 1: Analyzing project structure..."
python3 scripts/generate_technical_report.py --analyze-only > /tmp/project_analysis.json
echo "✅ Analysis complete"
echo ""

echo "🤖 Step 2: Generating technical report with Claude..."
echo "   Model: aws/claude-sonnet-4-5"
echo "   Output: docs/TECHNICAL_REPORT.md"
echo ""

python3 scripts/generate_technical_report.py \
    --output docs/TECHNICAL_REPORT.md

echo ""
echo "================================================"
echo "✅ Report generation complete!"
echo "================================================"
echo ""
echo "📄 Report saved to: docs/TECHNICAL_REPORT.md"
echo ""
echo "View the report:"
echo "  cat docs/TECHNICAL_REPORT.md"
echo "  open docs/TECHNICAL_REPORT.md  # macOS"
echo "  xdg-open docs/TECHNICAL_REPORT.md  # Linux"
echo ""
echo "Project analysis saved to: /tmp/project_analysis.json"
echo ""

# Made with Bob
