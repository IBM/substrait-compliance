#!/bin/bash
# Download the DuckDB JDBC driver into lib/ so that compile.sh and run.sh
# can pick it up without requiring Maven or Gradle to be installed.
#
# Usage: ./download-deps.sh [version]
#   version  DuckDB JDBC version to download (default: 1.3.1.0)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DUCKDB_VERSION="${1:-1.3.1.0}"
LIB_DIR="$SCRIPT_DIR/lib"
JAR="$LIB_DIR/duckdb_jdbc-${DUCKDB_VERSION}.jar"

mkdir -p "$LIB_DIR"

if [ -f "$JAR" ]; then
    echo "✅ DuckDB JDBC $DUCKDB_VERSION already present: $JAR"
    exit 0
fi

URL="https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/${DUCKDB_VERSION}/duckdb_jdbc-${DUCKDB_VERSION}.jar"
echo "⬇️  Downloading DuckDB JDBC $DUCKDB_VERSION..."

if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$JAR"
elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$JAR"
else
    echo "❌ Neither curl nor wget found. Install one and re-run." >&2
    exit 1
fi

echo "✅ Saved to: $JAR"
