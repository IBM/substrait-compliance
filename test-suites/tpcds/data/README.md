# TPC-DS Data Files

This directory contains TPC-DS benchmark data files at scale factor 0.01.

## Available Files (24 tables)

### Fact Tables (Sales Channels)
- `store_sales.csv` - Physical store transactions
- `store_returns.csv` - Store returns
- `catalog_sales.csv` - Catalog transactions
- `catalog_returns.csv` - Catalog returns
- `web_sales.csv` - Web transactions
- `web_returns.csv` - Web returns

### Dimension Tables
- `call_center.csv` - Call center information
- `catalog_page.csv` - Catalog page details
- `customer.csv` - Customer information
- `customer_address.csv` - Customer addresses
- `customer_demographics.csv` - Customer demographics
- `date_dim.csv` - Date dimension (73,049 rows)
- `household_demographics.csv` - Household demographics
- `income_band.csv` - Income bands
- `inventory.csv` - Inventory data
- `item.csv` - Product catalog (18,000 items)
- `promotion.csv` - Promotional information
- `reason.csv` - Return reasons
- `ship_mode.csv` - Shipping modes
- `store.csv` - Store locations
- `time_dim.csv` - Time dimension
- `warehouse.csv` - Warehouse information
- `web_page.csv` - Web page details
- `web_site.csv` - Web site information

## Data Characteristics

- **Scale Factor**: 0.01 (suitable for testing and development)
- **Format**: CSV (comma-delimited)
- **Encoding**: UTF-8
- **Source**: Generated using official TPC-DS tools v3.2.0
- **Total Size**: Approximately 500MB uncompressed

## Usage

These data files are used by the TPC-DS compliance tests. They are automatically loaded by the compliance framework when running TPC-DS benchmark queries.

## Schema

For detailed schema information, including column definitions and relationships, see the [TPC-DS Specification](http://www.tpc.org/tpc_documents_current_versions/pdf/tpc-ds_v3.2.0.pdf).

## License

This data follows the TPC Fair Use Policy. The TPC-DS benchmark specification is copyright © Transaction Processing Performance Council.
