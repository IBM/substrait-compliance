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

            String mismatch = describeMismatch(
                testCase.getExpectedOutput(),
                result.getOutputData()
            );

            if (mismatch == null) {
                return TestResult.passed(testCase.getId(), duration);
            } else {
                // Say exactly what differed so failures can be diagnosed from the report.
                return TestResult.failed(testCase.getId(),
                    "Output mismatch: " + mismatch, duration);
            }
            
        } catch (Exception e) {
            return TestResult.failed(testCase.getId(), 
                "Exception: " + e.getMessage(), 0);
        }
    }
    
    /**
     * Compare expected and actual results.
     *
     * @return null when the outputs match, otherwise a description of the first difference
     */
    String describeMismatch(TableData expected, TableData actual) {
        if (actual == null) {
            return "engine returned no output";
        }
        if (expected.getRowCount() != actual.getRowCount()) {
            return "expected " + expected.getRowCount() + " rows, got " + actual.getRowCount()
                + (actual.getRowCount() > 0 ? "; first row " + rowPreview(actual, 0) : "");
        }
        if (expected.getColumnCount() != actual.getColumnCount()) {
            return "expected " + expected.getColumnCount() + " columns " + expected.getColumnNames()
                + ", got " + actual.getColumnCount() + " " + actual.getColumnNames();
        }
        if (!columnNamesMatch(expected.getColumnNames(), actual.getColumnNames())) {
            return "expected column names " + expected.getColumnNames()
                + ", got " + actual.getColumnNames();
        }
        if (!compareColumnTypes(expected.getColumnTypes(), actual.getColumnTypes())) {
            return "expected column types " + expected.getColumnTypes()
                + ", got " + actual.getColumnTypes();
        }

        for (int row = 0; row < expected.getRowCount(); row++) {
            for (int col = 0; col < expected.getColumnCount(); col++) {
                Object expectedValue = expected.getValue(row, col);
                Object actualValue = actual.getValue(row, col);
                if (!valuesMatch(expectedValue, actualValue)) {
                    return "row " + row + ", column " + expected.getColumnNames().get(col)
                        + ": expected " + describeValue(expectedValue)
                        + ", got " + describeValue(actualValue)
                        + " (actual type " + actual.getColumnTypes().get(col) + ")";
                }
            }
        }

        return null;
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        String text = value instanceof java.math.BigDecimal
            ? ((java.math.BigDecimal) value).toPlainString()
            : String.valueOf(value);
        if (text.length() > 80) {
            text = text.substring(0, 77) + "...";
        }
        return value instanceof String ? "'" + text + "'" : text;
    }

    private static String rowPreview(TableData data, int row) {
        StringBuilder sb = new StringBuilder("(");
        for (int col = 0; col < data.getColumnCount(); col++) {
            if (col > 0) {
                sb.append(", ");
            }
            sb.append(describeValue(data.getValue(row, col)));
        }
        return sb.append(")").toString();
    }

    /**
     * Column names are compared case-insensitively: SQL identifiers are case-insensitive, and
     * plan producers commonly upper-case output names (REVENUE vs revenue).
     */
    private boolean columnNamesMatch(List<String> expectedNames, List<String> actualNames) {
        if (expectedNames == null || actualNames == null) {
            return expectedNames == actualNames;
        }
        if (expectedNames.size() != actualNames.size()) {
            return false;
        }
        for (int i = 0; i < expectedNames.size(); i++) {
            String expected = expectedNames.get(i);
            String actual = actualNames.get(i);
            if (expected == null || actual == null
                    ? expected != actual
                    : !expected.trim().equalsIgnoreCase(actual.trim())) {
                return false;
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
        // Drop type parameters such as DECIMAL(15,2), VARCHAR(25), CHAR(1).
        int paren = normalized.indexOf('(');
        if (paren > 0) {
            normalized = normalized.substring(0, paren).trim();
        }
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
            case "long":
            case "bigint":
            case "i64":
            case "int8":
            case "hugeint":
            case "int128":
            case "utinyint":
            case "usmallint":
            case "uinteger":
            case "ubigint":
            case "uhugeint":
            case "float":
            case "fp32":
            case "real":
            case "float4":
            case "double":
            case "fp64":
            case "decimal":
            case "numeric":
            case "float8":
            case "number":
                // One numeric family: reference outputs and engines legitimately differ in
                // width and representation (e.g. TPC-H money columns are DECIMAL in the plan
                // but double in the expected CSVs). Values are still compared numerically.
                return "numeric";
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

    /** Absolute tolerance near zero; relative tolerance for larger magnitudes. */
    static final double NUMERIC_ABS_TOLERANCE = 1e-9;
    static final double NUMERIC_REL_TOLERANCE = 1e-9;

    static boolean numbersClose(double expected, double actual) {
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            return Double.isNaN(expected) && Double.isNaN(actual);
        }
        if (Double.isInfinite(expected) || Double.isInfinite(actual)) {
            return expected == actual;
        }
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        return Math.abs(expected - actual)
            <= Math.max(NUMERIC_ABS_TOLERANCE, NUMERIC_REL_TOLERANCE * scale);
    }

    private boolean valuesMatch(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }

        // Both numeric — tolerance absorbs floating-point and decimal representation differences.
        if (expected instanceof Number && actual instanceof Number) {
            return numbersClose(((Number) expected).doubleValue(), ((Number) actual).doubleValue());
        }

        // One side is a Number, the other a String — try parsing the String as a number.
        // This handles engines that return typed numerics when the expected CSV column is declared
        // as string (or vice-versa).
        if (expected instanceof Number && actual instanceof String) {
            try {
                double actualValue = Double.parseDouble((String) actual);
                return numbersClose(((Number) expected).doubleValue(), actualValue);
            } catch (NumberFormatException ignored) {
                // Fall through to string comparison.
            }
        }
        if (actual instanceof Number && expected instanceof String) {
            try {
                double expectedValue = Double.parseDouble((String) expected);
                return numbersClose(expectedValue, ((Number) actual).doubleValue());
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
