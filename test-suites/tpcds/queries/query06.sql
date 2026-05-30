SELECT i_item_id,
       i_item_desc,
       s_store_id,
       s_store_name,
       SUM(ss_net_profit) AS store_sales_profit,
       SUM(sr_net_loss) AS store_returns_loss,
       SUM(cs_net_profit) AS catalog_sales_profit,
       RANK() OVER (PARTITION BY i_item_id ORDER BY SUM(ss_net_profit) DESC) AS rnk
FROM store_sales, store_returns, catalog_sales, date_dim d1, date_dim d2, date_dim d3,
     store, item
WHERE d1.d_moy = [MONTH]
  AND d1.d_year = [YEAR]
  AND d1.d_date_sk = ss_sold_date_sk
  AND i_item_sk = ss_item_sk
  AND s_store_sk = ss_store_sk
  AND ss_customer_sk = sr_customer_sk
  AND ss_item_sk = sr_item_sk
  AND ss_ticket_number = sr_ticket_number
  AND sr_returned_date_sk = d2.d_date_sk
  AND d2.d_moy BETWEEN [MONTH] AND [MONTH] + 3
  AND d2.d_year = [YEAR]
  AND sr_customer_sk = cs_bill_customer_sk
  AND sr_item_sk = cs_item_sk
  AND cs_sold_date_sk = d3.d_date_sk
  AND d3.d_moy BETWEEN [MONTH] AND [MONTH] + 3
  AND d3.d_year = [YEAR]
GROUP BY i_item_id, i_item_desc, s_store_id, s_store_name
HAVING SUM(ss_net_profit) > 0
ORDER BY i_item_id, s_store_id
LIMIT [LIMIT]
