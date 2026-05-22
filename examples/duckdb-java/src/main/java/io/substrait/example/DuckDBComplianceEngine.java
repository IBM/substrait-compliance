package io.substrait.example;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.sql.*;
import java.util.*;

/**
 * Example DuckDB engine implementation for Substrait compliance testing.
 *
 * This demonstrates how to integrate DuckDB with the compliance framework.
 */
public class DuckDBComplianceEngine implements ComplianceEngine {
    
    private final Connection connection;
    
    public DuckDBComplianceEngine() throws SQLException {
        // Initialize DuckDB in-memory database
        this.connection = DriverManager.getConnection("jdbc:duckdb:");
    }
    
    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo(
            "DuckDB",
            "0.10.0",
            "0.80.0"  // Substrait version
        );
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
            .addFunction("add")
            .addFunction("subtract")
            .addFunction("multiply")
            .addFunction("divide")
            .addFunction("sum")
            .addFunction("count")
            .addFunction("avg")
            .supportsExtensions(true)
            .build();
    }
    
    @Override
    public ComplianceResult executePlan(
        Plan plan,
        Map<String, TableData> inputData
    ) throws ComplianceException {
        long startTime = System.currentTimeMillis();
        try {
            // 1. Load input data into DuckDB
            loadInputData(inputData);
            
            // 2. Execute Substrait plan
            // Note: DuckDB's Substrait support would be used here
            // For this example, we'll simulate execution
            TableData output = executeSubstraitPlan(plan);
            
            long executionTime = System.currentTimeMillis() - startTime;
            return ComplianceResult.success(output, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ComplianceResult.failure(e.getMessage(), e, executionTime);
        }
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        try {
            // Validate Substrait plan structure
            // In real implementation, would use DuckDB's plan validator
            boolean isValid = plan != null && plan.getRelationsCount() > 0;
            
            if (isValid) {
                return PlanValidationResult.supported();
            } else {
                return PlanValidationResult.unsupported(
                    Collections.singletonList("Plan is empty or invalid")
                );
            }
            
        } catch (Exception e) {
            return PlanValidationResult.unsupported(
                Collections.singletonList("Validation error: " + e.getMessage())
            );
        }
    }
    
    private void loadInputData(Map<String, TableData> inputData) throws SQLException {
        for (Map.Entry<String, TableData> entry : inputData.entrySet()) {
            String tableName = entry.getKey();
            TableData data = entry.getValue();
            
            // Create table
            createTable(tableName, data);
            
            // Insert data
            insertData(tableName, data);
        }
    }
    
    private void createTable(String tableName, TableData data) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");
        
        List<String> columnNames = data.getColumnNames();
        List<String> columnTypes = data.getColumnTypes();
        
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(columnNames.get(i)).append(" ");
            sql.append(mapDataType(columnTypes.get(i)));
        }
        
        sql.append(")");
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql.toString());
        }
    }
    
    private void insertData(String tableName, TableData data) throws SQLException {
        // Simplified - would use prepared statements in production
        for (List<Object> row : data.getRows()) {
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(tableName).append(" VALUES (");
            
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sql.append(", ");
                Object value = row.get(i);
                if (value == null) {
                    sql.append("NULL");
                } else if (value instanceof String) {
                    sql.append("'").append(value.toString().replace("'", "''")).append("'");
                } else {
                    sql.append(value);
                }
            }
            
            sql.append(")");
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql.toString());
            }
        }
    }
    
    private TableData executeSubstraitPlan(Plan plan) throws SQLException {
        // In real implementation, would use DuckDB's Substrait execution
        // For this example, return empty result
        return new TableData(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
    
    private String mapDataType(String typeName) {
        // Map Substrait type names to DuckDB types
        String lowerType = typeName.toLowerCase();
        if (lowerType.contains("int32") || lowerType.contains("integer")) {
            return "INTEGER";
        } else if (lowerType.contains("int64") || lowerType.contains("bigint")) {
            return "BIGINT";
        } else if (lowerType.contains("fp64") || lowerType.contains("double")) {
            return "DOUBLE";
        } else if (lowerType.contains("string") || lowerType.contains("varchar")) {
            return "VARCHAR";
        } else if (lowerType.contains("date")) {
            return "DATE";
        } else if (lowerType.contains("bool")) {
            return "BOOLEAN";
        } else if (lowerType.contains("decimal")) {
            return "DECIMAL(18,2)";
        } else {
            return "VARCHAR";
        }
    }
    
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
