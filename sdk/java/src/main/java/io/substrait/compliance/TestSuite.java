package io.substrait.compliance;

import java.util.List;

/**
 * Represents a collection of compliance test cases.
 */
public interface TestSuite {
    
    /**
     * Get the test suite name.
     */
    String getName();
    
    /**
     * Get all test cases in this suite.
     */
    List<TestCase> getTestCases();
    
    /**
     * Get test suite metadata.
     */
    TestSuiteMetadata getMetadata();
}
