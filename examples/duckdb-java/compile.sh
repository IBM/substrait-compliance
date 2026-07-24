#!/bin/bash
# Compile the DuckDB Java example against the SDK fat jar.
#
# Run from the repo root or from examples/duckdb-java/ — the script locates
# the fat jar relative to its own directory so it works from either location.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$SCRIPT_DIR/../../sdk/java"
FAT_JAR="$SDK_DIR/build/libs/substrait-compliance-0.1.0-all.jar"

# Build the fat jar if it doesn't exist
if [ ! -f "$FAT_JAR" ]; then
    echo "🔧 Building SDK fat jar..."
    (cd "$SDK_DIR" && ./gradlew shadowJar -q)
fi

echo "🔨 Compiling DuckDB Java example..."
cd "$SCRIPT_DIR"
mkdir -p build

javac -proc:none \
    -cp "$FAT_JAR" \
    -d build \
    src/main/java/io/substrait/example/DuckDBComplianceEngine.java \
    src/main/java/io/substrait/example/DuckDBComplianceExample.java

echo "✅ Compilation successful."
echo ""
echo "NOTE: executeSubstraitPlan() is a stub — see Option A in the source"
echo "      for how to wire in DuckDB's native Substrait JDBC support."
echo ""
echo "To run:"
echo "  java -cp \"build:$FAT_JAR\" io.substrait.example.DuckDBComplianceExample"
