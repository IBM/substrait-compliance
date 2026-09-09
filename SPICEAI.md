# The `spiceai` branch

This branch is [IBM/substrait-compliance](https://github.com/IBM/substrait-compliance)
`main` plus the suite corrections below, which Spice's
[substrait-compliance harness](https://github.com/spiceai/spiceai/tree/trunk/tools/substrait-compliance)
runs against. Each correction is a defect in the suite itself, found by
running the plans through DataFusion 54.1 (`datafusion-substrait`) and
comparing every cell with the golden; nothing here loosens a comparison.
Upstream `main` is merged in as it moves.

| Query | Defect | Correction |
|-------|--------|------------|
| TPC-H q01 | `plans/q01.{bin,json}` filtered `l_shipdate <= 1998-09-01` (date 10470) while `expected/q01.csv` was produced from the TPC-H SQL, whose cutoff `date '1998-12-01' - interval '90' day` is 1998-09-02. `data/lineitem.csv` has 19 rows on 1998-09-02, all `N\|O`, so no engine could match the golden's `count_order` 29181 from the plan | Both plans carry 10471. Measured: all four groups match the golden's sums and counts |

Not corrected, because the suite is right: TPC-H q02 and q10 goldens keep the
leading space that `data/supplier.csv` and `data/customer.csv` carry in their
comment columns, and TPC-H q21's plan is a faithful encoding of the SQL — the
zero-row result seen with DataFusion was a consumer defect
([spiceai/datafusion](https://github.com/spiceai/datafusion): same-table
correlated subqueries), not a plan defect.
