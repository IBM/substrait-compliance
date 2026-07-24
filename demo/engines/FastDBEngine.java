package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Demo engine: near-perfect compliance.
 *
 * Returns the expected output for all 22 queries except q01, where sum_qty
 * in row 0 is shifted by +1.0 — simulating a minor floating-point bug in
 * a high-performance aggregation path.
 *
 * Score: 21/22 (95.5%) — tier VERIFIED.
 */
public class FastDBEngine extends DemoEngineBase {

    public FastDBEngine() { super("../test-suites/tpch"); }

    @Override public EngineInfo getEngineInfo() {
        return new EngineInfo("FastDB", "2.5.0", "0.20.0");
    }

    @Override public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
            .addRelation("read").addRelation("filter").addRelation("project")
            .addRelation("aggregate").addRelation("join").addRelation("sort")
            .addRelation("window").supportsExtensions(true)
            .build();
    }

    @Override public PlanValidationResult validatePlan(Plan plan) {
        return PlanValidationResult.supported();
    }

    @Override public void initialize() throws ComplianceException {
        System.out.println("Initializing FastDB (near-perfect — 21/22 expected)...");
        loadExpectedOutputs();
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        TableData expected = expectedFor(plan);
        if (expected == null) {
            return ComplianceResult.failure("No expected output cached for this plan", null);
        }
        if ("q01".equals(idFor(plan))) {
            expected = perturbQ01SumQty(expected);
        }
        return ComplianceResult.success(expected, 8L);
    }

    @Override public void cleanup() {
        System.out.println("Cleaning up FastDB...");
        expectedByHash.clear();
    }

    /**
     * Shift sum_qty of row 0 by +1.0.  The epsilon threshold is 1e-9 so any
     * integer difference causes the comparator to report FAILED.
     */
    private TableData perturbQ01SumQty(TableData original) {
        List<String> names = original.getColumnNames();
        List<String> types = original.getColumnTypes();
        List<List<Object>> rows = original.getRows();
        int col = names.indexOf("sum_qty");

        List<List<Object>> newRows = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = new ArrayList<>(rows.get(r));
            if (r == 0 && col >= 0 && row.get(col) instanceof Number) {
                row.set(col, ((Number) row.get(col)).doubleValue() + 1.0);
            }
            newRows.add(row);
        }
        return new TableData(names, types, newRows);
    }
}
