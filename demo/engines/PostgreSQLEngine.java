package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Demo engine: partial Substrait support — different set of plans rejected.
 *
 * Marks eight different queries as unsupported to simulate a mature SQL
 * engine whose Substrait translator does not yet cover set operations,
 * certain window functions, and deeply nested joins.
 *
 * Score: 14/22 (63.6%) — tier BASIC.
 */
public class PostgreSQLEngine extends DemoEngineBase {

    /** Queries requiring set ops, lateral joins, or recursive CTEs. */
    private static final Set<String> UNSUPPORTED =
        new HashSet<>(Arrays.asList("q07", "q09", "q13", "q16", "q18", "q19", "q20", "q21"));

    public PostgreSQLEngine() { super("../test-suites/tpch"); }

    @Override public EngineInfo getEngineInfo() {
        return new EngineInfo("PostgreSQL", "16.0", "0.20.0");
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
                Collections.singletonList("plan shape not supported by translator"));
        }
        return PlanValidationResult.supported();
    }

    @Override public void initialize() throws ComplianceException {
        System.out.println("Initializing PostgreSQL (partial — 14/22 expected, 8 unsupported)...");
        loadExpectedOutputs();
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        TableData expected = expectedFor(plan);
        if (expected == null) {
            return ComplianceResult.failure("No expected output cached for this plan", null);
        }
        return ComplianceResult.success(expected, 25L);
    }

    @Override public void cleanup() {
        System.out.println("Cleaning up PostgreSQL...");
        expectedByHash.clear();
    }
}
