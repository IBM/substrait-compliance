package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * DuckDB engine implementation for demo purposes.
 * Provides deterministic, framework-backed behavior for the demo flow.
 */
public class DuckDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "DuckDB";
    private static final String ENGINE_VERSION = "0.10.0";
    private static final String SUBSTRAIT_VERSION = "0.20.0";
    
    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo(ENGINE_NAME, ENGINE_VERSION, SUBSTRAIT_VERSION);
    }
    
    @Override
    public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
            .addRelation("read")
            .addRelation("filter")
            .addRelation("project")
            .addRelation("aggregate")
            .addRelation("join")
            .addRelation("sort")
            .addRelation("window")
            .addRelation("set")
            .build();
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        return PlanValidationResult.supported();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        
        long executionTime = estimateExecutionTime(plan, inputData);
        TableData output = buildDeterministicOutput(inputData);
        return ComplianceResult.success(output, executionTime);
    }
    
    @Override
    public void initialize() throws ComplianceException {
        System.out.println("Initializing " + ENGINE_NAME + " (deterministic analytical mode)...");
    }
    
    @Override
    public void cleanup() throws ComplianceException {
        System.out.println("Cleaning up " + ENGINE_NAME + "...");
    }
    
    private long estimateExecutionTime(Plan plan, Map<String, TableData> inputData) {
        int relationCount = plan != null ? plan.getRelationsCount() : 0;
        int rowCount = totalRowCount(inputData);
        return 35L + (relationCount * 8L) + Math.min(rowCount / 250, 90);
    }

    private TableData buildDeterministicOutput(Map<String, TableData> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return summaryTable(0, 0);
        }

        String firstTableName = inputData.keySet().stream().sorted().findFirst().orElse(null);
        if (firstTableName == null) {
            return summaryTable(0, 0);
        }

        TableData source = inputData.get(firstTableName);
        if (source == null) {
            return summaryTable(0, 0);
        }

        return new TableData(
            new ArrayList<>(source.getColumnNames()),
            new ArrayList<>(source.getColumnTypes()),
            new ArrayList<>(source.getRows())
        );
    }

    private TableData summaryTable(int columnCount, int rowCount) {
        return new TableData(
            Arrays.asList("column_count", "row_count"),
            Arrays.asList("integer", "integer"),
            Collections.singletonList(Arrays.asList(columnCount, rowCount))
        );
    }

    private int totalRowCount(Map<String, TableData> inputData) {
        if (inputData == null) {
            return 0;
        }

        int total = 0;
        for (TableData table : inputData.values()) {
            if (table != null && table.getRows() != null) {
                total += table.getRows().size();
            }
        }
        return total;
    }
}

// Made with Bob