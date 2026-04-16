#!/bin/bash

echo "=================================="
echo "Dashboard Setup Verification"
echo "=================================="
echo ""

# Check if we're in the demo directory
if [ ! -f "runner/SimpleDemoRunner.java" ]; then
    echo "❌ Error: Please run this script from the demo directory"
    echo "   cd demo && ./verify-setup.sh"
    exit 1
fi

echo "✓ Running from correct directory"
echo ""

# Check if output files exist
echo "Checking for generated reports..."
if [ -f "output/mockdb-report.json" ] && [ -f "output/fastdb-report.json" ] && [ -f "output/clouddb-report.json" ]; then
    echo "✓ All engine reports found"
else
    echo "❌ Missing engine reports. Run: ./runner/run-simple-demo.sh"
    exit 1
fi

if [ -f "output/leaderboard.json" ]; then
    echo "✓ Leaderboard found"
else
    echo "❌ Missing leaderboard. Run: ./runner/run-simple-demo.sh"
    exit 1
fi

if [ -f "dashboard/data/leaderboard.json" ]; then
    echo "✓ Dashboard data found"
else
    echo "❌ Missing dashboard data. Run: ./runner/run-simple-demo.sh"
    exit 1
fi

echo ""
echo "Checking for complexity data..."
COMPLEXITY_COUNT=$(grep -c '"complexity"' output/mockdb-report.json)
if [ "$COMPLEXITY_COUNT" -eq 22 ]; then
    echo "✓ Complexity data present (22 queries)"
else
    echo "❌ Complexity data missing or incomplete ($COMPLEXITY_COUNT/22)"
    echo "   Recompile and run: cd demo && javac -d . runner/SimpleDemoRunner.java && ./runner/run-simple-demo.sh"
    exit 1
fi

echo ""
echo "Checking dashboard files..."
if [ -f "dashboard/index.html" ]; then
    echo "✓ index.html found"
else
    echo "❌ Missing index.html"
    exit 1
fi

if [ -f "dashboard/styles.css" ]; then
    echo "✓ styles.css found"
else
    echo "❌ Missing styles.css"
    exit 1
fi

if [ -f "dashboard/dashboard.js" ]; then
    echo "✓ dashboard.js found"
else
    echo "❌ Missing dashboard.js"
    exit 1
fi

echo ""
echo "Checking for key features..."

# Check for modal in HTML
if grep -q "queryModal" dashboard/index.html; then
    echo "✓ Modal structure present"
else
    echo "❌ Modal structure missing"
fi

# Check for filter dropdown
if grep -q "complexityFilter" dashboard/index.html; then
    echo "✓ Filter dropdown present"
else
    echo "❌ Filter dropdown missing"
fi

# Check for filterByComplexity function
if grep -q "function filterByComplexity" dashboard/dashboard.js; then
    echo "✓ Filter function present"
else
    echo "❌ Filter function missing"
fi

# Check for showEngineDetails function
if grep -q "async function showEngineDetails" dashboard/dashboard.js; then
    echo "✓ Modal function present"
else
    echo "❌ Modal function missing"
fi

echo ""
echo "=================================="
echo "✅ Setup verification complete!"
echo "=================================="
echo ""
echo "Next steps:"
echo "1. cd dashboard"
echo "2. python3 -m http.server 8080"
echo "3. Open http://localhost:8080"
echo ""
echo "Test the features:"
echo "• Click any engine row to open modal"
echo "• Use complexity filter dropdown"
echo "• Verify query cards show correct data"
echo ""
echo "See TEST_INSTRUCTIONS.md for detailed testing"

# Made with Bob
