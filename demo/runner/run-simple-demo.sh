#!/bin/bash

# Substrait Compliance Framework - Simplified Demo Runner
# This script runs a standalone demo without requiring full SDK compilation

set -e  # Exit on error

echo "================================================================================"
echo "Substrait Compliance Framework - Simplified Demo"
echo "================================================================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if we're in the demo directory
if [ ! -f "README.md" ] || [ ! -d "runner" ]; then
    echo -e "${RED}❌ Error: Please run this script from the demo directory${NC}"
    echo "   cd demo && ./runner/run-simple-demo.sh"
    exit 1
fi

# Step 1: Create output directories
echo -e "${BLUE}📁 Step 1: Creating output directories...${NC}"
mkdir -p output
mkdir -p dashboard/data
echo -e "${GREEN}✅ Directories created${NC}"
echo ""

# Step 2: Compile simple demo runner
echo -e "${BLUE}🔧 Step 2: Compiling demo runner...${NC}"
mkdir -p build

javac -d build runner/SimpleDemoRunner.java

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Demo runner compiled successfully${NC}"
else
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi
echo ""

# Step 3: Run the demo
echo -e "${BLUE}🚀 Step 3: Running compliance demo...${NC}"
echo ""

java -cp build SimpleDemoRunner

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Demo completed successfully!${NC}"
else
    echo -e "${RED}❌ Demo execution failed${NC}"
    exit 1
fi

# Step 4: Create symlink for dashboard to access output files
echo ""
echo -e "${BLUE}🔗 Step 4: Setting up dashboard access...${NC}"
if [ ! -L "dashboard/output" ]; then
    ln -s ../output dashboard/output
    echo -e "${GREEN}✅ Created symlink: dashboard/output -> ../output${NC}"
else
    echo -e "${GREEN}✅ Symlink already exists${NC}"
fi
echo ""

# Step 4: Display results
echo "================================================================================"
echo -e "${GREEN}🎉 Demo Complete!${NC}"
echo "================================================================================"
echo ""
echo "📁 Generated Files:"
echo "   • output/mockdb-report.json"
echo "   • output/fastdb-report.json"
echo "   • output/clouddb-report.json"
echo "   • output/leaderboard.json"
echo "   • dashboard/data/leaderboard.json"
echo ""
echo "🌐 View Dashboard:"
echo "   ⚠️  IMPORTANT: Must use a web server (browsers block local file access)"
echo ""
echo "   Start web server:"
echo "      cd dashboard && python3 -m http.server 8080"
echo ""
echo "   Then open in browser:"
echo "      http://localhost:8080"
echo ""
echo "   If port 8080 is in use, try: 8081, 9000, or 3000"
echo ""
echo "📊 View Reports:"
echo "   cat output/leaderboard.json"
echo "   cat output/mockdb-report.json"
echo ""
echo "🔄 Run Again:"
echo "   ./runner/run-simple-demo.sh"
echo ""
echo "================================================================================"

# Made with Bob
