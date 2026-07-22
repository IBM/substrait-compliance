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

# Step 1: Create output directories
echo -e "${BLUE}📁 Step 1: Creating output directories...${NC}"
mkdir -p output
mkdir -p dashboard/data
echo -e "${GREEN}✅ Directories created${NC}"
echo ""

# Step 2: Build and validate the Java SDK
echo -e "${BLUE}🔧 Step 2: Building and validating Java SDK...${NC}"
(
    cd ../sdk/java
    ./gradlew test jar
)

echo -e "${GREEN}✅ Java SDK validated successfully${NC}"
echo ""

# Step 3: Build framework-backed demo classes
echo -e "${BLUE}🏗️  Step 3: Compiling framework-backed demo classes...${NC}"
mkdir -p build

SDK_JARS=$(find ../sdk/java/build/libs -maxdepth 1 -name "*.jar" | tr '\n' ':')
SDK_CLASSES="../sdk/java/build/classes/java/main"
SUBSTRAIT_CORE_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/io.substrait/core/0.80.0 -name "core-0.80.0.jar" | head -1)
PROTOBUF_JAVA_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-java -name "protobuf-java-*.jar" ! -name "*-sources.jar" | sort | tail -1)
JACKSON_DATABIND_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-databind -name "jackson-databind-*.jar" ! -name "*-sources.jar" | sort | tail -1)
JACKSON_CORE_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-core -name "jackson-core-*.jar" ! -name "*-sources.jar" | sort | tail -1)
JACKSON_ANNOTATIONS_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-annotations -name "jackson-annotations-*.jar" ! -name "*-sources.jar" | sort | tail -1)
JACKSON_YAML_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.dataformat/jackson-dataformat-yaml -name "jackson-dataformat-yaml-*.jar" ! -name "*-sources.jar" | sort | tail -1)
SNAKEYAML_JAR=$(find /Users/rsinha/.gradle/caches/modules-2/files-2.1/org.yaml/snakeyaml -name "snakeyaml-*.jar" ! -name "*-sources.jar" | sort | tail -1)
if [ -z "$SDK_JARS" ]; then
    echo -e "${RED}❌ No Java SDK jars found. Expected build output in ../sdk/java/build/libs${NC}"
    exit 1
fi

DEMO_CLASSPATH="${SDK_CLASSES}:${SDK_JARS}:${SUBSTRAIT_CORE_JAR}:${PROTOBUF_JAVA_JAR}:${JACKSON_DATABIND_JAR}:${JACKSON_CORE_JAR}:${JACKSON_ANNOTATIONS_JAR}:${JACKSON_YAML_JAR}:${SNAKEYAML_JAR}"
if [ ! -d "${SDK_CLASSES}" ]; then
    echo -e "${RED}❌ Java SDK classes directory not found: ${SDK_CLASSES}${NC}"
    exit 1
fi

javac -proc:none -cp "${DEMO_CLASSPATH}" -d build \
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
