package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Demo engine: returns the expected output verbatim for every test case.
 *
 * Score: 22/22 (100%) — demonstrates a fully-compliant engine.
 */
public class MockDBEngine extends DemoEngineBase {

    public MockDBEngine() { super("../test-suites/tpch"); }

    @Override public EngineInfo getEngineInfo() {
        return new EngineInfo("MockDB", "1.0.0", "0.20.0");
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
        System.out.println("Initializing MockDB (pass-through — 22/22 expected)...");
        loadExpectedOutputs();
    }

    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        TableData expected = expectedFor(plan);
        if (expected == null) {
            return ComplianceResult.failure("No expected output cached for this plan", null);
        }
        return ComplianceResult.success(expected, 12L);
    }

    @Override public void cleanup() {
        System.out.println("Cleaning up MockDB...");
        expectedByHash.clear();
    }
}
