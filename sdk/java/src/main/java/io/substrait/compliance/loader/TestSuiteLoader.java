package io.substrait.compliance.loader;

import io.substrait.compliance.TestSuite;
import io.substrait.compliance.ComplianceException;
import java.nio.file.Path;

/**
 * Interface for loading test suites from various formats.
 */
public interface TestSuiteLoader {
    
    /**
     * Load a test suite from a file.
     */
    TestSuite load(Path path) throws ComplianceException;
    
    /**
     * Check if this loader supports the given file.
     */
    boolean supports(Path path);
}
