SELECT dt.d_year,
       item.i_brand_id AS brand_id,
       item.i_brand AS brand,
       item.i_category_id AS category_id,
       item.i_category AS category,
       SUM(ss_ext_sales_price) AS ext_price
FROM date_dim dt, store_sales, item
WHERE dt.d_date_sk = store_sales.ss_sold_date_sk
  AND store_sales.ss_item_sk = item.i_item_sk
  AND item.i_manager_id = 19
  AND dt.d_moy = [MONTH]
  AND dt.d_year = [YEAR]
GROUP BY dt.d_year, item.i_brand, item.i_brand_id, item.i_category_id, item.i_category
ORDER BY dt.d_year, ext_price DESC, brand_id
LIMIT [LIMIT]
