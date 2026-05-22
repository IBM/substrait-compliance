#!/bin/bash

# Compile DuckDB example with proper classpath
# This script compiles the DuckDB compliance example against the SDK

set -e

echo "Building SDK..."
cd ../../sdk/java
./gradlew build -q
cd ../../examples/duckdb-java

echo "Compiling DuckDB example..."

# Find required JARs from Gradle cache
SUBSTRAIT_CORE=$(find ~/.gradle/caches/modules-2/files-2.1/io.substrait/core/0.80.0 -name "core-0.80.0.jar" 2>/dev/null | head -1)
PROTOBUF=$(find ~/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-java -name "protobuf-java-3.*.jar" 2>/dev/null | head -1)

if [ -z "$SUBSTRAIT_CORE" ] || [ -z "$PROTOBUF" ]; then
    echo "Error: Required dependencies not found in Gradle cache."
    echo "Please run 'cd ../../sdk/java && ./gradlew build' first to download dependencies."
    exit 1
fi

# Compile with full classpath
javac -cp "../../sdk/java/build/libs/*:$SUBSTRAIT_CORE:$PROTOBUF:." \
    src/main/java/io/substrait/example/*.java

echo "✓ Compilation successful!"
echo ""
echo "Compiled classes are in: src/main/java/io/substrait/example/"
echo ""
echo "To run the example, you'll need to add DuckDB JDBC driver to the classpath."

# Made with Bob
