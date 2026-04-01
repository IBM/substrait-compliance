package io.substrait.compliance.loader;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads test suites from YAML files.
 */
public class YamlTestSuiteLoader implements TestSuiteLoader {
    
    private final ObjectMapper mapper;
    
    public YamlTestSuiteLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }
    
    @Override
    public TestSuite load(Path path) throws ComplianceException {
        try {
            TestSuiteDefinition def = mapper.readValue(path.toFile(), TestSuiteDefinition.class);
            return createTestSuite(def, path.getParent());
        } catch (IOException e) {
            throw new ComplianceException("Failed to load test suite from " + path, e);
        }
    }
    
    @Override
    public boolean supports(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }
    
    private TestSuite createTestSuite(TestSuiteDefinition def, Path baseDir) throws ComplianceException {
        List<TestCase> testCases = new ArrayList<>();
        
        for (TestCaseDefinition tcDef : def.testCases) {
            try {
                // Load Substrait plan
                Path planPath = baseDir.resolve(tcDef.planBinary);
                byte[] planBytes = Files.readAllBytes(planPath);
                Plan plan = Plan.parseFrom(planBytes);
                
                // Load input data (simplified - would need CSV parser)
                Map<String, TableData> inputData = new HashMap<>();
                // TODO: Load actual CSV data
                
                // Load expected output (simplified)
                TableData expectedOutput = null;
                // TODO: Load expected CSV
                
                TestCase testCase = new TestCase(
                    tcDef.id,
                    tcDef.description,
                    plan,
                    inputData,
                    expectedOutput
                );
                testCases.add(testCase);
            } catch (Exception e) {
                throw new ComplianceException("Failed to load test case: " + tcDef.id, e);
            }
        }
        
        TestSuiteMetadata metadata = new TestSuiteMetadata(
            def.name,
            def.version,
            def.description
        );
        
        return new SimpleTestSuite(def.name, testCases, metadata);
    }
    
    // Inner classes for YAML mapping
    public static class TestSuiteDefinition {
        public String name;
        public String version;
        public String description;
        public List<TestCaseDefinition> testCases = new ArrayList<>();
    }
    
    public static class TestCaseDefinition {
        public String id;
        public String description;
        public String planBinary;
        public String planJson;
        public List<InputTableDefinition> inputTables = new ArrayList<>();
        public String expectedOutput;
    }
    
    public static class InputTableDefinition {
        public String name;
        public String file;
        public int rows;
    }
}
