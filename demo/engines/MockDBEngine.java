package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Deterministic baseline demo engine backed by framework-compatible behavior.
 */
public class MockDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "MockDB";
    private static final String ENGINE_VERSION = "1.0.0";
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
        System.out.println("Initializing " + ENGINE_NAME + " (deterministic baseline mode)...");
    }
    
    @Override
    public void cleanup() throws ComplianceException {
        System.out.println("Cleaning up " + ENGINE_NAME + "...");
    }
    
    // Helper methods
    
    private long estimateExecutionTime(Plan plan, Map<String, TableData> inputData) {
        int relationCount = plan != null ? plan.getRelationsCount() : 0;
        int rowCount = totalRowCount(inputData);
        return 100L + (relationCount * 15L) + (rowCount * 5L);
    }
    
    private TableData buildDeterministicOutput(Map<String, TableData> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return summaryTable(0, 0);
        }

        String primaryTableName = inputData.keySet().stream().sorted().findFirst().orElse(null);
        TableData primaryTable = primaryTableName != null ? inputData.get(primaryTableName) : null;
        if (primaryTable == null) {
            return summaryTable(0, 0);
        }

        List<String> columnNames = primaryTable.getColumnNames();
        List<String> columnTypes = primaryTable.getColumnTypes();
        List<List<Object>> rows = primaryTable.getRows();

        if (!rows.isEmpty()) {
            return new TableData(columnNames, columnTypes, rows);
        }

        return summaryTable(columnNames.size(), 0);
    }

    private TableData summaryTable(int columnCount, int rowCount) {
        return new TableData(
            Arrays.asList("column_count", "row_count"),
            Arrays.asList("integer", "integer"),
            List.of(Arrays.asList(columnCount, rowCount))
        );
    }

    private int totalRowCount(Map<String, TableData> inputData) {
        if (inputData == null) {
            return 0;
        }

        int total = 0;
        for (TableData table : inputData.values()) {
            if (table != null) {
                total += table.getRowCount();
            }
        }
        return total;
    }
}

