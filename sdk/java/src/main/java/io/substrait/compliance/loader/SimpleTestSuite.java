package io.substrait.compliance.loader;

import io.substrait.compliance.TestCase;
import io.substrait.compliance.TestSuite;
import io.substrait.compliance.TestSuiteMetadata;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple implementation of TestSuite.
 */
public class SimpleTestSuite implements TestSuite {
    
    private final String name;
    private final List<TestCase> testCases;
    private final TestSuiteMetadata metadata;
    
    public SimpleTestSuite(String name, List<TestCase> testCases, TestSuiteMetadata metadata) {
        this.name = name;
        this.testCases = new ArrayList<>(testCases);
        this.metadata = metadata;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public List<TestCase> getTestCases() {
        return new ArrayList<>(testCases);
    }
    
    @Override
    public TestSuiteMetadata getMetadata() {
        return metadata;
    }
}
