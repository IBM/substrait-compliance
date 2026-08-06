#!/bin/bash
# Compile the DuckDB Java example against the SDK fat jar and DuckDB JDBC driver.
#
# Run from the repo root or from examples/duckdb-java/ — the script locates
# all jars relative to its own directory so it works from either location.
#
# Usage:
#   ./compile.sh            — builds SDK fat jar (if needed) then compiles
#   ./compile.sh --no-sdk   — skips the SDK build step (faster on CI)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="$SCRIPT_DIR/../../sdk/java"
FAT_JAR="$SDK_DIR/build/libs/substrait-compliance-0.1.1-all.jar"
DUCKDB_VERSION="1.3.1.0"
DUCKDB_JAR="$SCRIPT_DIR/lib/duckdb_jdbc-${DUCKDB_VERSION}.jar"

# ── SDK fat jar ──────────────────────────────────────────────────────────────
if [[ "$1" != "--no-sdk" ]]; then
    if [ ! -f "$FAT_JAR" ]; then
        echo "🔧 Building SDK fat jar..."
        (cd "$SDK_DIR" && ./gradlew shadowJar -q)
    fi
fi

# ── DuckDB JDBC driver ───────────────────────────────────────────────────────
if [ ! -f "$DUCKDB_JAR" ]; then
    echo "⬇️  DuckDB JDBC jar not found — running download-deps.sh..."
    "$SCRIPT_DIR/download-deps.sh"
fi

# ── Compile ──────────────────────────────────────────────────────────────────
echo "🔨 Compiling DuckDB Java example..."
cd "$SCRIPT_DIR"
mkdir -p build

javac -proc:none \
    -cp "$FAT_JAR:$DUCKDB_JAR" \
    -d build \
    src/main/java/io/substrait/example/DuckDBComplianceEngine.java \
    src/main/java/io/substrait/example/DuckDBComplianceExample.java

echo "✅ Compilation successful."
echo ""
echo "To run:"
echo "  java -cp \"build:$FAT_JAR:$DUCKDB_JAR\" io.substrait.example.DuckDBComplianceExample"
