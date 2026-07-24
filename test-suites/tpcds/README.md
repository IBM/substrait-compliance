# TPC-DS Benchmark Compliance Suite

## Overview

The TPC-DS (Transaction Processing Performance Council - Decision Support) benchmark is an industry-standard decision support benchmark that models complex business intelligence and data warehousing scenarios. This compliance suite provides Substrait implementations of TPC-DS queries for testing query engine compatibility.

## About TPC-DS

- **Purpose**: Decision support and business intelligence workload testing
- **Queries**: 99 queries covering various analytical patterns
- **Complexity**: More complex than TPC-H with multi-channel retail scenarios
- **Focus Areas**: 
  - Multi-channel retail (store, catalog, web)
  - Customer behavior analysis
  - Sales and returns analysis
  - Inventory management
  - Marketing effectiveness

## Current Implementation

This release includes **99 TPC-DS queries** with comprehensive data and plan files:

### Available Resources

- **Queries**: 99 SQL query files (query01.sql - query99.sql)
- **Plans**: 194 Substrait plan files (97 JSON + 97 binary format)
- **Data**: 24 CSV data files covering all TPC-DS tables

### Query Coverage

All 99 TPC-DS queries are available, covering:
- Customer behavior analysis
- Multi-channel retail analytics (store, catalog, web)
- Sales and returns analysis
- Inventory management
- Marketing effectiveness
- Complex business intelligence scenarios

### Sample Queries

| Query | Description | Complexity | Key Features |
|-------|-------------|------------|--------------|
| Q01 | Customer Returns Analysis | MEDIUM | Aggregation, Joins, Filtering |
| Q06 | Item Sales Analysis | COMPLEX | Subqueries, Date Operations |
| Q07 | Promotional Sales | COMPLEX | Multi-table Joins, Aggregation |
| Q20 | Channel Comparison | VERY_COMPLEX | Multi-channel, Window Functions |
| Q99 | Shipping Analysis | COMPLEX | Date Operations, Grouping |

## Data Schema

### Fact Tables (Sales Channels)

1. **store_sales** (287,997 rows) - Physical store transactions
2. **store_returns** (28,795 rows) - Store returns
3. **catalog_sales** (143,997 rows) - Catalog transactions
4. **catalog_returns** (14,416 rows) - Catalog returns
5. **web_sales** (71,997 rows) - Web transactions
6. **web_returns** (7,197 rows) - Web returns

### Dimension Tables

1. **date_dim** (73,049 rows) - Date dimension
2. **customer** (10,000 rows) - Customer information
3. **item** (18,000 rows) - Product catalog
4. **store** (12 rows) - Store locations

**Total Rows**: 288,000 (at scale factor 0.01)

## Directory Structure

```
test-suites/tpcds/
├── README.md              # This file
├── metadata.yaml          # Test suite metadata and configuration
├── queries/               # SQL query files (99 queries)
│   ├── query01.sql
│   ├── query02.sql
│   └── ... (query99.sql)
├── data/                  # CSV data files (24 tables)
│   ├── call_center.csv
│   ├── catalog_page.csv
│   ├── catalog_returns.csv
│   ├── catalog_sales.csv
│   ├── customer.csv
│   ├── customer_address.csv
│   ├── customer_demographics.csv
│   ├── date_dim.csv
│   ├── household_demographics.csv
│   ├── income_band.csv
│   ├── inventory.csv
│   ├── item.csv
│   ├── promotion.csv
│   ├── reason.csv
│   ├── ship_mode.csv
│   ├── store.csv
│   ├── store_returns.csv
│   ├── store_sales.csv
│   ├── time_dim.csv
│   ├── warehouse.csv
│   ├── web_page.csv
│   ├── web_returns.csv
│   ├── web_sales.csv
│   └── web_site.csv
├── plans/                 # Substrait query plans (194 files)
│   ├── q01.bin           # Binary format
│   ├── q01.json          # JSON format
│   ├── q03.bin
│   ├── q03.json
│   └── ... (through q99.bin/json)
└── expected/              # ⚠️  Empty — expected query results not yet available
    └── .gitkeep           #     Tests run as SKIPPED until outputs are added
```

## Data Files

The TPC-DS data files are included in this repository at scale factor 0.01:

### Available Tables (24 CSV files)

**Fact Tables:**
- `store_sales.csv` - Physical store transactions
- `store_returns.csv` - Store returns
- `catalog_sales.csv` - Catalog transactions
- `catalog_returns.csv` - Catalog returns
- `web_sales.csv` - Web transactions
- `web_returns.csv` - Web returns

**Dimension Tables:**
- `call_center.csv` - Call center information
- `catalog_page.csv` - Catalog page details
- `customer.csv` - Customer information
- `customer_address.csv` - Customer addresses
- `customer_demographics.csv` - Customer demographics
- `date_dim.csv` - Date dimension
- `household_demographics.csv` - Household demographics
- `income_band.csv` - Income bands
- `inventory.csv` - Inventory data
- `item.csv` - Product catalog
- `promotion.csv` - Promotional information
- `reason.csv` - Return reasons
- `ship_mode.csv` - Shipping modes
- `store.csv` - Store locations
- `time_dim.csv` - Time dimension
- `warehouse.csv` - Warehouse information
- `web_page.csv` - Web page details
- `web_site.csv` - Web site information

### Data Format

