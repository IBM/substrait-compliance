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

# Step 3: Compile demo engines
echo -e "${BLUE}🔧 Step 3: Compiling demo engines...${NC}"
mkdir -p build/classes

# Find all JAR files in SDK
SDK_LIBS="../sdk/java/build/libs"
CLASSPATH="$SDK_LIBS/*"

# Compile engines
javac -cp "$CLASSPATH" \
    -d build/classes \
    engines/*.java \
    runner/DemoRunner.java

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Demo code compiled successfully${NC}"
else
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi
echo ""

# Step 4: Run the demo
echo -e "${BLUE}🚀 Step 4: Running compliance tests...${NC}"
echo ""

java -cp "build/classes:$SDK_LIBS/*" \
    io.substrait.demo.runner.DemoRunner

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Demo completed successfully!${NC}"
else
    echo -e "${RED}❌ Demo execution failed${NC}"
    exit 1
fi
echo ""

# Step 5: Generate Python leaderboard (optional)
echo -e "${BLUE}📊 Step 5: Generating enhanced leaderboard...${NC}"
if command -v python3 &> /dev/null; then
    python3 ../scripts/generate_leaderboard.py \
        --input output \
        --output output/leaderboard.md \
        --format both
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Leaderboard generated${NC}"
        # Copy to dashboard
        cp output/leaderboard.json dashboard/data/
    else
        echo -e "${YELLOW}⚠️  Leaderboard generation failed (non-critical)${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Python3 not found, skipping enhanced leaderboard${NC}"
fi
echo ""

# Step 6: Display results
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
echo "   Option 1: Open file directly"
echo "      open dashboard/index.html"
echo ""
echo "   Option 2: Use local web server"
echo "      cd dashboard && python3 -m http.server 8000"
echo "      Then open: http://localhost:8000"
echo ""
echo "📊 View Reports:"
echo "   cat output/leaderboard.json | jq"
echo "   cat output/mockdb-report.json | jq"
echo ""
echo "🔄 Run Again:"
echo "   ./runner/run-demo.sh"
echo ""
echo "================================================================================"

# Made with Bob
