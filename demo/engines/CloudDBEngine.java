package io.substrait.demo.engines;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.util.*;

/**
 * Mock cloud-native database engine for demo purposes.
 * Simulates a cloud database with ~78% compliance (good for simple queries).
 */
public class CloudDBEngine implements ComplianceEngine {
    
    private static final String ENGINE_NAME = "CloudDB";
    private static final String ENGINE_VERSION = "3.1.0";
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
        
        // Slightly slower due to network latency simulation
        long executionTime = simulateExecution();
        
        // Lower pass rate ~78%
        boolean shouldPass = Math.random() > 0.22;
        
        if (shouldPass) {
            TableData output = generateMockOutput();
            return ComplianceResult.success(output, executionTime);
        } else {
            return ComplianceResult.failure(
                "Complex analytical query not optimized for cloud execution",
                new RuntimeException("Simulated failure"),
                executionTime
            );
        }
    }
    
    @Override
    public void initialize() throws ComplianceException {
        System.out.println("Initializing " + ENGINE_NAME + " (cloud connection)...");
    }
    
    @Override
    public void cleanup() throws ComplianceException {
        System.out.println("Cleaning up " + ENGINE_NAME + "...");
    }
    
    private long simulateExecution() {
        // Slower due to network: 150-400ms
        return 150 + (long)(Math.random() * 250);
    }
    
    private TableData generateMockOutput() {
        List<String> columnNames = Arrays.asList("result_col1", "result_col2");
        List<String> columnTypes = Arrays.asList("string", "integer");
        List<List<Object>> rows = new ArrayList<>();
        
        for (int i = 0; i < 8; i++) {
            rows.add(Arrays.asList("cloud_value" + i, i * 50));
        }
        
        return new TableData(columnNames, columnTypes, rows);
    }
}

// Made with Bob
