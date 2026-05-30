SELECT ca_zip, SUM(cs_sales_price)
FROM catalog_sales, customer, customer_address, date_dim
WHERE cs_bill_customer_sk = c_customer_sk
  AND c_current_addr_sk = ca_address_sk
  AND cs_sold_date_sk = d_date_sk
  AND d_qoy = [QUARTER]
  AND d_year = [YEAR]
  AND (
    SUBSTR(ca_zip,1,5) IN (
      SELECT SUBSTR(ca_zip,1,5)
      FROM customer_address
      WHERE SUBSTR(ca_zip,1,5) IN ('85669', '86197','88274','83405','86475')
    )
    OR ca_state IN ('CA','WA','GA')
    OR cs_sales_price > [SALES]
  )
GROUP BY ca_zip
ORDER BY ca_zip
LIMIT [LIMIT]
