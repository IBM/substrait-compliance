WITH sales_summary AS (
  SELECT i_item_id,
         i_item_desc,
         s_state,
         COUNT(*) AS store_cnt,
         AVG(ss_quantity) AS store_avg,
         STDDEV_SAMP(ss_quantity) AS store_stddev,
         ROW_NUMBER() OVER (PARTITION BY s_state ORDER BY COUNT(*) DESC) AS rn
  FROM store_sales, item, store, date_dim
  WHERE ss_item_sk = i_item_sk
    AND ss_store_sk = s_store_sk
    AND ss_sold_date_sk = d_date_sk
    AND d_year = [YEAR]
  GROUP BY i_item_id, i_item_desc, s_state
)
SELECT i_item_id, i_item_desc, s_state, store_cnt, store_avg, store_stddev
FROM sales_summary
WHERE rn <= 10
ORDER BY i_item_id, s_state
LIMIT [LIMIT]
