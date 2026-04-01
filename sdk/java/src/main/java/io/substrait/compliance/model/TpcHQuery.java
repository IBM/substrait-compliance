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

import java.util.List;
import java.util.Objects;

/**
 * Represents a single TPC-H benchmark query with metadata.
 *
 * <p>Each TPC-H query includes:
 * <ul>
 *   <li>Query number (1-22)</li>
 *   <li>SQL text</li>
 *   <li>Description of what it tests</li>
 *   <li>Expected complexity level</li>
 *   <li>Tables referenced</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe.
 *
 * @see TpcHQueries
 */
public final class TpcHQuery {

  /** Query number from TPC-H specification (1-22) */
  private final int queryNumber;

  /** SQL query text */
  private final String sql;

  /** Human-readable description of what this query tests */
  private final String description;

  /** Complexity level for categorizing tests */
  private final Complexity complexity;

  /** List of table names referenced in this query */
  private final List<String> referencedTables;

  /** Estimated execution time category (for test ordering) */
  private final ExecutionTime estimatedTime;

  /**
   * Complexity categories for TPC-H queries.
   */
  public enum Complexity {
    /** Simple: Single table, basic filters, simple aggregations */
    SIMPLE,
    /** Medium: 2-3 table joins, GROUP BY, subqueries */
    MEDIUM,
    /** Complex: 4+ joins, nested subqueries, complex expressions */
    COMPLEX,
    /** Very Complex: Multiple nested subqueries, correlated subqueries */
    VERY_COMPLEX
  }

  /**
   * Execution time categories (based on TPC-H SF 0.01).
   */
  public enum ExecutionTime {
    /** Very fast: < 100ms */
    VERY_FAST,
    /** Fast: 100-500ms */
    FAST,
    /** Medium: 500ms-2s */
    MEDIUM,
    /** Slow: 2s+ */
    SLOW
  }

  /**
   * Creates a new TPC-H query instance.
   *
   * @param queryNumber TPC-H query number (1-22)
   * @param sql SQL query text; must not be null or empty
   * @param description human-readable description
   * @param complexity query complexity level
   * @param referencedTables list of table names used in query
   * @param estimatedTime estimated execution time category
   * @throws IllegalArgumentException if queryNumber is out of range or sql is invalid
   */
  public TpcHQuery(
      int queryNumber,
      String sql,
      String description,
      Complexity complexity,
      List<String> referencedTables,
      ExecutionTime estimatedTime) {

    if (queryNumber < 1 || queryNumber > 22) {
      throw new IllegalArgumentException(
          "Query number must be between 1 and 22, got: " + queryNumber);
    }
    Objects.requireNonNull(sql, "SQL cannot be null");
    if (sql.trim().isEmpty()) {
      throw new IllegalArgumentException("SQL cannot be empty");
    }

    this.queryNumber = queryNumber;
    this.sql = sql;
    this.description = description;
    this.complexity = complexity;
    this.referencedTables = List.copyOf(referencedTables); // Defensive copy
    this.estimatedTime = estimatedTime;
  }

  /**
   * Returns the TPC-H query number.
   *
   * @return query number (1-22)
   */
  public int getQueryNumber() {
    return queryNumber;
  }

  /**
   * Returns the SQL query text.
   *
   * @return SQL string
   */
  public String getSql() {
    return sql;
  }

  /**
   * Returns the query description.
   *
   * @return human-readable description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the complexity level.
   *
   * @return complexity category
   */
  public Complexity getComplexity() {
    return complexity;
  }

  /**
   * Returns the list of tables referenced in this query.
   *
   * @return immutable list of table names
   */
  public List<String> getReferencedTables() {
    return referencedTables;
  }

  /**
   * Returns the estimated execution time category.
   *
   * @return execution time estimate
   */
  public ExecutionTime getEstimatedTime() {
    return estimatedTime;
  }

  /**
   * Returns a display name for this query (e.g., "TPC-H Q1").
   *
   * @return formatted query name
   */
  public String getDisplayName() {
    return String.format("TPC-H Q%d", queryNumber);
  }

  /**
   * Returns a test name suitable for JUnit parameterized tests.
   *
   * @return test name like "Q01_PricingSummaryReport"
   */
  public String getTestName() {
    // Convert description to camelCase test name
    String simplifiedDesc = description
        .replaceAll("[^a-zA-Z0-9\\s]", "")  // Remove special chars
        .replaceAll("\\s+", "_");            // Replace spaces with underscore
    return String.format("Q%02d_%s", queryNumber, simplifiedDesc);
  }

  @Override
  public String toString() {
    return String.format("TPC-H Q%d: %s [%s, %s]",
        queryNumber, description, complexity, estimatedTime);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TpcHQuery that = (TpcHQuery) o;
    return queryNumber == that.queryNumber;
  }

  @Override
  public int hashCode() {
    return Objects.hash(queryNumber);
  }
}
