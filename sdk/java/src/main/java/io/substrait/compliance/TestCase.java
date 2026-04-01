package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.Map;

/**
 * Represents a single compliance test case.
 */
public class TestCase {
    
    private final String id;
    private final String description;
    private final Plan plan;
    private final Map<String, TableData> inputData;
    private final TableData expectedOutput;
    
    public TestCase(String id, String description, Plan plan,
                   Map<String, TableData> inputData, TableData expectedOutput) {
        this.id = id;
        this.description = description;
        this.plan = plan;
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
    }
    
    public String getId() { return id; }
    public String getDescription() { return description; }
    public Plan getPlan() { return plan; }
    public Map<String, TableData> getInputData() { return inputData; }
    public TableData getExpectedOutput() { return expectedOutput; }
}
