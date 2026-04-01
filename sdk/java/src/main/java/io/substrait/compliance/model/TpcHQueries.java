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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Repository of all 22 TPC-H benchmark queries.
 *
 * <p>This class provides the complete set of TPC-H queries optimized for testing
 * Substrait round-trip conversion. Each query is defined with:
 * <ul>
 *   <li>Original TPC-H SQL (simplified for Substrait testing)</li>
 *   <li>Metadata (complexity, referenced tables)</li>
 *   <li>Expected execution characteristics</li>
 * </ul>
 *
 * <h2>TPC-H Schema</h2>
 * The queries reference 8 tables from the TPC-H benchmark:
 * <ul>
 *   <li>NATION - 25 rows</li>
 *   <li>REGION - 5 rows</li>
 *   <li>PART - varies by scale factor</li>
 *   <li>SUPPLIER - varies by scale factor</li>
 *   <li>PARTSUPP - varies by scale factor</li>
 *   <li>CUSTOMER - varies by scale factor</li>
 *   <li>ORDERS - varies by scale factor</li>
 *   <li>LINEITEM - varies by scale factor</li>
 * </ul>
 *
 * <h2>Simplifications for Substrait Testing</h2>
 * Some queries have been simplified from the original TPC-H specification:
 * <ul>
 *   <li>Date parameters replaced with literals for deterministic results</li>
 *   <li>Some complex expressions simplified for better Substrait support</li>
 *   <li>Comments and formatting standardized</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get all queries
 * List<TpcHQuery> allQueries = TpcHQueries.getAllQueries();
 *
 * // Get specific query
 * TpcHQuery q1 = TpcHQueries.getQuery(1);
 *
 * // Get queries by complexity
 * Stream<TpcHQuery> complexQueries = TpcHQueries.getQueriesByComplexity(
 *     TpcHQuery.Complexity.COMPLEX
 * );
 *
 * // Use in parameterized test
 * @ParameterizedTest
 * @MethodSource("io.substrait.test.tpch.TpcHQueries#getAllQueries")
 * void testQuery(TpcHQuery query) {
 *     // Test implementation
 * }
 * }</pre>
 *
 * @see <a href="http://www.tpc.org/tpch/">TPC-H Benchmark Specification</a>
 */
public final class TpcHQueries {

