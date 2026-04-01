/*
 * Copyright 2026 Substrait Validation Framework Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.substrait.test.tpch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares SQL query results for equality with tolerance handling.
 *
 * <p>This class provides sophisticated result comparison for validating
 * Substrait round-trip conversion correctness. It handles:
 * <ul>
 *   <li>Row count comparison</li>
 *   <li>Schema compatibility checking</li>
 *   <li>Value-by-value comparison with type awareness</li>
 *   <li>Floating-point tolerance</li>
 *   <li>NULL handling</li>
 *   <li>Unordered result sets (set comparison)</li>
 * </ul>
 *
 * <h2>Comparison Modes</h2>
 * <ul>
 *   <li><strong>EXACT</strong>: Rows must match exactly in order and values</li>
 *   <li><strong>UNORDERED</strong>: Row order ignored, compares as sets</li>
 *   <li><strong>SCHEMA_ONLY</strong>: Only validates schema compatibility</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create comparator with tolerance for floating-point
 * ResultComparator comparator = ResultComparator.builder()
 *     .withNumericTolerance(0.0001)
 *     .withMode(ComparisonMode.UNORDERED)  // For queries without ORDER BY
 *     .build();
 *
 * // Execute both queries
 * ResultSet expected = executeQuery(originalSql);
 * ResultSet actual = executeQuery(generatedSql);
 *
 * // Compare results
 * ComparisonResult result = comparator.compare(expected, actual);
 *
 * if (!result.matches()) {
 *     System.err.println("Mismatch found:");
 *     result.getDifferences().forEach(System.err::println);
 * }
 * }</pre>
 *
 * @see ComparisonMode
 * @see ComparisonResult
 */
public class ResultComparator {

  private static final Logger logger = LoggerFactory.getLogger(ResultComparator.class);

  /** Default numeric tolerance for floating-point comparisons */
  public static final double DEFAULT_NUMERIC_TOLERANCE = 0.0001;

  /** Tolerance for floating-point comparisons (default: 0.0001) */
  private final double numericTolerance;

  /** Comparison mode (EXACT, UNORDERED, SCHEMA_ONLY) */
  private final ComparisonMode mode;

  /** Whether to ignore column name case when comparing schemas */
  private final boolean ignoreColumnNameCase;

  /** Maximum number of differences to report (prevents huge logs) */
  private final int maxDifferencesToReport;

  /**
   * Comparison modes for different query types.
   */
  public enum ComparisonMode {
    /**
     * Exact comparison - rows must match in order and values.
     * Use for queries with ORDER BY clause.
     */
    EXACT,

    /**
     * Unordered comparison - rows compared as sets, order ignored.
     * Use for queries without ORDER BY or when order is non-deterministic.
     */
    UNORDERED,

    /**
     * Schema-only comparison - validates column count and types only.
     * Use for quick sanity checks without data validation.
     */
    SCHEMA_ONLY
  }

