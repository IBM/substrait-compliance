#!/bin/bash

# Substrait Compliance Framework - TPC-DS Demo (Python version)
# This script runs TPC-DS compliance tests using the Python SDK

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║   Substrait TPC-DS Compliance Testing Demo (Python)      ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if we're in the right directory
if [ ! -d "../../test-suites/tpcds" ]; then
    echo "❌ Error: Must run from demo/runner directory"
    exit 1
fi

# Check Python version
PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}' | cut -d. -f1,2)
echo "🐍 Using Python: $PYTHON_VERSION"
echo ""

# Create output directory
mkdir -p ../output

# Run the Python TPC-DS demo
echo "🚀 Running TPC-DS compliance tests (99 queries)..."
echo ""

cd ..
python3 runner/tpcds_demo.py

echo ""
echo "════════════════════════════════════════════════════════════"
echo "✅ TPC-DS demo completed successfully!"
echo ""
echo "📊 Results:"
echo "   - JSON files: runner/output/*_tpcds_function_tests.json"
echo "   - Summary: runner/output/tpcds_function_tests_summary.json"
echo ""
echo "🌐 View results in the output directory"
echo "════════════════════════════════════════════════════════════"
