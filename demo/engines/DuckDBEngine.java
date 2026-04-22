package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.sql.*;
import java.util.*;

/**
 * DuckDB engine implementation for demo purposes.
 * Simulates DuckDB with high compliance (~92% pass rate).
 * 
 * DuckDB is known for excellent SQL and analytical query support,
 * so we simulate strong performance across most test cases.
 */
public class DuckDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "DuckDB";
    private static final String ENGINE_VERSION = "0.10.0";
    private static final String SUBSTRAIT_VERSION = "0.20.0";
    
    // Simulate queries that even DuckDB might struggle with
    private static final Set<String> DIFFICULT_QUERIES = Set.of(
        "tpch-q17", // Very complex aggregation with correlation
        "tpch-q20"  // Complex nested subqueries
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
            .addRelation("window")
            .addRelation("set")
            .build();
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        // DuckDB has excellent plan validation
        return PlanValidationResult.supported();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        
        // Simulate execution time (DuckDB is fast!)
        long executionTime = simulateExecution();
        
        // DuckDB has ~92% pass rate - very high compliance
        boolean shouldPass = Math.random() > 0.08;
        
        if (shouldPass) {
            // Generate mock output data
            TableData output = generateMockOutput();
            return ComplianceResult.success(output, executionTime);
        } else {
            // Simulate rare failure
            return ComplianceResult.failure(
                "Complex query optimization challenge",
                new RuntimeException("Simulated DuckDB edge case"),
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
        // DuckDB is fast - simulate 50-150ms execution
        return 50 + (long)(Math.random() * 100);
    }
    
    private TableData generateMockOutput() {
        // Generate simple mock output
        List<String> columnNames = Arrays.asList("result_col1", "result_col2", "result_col3");
        List<String> columnTypes = Arrays.asList("string", "integer", "double");
        List<List<Object>> rows = new ArrayList<>();
        
        // Add mock rows
        for (int i = 0; i < 8; i++) {
            rows.add(Arrays.asList("duckdb_value" + i, i * 100, i * 1.5));
        }
        
        return new TableData(columnNames, columnTypes, rows);
    }
}

// Made with Bob