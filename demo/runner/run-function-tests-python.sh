#!/bin/bash

# Run Function-Level Compliance Tests Demo (Python Version)
# This script demonstrates the new function test framework

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║   Substrait Function-Level Compliance Testing Demo        ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if we're in the right directory
if [ ! -d "../../test-suites/functions" ]; then
    echo "❌ Error: Must run from demo/runner directory"
    exit 1
fi

# Check for Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Error: Python 3 is required but not installed"
    exit 1
fi

echo "🐍 Using Python: $(python3 --version)"
echo ""

# Make script executable
chmod +x function_test_demo.py

# Run the demo
echo "🚀 Running function tests..."
echo ""

python3 function_test_demo.py

if [ $? -ne 0 ]; then
    echo "❌ Demo execution failed"
    exit 1
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "✅ Function tests completed successfully!"
echo ""
echo "📊 Results:"
echo "   - JSON files: ../output/*_function_tests.json"
echo "   - Summary: ../output/function_tests_summary.json"
echo ""
echo "💡 Next steps:"
echo "   1. Review the function test results"
echo "   2. Compare with TPC-H results"
echo "   3. Identify function-level gaps"
echo ""
echo "📖 Documentation:"
echo "   - Implementation: ../../docs/FUNCTION_TESTS_IMPLEMENTATION.md"
echo "   - Test Suite README: ../../test-suites/functions/README.md"
echo "════════════════════════════════════════════════════════════"

# Made with Bob
