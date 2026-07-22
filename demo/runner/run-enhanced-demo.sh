#!/bin/bash

# Enhanced Demo Runner Script
# Demonstrates all phases of the Substrait Compliance Framework

set -e

echo "================================================================================"
echo "Substrait Compliance Framework - Enhanced Demo"
echo "================================================================================"
echo ""

# Change to demo directory
cd "$(dirname "$0")/.."

# Clean previous outputs
echo "🧹 Cleaning previous outputs..."
rm -rf output/storage output/analytics output/reproductions
mkdir -p output/storage output/analytics output/reproductions

# Compile the enhanced demo
echo ""
echo "🔨 Compiling enhanced demo..."
javac -d . runner/EnhancedDemoRunner.java

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Compilation successful!"

# Run the demo
echo ""
echo "🚀 Running enhanced demo..."
echo ""
java EnhancedDemoRunner

# Check if demo succeeded
if [ $? -eq 0 ]; then
    echo ""
    echo "================================================================================"
    echo "✅ Enhanced Demo Completed Successfully!"
    echo "================================================================================"
    echo ""
    echo "Generated artifacts:"
    echo "  📁 output/                    - All generated files"
    echo "  📁 output/storage/private/    - Private storage (full data)"
    echo "  📁 output/storage/public/     - Public storage (anonymized)"
    echo "  📁 output/analytics/          - Analytics reports"
    echo "  📁 dashboard/data/            - Dashboard data"
    echo ""
    echo "To view the dashboard:"
    echo "  1. Open: demo/dashboard/index.html"
    echo "  2. Or run: cd dashboard && python -m http.server 8080"
    echo ""
else
    echo ""
    echo "❌ Demo failed!"
    exit 1
fi

# Made with Bob
