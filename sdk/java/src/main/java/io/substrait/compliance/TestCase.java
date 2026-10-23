package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.List;
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
    private final List<Integer> declaredDecimalScales;
    
    public TestCase(String id, String description, Plan plan,
                   Map<String, TableData> inputData, TableData expectedOutput) {
        this(id, description, plan, inputData, expectedOutput, null);
    }

    /**
     * @param declaredDecimalScales per output column, the scale of the decimal type the plan
     *     declares (null for other columns), or null when unknown; see {@link PlanOutputTypes}
     */
    public TestCase(String id, String description, Plan plan,
                   Map<String, TableData> inputData, TableData expectedOutput,
                   List<Integer> declaredDecimalScales) {
        this.id = id;
        this.description = description;
        this.plan = plan;
        this.inputData = inputData;
        this.expectedOutput = expectedOutput;
        this.declaredDecimalScales = declaredDecimalScales;
    }
    
    public String getId() { return id; }
    public String getDescription() { return description; }
    public Plan getPlan() { return plan; }
    public Map<String, TableData> getInputData() { return inputData; }
    public TableData getExpectedOutput() { return expectedOutput; }
    public List<Integer> getDeclaredDecimalScales() { return declaredDecimalScales; }
}
