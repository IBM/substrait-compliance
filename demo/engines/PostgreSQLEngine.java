package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL engine implementation for demo purposes.
 * Simulates PostgreSQL with good compliance (~88% pass rate).
 * 
 * PostgreSQL is a mature, feature-rich RDBMS with strong SQL support,
 * so we simulate solid performance across most test cases.
 */
public class PostgreSQLEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "PostgreSQL";
    private static final String ENGINE_VERSION = "16.0";
    private static final String SUBSTRAIT_VERSION = "0.20.0";
    
    // Simulate queries that PostgreSQL might struggle with
    private static final Set<String> DIFFICULT_QUERIES = Set.of(
        "tpch-q13", // Complex correlated subquery
        "tpch-q17", // Complex aggregation
        "tpch-q20"  // Multiple nested subqueries
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
        // PostgreSQL has good plan validation
        return PlanValidationResult.supported();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        
        // Simulate execution time (PostgreSQL is reasonably fast)
        long executionTime = simulateExecution();
        
        // PostgreSQL has ~88% pass rate - good compliance
        boolean shouldPass = Math.random() > 0.12;
        
        if (shouldPass) {
            // Generate mock output data
            TableData output = generateMockOutput();
            return ComplianceResult.success(output, executionTime);
        } else {
            // Simulate failure
            return ComplianceResult.failure(
                "Complex query feature not fully supported",
                new RuntimeException("Simulated PostgreSQL limitation"),
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
        // PostgreSQL execution time - simulate 80-200ms
        return 80 + (long)(Math.random() * 120);
    }
    
    private TableData generateMockOutput() {
        // Generate simple mock output
        List<String> columnNames = Arrays.asList("result_col1", "result_col2", "result_col3");
        List<String> columnTypes = Arrays.asList("string", "integer", "double");
        List<List<Object>> rows = new ArrayList<>();
        
        // Add mock rows
        for (int i = 0; i < 7; i++) {
            rows.add(Arrays.asList("postgres_value" + i, i * 50, i * 2.5));
        }
        
        return new TableData(columnNames, columnTypes, rows);
    }
}

// Made with Bob