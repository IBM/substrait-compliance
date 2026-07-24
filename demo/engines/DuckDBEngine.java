package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Demo engine: partial Substrait support — complex join/subquery plans rejected.
 *
 * Marks eight queries as unsupported (VERY_COMPLEX + selected COMPLEX) to
 * simulate an engine that implements core aggregation and simple joins but
 * has not yet landed correlated subquery or advanced window support.
 *
 * Score: 14/22 (63.6%) — tier BASIC.
 */
public class DuckDBEngine extends DemoEngineBase {

    /** Queries requiring correlated subqueries or nested aggregation. */
    private static final Set<String> UNSUPPORTED =
        new HashSet<>(Arrays.asList("q02", "q08", "q11", "q15", "q17", "q20", "q21", "q22"));

    public DuckDBEngine() { super("../test-suites/tpch"); }

    @Override public EngineInfo getEngineInfo() {
        return new EngineInfo("DuckDB", "0.10.0", "0.20.0");
    }

    @Override public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
            .addRelation("read").addRelation("filter").addRelation("project")
            .addRelation("aggregate").addRelation("join").addRelation("sort")
            .addRelation("window").addRelation("set")
            .build();
    }

    @Override public PlanValidationResult validatePlan(Plan plan) {
        String id = idFor(plan);
        if (UNSUPPORTED.contains(id)) {
            return PlanValidationResult.unsupported(
                Collections.singletonList("correlated subquery not yet supported"));
        }
        return PlanValidationResult.supported();
    }

    @Override public void initialize() throws ComplianceException {
        System.out.println("Initializing DuckDB (partial — 14/22 expected, 8 unsupported)...");
        loadExpectedOutputs();
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        TableData expected = expectedFor(plan);
        if (expected == null) {
            return ComplianceResult.failure("No expected output cached for this plan", null);
        }
        return ComplianceResult.success(expected, 18L);
    }

    @Override public void cleanup() {
        System.out.println("Cleaning up DuckDB...");
        expectedByHash.clear();
    }
}
