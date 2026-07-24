# TPC-H Compliance Test Suite

## Overview

This test suite contains 22 TPC-H queries for Substrait compliance testing.

- **Scale Factor:** 0.01 (~86K rows total)
- **Version:** 1.0.0
- **Queries:** 22 (Q1-Q22)
- **Result verification:** ✅ Full — all 22 expected output files are present

## Structure

```
tpch/
├── metadata.yaml          # Test suite definition
├── plans/
│   ├── q01.bin           # Binary Substrait plans (44 files: q01–q22)
│   ├── q01.json          # JSON Substrait plans
│   └── ...
├── data/
│   ├── lineitem.csv      # Input data (60,175 rows)
│   ├── orders.csv        # Input data (15,000 rows)
│   └── ...              # 8 CSV files total
└── expected/             # ✅ Reference outputs for all 22 queries
    ├── q01.csv           # Pipe-delimited, header row included
    └── ...              # q01.csv – q22.csv
```

When your engine runs TPC-H tests against the compliance runner, each query
result is compared against the corresponding file in `expected/`. A `PASSED`
result means the engine produced the correct answer. A `FAILED` result means
output was produced but did not match. A `SKIPPED` result is not expected for
this suite — if you see it, the expected files are missing from your checkout
(run `git status test-suites/tpch/expected/` to check).

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

| File | Rows |
|------|------|
| `region.csv` | 5 |
| `nation.csv` | 25 |
| `part.csv` | 2,000 |
| `supplier.csv` | 100 |
| `partsupp.csv` | 8,000 |
| `customer.csv` | 1,500 |
| `orders.csv` | 15,000 |
| `lineitem.csv` | 60,175 |

**Total:** ~86,630 rows

## Expected Outputs

All 22 expected output files are present in `expected/` (pipe-delimited CSV,
header row on line 1). They were generated from the same input data at scale
factor 0.01 and serve as the reference for correctness comparison.

**Status:** ✅ Complete — result correctness is fully verifiable for all 22 queries.
