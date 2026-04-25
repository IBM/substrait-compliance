#!/bin/bash
# Enhance all function test categories with error fixes and new test cases
# This script processes all test categories in test-suites/functions/

set -e  # Exit on error

echo "============================================================"
echo "Substrait Test Enhancer - Batch Processing"
echo "============================================================"
echo "This will enhance all function test categories with:"
echo "  - Error corrections (--fix-errors)"
echo "  - New test cases for better coverage"
echo ""
echo "Original files: test-suites/functions/"
echo "Enhanced files: test-suites-enhanced/functions/"
echo "============================================================"
echo ""

# Define all categories
categories=(
  "aggregate"
  "arithmetic"
  "array"
  "boolean"
  "cast"
  "comparison"
  "conditional"
  "datetime"
  "geospatial"
  "json"
  "map"
  "math"
  "set"
  "string"
  "struct"
  "window"
)

# Track statistics
total_categories=${#categories[@]}
completed=0
failed=0
failed_categories=()

echo "Found $total_categories categories to process"
echo ""

# Process each category
for category in "${categories[@]}"; do
  echo "[$((completed + failed + 1))/$total_categories] Processing $category..."
  echo "-----------------------------------------------------------"
  
  if python scripts/test_enhancer.py "$category" --fix-errors; then
    echo "✓ Completed $category"
    ((completed++))
  else
    echo "✗ Failed to process $category"
    ((failed++))
    failed_categories+=("$category")
  fi
  
  echo ""
done

# Print summary
echo "============================================================"
echo "Enhancement Complete"
echo "============================================================"
echo "Total categories: $total_categories"
echo "Successfully processed: $completed"
echo "Failed: $failed"

if [ $failed -gt 0 ]; then
  echo ""
  echo "Failed categories:"
  for cat in "${failed_categories[@]}"; do
    echo "  - $cat"
  done
fi

echo ""
echo "Enhanced files saved to: test-suites-enhanced/functions/"
echo ""
echo "Next steps:"
echo "  1. Review enhanced files in test-suites-enhanced/functions/"
echo "  2. Clean files: python scripts/clean_test_file.py test-suites-enhanced/functions --output test-suites-final/functions --recursive"
echo "============================================================"

# Made with Bob
