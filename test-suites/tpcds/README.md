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

This initial release includes **5 representative queries** (Q1-Q5) that cover key TPC-DS patterns:

| Query | Description | Complexity | Key Features |
|-------|-------------|------------|--------------|
| Q01 | Customer Returns Analysis | MEDIUM | Aggregation, Joins, Filtering |
| Q02 | Web Sales Analysis | COMPLEX | Subqueries, Correlation, Date Operations |
| Q03 | Item Sales by Brand | MEDIUM | Joins, Aggregation, Grouping |
| Q04 | Customer Profitability | VERY_COMPLEX | Multi-channel, Window Functions, Ranking |
| Q05 | Sales Channel Comparison | COMPLEX | Union Operations, Channel Analysis |

### Roadmap

- **Phase 1** (Current): Queries 1-5 with core infrastructure
- **Phase 2**: Queries 6-25 (additional analytical patterns)
- **Phase 3**: Queries 26-50 (advanced analytics)
- **Phase 4**: Queries 51-75 (complex business logic)
- **Phase 5**: Queries 76-99 (complete benchmark)

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
├── data/                  # CSV data files (to be generated)
│   ├── store_sales.csv
│   ├── store_returns.csv
│   ├── catalog_sales.csv
│   ├── catalog_returns.csv
│   ├── web_sales.csv
│   ├── web_returns.csv
│   ├── date_dim.csv
│   ├── customer.csv
│   ├── item.csv
│   └── store.csv
├── plans/                 # Substrait query plans
│   ├── q01.bin           # Binary format
│   ├── q01.json          # JSON format
│   ├── q02.bin
│   ├── q02.json
│   └── ...
└── expected/              # Expected query results
    ├── q01.csv
    ├── q02.csv
    └── ...
```

## Data Generation

TPC-DS data must be generated using the official TPC-DS tools:

### Prerequisites

1. Download TPC-DS tools from [tpc.org](http://www.tpc.org/tpcds/)
2. Build the data generator (`dsdgen`)
3. Generate data at scale factor 0.01

### Generation Commands

```bash
# Generate all tables at SF 0.01
./dsdgen -SCALE 0.01 -DIR ./data -FORCE Y

# Generate specific tables
./dsdgen -SCALE 0.01 -TABLE store_sales -DIR ./data
./dsdgen -SCALE 0.01 -TABLE date_dim -DIR ./data
./dsdgen -SCALE 0.01 -TABLE customer -DIR ./data
```

### Data Format

- **Format**: CSV (pipe-delimited by default)
- **Encoding**: UTF-8
- **Line Endings**: Unix (LF)
- **Null Values**: Empty strings or NULL keyword

## Query Plans

Substrait query plans are provided in two formats:

1. **Binary (.bin)**: Compact protobuf format for execution
2. **JSON (.json)**: Human-readable format for inspection

### Plan Generation

Plans should be generated from SQL using a Substrait-compatible producer:

```python
# Example using a Substrait producer
from substrait_producer import SubstraitProducer

producer = SubstraitProducer()
sql = "SELECT ... FROM store_sales ..."
plan = producer.sql_to_substrait(sql)

# Save binary format
with open('plans/q01.bin', 'wb') as f:
    f.write(plan.SerializeToString())

# Save JSON format
with open('plans/q01.json', 'w') as f:
    f.write(plan.to_json())
```

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

Expected results are provided in CSV format in the `expected/` directory. Results should match:

- **Column order**: As specified in query
- **Row order**: As specified by ORDER BY clause
- **Precision**: Decimal values to 2 places
- **Null handling**: Consistent with SQL standard

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

**Status**: Initial Release (5 queries)  
**Version**: 1.0.0  
**Last Updated**: 2026-04-30