  /**
   * Private constructor - use {@link Builder}.
   */
  private ResultComparator(Builder builder) {
    this.numericTolerance = builder.numericTolerance;
    this.mode = builder.mode;
    this.ignoreColumnNameCase = builder.ignoreColumnNameCase;
    this.maxDifferencesToReport = builder.maxDifferencesToReport;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PUBLIC API - Comparison Methods
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Compares two ResultSets for equality.
   *
   * <p>This is the main comparison method. It:
   * <ol>
   *   <li>Materializes both ResultSets into memory</li>
   *   <li>Compares schemas (column count, types, names)</li>
   *   <li>Compares row counts</li>
   *   <li>Compares data values (mode-dependent)</li>
   * </ol>
   *
   * <p><strong>Note:</strong> This method materializes the entire ResultSet
   * into memory. For very large result sets (>100K rows), consider comparing
   * incrementally or using a streaming approach.
   *
   * @param expected expected ResultSet (from original query)
   * @param actual actual ResultSet (from generated query)
   * @return comparison result with match status and differences
   * @throws SQLException if ResultSet access fails
   */
  public ComparisonResult compare(ResultSet expected, ResultSet actual)
      throws SQLException {

    logger.debug("Comparing ResultSets in {} mode", mode);
    long startTime = System.currentTimeMillis();

    ComparisonResult.Builder resultBuilder = ComparisonResult.builder();

    try {
      // Step 1: Compare schemas
      ResultSetMetaData expectedMeta = expected.getMetaData();
      ResultSetMetaData actualMeta = actual.getMetaData();

      if (!compareSchemas(expectedMeta, actualMeta, resultBuilder)) {
        // Schema mismatch - no point comparing data
        return resultBuilder.build();
      }

      // For SCHEMA_ONLY mode, we're done
      if (mode == ComparisonMode.SCHEMA_ONLY) {
        resultBuilder.setMatches(true);
        return resultBuilder.build();
      }

      // Step 2: Materialize ResultSets to Lists
      List<Row> expectedRows = materializeResultSet(expected, expectedMeta);
      List<Row> actualRows = materializeResultSet(actual, actualMeta);

      logger.debug("Materialized {} expected rows, {} actual rows",
          expectedRows.size(), actualRows.size());

      // Step 3: Compare row counts
      if (expectedRows.size() != actualRows.size()) {
        resultBuilder.addDifference(String.format(
            "Row count mismatch: expected %d rows, got %d rows",
            expectedRows.size(), actualRows.size()
        ));
        return resultBuilder.build();
      }

      // Step 4: Compare data (mode-dependent)
      switch (mode) {
        case EXACT:
          compareRowsExact(expectedRows, actualRows, resultBuilder);
          break;
        case UNORDERED:
          compareRowsUnordered(expectedRows, actualRows, resultBuilder);
          break;
        default:
          throw new IllegalStateException("Unexpected mode: " + mode);
      }

      long duration = System.currentTimeMillis() - startTime;
      logger.debug("Comparison completed in {}ms", duration);

      return resultBuilder.build();

    } catch (SQLException e) {
      logger.error("Error during result comparison", e);
      throw e;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PRIVATE HELPER METHODS - Schema Comparison
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Compares ResultSet schemas (column count, types, names).
   *
   * @return true if schemas are compatible
   */
  private boolean compareSchemas(
      ResultSetMetaData expected,
      ResultSetMetaData actual,
      ComparisonResult.Builder result) throws SQLException {

    int expectedColCount = expected.getColumnCount();
    int actualColCount = actual.getColumnCount();

    // Check column count
    if (expectedColCount != actualColCount) {
      result.addDifference(String.format(
          "Column count mismatch: expected %d columns, got %d columns",
          expectedColCount, actualColCount
      ));
      return false;
    }

    // Check each column
    for (int i = 1; i <= expectedColCount; i++) {
      String expectedName = expected.getColumnName(i);
      String actualName = actual.getColumnName(i);
      int expectedType = expected.getColumnType(i);
      int actualType = actual.getColumnType(i);

      // Compare column names
      boolean namesMatch = ignoreColumnNameCase
          ? expectedName.equalsIgnoreCase(actualName)
          : expectedName.equals(actualName);

      if (!namesMatch) {
        result.addDifference(String.format(
            "Column %d name mismatch: expected '%s', got '%s'",
            i, expectedName, actualName
        ));
        // NOTE: We continue checking other columns even after name mismatch
      }

      // Compare column types
      if (expectedType != actualType) {
        result.addDifference(String.format(
            "Column %d (%s) type mismatch: expected %s, got %s",
            i, expectedName,
            expected.getColumnTypeName(i),
            actual.getColumnTypeName(i)
        ));
      }
    }

    return result.differences.isEmpty();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PRIVATE HELPER METHODS - Data Comparison
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Compares rows in exact order.
   *
   * <p>Used for queries with ORDER BY clause where row order matters.
   */
  private void compareRowsExact(
      List<Row> expected,
      List<Row> actual,
      ComparisonResult.Builder result) {

    int differencesFound = 0;

    for (int i = 0; i < expected.size(); i++) {
      Row expectedRow = expected.get(i);
      Row actualRow = actual.get(i);

      if (!rowsEqual(expectedRow, actualRow)) {
        if (differencesFound < maxDifferencesToReport) {
          result.addDifference(String.format(
              "Row %d mismatch:%n  Expected: %s%n  Actual:   %s",
              i + 1, expectedRow, actualRow
          ));
        }
        differencesFound++;
      }
    }

    if (differencesFound > maxDifferencesToReport) {
      result.addDifference(String.format(
          "... and %d more differences (truncated)",
          differencesFound - maxDifferencesToReport
      ));
    }

    result.setMatches(differencesFound == 0);
  }

  /**
   * Compares rows as unordered sets.
   *
   * <p>Used for queries without ORDER BY where row order doesn't matter.
   * This is more expensive (O(n²) worst case) but necessary for some queries.
   *
   * <p><strong>Performance Note:</strong> For very large result sets (>100K rows),
   * this method creates two hash maps in memory. Consider using streaming comparison
   * or pagination for memory-constrained environments.
   */
  private void compareRowsUnordered(
      List<Row> expected,
      List<Row> actual,
      ComparisonResult.Builder result) {

    // Convert to multisets for comparison (handles duplicate rows)
    // NOTE: This creates two full hash maps - memory usage is O(n)
    Map<Row, Integer> expectedCounts = countRows(expected);
    Map<Row, Integer> actualCounts = countRows(actual);

    // Find rows in expected but not in actual
    Set<Row> missingRows = new HashSet<>();
    for (Map.Entry<Row, Integer> entry : expectedCounts.entrySet()) {
      Row row = entry.getKey();
      int expectedCount = entry.getValue();
      int actualCount = actualCounts.getOrDefault(row, 0);

      if (actualCount < expectedCount) {
        missingRows.add(row);
      }
    }

    // Find rows in actual but not in expected
    Set<Row> extraRows = new HashSet<>();
    for (Map.Entry<Row, Integer> entry : actualCounts.entrySet()) {
      Row row = entry.getKey();
      int actualCount = entry.getValue();
      int expectedCount = expectedCounts.getOrDefault(row, 0);

      if (actualCount > expectedCount) {
        extraRows.add(row);
      }
    }

    // Report differences
    int differencesReported = 0;

    for (Row row : missingRows) {
      if (differencesReported >= maxDifferencesToReport) break;
      int expectedCount = expectedCounts.get(row);
      int actualCount = actualCounts.getOrDefault(row, 0);
      result.addDifference(String.format(
          "Missing row (expected %d times, found %d): %s",
          expectedCount, actualCount, row
      ));
      differencesReported++;
    }

    for (Row row : extraRows) {
      if (differencesReported >= maxDifferencesToReport) break;
      int expectedCount = expectedCounts.getOrDefault(row, 0);
      int actualCount = actualCounts.get(row);
      result.addDifference(String.format(
          "Extra row (expected %d times, found %d): %s",
          expectedCount, actualCount, row
      ));
      differencesReported++;
    }

    boolean matches = missingRows.isEmpty() && extraRows.isEmpty();
    result.setMatches(matches);
  }

  /**
   * Counts occurrences of each unique row.
   *
   * @return map of row to count
   */
  private Map<Row, Integer> countRows(List<Row> rows) {
    Map<Row, Integer> counts = new HashMap<>();
    for (Row row : rows) {
      counts.merge(row, 1, Integer::sum);
    }
    return counts;
  }

  /**
   * Compares two rows for equality.
   *
   * @return true if all values in the rows are equal
   */
  private boolean rowsEqual(Row row1, Row row2) {
    if (row1.values.size() != row2.values.size()) {
      return false;
    }

    for (int i = 0; i < row1.values.size(); i++) {
      if (!valuesEqual(row1.values.get(i), row2.values.get(i))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Compares two values for equality with type-specific handling.
   *
   * <p>Handles:
   * <ul>
   *   <li>NULL comparison</li>
   *   <li>Numeric types with tolerance</li>
   *   <li>String comparison</li>
   *   <li>Date/timestamp comparison</li>
   *   <li>Boolean comparison</li>
   * </ul>
   *
   * @return true if values are considered equal
   */
  private boolean valuesEqual(Object expected, Object actual) {
    // Handle NULL values
    if (expected == null && actual == null) {
      return true;
    }
    if (expected == null || actual == null) {
      return false;
    }

    // Handle numeric types with tolerance
    if (expected instanceof Number && actual instanceof Number) {
      return numbersEqual((Number) expected, (Number) actual);
    }

    // Handle BigDecimal specially (common in SQL results)
    if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
      BigDecimal exp = (BigDecimal) expected;
      BigDecimal act = (BigDecimal) actual;
      // Use compareTo for BigDecimal (ignores scale)
      return exp.compareTo(act) == 0 ||
             Math.abs(exp.doubleValue() - act.doubleValue()) < numericTolerance;
    }

    // Handle dates and timestamps
    if (expected instanceof Date && actual instanceof Date) {
      return expected.equals(actual);
    }
    if (expected instanceof Timestamp && actual instanceof Timestamp) {
      return expected.equals(actual);
    }

    // Handle strings (trim whitespace for CHAR types)
    if (expected instanceof String && actual instanceof String) {
      return expected.toString().trim().equals(actual.toString().trim());
    }

    // Default: use equals()
    return expected.equals(actual);
  }

  /**
   * Compares two numbers with tolerance for floating-point types.
   *
   * @return true if numbers are equal within tolerance
   */
  private boolean numbersEqual(Number expected, Number actual) {
    // For integer types, use exact comparison
    if (expected instanceof Integer || expected instanceof Long) {
      return expected.longValue() == actual.longValue();
    }

    // For floating-point types, use tolerance
    double diff = Math.abs(expected.doubleValue() - actual.doubleValue());
    return diff < numericTolerance;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // PRIVATE HELPER METHODS - ResultSet Materialization
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Materializes a ResultSet into a list of Row objects.
   *
   * <p>This loads the entire ResultSet into memory. For very large results,
   * this could cause memory issues.
   *
   * @param rs ResultSet to materialize
   * @param meta ResultSet metadata
   * @return list of rows
   * @throws SQLException if ResultSet access fails
   */
  private List<Row> materializeResultSet(ResultSet rs, ResultSetMetaData meta)
      throws SQLException {

    List<Row> rows = new ArrayList<>();
    int columnCount = meta.getColumnCount();

    while (rs.next()) {
      List<Object> values = new ArrayList<>(columnCount);

      for (int i = 1; i <= columnCount; i++) {
        Object value = rs.getObject(i);
        values.add(value);
      }

      rows.add(new Row(values));
    }

    return rows;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // BUILDER PATTERN
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Creates a new builder with default settings.
   *
   * @return new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for creating configured ResultComparator instances.
   */
  public static class Builder {
    private double numericTolerance = DEFAULT_NUMERIC_TOLERANCE;
    private ComparisonMode mode = ComparisonMode.EXACT;
    private boolean ignoreColumnNameCase = false;
    private int maxDifferencesToReport = 10;

    /**
     * Sets the numeric tolerance for floating-point comparisons.
     *
     * <p>Two numbers are considered equal if their absolute difference is
     * less than this tolerance.
     *
     * <p>Default: 0.0001
     *
     * @param tolerance tolerance value (must be positive)
     * @return this builder
     */
    public Builder withNumericTolerance(double tolerance) {
      if (tolerance < 0) {
        throw new IllegalArgumentException("Tolerance must be non-negative");
      }
      this.numericTolerance = tolerance;
      return this;
    }

    /**
     * Sets the comparison mode.
     *
     * @param mode comparison mode
     * @return this builder
     */
    public Builder withMode(ComparisonMode mode) {
      this.mode = Objects.requireNonNull(mode, "Mode cannot be null");
      return this;
    }

    /**
     * Sets whether to ignore column name case.
     *
     * <p>When true, column names are compared case-insensitively.
     *
     * @param ignore true to ignore case
     * @return this builder
     */
    public Builder withIgnoreColumnNameCase(boolean ignore) {
      this.ignoreColumnNameCase = ignore;
      return this;
    }

    /**
     * Sets the maximum number of differences to report.
     *
     * <p>Prevents huge log files when there are many mismatches.
     *
     * @param max maximum differences (must be positive)
     * @return this builder
     */
    public Builder withMaxDifferencesToReport(int max) {
      if (max <= 0) {
        throw new IllegalArgumentException("Max differences must be positive");
      }
      this.maxDifferencesToReport = max;
      return this;
    }

    /**
     * Builds the ResultComparator.
     *
     * @return configured ResultComparator instance
     */
    public ResultComparator build() {
      return new ResultComparator(this);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // INNER CLASSES - Data Models
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Represents a single row in a ResultSet.
   *
   * <p>Immutable value class with proper equals/hashCode for use in Sets/Maps.
   */
  private static class Row {
    private final List<Object> values;

    Row(List<Object> values) {
      // Use ArrayList instead of List.copyOf() to support null values
      this.values = new java.util.ArrayList<>(values);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Row)) return false;
      Row row = (Row) o;
      return values.equals(row.values);
    }

    @Override
    public int hashCode() {
      return values.hashCode();
    }

    @Override
    public String toString() {
      return values.toString();
    }
  }

  /**
   * Result of a comparison operation.
   *
   * <p>Contains match status and list of differences found.
   */
  public static class ComparisonResult {
    private final boolean matches;
    private final List<String> differences;

    private ComparisonResult(boolean matches, List<String> differences) {
      this.matches = matches;
      this.differences = List.copyOf(differences);
    }

    /**
     * Returns whether the ResultSets match.
     *
     * @return true if results are equivalent
     */
    public boolean matches() {
      return matches;
    }

    /**
     * Returns list of differences found.
     *
     * @return immutable list of difference descriptions
     */
    public List<String> getDifferences() {
      return differences;
    }

    /**
     * Returns whether any differences were found.
     *
     * @return true if differences exist
     */
    public boolean hasDifferences() {
      return !differences.isEmpty();
    }

    static Builder builder() {
      return new Builder();
    }

    static class Builder {
      private boolean matches = true;
      private final List<String> differences = new ArrayList<>();

      Builder setMatches(boolean matches) {
        this.matches = matches;
        return this;
      }

      Builder addDifference(String difference) {
        this.differences.add(difference);
        this.matches = false;
        return this;
      }

      ComparisonResult build() {
        return new ComparisonResult(matches, differences);
      }
    }
  }
}
