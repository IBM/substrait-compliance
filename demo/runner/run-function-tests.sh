#!/bin/bash

# Run Function-Level Compliance Tests Demo
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

# Create output directories
mkdir -p ../output dashboard/data

# Store SDK JAR path before cd
SDK_JAR="$(cd ../../sdk/java/build/libs && pwd)/substrait-compliance-0.1.1-all.jar"

# Compile the demo
echo "📦 Compiling demo..."
javac -d ../output \
    -cp ".:../engines:${SDK_JAR}:lib/*" \
    FunctionTestDemo.java \
    ../engines/*.java

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo ""

# Run the demo
echo "🚀 Running function tests..."
echo ""

java -cp "../output:../../engines:lib/*:${SDK_JAR}" \
    demo.runner.FunctionTestDemo

if [ $? -ne 0 ]; then
    echo "❌ Demo execution failed"
    exit 1
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "✅ Function tests completed successfully!"
echo ""
echo "📊 Results:"
echo "   - JSON files: output/*_function_tests.json"
echo "   - Summary: output/function_tests_summary.json"
echo ""
echo "🌐 View results:"
echo "   Open dashboard/index.html in your browser"
echo ""
echo "💡 Next steps:"
echo "   1. Review the function test results"
echo "   2. Compare with TPC-H results"
echo "   3. Identify function-level gaps"
echo "════════════════════════════════════════════════════════════"

# Made with Bob
