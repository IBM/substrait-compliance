package io.substrait.compliance.loader;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
                
                Map<String, TableData> inputData = loadInputTables(tcDef.inputTables, baseDir);
                TableData expectedOutput = loadOptionalTable(tcDef.expectedOutput, baseDir);
                
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
    
    private Map<String, TableData> loadInputTables(List<InputTableDefinition> inputTables, Path baseDir)
            throws IOException, ComplianceException {
        Map<String, TableData> tables = new HashMap<>();
        for (InputTableDefinition inputTable : inputTables) {
            if (inputTable.name == null || inputTable.name.isBlank()) {
                throw new ComplianceException("Input table name is required");
            }
            if (inputTable.file == null || inputTable.file.isBlank()) {
                throw new ComplianceException("Input table file is required for table: " + inputTable.name);
            }
            tables.put(inputTable.name, loadCsvTable(baseDir.resolve(inputTable.file)));
        }
        return tables;
    }

    private TableData loadOptionalTable(String relativePath, Path baseDir)
            throws IOException, ComplianceException {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return loadCsvTable(baseDir.resolve(relativePath));
    }

    private TableData loadCsvTable(Path csvPath) throws IOException, ComplianceException {
        List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) {
            throw new ComplianceException("CSV file is empty: " + csvPath);
        }

        List<String> headers = parseCsvLine(lines.get(0));
        if (headers.isEmpty()) {
            throw new ComplianceException("CSV header is empty: " + csvPath);
        }

        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        for (String header : headers) {
            String trimmed = header.trim();
            if (trimmed.isEmpty()) {
                throw new ComplianceException("CSV header contains empty column name: " + csvPath);
            }

            int separatorIndex = trimmed.indexOf(':');
            if (separatorIndex >= 0) {
                columnNames.add(trimmed.substring(0, separatorIndex).trim());
                columnTypes.add(trimmed.substring(separatorIndex + 1).trim().toLowerCase(Locale.ROOT));
            } else {
                columnNames.add(trimmed);
                columnTypes.add("string");
            }
        }

        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }

            List<String> values = parseCsvLine(line);
            if (values.size() != columnNames.size()) {
                throw new ComplianceException(
                    "CSV row " + (i + 1) + " in " + csvPath + " has " + values.size()
                        + " values but expected " + columnNames.size()
                );
            }

            List<Object> row = new ArrayList<>();
            for (int col = 0; col < values.size(); col++) {
                row.add(convertValue(values.get(col), columnTypes.get(col)));
            }
            rows.add(row);
        }

        return new TableData(columnNames, columnTypes, rows);
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        values.add(current.toString().trim());
        return values.stream().map(this::stripWrappingQuotes).collect(Collectors.toList());
    }

    private String stripWrappingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private Object convertValue(String rawValue, String type) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }

        String normalizedType = type == null ? "string" : type.toLowerCase(Locale.ROOT);
        try {
            switch (normalizedType) {
                case "int":
                case "integer":
                case "i8":
                case "i16":
                case "i32":
                    return Integer.parseInt(value);
                case "long":
                case "bigint":
                case "i64":
                    return Long.parseLong(value);
                case "float":
                case "fp32":
                    return Float.parseFloat(value);
                case "double":
                case "fp64":
                case "decimal":
                    return Double.parseDouble(value);
                case "bool":
                case "boolean":
                    return Boolean.parseBoolean(value);
                default:
                    return value;
            }
        } catch (NumberFormatException e) {
            return value;
        }
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
