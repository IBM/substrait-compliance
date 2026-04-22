#!/bin/bash

# Substrait Compliance Framework - Demo Runner Script
# This script compiles and runs the complete demo

set -e  # Exit on error

echo "================================================================================"
echo "Substrait Compliance Framework - Demo Setup"
echo "================================================================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if we're in the demo directory
if [ ! -f "README.md" ] || [ ! -d "engines" ]; then
    echo -e "${RED}❌ Error: Please run this script from the demo directory${NC}"
    echo "   cd demo && ./runner/run-demo.sh"
    exit 1
fi

# Step 1: Build the main SDK
echo -e "${BLUE}📦 Step 1: Building Substrait Compliance SDK...${NC}"
cd ../sdk/java
if [ ! -f "gradlew" ]; then
    echo -e "${RED}❌ Error: Gradle wrapper not found${NC}"
    exit 1
fi

./gradlew build -x test
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ SDK built successfully${NC}"
else
    echo -e "${RED}❌ SDK build failed${NC}"
    exit 1
fi

cd ../../demo
echo ""

# Step 2: Create output directories
echo -e "${BLUE}📁 Step 2: Creating output directories...${NC}"
mkdir -p output
mkdir -p dashboard/data
echo -e "${GREEN}✅ Directories created${NC}"
echo ""

# Step 3: Run standalone TPC-H demo
echo -e "${BLUE}🚀 Step 3: Running TPC-H compliance demo...${NC}"
echo ""

mkdir -p build
javac -d build runner/SimpleDemoRunner.java

if [ $? -eq 0 ]; then
    java -cp build SimpleDemoRunner
    echo ""
    echo -e "${GREEN}✅ TPC-H demo completed successfully!${NC}"
else
    echo -e "${RED}❌ TPC-H demo compilation failed${NC}"
    exit 1
fi
echo ""

# Step 4: Run standalone function demo
echo -e "${BLUE}🔬 Step 4: Running function compliance demo...${NC}"
echo ""

if command -v python3 &> /dev/null; then
    chmod +x runner/function_test_demo.py
    python3 runner/function_test_demo.py
    echo ""
    echo -e "${GREEN}✅ Function demo completed successfully!${NC}"
else
    echo -e "${RED}❌ Python3 not found for function demo${NC}"
    exit 1
fi
echo ""

# Step 5: Create symlink for dashboard to access output files
echo -e "${BLUE}🔗 Step 5: Setting up dashboard access...${NC}"
if [ ! -L "dashboard/output" ]; then
    ln -s ../output dashboard/output
    echo -e "${GREEN}✅ Created symlink: dashboard/output -> ../output${NC}"
else
    echo -e "${GREEN}✅ Symlink already exists${NC}"
fi
echo ""

# Step 7: Display results
echo "================================================================================"
echo -e "${GREEN}🎉 Demo Complete!${NC}"
echo "================================================================================"
echo ""
echo "📁 Generated Files:"
echo "   • output/mockdb-report.json"
echo "   • output/fastdb-report.json"
echo "   • output/clouddb-report.json"
echo "   • output/duckdb-report.json"
echo "   • output/postgresql-report.json"
echo "   • output/MockDB_function_tests.json"
echo "   • output/FastDB_function_tests.json"
echo "   • output/CloudDB_function_tests.json"
echo "   • output/DuckDB_function_tests.json"
echo "   • output/PostgreSQL_function_tests.json"
echo "   • output/leaderboard.json"
echo "   • output/function_tests_summary.json"
echo "   • dashboard/data/leaderboard.json"
echo "   • dashboard/data/summary.json"
echo ""
echo "🌐 View Dashboard:"
echo "   Option 1: Open file directly"
echo "      open dashboard/index.html"
echo ""
echo "   Option 2: Use local web server"
echo "      cd dashboard && python3 -m http.server 8000"
echo "      Then open: http://localhost:8000"
echo ""
echo "📊 View Reports:"
echo "   cat output/leaderboard.json | jq"
echo "   cat output/function_tests_summary.json | jq"
echo "   cat dashboard/data/summary.json | jq"
echo "   cat output/mockdb-report.json | jq"
echo ""
echo "🔄 Run Again:"
echo "   ./runner/run-demo.sh"
echo ""
echo "================================================================================"

# Made with Bob
