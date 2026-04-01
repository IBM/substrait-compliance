# TPC-H Compliance Test Suite

## Overview

This test suite contains 22 TPC-H queries for Substrait compliance testing.

- **Scale Factor:** 0.01 (~86K rows total)
- **Version:** 1.0.0
- **Queries:** 22 (Q1-Q22)

## Structure

```
tpch/
├── metadata.yaml          # Test suite definition
├── plans/
│   ├── q01.bin           # Binary Substrait plans
│   ├── q01.json          # JSON Substrait plans
│   └── ...
├── data/
│   ├── lineitem.csv      # Input data (60,175 rows)
│   ├── orders.csv        # Input data (15,000 rows)
│   └── ...
└── expected/
    ├── q01.csv           # Expected output
    └── ...
```

## Usage

### Java

```java
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load(Paths.get("test-suites/tpch/metadata.yaml"));

ComplianceRunner runner = new ComplianceRunner(myEngine);
ComplianceReport report = runner.runTestSuite(suite);
```

### Python

```python
from substrait_compliance import YamlTestSuiteLoader, ComplianceRunner

loader = YamlTestSuiteLoader()
suite = loader.load("test-suites/tpch/metadata.yaml")

runner = ComplianceRunner(my_engine)
report = runner.run_test_suite(suite)
```

## Query Complexity

- **SIMPLE** (3): Q1, Q6, Q14
- **MEDIUM** (7): Q3, Q4, Q10, Q12, Q13, Q16, Q19
- **COMPLEX** (8): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22
- **VERY_COMPLEX** (4): Q2, Q8, Q20, Q21

## Data Files

All data files are in CSV format with headers:

- `region.csv` - 5 rows
- `nation.csv` - 25 rows
- `part.csv` - 2,000 rows
- `supplier.csv` - 100 rows
- `partsupp.csv` - 8,000 rows
- `customer.csv` - 1,500 rows
- `orders.csv` - 15,000 rows
- `lineitem.csv` - 60,175 rows

**Total:** ~86,630 rows
