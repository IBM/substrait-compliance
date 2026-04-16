package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Mock high-performance database engine for demo purposes.
 * Simulates a well-optimized database with ~95% compliance.
 */
public class FastDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "FastDB";
    private static final String ENGINE_VERSION = "2.5.0";
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
            .supportsExtensions(true)
            .build();
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        return PlanValidationResult.supported();
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData)
            throws ComplianceException {
        
        // Faster execution time
        long executionTime = simulateExecution();
        
        // High pass rate ~95%
        boolean shouldPass = Math.random() > 0.05;
        
        if (shouldPass) {
            TableData output = generateMockOutput();
            return ComplianceResult.success(output, executionTime);
        } else {
            return ComplianceResult.failure(
                "Edge case not yet supported",
                new RuntimeException("Simulated failure"),
                executionTime
            );
        }
    }
    
    @Override
    public void initialize() throws ComplianceException {
        System.out.println("Initializing " + ENGINE_NAME + " (optimized mode)...");
    }
    
    @Override
    public void cleanup() throws ComplianceException {
        System.out.println("Cleaning up " + ENGINE_NAME + "...");
    }
    
    private long simulateExecution() {
        // Faster execution: 50-150ms
        return 50 + (long)(Math.random() * 100);
    }
    
    private TableData generateMockOutput() {
        List<String> columnNames = Arrays.asList("result_col1", "result_col2", "result_col3");
        List<String> columnTypes = Arrays.asList("string", "integer", "double");
        List<List<Object>> rows = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            rows.add(Arrays.asList("value" + i, i * 100, i * 1.5));
        }
        
        return new TableData(columnNames, columnTypes, rows);
    }
}

// Made with Bob
