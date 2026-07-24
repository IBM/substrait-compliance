#!/bin/bash

# Substrait Compliance Framework - Demo Runner
# This script validates the actual Java framework and runs the framework-backed demo

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

# Check Java version — 17+ required by the SDK Gradle build and demo runner
JAVA_MAJOR=$(java -version 2>&1 | awk -F[\"._] '/version/ { print ($2 == "1" ? $3 : $2) }')
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo -e "${RED}❌ Java 17 or higher is required.${NC}"
    echo "   Detected: $(java -version 2>&1 | head -1)"
    echo ""
    echo "   Install options:"
    echo "     macOS (Homebrew):  brew install openjdk@17"
    echo "                        export JAVA_HOME=\$(brew --prefix openjdk@17)"
    echo "     macOS (SDKMAN):    sdk install java 17.0.11-tem && sdk use java 17.0.11-tem"
    echo "     Ubuntu/Debian:     sudo apt-get install openjdk-17-jdk"
    echo "     RHEL/Fedora:       sudo dnf install java-17-openjdk"
    echo ""
    echo "   After installing, update your PATH and re-run:"
    echo "     export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    echo "     java -version   # should show 17 or higher"
    exit 1
fi

# Step 1: Create output directories
echo -e "${BLUE}📁 Step 1: Creating output directories...${NC}"
mkdir -p output
mkdir -p dashboard/data
echo -e "${GREEN}✅ Directories created${NC}"
echo ""

# Step 2: Build and validate the Java SDK (tests + fat jar)
echo -e "${BLUE}🔧 Step 2: Building and validating Java SDK...${NC}"
(
    cd ../sdk/java
    ./gradlew test shadowJar
)

echo -e "${GREEN}✅ Java SDK validated successfully${NC}"
echo ""

# Step 3: Build framework-backed demo classes
# The fat jar (substrait-compliance-*-all.jar) produced by shadowJar bundles
# every runtime dependency, so the demo classpath is just two entries:
# the fat jar + the compiled demo classes.
echo -e "${BLUE}🏗️  Step 3: Compiling framework-backed demo classes...${NC}"
mkdir -p build

FAT_JAR=$(find ../sdk/java/build/libs -maxdepth 1 -name "*-all.jar" | sort | tail -1)
if [ -z "$FAT_JAR" ]; then
    echo -e "${RED}❌ Fat jar not found in ../sdk/java/build/libs${NC}"
    echo "   Expected a file matching *-all.jar — run './gradlew shadowJar' in sdk/java."
    exit 1
fi
echo "   Using SDK fat jar: ${FAT_JAR}"

DEMO_CLASSPATH="${FAT_JAR}"

javac -proc:none -cp "${DEMO_CLASSPATH}" -d build \
    engines/DemoEngineBase.java \
    engines/MockDBEngine.java \
    engines/FastDBEngine.java \
    engines/CloudDBEngine.java \
    engines/DuckDBEngine.java \
    engines/PostgreSQLEngine.java \
    runner/DemoRunner.java

echo -e "${GREEN}✅ Demo classes compiled successfully${NC}"
echo ""

# Step 4: Run the framework-backed demo
echo -e "${BLUE}🚀 Step 4: Running framework-backed compliance demo...${NC}"
echo ""

java -cp "build:${DEMO_CLASSPATH}" io.substrait.demo.runner.DemoRunner

echo ""
echo -e "${GREEN}✅ Demo completed successfully!${NC}"

# Step 5: Create symlink for dashboard to access output files
echo ""
echo -e "${BLUE}🔗 Step 5: Setting up dashboard access...${NC}"
if [ ! -L "dashboard/output" ]; then
    ln -s ../output dashboard/output
    echo -e "${GREEN}✅ Created symlink: dashboard/output -> ../output${NC}"
else
    echo -e "${GREEN}✅ Symlink already exists${NC}"
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
echo "   • output/duckdb-report.json"
echo "   • output/postgresql-report.json"
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
echo "   (validates the Java SDK and runs the framework-backed demo)"
echo ""
echo "================================================================================"

# Made with Bob
