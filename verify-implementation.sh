#!/bin/bash
echo "=========================================="
echo "Final Implementation Verification"
echo "=========================================="
echo ""

echo "1. Checking Java SDK structure..."
find sdk/java/src/main/java/io/substrait/compliance/loader -name "*.java" | sort
echo ""

echo "2. Checking test structure..."
find sdk/java/src/test/java/io/substrait/compliance/loader -name "*.java" | sort
echo ""

echo "3. Running all tests..."
cd sdk/java
./gradlew test --console=plain 2>&1 | grep -E "(BUILD|test|passed|failed)"
echo ""

echo "4. Checking TPC-H test suite..."
cd ../../test-suites/tpch
echo "Metadata file:"
ls -lh metadata.yaml
echo ""
echo "Data files:"
ls -lh data/ | tail -9
echo ""
echo "Plan files (sample):"
ls -1 plans/*.bin | head -5
echo "..."
ls -1 plans/*.json | head -5
echo ""

echo "5. Counting resources..."
echo "Total data rows:"
wc -l data/*.csv | tail -1
echo ""
echo "Total plan files:"
ls -1 plans/* | wc -l
echo ""

echo "6. Verifying metadata completeness..."
grep -c "^  - id:" metadata.yaml
echo "queries defined in metadata.yaml"
echo ""

echo "=========================================="
echo "✅ Verification Complete"
echo "=========================================="