- **Format**: CSV (comma-delimited)
- **Encoding**: UTF-8
- **Scale Factor**: 0.01 (suitable for testing and development)
- **Source**: Generated using official TPC-DS tools v3.2.0

## Query Plans

Substrait query plans are provided in two formats for 97 queries:

1. **Binary (.bin)**: Compact protobuf format for execution (97 files)
2. **JSON (.json)**: Human-readable format for inspection (97 files)

### Available Plans

Plans are available for queries: Q01, Q03, Q04, Q06-Q99 (97 total queries)

**Note**: Some queries (Q02, Q05) may not have plans yet as they are being validated.

### Plan Format

- **Binary Format**: Substrait protobuf serialization
- **JSON Format**: Human-readable JSON representation
- **Compatibility**: Generated for Substrait specification compliance
- **Source**: Converted from TPC-DS SQL queries using Substrait producers

## Running Tests

### Using Java SDK

```java
import io.substrait.compliance.*;

// Load test suite
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load("test-suites/tpcds/metadata.yaml");

// Run tests
ComplianceRunner runner = new ComplianceRunner(myEngine);
ComplianceReport report = runner.runTestSuite(suite);

// View results
System.out.println("TPC-DS Pass Rate: " + report.getPassRate() + "%");
```

### Using Python SDK

```python
from substrait_compliance import ComplianceRunner, YamlTestSuiteLoader

# Load test suite
loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/tpcds/metadata.yaml")

# Run tests
runner = ComplianceRunner(my_engine)
report = runner.run_test_suite(suite)

# View results
print(f"TPC-DS Pass Rate: {report.pass_rate}%")
```

## Complexity Levels

### SIMPLE
- Single table queries
- Basic aggregation
- Simple filtering

### MEDIUM
- Multi-table joins (2-3 tables)
- Aggregation with grouping
- Date filtering

### COMPLEX
- Complex joins (4-5 tables)
- Subqueries
- Advanced aggregation
- Union operations

### VERY_COMPLEX
- Multi-channel analysis (6+ tables)
- Window functions
- Ranking and analytics
- Complex business logic

## Key Differences from TPC-H

| Aspect | TPC-H | TPC-DS |
|--------|-------|--------|
| **Queries** | 22 | 99 |
| **Focus** | Ad-hoc queries | Decision support |
| **Schema** | 8 tables | 24 tables |
| **Channels** | Single | Multi-channel (store/catalog/web) |
| **Complexity** | Moderate | High |
| **Use Case** | General analytics | Retail BI |

## Expected Results

> ⚠️ **Not yet available.** The `expected/` directory contains only a `.gitkeep` placeholder.
> Running TPC-DS tests currently verifies structural plan execution (parse and run without error);
> result correctness cannot be checked until reference outputs are added.
> Tests without an expected output file are reported as `SKIPPED` by the compliance runner.
>
> When expected outputs are added they will be CSV files matching this format:
> - **Column order**: As specified in the query
> - **Row order**: As specified by `ORDER BY` clause
> - **Precision**: Decimal values to 2 places
> - **Null handling**: Consistent with SQL standard
>
> See [ROADMAP.md](../../ROADMAP.md) for the planned timeline.

## Compliance Scoring

Engines are scored based on:

1. **Query Success Rate**: Percentage of queries that execute successfully
2. **Result Accuracy**: Correctness of query results
3. **Feature Coverage**: Support for required Substrait features

### Minimum Requirements

- **Bronze**: 40% pass rate (2/5 queries)
- **Silver**: 60% pass rate (3/5 queries)
- **Gold**: 80% pass rate (4/5 queries)
- **Platinum**: 100% pass rate (5/5 queries)

## Features Tested

This suite tests the following Substrait features:

- ✅ **Joins**: Inner, left, right, full outer
- ✅ **Aggregation**: SUM, COUNT, AVG, MIN, MAX
- ✅ **Filtering**: WHERE clauses with complex predicates
- ✅ **Grouping**: GROUP BY with multiple columns
- ✅ **Subqueries**: Correlated and uncorrelated
- ✅ **Window Functions**: RANK, DENSE_RANK, ROW_NUMBER
- ✅ **Set Operations**: UNION, UNION ALL
- ✅ **Date Operations**: Date arithmetic and extraction
- ✅ **String Operations**: Pattern matching and manipulation
- ✅ **Null Handling**: COALESCE, IS NULL, IS NOT NULL

## Contributing

To add more TPC-DS queries:

1. Generate the query plan from SQL
2. Add entry to `metadata.yaml`
3. Place plans in `plans/` directory
4. Add expected results to `expected/`
5. Update this README with query description
6. Submit pull request

## References

- [TPC-DS Specification](http://www.tpc.org/tpc_documents_current_versions/pdf/tpc-ds_v3.2.0.pdf)
- [TPC-DS Tools](http://www.tpc.org/tpcds/)
- [Substrait Specification](https://substrait.io/)
- [Compliance Framework Documentation](../../README.md)

## License

This test suite follows the TPC Fair Use Policy. The TPC-DS benchmark specification is copyright © Transaction Processing Performance Council.

## Support

For questions or issues:
- Open an issue on GitHub
- Consult the main [Compliance Framework documentation](../../README.md)
- Join the Substrait community discussions

---

**Status**: Plans and data complete (99 queries, 24 data tables, 194 plan files) — expected outputs pending
**Version**: 2.0.0
**Last Updated**: 2026-05-22