  // Prevent instantiation - this is a utility class
  private TpcHQueries() {
    throw new AssertionError("No instances allowed");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERY DEFINITIONS - All 22 TPC-H Queries
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * TPC-H Query 1: Pricing Summary Report.
   *
   * <p>Tests basic aggregation, filtering, and sorting.
   * This is one of the most commonly executed queries in TPC-H.
   */
  public static final TpcHQuery Q1 = new TpcHQuery(
      1,
      "SELECT " +
      "  l_returnflag, " +
      "  l_linestatus, " +
      "  SUM(l_quantity) AS sum_qty, " +
      "  SUM(l_extendedprice) AS sum_base_price, " +
      "  SUM(l_extendedprice * (1 - l_discount)) AS sum_disc_price, " +
      "  SUM(l_extendedprice * (1 - l_discount) * (1 + l_tax)) AS sum_charge, " +
      "  AVG(l_quantity) AS avg_qty, " +
      "  AVG(l_extendedprice) AS avg_price, " +
      "  AVG(l_discount) AS avg_disc, " +
      "  COUNT(*) AS count_order " +
      "FROM lineitem " +
      "WHERE l_shipdate <= DATE '1998-09-01' " +
      "GROUP BY l_returnflag, l_linestatus " +
      "ORDER BY l_returnflag, l_linestatus",
      "Pricing Summary Report - aggregations and filtering on lineitem",
      TpcHQuery.Complexity.SIMPLE,
      List.of("lineitem"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 2: Minimum Cost Supplier.
   *
   * <p>Tests complex joins with subqueries and correlated conditions.
   * One of the most complex queries in the benchmark.
   */
  public static final TpcHQuery Q2 = new TpcHQuery(
      2,
      "SELECT " +
      "  s_acctbal, s_name, n_name, p_partkey, p_mfgr, " +
      "  s_address, s_phone, s_comment " +
      "FROM part, supplier, partsupp, nation, region " +
      "WHERE p_partkey = ps_partkey " +
      "  AND s_suppkey = ps_suppkey " +
      "  AND p_size = 15 " +
      "  AND p_type LIKE '%BRASS' " +
      "  AND s_nationkey = n_nationkey " +
      "  AND n_regionkey = r_regionkey " +
      "  AND r_name = 'EUROPE' " +
      "  AND ps_supplycost = ( " +
      "    SELECT MIN(ps_supplycost) " +
      "    FROM partsupp, supplier, nation, region " +
      "    WHERE p_partkey = ps_partkey " +
      "      AND s_suppkey = ps_suppkey " +
      "      AND s_nationkey = n_nationkey " +
      "      AND n_regionkey = r_regionkey " +
      "      AND r_name = 'EUROPE' " +
      "  ) " +
      "ORDER BY s_acctbal DESC, n_name, s_name, p_partkey " +
      "LIMIT 100",
      "Minimum Cost Supplier - correlated subquery with 5-way join",
      TpcHQuery.Complexity.VERY_COMPLEX,
      List.of("part", "supplier", "partsupp", "nation", "region"),
      TpcHQuery.ExecutionTime.SLOW
  );

  /**
   * TPC-H Query 3: Shipping Priority.
   *
   * <p>Tests joins, aggregation, and filtering with date ranges.
   */
  public static final TpcHQuery Q3 = new TpcHQuery(
      3,
      "SELECT " +
      "  l_orderkey, " +
      "  SUM(l_extendedprice * (1 - l_discount)) AS revenue, " +
      "  o_orderdate, " +
      "  o_shippriority " +
      "FROM customer, orders, lineitem " +
      "WHERE c_mktsegment = 'BUILDING' " +
      "  AND c_custkey = o_custkey " +
      "  AND l_orderkey = o_orderkey " +
      "  AND o_orderdate < DATE '1995-03-15' " +
      "  AND l_shipdate > DATE '1995-03-15' " +
      "GROUP BY l_orderkey, o_orderdate, o_shippriority " +
      "ORDER BY revenue DESC, o_orderdate " +
      "LIMIT 10",
      "Shipping Priority - 3-way join with aggregation",
      TpcHQuery.Complexity.MEDIUM,
      List.of("customer", "orders", "lineitem"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 4: Order Priority Checking.
   *
   * <p>Tests EXISTS subquery with date filtering.
   */
  public static final TpcHQuery Q4 = new TpcHQuery(
      4,
      "SELECT " +
      "  o_orderpriority, " +
      "  COUNT(*) AS order_count " +
      "FROM orders " +
      "WHERE o_orderdate >= DATE '1993-07-01' " +
      "  AND o_orderdate < DATE '1993-10-01' " +
      "  AND EXISTS ( " +
      "    SELECT * " +
      "    FROM lineitem " +
      "    WHERE l_orderkey = o_orderkey " +
      "      AND l_commitdate < l_receiptdate " +
      "  ) " +
      "GROUP BY o_orderpriority " +
      "ORDER BY o_orderpriority",
      "Order Priority - EXISTS subquery with aggregation",
      TpcHQuery.Complexity.MEDIUM,
      List.of("orders", "lineitem"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 5: Local Supplier Volume.
   *
   * <p>Tests 6-way join with aggregation and grouping.
   */
  public static final TpcHQuery Q5 = new TpcHQuery(
      5,
      "SELECT " +
      "  n_name, " +
      "  SUM(l_extendedprice * (1 - l_discount)) AS revenue " +
      "FROM customer, orders, lineitem, supplier, nation, region " +
      "WHERE c_custkey = o_custkey " +
      "  AND l_orderkey = o_orderkey " +
      "  AND l_suppkey = s_suppkey " +
      "  AND c_nationkey = s_nationkey " +
      "  AND s_nationkey = n_nationkey " +
      "  AND n_regionkey = r_regionkey " +
      "  AND r_name = 'ASIA' " +
      "  AND o_orderdate >= DATE '1994-01-01' " +
      "  AND o_orderdate < DATE '1995-01-01' " +
      "GROUP BY n_name " +
      "ORDER BY revenue DESC",
      "Local Supplier Volume - 6-way join with regional filtering",
      TpcHQuery.Complexity.COMPLEX,
      List.of("customer", "orders", "lineitem", "supplier", "nation", "region"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 6: Forecasting Revenue Change.
   *
   * <p>Simple aggregation query - fastest TPC-H query.
   * Often used for initial testing due to simplicity.
   */
  public static final TpcHQuery Q6 = new TpcHQuery(
      6,
      "SELECT " +
      "  SUM(l_extendedprice * l_discount) AS revenue " +
      "FROM lineitem " +
      "WHERE l_shipdate >= DATE '1994-01-01' " +
      "  AND l_shipdate < DATE '1995-01-01' " +
      "  AND l_discount BETWEEN 0.05 AND 0.07 " +
      "  AND l_quantity < 24",
      "Forecasting Revenue - simple aggregation with filters",
      TpcHQuery.Complexity.SIMPLE,
      List.of("lineitem"),
      TpcHQuery.ExecutionTime.VERY_FAST
  );

  /**
   * TPC-H Query 7: Volume Shipping.
   *
   * <p>Tests bidirectional joins with date ranges and aggregation.
   */
  public static final TpcHQuery Q7 = new TpcHQuery(
      7,
      "SELECT " +
      "  supp_nation, cust_nation, l_year, " +
      "  SUM(volume) AS revenue " +
      "FROM ( " +
      "  SELECT " +
      "    n1.n_name AS supp_nation, " +
      "    n2.n_name AS cust_nation, " +
      "    EXTRACT(YEAR FROM l_shipdate) AS l_year, " +
      "    l_extendedprice * (1 - l_discount) AS volume " +
      "  FROM supplier, lineitem, orders, customer, nation n1, nation n2 " +
      "  WHERE s_suppkey = l_suppkey " +
      "    AND o_orderkey = l_orderkey " +
      "    AND c_custkey = o_custkey " +
      "    AND s_nationkey = n1.n_nationkey " +
      "    AND c_nationkey = n2.n_nationkey " +
      "    AND ( " +
      "      (n1.n_name = 'FRANCE' AND n2.n_name = 'GERMANY') " +
      "      OR (n1.n_name = 'GERMANY' AND n2.n_name = 'FRANCE') " +
      "    ) " +
      "    AND l_shipdate BETWEEN DATE '1995-01-01' AND DATE '1996-12-31' " +
      ") AS shipping " +
      "GROUP BY supp_nation, cust_nation, l_year " +
      "ORDER BY supp_nation, cust_nation, l_year",
      "Volume Shipping - bidirectional join with subquery",
      TpcHQuery.Complexity.COMPLEX,
      List.of("supplier", "lineitem", "orders", "customer", "nation"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 8: National Market Share.
   *
   * <p>Tests complex aggregation with CASE expressions.
   */
  public static final TpcHQuery Q8 = new TpcHQuery(
      8,
      "SELECT " +
      "  o_year, " +
      "  SUM(CASE WHEN nation = 'BRAZIL' THEN volume ELSE 0 END) / " +
      "  SUM(volume) AS mkt_share " +
      "FROM ( " +
      "  SELECT " +
      "    EXTRACT(YEAR FROM o_orderdate) AS o_year, " +
      "    l_extendedprice * (1 - l_discount) AS volume, " +
      "    n2.n_name AS nation " +
      "  FROM part, supplier, lineitem, orders, customer, nation n1, nation n2, region " +
      "  WHERE p_partkey = l_partkey " +
      "    AND s_suppkey = l_suppkey " +
      "    AND l_orderkey = o_orderkey " +
      "    AND o_custkey = c_custkey " +
      "    AND c_nationkey = n1.n_nationkey " +
      "    AND n1.n_regionkey = r_regionkey " +
      "    AND r_name = 'AMERICA' " +
      "    AND s_nationkey = n2.n_nationkey " +
      "    AND o_orderdate BETWEEN DATE '1995-01-01' AND DATE '1996-12-31' " +
      "    AND p_type = 'ECONOMY ANODIZED STEEL' " +
      ") AS all_nations " +
      "GROUP BY o_year " +
      "ORDER BY o_year",
      "National Market Share - CASE expressions with 8-way join",
      TpcHQuery.Complexity.VERY_COMPLEX,
      List.of("part", "supplier", "lineitem", "orders", "customer", "nation", "region"),
      TpcHQuery.ExecutionTime.SLOW
  );

  /**
   * TPC-H Query 9: Product Type Profit Measure.
   *
   * <p>Tests complex expressions with multiple joins and aggregation.
   */
  public static final TpcHQuery Q9 = new TpcHQuery(
      9,
      "SELECT " +
      "  nation, o_year, SUM(amount) AS sum_profit " +
      "FROM ( " +
      "  SELECT " +
      "    n_name AS nation, " +
      "    EXTRACT(YEAR FROM o_orderdate) AS o_year, " +
      "    l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity AS amount " +
      "  FROM part, supplier, lineitem, partsupp, orders, nation " +
      "  WHERE s_suppkey = l_suppkey " +
      "    AND ps_suppkey = l_suppkey " +
      "    AND ps_partkey = l_partkey " +
      "    AND p_partkey = l_partkey " +
      "    AND o_orderkey = l_orderkey " +
      "    AND s_nationkey = n_nationkey " +
      "    AND p_name LIKE '%green%' " +
      ") AS profit " +
      "GROUP BY nation, o_year " +
      "ORDER BY nation, o_year DESC",
      "Product Type Profit - complex profit calculation with 6-way join",
      TpcHQuery.Complexity.COMPLEX,
      List.of("part", "supplier", "lineitem", "partsupp", "orders", "nation"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 10: Returned Item Reporting.
   *
   * <p>Tests aggregation with multiple joins and TOP-N results.
   */
  public static final TpcHQuery Q10 = new TpcHQuery(
      10,
      "SELECT " +
      "  c_custkey, c_name, " +
      "  SUM(l_extendedprice * (1 - l_discount)) AS revenue, " +
      "  c_acctbal, n_name, c_address, c_phone, c_comment " +
      "FROM customer, orders, lineitem, nation " +
      "WHERE c_custkey = o_custkey " +
      "  AND l_orderkey = o_orderkey " +
      "  AND o_orderdate >= DATE '1993-10-01' " +
      "  AND o_orderdate < DATE '1994-01-01' " +
      "  AND l_returnflag = 'R' " +
      "  AND c_nationkey = n_nationkey " +
      "GROUP BY c_custkey, c_name, c_acctbal, c_phone, n_name, c_address, c_comment " +
      "ORDER BY revenue DESC " +
      "LIMIT 20",
      "Returned Item Reporting - TOP-N with 4-way join",
      TpcHQuery.Complexity.MEDIUM,
      List.of("customer", "orders", "lineitem", "nation"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 11: Important Stock Identification.
   *
   * <p>Tests aggregation in HAVING clause with subquery.
   */
  public static final TpcHQuery Q11 = new TpcHQuery(
      11,
      "SELECT " +
      "  ps_partkey, SUM(ps_supplycost * ps_availqty) AS total_value " +
      "FROM partsupp, supplier, nation " +
      "WHERE ps_suppkey = s_suppkey " +
      "  AND s_nationkey = n_nationkey " +
      "  AND n_name = 'GERMANY' " +
      "GROUP BY ps_partkey " +
      "HAVING SUM(ps_supplycost * ps_availqty) > ( " +
      "  SELECT SUM(ps_supplycost * ps_availqty) * 0.0001 " +
      "  FROM partsupp, supplier, nation " +
      "  WHERE ps_suppkey = s_suppkey " +
      "    AND s_nationkey = n_nationkey " +
      "    AND n_name = 'GERMANY' " +
      ") " +
      "ORDER BY total_value DESC",
      "Important Stock - HAVING clause with aggregated subquery",
      TpcHQuery.Complexity.COMPLEX,
      List.of("partsupp", "supplier", "nation"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 12: Shipping Modes and Order Priority.
   *
   * <p>Tests conditional aggregation with CASE expressions.
   */
  public static final TpcHQuery Q12 = new TpcHQuery(
      12,
      "SELECT " +
      "  l_shipmode, " +
      "  SUM(CASE WHEN o_orderpriority = '1-URGENT' OR o_orderpriority = '2-HIGH' " +
      "      THEN 1 ELSE 0 END) AS high_line_count, " +
      "  SUM(CASE WHEN o_orderpriority <> '1-URGENT' AND o_orderpriority <> '2-HIGH' " +
      "      THEN 1 ELSE 0 END) AS low_line_count " +
      "FROM orders, lineitem " +
      "WHERE o_orderkey = l_orderkey " +
      "  AND l_shipmode IN ('MAIL', 'SHIP') " +
      "  AND l_commitdate < l_receiptdate " +
      "  AND l_shipdate < l_commitdate " +
      "  AND l_receiptdate >= DATE '1994-01-01' " +
      "  AND l_receiptdate < DATE '1995-01-01' " +
      "GROUP BY l_shipmode " +
      "ORDER BY l_shipmode",
      "Shipping Modes - conditional aggregation with CASE",
      TpcHQuery.Complexity.MEDIUM,
      List.of("orders", "lineitem"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 13: Customer Distribution.
   *
   * <p>Tests LEFT OUTER JOIN with aggregation.
   */
  public static final TpcHQuery Q13 = new TpcHQuery(
      13,
      "SELECT " +
      "  c_count, COUNT(*) AS custdist " +
      "FROM ( " +
      "  SELECT c_custkey, COUNT(o_orderkey) AS c_count " +
      "  FROM customer " +
      "  LEFT OUTER JOIN orders ON c_custkey = o_custkey " +
      "    AND o_comment NOT LIKE '%special%requests%' " +
      "  GROUP BY c_custkey " +
      ") AS c_orders " +
      "GROUP BY c_count " +
      "ORDER BY custdist DESC, c_count DESC",
      "Customer Distribution - LEFT OUTER JOIN with nested aggregation",
      TpcHQuery.Complexity.MEDIUM,
      List.of("customer", "orders"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 14: Promotion Effect.
   *
   * <p>Tests percentage calculation with conditional aggregation.
   */
  public static final TpcHQuery Q14 = new TpcHQuery(
      14,
      "SELECT " +
      "  100.00 * SUM(CASE WHEN p_type LIKE 'PROMO%' " +
      "      THEN l_extendedprice * (1 - l_discount) ELSE 0 END) / " +
      "  SUM(l_extendedprice * (1 - l_discount)) AS promo_revenue " +
      "FROM lineitem, part " +
      "WHERE l_partkey = p_partkey " +
      "  AND l_shipdate >= DATE '1995-09-01' " +
      "  AND l_shipdate < DATE '1995-10-01'",
      "Promotion Effect - percentage calculation with CASE",
      TpcHQuery.Complexity.SIMPLE,
      List.of("lineitem", "part"),
      TpcHQuery.ExecutionTime.VERY_FAST
  );

  /**
   * TPC-H Query 15: Top Supplier (uses view).
   *
   * <p>Tests view creation and usage. Simplified without CREATE VIEW.
   */
  public static final TpcHQuery Q15 = new TpcHQuery(
      15,
      "SELECT " +
      "  s_suppkey, s_name, s_address, s_phone, total_revenue " +
      "FROM supplier, ( " +
      "  SELECT " +
      "    l_suppkey AS supplier_no, " +
      "    SUM(l_extendedprice * (1 - l_discount)) AS total_revenue " +
      "  FROM lineitem " +
      "  WHERE l_shipdate >= DATE '1996-01-01' " +
      "    AND l_shipdate < DATE '1996-04-01' " +
      "  GROUP BY l_suppkey " +
      ") AS revenue " +
      "WHERE s_suppkey = supplier_no " +
      "  AND total_revenue = ( " +
      "    SELECT MAX(total_revenue) " +
      "    FROM ( " +
      "      SELECT SUM(l_extendedprice * (1 - l_discount)) AS total_revenue " +
      "      FROM lineitem " +
      "      WHERE l_shipdate >= DATE '1996-01-01' " +
      "        AND l_shipdate < DATE '1996-04-01' " +
      "      GROUP BY l_suppkey " +
      "    ) AS revenue_inner " +
      "  ) " +
      "ORDER BY s_suppkey",
      "Top Supplier - nested subqueries with MAX aggregation",
      TpcHQuery.Complexity.COMPLEX,
      List.of("supplier", "lineitem"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 16: Parts/Supplier Relationship.
   *
   * <p>Tests COUNT DISTINCT with NOT IN subquery.
   */
  public static final TpcHQuery Q16 = new TpcHQuery(
      16,
      "SELECT " +
      "  p_brand, p_type, p_size, " +
      "  COUNT(DISTINCT ps_suppkey) AS supplier_cnt " +
      "FROM partsupp, part " +
      "WHERE p_partkey = ps_partkey " +
      "  AND p_brand <> 'Brand#45' " +
      "  AND p_type NOT LIKE 'MEDIUM POLISHED%' " +
      "  AND p_size IN (49, 14, 23, 45, 19, 3, 36, 9) " +
      "  AND ps_suppkey NOT IN ( " +
      "    SELECT s_suppkey " +
      "    FROM supplier " +
      "    WHERE s_comment LIKE '%Customer%Complaints%' " +
      "  ) " +
      "GROUP BY p_brand, p_type, p_size " +
      "ORDER BY supplier_cnt DESC, p_brand, p_type, p_size",
      "Parts Supplier - COUNT DISTINCT with NOT IN subquery",
      TpcHQuery.Complexity.MEDIUM,
      List.of("partsupp", "part", "supplier"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 17: Small-Quantity-Order Revenue.
   *
   * <p>Tests correlated subquery with AVG aggregation.
   */
  public static final TpcHQuery Q17 = new TpcHQuery(
      17,
      "SELECT SUM(l_extendedprice) / 7.0 AS avg_yearly " +
      "FROM lineitem, part " +
      "WHERE p_partkey = l_partkey " +
      "  AND p_brand = 'Brand#23' " +
      "  AND p_container = 'MED BOX' " +
      "  AND l_quantity < ( " +
      "    SELECT 0.2 * AVG(l_quantity) " +
      "    FROM lineitem " +
      "    WHERE l_partkey = p_partkey " +
      "  )",
      "Small Quantity Revenue - correlated subquery with AVG",
      TpcHQuery.Complexity.COMPLEX,
      List.of("lineitem", "part"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 18: Large Volume Customer.
   *
   * <p>Tests IN subquery with aggregation and TOP-N.
   */
  public static final TpcHQuery Q18 = new TpcHQuery(
      18,
      "SELECT " +
      "  c_name, c_custkey, o_orderkey, o_orderdate, o_totalprice, " +
      "  SUM(l_quantity) AS total_qty " +
      "FROM customer, orders, lineitem " +
      "WHERE o_orderkey IN ( " +
      "  SELECT l_orderkey " +
      "  FROM lineitem " +
      "  GROUP BY l_orderkey " +
      "  HAVING SUM(l_quantity) > 300 " +
      ") " +
      "  AND c_custkey = o_custkey " +
      "  AND o_orderkey = l_orderkey " +
      "GROUP BY c_name, c_custkey, o_orderkey, o_orderdate, o_totalprice " +
      "ORDER BY o_totalprice DESC, o_orderdate " +
      "LIMIT 100",
      "Large Volume Customer - IN subquery with HAVING",
      TpcHQuery.Complexity.COMPLEX,
      List.of("customer", "orders", "lineitem"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  /**
   * TPC-H Query 19: Discounted Revenue.
   *
   * <p>Tests complex OR conditions with multiple AND clauses.
   */
  public static final TpcHQuery Q19 = new TpcHQuery(
      19,
      "SELECT SUM(l_extendedprice * (1 - l_discount)) AS revenue " +
      "FROM lineitem, part " +
      "WHERE ( " +
      "  p_partkey = l_partkey " +
      "  AND p_brand = 'Brand#12' " +
      "  AND p_container IN ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG') " +
      "  AND l_quantity >= 1 AND l_quantity <= 11 " +
      "  AND p_size BETWEEN 1 AND 5 " +
      "  AND l_shipmode IN ('AIR', 'AIR REG') " +
      "  AND l_shipinstruct = 'DELIVER IN PERSON' " +
      ") OR ( " +
      "  p_partkey = l_partkey " +
      "  AND p_brand = 'Brand#23' " +
      "  AND p_container IN ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK') " +
      "  AND l_quantity >= 10 AND l_quantity <= 20 " +
      "  AND p_size BETWEEN 1 AND 10 " +
      "  AND l_shipmode IN ('AIR', 'AIR REG') " +
      "  AND l_shipinstruct = 'DELIVER IN PERSON' " +
      ") OR ( " +
      "  p_partkey = l_partkey " +
      "  AND p_brand = 'Brand#34' " +
      "  AND p_container IN ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG') " +
      "  AND l_quantity >= 20 AND l_quantity <= 30 " +
      "  AND p_size BETWEEN 1 AND 15 " +
      "  AND l_shipmode IN ('AIR', 'AIR REG') " +
      "  AND l_shipinstruct = 'DELIVER IN PERSON' " +
      ")",
      "Discounted Revenue - complex OR predicates",
      TpcHQuery.Complexity.MEDIUM,
      List.of("lineitem", "part"),
      TpcHQuery.ExecutionTime.FAST
  );

  /**
   * TPC-H Query 20: Potential Part Promotion.
   *
   * <p>Tests IN subquery with EXISTS subquery.
   */
  public static final TpcHQuery Q20 = new TpcHQuery(
      20,
      "SELECT s_name, s_address " +
      "FROM supplier, nation " +
      "WHERE s_suppkey IN ( " +
      "  SELECT ps_suppkey " +
      "  FROM partsupp " +
      "  WHERE ps_partkey IN ( " +
      "    SELECT p_partkey " +
      "    FROM part " +
      "    WHERE p_name LIKE 'forest%' " +
      "  ) " +
      "    AND ps_availqty > ( " +
      "      SELECT 0.5 * SUM(l_quantity) " +
      "      FROM lineitem " +
      "      WHERE l_partkey = ps_partkey " +
      "        AND l_suppkey = ps_suppkey " +
      "        AND l_shipdate >= DATE '1994-01-01' " +
      "        AND l_shipdate < DATE '1995-01-01' " +
      "    ) " +
      ") " +
      "  AND s_nationkey = n_nationkey " +
      "  AND n_name = 'CANADA' " +
      "ORDER BY s_name",
      "Potential Part Promotion - nested IN and correlated subqueries",
      TpcHQuery.Complexity.VERY_COMPLEX,
      List.of("supplier", "nation", "partsupp", "part", "lineitem"),
      TpcHQuery.ExecutionTime.SLOW
  );

  /**
   * TPC-H Query 21: Suppliers Who Kept Orders Waiting.
   *
   * <p>Tests multiple EXISTS subqueries.
   */
  public static final TpcHQuery Q21 = new TpcHQuery(
      21,
      "SELECT s_name, COUNT(*) AS numwait " +
      "FROM supplier, lineitem l1, orders, nation " +
      "WHERE s_suppkey = l1.l_suppkey " +
      "  AND o_orderkey = l1.l_orderkey " +
      "  AND o_orderstatus = 'F' " +
      "  AND l1.l_receiptdate > l1.l_commitdate " +
      "  AND EXISTS ( " +
      "    SELECT * FROM lineitem l2 " +
      "    WHERE l2.l_orderkey = l1.l_orderkey " +
      "      AND l2.l_suppkey <> l1.l_suppkey " +
      "  ) " +
      "  AND NOT EXISTS ( " +
      "    SELECT * FROM lineitem l3 " +
      "    WHERE l3.l_orderkey = l1.l_orderkey " +
      "      AND l3.l_suppkey <> l1.l_suppkey " +
      "      AND l3.l_receiptdate > l3.l_commitdate " +
      "  ) " +
      "  AND s_nationkey = n_nationkey " +
      "  AND n_name = 'SAUDI ARABIA' " +
      "GROUP BY s_name " +
      "ORDER BY numwait DESC, s_name " +
      "LIMIT 100",
      "Waiting Orders - multiple EXISTS with self-joins",
      TpcHQuery.Complexity.VERY_COMPLEX,
      List.of("supplier", "lineitem", "orders", "nation"),
      TpcHQuery.ExecutionTime.SLOW
  );

  /**
   * TPC-H Query 22: Global Sales Opportunity.
   *
   * <p>Tests substring functions with NOT EXISTS subquery.
   */
  public static final TpcHQuery Q22 = new TpcHQuery(
      22,
      "SELECT " +
      "  cntrycode, COUNT(*) AS numcust, SUM(c_acctbal) AS totacctbal " +
      "FROM ( " +
      "  SELECT SUBSTRING(c_phone FROM 1 FOR 2) AS cntrycode, c_acctbal " +
      "  FROM customer " +
      "  WHERE SUBSTRING(c_phone FROM 1 FOR 2) IN " +
      "      ('13', '31', '23', '29', '30', '18', '17') " +
      "    AND c_acctbal > ( " +
      "      SELECT AVG(c_acctbal) " +
      "      FROM customer " +
      "      WHERE c_acctbal > 0.00 " +
      "        AND SUBSTRING(c_phone FROM 1 FOR 2) IN " +
      "            ('13', '31', '23', '29', '30', '18', '17') " +
      "    ) " +
      "    AND NOT EXISTS ( " +
      "      SELECT * FROM orders " +
      "      WHERE o_custkey = c_custkey " +
      "    ) " +
      ") AS custsale " +
      "GROUP BY cntrycode " +
      "ORDER BY cntrycode",
      "Global Sales - SUBSTRING with NOT EXISTS subquery",
      TpcHQuery.Complexity.COMPLEX,
      List.of("customer", "orders"),
      TpcHQuery.ExecutionTime.MEDIUM
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERY ACCESS METHODS
  // ═══════════════════════════════════════════════════════════════════════════

  /** All 22 TPC-H queries in order */
  private static final List<TpcHQuery> ALL_QUERIES = List.of(
      Q1, Q2, Q3, Q4, Q5, Q6, Q7, Q8, Q9, Q10, Q11,
      Q12, Q13, Q14, Q15, Q16, Q17, Q18, Q19, Q20, Q21, Q22
  );

  /**
   * Returns all 22 TPC-H queries.
   *
   * <p>Useful for parameterized tests that run against all queries.
   *
   * @return immutable list of all queries
   */
  public static List<TpcHQuery> getAllQueries() {
    return ALL_QUERIES;
  }

  /**
   * Returns a specific TPC-H query by number.
   *
   * @param queryNumber query number (1-22)
   * @return the specified query
   * @throws IllegalArgumentException if query number is out of range
   */
  public static TpcHQuery getQuery(int queryNumber) {
    if (queryNumber < 1 || queryNumber > 22) {
      throw new IllegalArgumentException(
          "Query number must be between 1 and 22, got: " + queryNumber);
    }
    return ALL_QUERIES.get(queryNumber - 1);
  }

  /**
   * Returns queries filtered by complexity level.
   *
   * @param complexity desired complexity level
   * @return stream of queries matching the complexity
   */
  public static Stream<TpcHQuery> getQueriesByComplexity(TpcHQuery.Complexity complexity) {
    return ALL_QUERIES.stream()
        .filter(q -> q.getComplexity() == complexity);
  }

  /**
   * Returns queries filtered by estimated execution time.
   *
   * @param time desired execution time category
   * @return stream of queries matching the time estimate
   */
  public static Stream<TpcHQuery> getQueriesByExecutionTime(TpcHQuery.ExecutionTime time) {
    return ALL_QUERIES.stream()
        .filter(q -> q.getEstimatedTime() == time);
  }

  /**
   * Returns queries that reference a specific table.
   *
   * @param tableName table name (case-insensitive)
   * @return stream of queries that use this table
   */
  public static Stream<TpcHQuery> getQueriesUsingTable(String tableName) {
    String lowerTableName = tableName.toLowerCase();
    return ALL_QUERIES.stream()
        .filter(q -> q.getReferencedTables().stream()
            .anyMatch(t -> t.equalsIgnoreCase(lowerTableName)));
  }

  /**
   * Returns a subset of simple queries for quick testing.
   *
   * <p>Includes Q1, Q6, and Q14 - the fastest and simplest queries.
   *
   * @return list of simple queries
   */
  public static List<TpcHQuery> getSimpleQueries() {
    return List.of(Q1, Q6, Q14);
  }

  /**
   * Returns a smoke test suite of representative queries.
   *
   * <p>Includes one query from each complexity category for quick validation.
   *
   * @return list of queries for smoke testing
   */
  public static List<TpcHQuery> getSmokeTestQueries() {
    return List.of(
        Q6,   // SIMPLE - single table aggregation
        Q3,   // MEDIUM - 3-way join
        Q5,   // COMPLEX - 6-way join
        Q2    // VERY_COMPLEX - correlated subquery
    );
  }
}
