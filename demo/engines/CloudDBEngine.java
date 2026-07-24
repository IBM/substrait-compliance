package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Demo engine: row-dropping bug in five queries.
 *
 * Returns the expected output for 17 queries unchanged.  For q08, q11, q14,
 * q17, and q19 the last row is silently dropped — simulating an engine that
 * truncates result sets under certain plan shapes (e.g. a LIMIT pushdown bug
 * or an off-by-one in a window partition).
 *
 * Score: 17/22 (77.3%) — tier EDGE.
 */
public class CloudDBEngine extends DemoEngineBase {

    private static final Set<String> DROP_LAST_ROW =
        new HashSet<>(Arrays.asList("q08", "q11", "q14", "q17", "q19"));

    public CloudDBEngine() { super("../test-suites/tpch"); }

    @Override public EngineInfo getEngineInfo() {
        return new EngineInfo("CloudDB", "3.1.0", "0.20.0");
    }

    @Override public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
            .addRelation("read").addRelation("filter").addRelation("project")
            .addRelation("aggregate").addRelation("join").addRelation("sort")
            .build();
    }

    @Override public PlanValidationResult validatePlan(Plan plan) {
        return PlanValidationResult.supported();
    }

    @Override public void initialize() throws ComplianceException {
        System.out.println("Initializing CloudDB (row-drop bug in 5 queries — 17/22 expected)...");
        loadExpectedOutputs();
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        TableData expected = expectedFor(plan);
        if (expected == null) {
            return ComplianceResult.failure("No expected output cached for this plan", null);
        }
        String id = idFor(plan);
        if (DROP_LAST_ROW.contains(id) && expected.getRowCount() > 0) {
            expected = dropLastRow(expected);
        }
        return ComplianceResult.success(expected, 45L);
    }

    @Override public void cleanup() {
        System.out.println("Cleaning up CloudDB...");
        expectedByHash.clear();
    }

    private TableData dropLastRow(TableData original) {
        List<List<Object>> rows = original.getRows();
        return new TableData(
            original.getColumnNames(),
            original.getColumnTypes(),
            rows.subList(0, rows.size() - 1));
    }
}
