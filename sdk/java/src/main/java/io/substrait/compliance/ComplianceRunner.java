package io.substrait.compliance;

import io.substrait.proto.Plan;
import java.util.*;

/**
 * Main test runner that executes compliance test suites against an engine.
 */
public class ComplianceRunner {
    
    private final ComplianceEngine engine;
    private final Map<String, TestSuite> testSuites;
    
    public ComplianceRunner(ComplianceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.testSuites = new HashMap<>();
    }
    
    /**
     * Register a test suite.
     */
    public void registerTestSuite(TestSuite suite) {
        testSuites.put(suite.getName(), suite);
    }
    
    /**
     * Run a specific test suite by name.
     */
    public ComplianceReport runTestSuite(String suiteName) throws ComplianceException {
        TestSuite suite = testSuites.get(suiteName);
        if (suite == null) {
            throw new ComplianceException("Test suite not found: " + suiteName);
        }
        
        return runTestSuite(suite);
    }
    
    /**
     * Run a test suite.
     */
    public ComplianceReport runTestSuite(TestSuite suite) throws ComplianceException {
        engine.initialize();
        
        try {
            ComplianceReport report = new ComplianceReport(
                engine.getEngineInfo(),
                suite.getName()
            );
            
            for (TestCase testCase : suite.getTestCases()) {
                TestResult result = runTestCase(testCase);
                report.addTestResult(result);
            }
            
            return report;
        } finally {
            engine.cleanup();
        }
    }
    
    /**
     * Run a single test case.
     */
    private TestResult runTestCase(TestCase testCase) {
        try {
            // Validate plan first
            PlanValidationResult validation = engine.validatePlan(testCase.getPlan());
            if (!validation.isSupported()) {
                return TestResult.skipped(testCase.getId(), 
                    "Plan not supported: " + validation.getIssues());
            }
            
            // Execute plan
            long startTime = System.currentTimeMillis();
            ComplianceResult result = engine.executePlan(
                testCase.getPlan(),
                testCase.getInputData()
            );
            long duration = System.currentTimeMillis() - startTime;
            
            if (!result.isSuccess()) {
                return TestResult.failed(testCase.getId(), 
                    result.getErrorMessage(), duration);
            }
            
            // Compare results against expected output.
            // If no expected output is present we cannot verify correctness —
            // return SKIPPED rather than PASSED so the result is honest.
            if (testCase.getExpectedOutput() == null) {
                return TestResult.skipped(testCase.getId(),
                    "No expected output — cannot verify correctness");
            }

            boolean matches = compareResults(
                testCase.getExpectedOutput(),
                result.getOutputData()
            );

            if (matches) {
                return TestResult.passed(testCase.getId(), duration);
            } else {
                return TestResult.failed(testCase.getId(),
                    "Output mismatch", duration);
            }
            
        } catch (Exception e) {
            return TestResult.failed(testCase.getId(), 
                "Exception: " + e.getMessage(), 0);
        }
    }
    
    /**
     * Compare expected and actual results.
     */
    private boolean compareResults(TableData expected, TableData actual) {
        if (actual == null) {
            return false;
        }
        if (expected.getRowCount() != actual.getRowCount()) {
            return false;
        }
        if (expected.getColumnCount() != actual.getColumnCount()) {
            return false;
        }
        if (!Objects.equals(expected.getColumnNames(), actual.getColumnNames())) {
            return false;
        }
        if (!compareColumnTypes(expected.getColumnTypes(), actual.getColumnTypes())) {
            return false;
        }

        for (int row = 0; row < expected.getRowCount(); row++) {
            for (int col = 0; col < expected.getColumnCount(); col++) {
                if (!valuesMatch(expected.getValue(row, col), actual.getValue(row, col))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean compareColumnTypes(List<String> expectedTypes, List<String> actualTypes) {
        if (expectedTypes == null || actualTypes == null) {
            return expectedTypes == actualTypes;
        }
        if (expectedTypes.size() != actualTypes.size()) {
            return false;
        }

        for (int i = 0; i < expectedTypes.size(); i++) {
            String expected = normalizeType(expectedTypes.get(i));
            String actual = normalizeType(actualTypes.get(i));
            if (!Objects.equals(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }

        String normalized = type.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "int":
            case "integer":
            case "i8":
            case "i16":
            case "i32":
            case "int2":
            case "int4":
            case "smallint":
            case "tinyint":
                return "integer";
            case "long":
            case "bigint":
            case "i64":
            case "int8":
                return "bigint";
            case "float":
            case "fp32":
            case "real":
            case "float4":
                return "float";
            case "double":
            case "fp64":
            case "decimal":
            case "numeric":
            case "float8":
            case "number":
                return "double";
            case "bool":
            case "boolean":
                return "boolean";
            case "varchar":
            case "char":
            case "text":
            case "string":
            case "utf8":
                return "string";
            case "date":
            case "timestamp":
            case "timestamptz":
            case "time":
                return "string";
            default:
                return normalized;
        }
    }

    private boolean valuesMatch(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }

        // Both numeric — use epsilon comparison to absorb floating-point representation differences.
        if (expected instanceof Number && actual instanceof Number) {
            double expectedValue = ((Number) expected).doubleValue();
            double actualValue = ((Number) actual).doubleValue();
            return Math.abs(expectedValue - actualValue) < 1e-9;
        }

        // One side is a Number, the other a String — try parsing the String as a number.
        // This handles engines that return typed numerics when the expected CSV column is declared
        // as string (or vice-versa).
        if (expected instanceof Number && actual instanceof String) {
            try {
                double actualValue = Double.parseDouble((String) actual);
                return Math.abs(((Number) expected).doubleValue() - actualValue) < 1e-9;
            } catch (NumberFormatException ignored) {
                // Fall through to string comparison.
            }
        }
        if (actual instanceof Number && expected instanceof String) {
            try {
                double expectedValue = Double.parseDouble((String) expected);
                return Math.abs(expectedValue - ((Number) actual).doubleValue()) < 1e-9;
            } catch (NumberFormatException ignored) {
                // Fall through to string comparison.
            }
        }

        if (expected instanceof Boolean || actual instanceof Boolean) {
            return Boolean.parseBoolean(String.valueOf(expected))
                == Boolean.parseBoolean(String.valueOf(actual));
        }

        return Objects.equals(String.valueOf(expected), String.valueOf(actual));
    }
}
