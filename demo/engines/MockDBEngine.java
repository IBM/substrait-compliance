package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Mock database engine for demo purposes.
 * Simulates a baseline database with ~85% compliance.
 */
public class MockDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "MockDB";
    private static final String ENGINE_VERSION = "1.0.0";
    private static final String SUBSTRAIT_VERSION = "0.20.0";
    
    // Simulate queries that this engine struggles with
    private static final Set<String> DIFFICULT_QUERIES = Set.of(
        "tpch-q02", // Complex subquery
        "tpch-q17", // Complex aggregation
        "tpch-q21"  // Multiple joins
    );
    
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
        // Simple validation - accept most plans
        return PlanValidationResult.supported();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        
        // Simulate execution time
        long executionTime = simulateExecution();
        
        // Determine if this query should pass or fail based on random
        boolean shouldPass = Math.random() > 0.15; // ~85% pass rate
        
        if (shouldPass) {
            // Generate mock output data
            TableData output = generateMockOutput();
            return ComplianceResult.success(output, executionTime);
        } else {
            // Simulate failure
            return ComplianceResult.failure(
                "Query too complex for MockDB",
                new RuntimeException("Simulated failure"),
                executionTime
            );
        }
    }
    
    @Override
    public void initialize() throws ComplianceException {
        System.out.println("Initializing " + ENGINE_NAME + "...");
    }
    
    @Override
    public void cleanup() throws ComplianceException {
        System.out.println("Cleaning up " + ENGINE_NAME + "...");
    }
    
    // Helper methods
    
    private long simulateExecution() {
        // Simulate execution time between 100-300ms
        return 100 + (long)(Math.random() * 200);
    }
    
    private TableData generateMockOutput() {
        // Generate simple mock output
        List<String> columnNames = Arrays.asList("result_col1", "result_col2");
        List<String> columnTypes = Arrays.asList("string", "integer");
        List<List<Object>> rows = new ArrayList<>();
        
        // Add a few mock rows
        for (int i = 0; i < 5; i++) {
            rows.add(Arrays.asList("value" + i, i * 100));
        }
        
        return new TableData(columnNames, columnTypes, rows);
    }
}

// Made with Bob
