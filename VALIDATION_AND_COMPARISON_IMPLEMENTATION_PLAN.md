# Substrait Compliance Framework Enhancement Plan
## Semantic Validation & Type-Aware Result Comparison

**Priority Focus**: Phase 2-3 (Validation and Comparison) for immediate value
**Timeline**: 4-6 weeks for core implementation
**Status**: Planning Phase

---

## Executive Summary

This plan implements semantic validation and type-aware result comparison to enhance the substrait-compliance framework. These features provide immediate value by:
1. Catching invalid plans before execution (reducing false failures)
2. Accurately comparing results with type awareness (reducing false positives/negatives)
3. Providing detailed diagnostic information for debugging

---

## Phase 1: Foundation - Enhanced Result Models (Week 1)

### 1.1 Create Enhanced Result Classes

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/EnhancedComplianceResult.java`
- `sdk/java/src/main/java/io/substrait/compliance/FailureCategory.java`
- `sdk/java/src/main/java/io/substrait/compliance/ComparisonResult.java`

**Implementation Details:**

```java
// EnhancedComplianceResult.java
public class EnhancedComplianceResult extends ComplianceResult {
    private final Object actualResult;
    private final String actualResultType;
    private final Object expectedResult;
    private final String expectedResultType;
    private final PlanValidationResult planValidation;
    private final FailureCategory failureCategory;
    private final ComparisonResult comparisonResult;
    private final Map<String, Object> diagnosticData;
    
    // Builder pattern for construction
    public static class Builder {
        // ... builder implementation
    }
}

// FailureCategory.java
public enum FailureCategory {
    PARSING_ERROR("Plan parsing failed"),
    VALIDATION_ERROR("Plan validation failed"),
    UNSUPPORTED_FEATURE("Feature not supported by engine"),
    TYPE_MISMATCH("Result type mismatch"),
    VALUE_MISMATCH("Result value mismatch"),
    RUNTIME_ERROR("Runtime execution error"),
    TIMEOUT("Execution timeout"),
    RESOURCE_EXHAUSTION("Resource limits exceeded"),
    COMPARISON_ERROR("Result comparison failed");
    
    private final String description;
    // ... implementation
}

// ComparisonResult.java
public class ComparisonResult {
    private final boolean matches;
    private final String detailedMessage;
    private final ComparisonType comparisonType;
    private final double numericDifference; // for numeric comparisons
    
    public enum ComparisonType {
        EXACT_MATCH,
        EPSILON_MATCH,
        TYPE_COERCION_MATCH,
        SPECIAL_VALUE_MATCH, // NaN, Infinity, etc.
        NO_MATCH
    }
}
```

**Tasks:**
- [ ] Create `EnhancedComplianceResult` class with all diagnostic fields
- [ ] Create `FailureCategory` enum with descriptions
- [ ] Create `ComparisonResult` class for detailed comparison info
- [ ] Add unit tests for all new classes
- [ ] Update existing `ComplianceResult` to be compatible

**Dependencies:** None
**Estimated Time:** 3 days

---

## Phase 2: Semantic Validation Layer (Week 1-2)

### 2.1 Port Plan Validator from Validation Framework

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/validator/SubstraitPlanValidator.java`
- `sdk/java/src/main/java/io/substrait/compliance/validator/ValidationResult.java`
- `sdk/java/src/main/java/io/substrait/compliance/validator/ValidationLevel.java`

**Implementation Details:**

```java
// SubstraitPlanValidator.java
public class SubstraitPlanValidator {
    
    public ValidationResult validate(Plan plan) {
        ValidationResult result = new ValidationResult();
        
        // Structural validation
        validatePlanStructure(plan, result);
        
        // Semantic validation
        validateRelations(plan, result);
        validateTypes(plan, result);
        validateReferences(plan, result);
        
        // Extension validation
        validateExtensions(plan, result);
        
        // Best practices
        checkBestPractices(plan, result);
        
        return result;
    }
    
    private void validatePlanStructure(Plan plan, ValidationResult result) {
        // Check for required components
        if (plan.getRelationsCount() == 0) {
            result.addError(ValidationLevel.ERROR, "Plan has no relations");
        }
        
        // Check for root relations
        boolean hasRoot = hasRootRelation(plan);
        if (!hasRoot) {
            result.addWarning(ValidationLevel.WARNING, 
                "Plan has no root relation (may be incomplete)");
        }
    }
    
    private void validateTypes(Plan plan, ValidationResult result) {
        // Type consistency checks
        // - Input/output type matching
        // - Cast validity
        // - Function signature matching
    }
    
    private void validateReferences(Plan plan, ValidationResult result) {
        // Reference validity checks
        // - Field references exist
        // - Function references declared
        // - Type references valid
    }
}

// ValidationResult.java
public class ValidationResult {
    private final List<ValidationIssue> errors = new ArrayList<>();
    private final List<ValidationIssue> warnings = new ArrayList<>();
    private final List<ValidationIssue> info = new ArrayList<>();
    
    public static class ValidationIssue {
        private final ValidationLevel level;
        private final String message;
        private final String location; // e.g., "Relation 3, Filter condition"
        private final String suggestion; // How to fix
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}

// ValidationLevel.java
public enum ValidationLevel {
    ERROR,      // Blocks execution
    WARNING,    // Execution allowed but may fail
    INFO        // Informational only
}
```

**Tasks:**
- [ ] Create `SubstraitPlanValidator` class with comprehensive validation
- [ ] Implement structural validation (relations, roots, connections)
- [ ] Implement semantic validation (types, references)
- [ ] Implement extension validation (function declarations)
- [ ] Add best practices checks
- [ ] Create `ValidationResult` with detailed issue tracking
- [ ] Add unit tests with various plan scenarios
- [ ] Create integration tests with real Substrait plans

**Dependencies:** Phase 1 (for integration)
**Estimated Time:** 5 days

### 2.2 Integrate Validation into Test Execution

**Files to Modify:**
- `sdk/java/src/main/java/io/substrait/compliance/ComplianceRunner.java`

**Implementation Details:**

```java
public class ComplianceRunner {
    private final SubstraitPlanValidator validator;
    
    public ComplianceReport runTests(TestSuite suite, ComplianceEngine engine) {
        ComplianceReport report = new ComplianceReport(engine.getInfo(), suite.getName());
        
        for (TestCase testCase : suite.getTestCases()) {
            // STEP 1: Validate plan before execution
            ValidationResult validation = validator.validate(testCase.getPlan());
            
            if (!validation.isValid()) {
                // Plan is invalid - record validation failure
                TestResult result = TestResult.failed(
                    testCase.getId(),
                    "Plan validation failed: " + validation.getErrors(),
                    0
                );
                report.addTestResult(result);
                continue;
            }
            
            // Log warnings but continue
            if (validation.hasWarnings()) {
                logger.warn("Plan has warnings: {}", validation.getWarnings());
            }
            
            // STEP 2: Execute test
            EnhancedComplianceResult execResult = executeTest(testCase, engine, validation);
            
            // STEP 3: Convert to test result
            TestResult result = convertToTestResult(execResult);
            report.addTestResult(result);
        }
        
        return report;
    }
}
```

**Tasks:**
- [ ] Add validation step before test execution
- [ ] Handle validation failures appropriately
- [ ] Log validation warnings
- [ ] Pass validation results to enhanced result
- [ ] Update test result conversion logic
- [ ] Add integration tests

**Dependencies:** Phase 2.1
**Estimated Time:** 2 days

---

## Phase 3: Type-Aware Result Comparison (Week 2-3)

### 3.1 Create Result Comparator Framework

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/comparison/ResultComparator.java`
- `sdk/java/src/main/java/io/substrait/compliance/comparison/TypeAwareComparator.java`
- `sdk/java/src/main/java/io/substrait/compliance/comparison/SpecialValueHandler.java`
- `sdk/java/src/main/java/io/substrait/compliance/comparison/ComparisonStrategy.java`

**Implementation Details:**

```java
// ResultComparator.java
public class ResultComparator {
    private final Map<String, TypeAwareComparator> comparators;
    private final SpecialValueHandler specialValueHandler;
    
    public ComparisonResult compare(
            Object expected, 
            String expectedType,
            Object actual, 
            String actualType) {
        
        // Handle null cases
        if (expected == null && actual == null) {
            return ComparisonResult.exactMatch();
        }
        if (expected == null || actual == null) {
            return ComparisonResult.noMatch("Null mismatch");
        }
        
        // Handle special values (NaN, Infinity, UNDEFINED, ERROR)
        if (specialValueHandler.isSpecialValue(expected) || 
            specialValueHandler.isSpecialValue(actual)) {
            return specialValueHandler.compare(expected, actual);
        }
        
        // Type-aware comparison
        TypeAwareComparator comparator = getComparator(expectedType, actualType);
        return comparator.compare(expected, expectedType, actual, actualType);
    }
    
    private TypeAwareComparator getComparator(String expectedType, String actualType) {
        // Try exact type match first
        TypeAwareComparator comparator = comparators.get(expectedType);
        if (comparator != null) {
            return comparator;
        }
        
        // Try type family match (e.g., all integers)
        String typeFamily = getTypeFamily(expectedType);
        comparator = comparators.get(typeFamily);
        if (comparator != null) {
            return comparator;
        }
        
        // Fall back to string comparison
        return comparators.get("default");
    }
}

// TypeAwareComparator.java (interface)
public interface TypeAwareComparator {
    ComparisonResult compare(Object expected, String expectedType, 
                            Object actual, String actualType);
    boolean supports(String type);
}

// Implementations:
public class IntegerComparator implements TypeAwareComparator {
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        long expectedValue = toLong(expected);
        long actualValue = toLong(actual);
        
        if (expectedValue == actualValue) {
            return ComparisonResult.exactMatch();
        }
        
        return ComparisonResult.noMatch(
            String.format("Expected %d but got %d", expectedValue, actualValue)
        );
    }
}

public class FloatingPointComparator implements TypeAwareComparator {
    private static final double DEFAULT_EPSILON = 1e-9;
    private final double epsilon;
    
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        double expectedValue = toDouble(expected);
        double actualValue = toDouble(actual);
        
        // Handle infinity
        if (Double.isInfinite(expectedValue) || Double.isInfinite(actualValue)) {
            if (expectedValue == actualValue) {
                return ComparisonResult.specialValueMatch("Infinity");
            }
            return ComparisonResult.noMatch("Infinity mismatch");
        }
        
        // Handle NaN
        if (Double.isNaN(expectedValue) && Double.isNaN(actualValue)) {
            return ComparisonResult.specialValueMatch("NaN");
        }
        
        // Epsilon comparison
        double diff = Math.abs(expectedValue - actualValue);
        if (diff < epsilon) {
            return ComparisonResult.epsilonMatch(diff);
        }
        
        return ComparisonResult.noMatch(
            String.format("Expected %.10f but got %.10f (diff: %.10e)", 
                expectedValue, actualValue, diff)
        );
    }
}

public class BooleanComparator implements TypeAwareComparator {
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        boolean expectedValue = toBoolean(expected);
        boolean actualValue = toBoolean(actual);
        
        if (expectedValue == actualValue) {
            return ComparisonResult.exactMatch();
        }
        
        return ComparisonResult.noMatch(
            String.format("Expected %b but got %b", expectedValue, actualValue)
        );
    }
}

public class StringComparator implements TypeAwareComparator {
    @Override
    public ComparisonResult compare(Object expected, String expectedType,
                                   Object actual, String actualType) {
        String expectedValue = toString(expected);
        String actualValue = toString(actual);
        
        if (expectedValue.equals(actualValue)) {
            return ComparisonResult.exactMatch();
        }
        
        return ComparisonResult.noMatch(
            String.format("Expected '%s' but got '%s'", expectedValue, actualValue)
        );
    }
}

// SpecialValueHandler.java
public class SpecialValueHandler {
    private static final Set<String> SPECIAL_VALUES = Set.of(
        "<!ERROR>", "<!UNDEFINED>", "<!NAN>", "inf", "-inf", 
        "Infinity", "-Infinity", "NaN"
    );
    
    public boolean isSpecialValue(Object value) {
        if (value == null) return false;
        String str = value.toString().trim();
        return SPECIAL_VALUES.contains(str) || 
               SPECIAL_VALUES.contains(str.toLowerCase());
    }
    
    public ComparisonResult compare(Object expected, Object actual) {
        String expectedStr = normalize(expected);
        String actualStr = normalize(actual);
        
        // ERROR handling
        if (isError(expectedStr)) {
            return isError(actualStr) ? 
                ComparisonResult.specialValueMatch("ERROR") :
                ComparisonResult.noMatch("Expected error but got value");
        }
        
        // UNDEFINED handling (matches anything)
        if (isUndefined(expectedStr)) {
            return ComparisonResult.specialValueMatch("UNDEFINED");
        }
        
        // NaN handling
        if (isNaN(expectedStr) && isNaN(actualStr)) {
            return ComparisonResult.specialValueMatch("NaN");
        }
        
        // Infinity handling
        if (isInfinity(expectedStr) && isInfinity(actualStr)) {
            return ComparisonResult.specialValueMatch("Infinity");
        }
        
        return ComparisonResult.noMatch("Special value mismatch");
    }
    
    private boolean isError(String value) {
        return value.equals("<!ERROR>");
    }
    
    private boolean isUndefined(String value) {
        return value.equals("<!UNDEFINED>");
    }
    
    private boolean isNaN(String value) {
        return value.equals("<!NAN>") || value.equalsIgnoreCase("nan");
    }
    
    private boolean isInfinity(String value) {
        return value.equalsIgnoreCase("inf") || 
               value.equalsIgnoreCase("infinity") ||
               value.equalsIgnoreCase("-inf") ||
               value.equalsIgnoreCase("-infinity");
    }
}
```

**Tasks:**
- [ ] Create `ResultComparator` main class
- [ ] Implement `TypeAwareComparator` interface
- [ ] Create comparators for: integers, floats, booleans, strings, decimals, dates, timestamps
- [ ] Implement `SpecialValueHandler` for NaN, Infinity, ERROR, UNDEFINED
- [ ] Add configurable epsilon for floating-point comparison
- [ ] Create comprehensive unit tests for each comparator
- [ ] Test edge cases (overflow, underflow, precision loss)

**Dependencies:** Phase 1
**Estimated Time:** 5 days

### 3.2 Integrate Comparison into Test Execution

**Files to Modify:**
- `sdk/java/src/main/java/io/substrait/compliance/ComplianceRunner.java`

**Implementation Details:**

```java
public class ComplianceRunner {
    private final ResultComparator comparator;
    
    private EnhancedComplianceResult executeTest(
            TestCase testCase, 
            ComplianceEngine engine,
            ValidationResult validation) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Execute the test
            ComplianceResult execResult = engine.execute(testCase.getPlan());
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (!execResult.isSuccess()) {
                // Execution failed
                return EnhancedComplianceResult.builder()
                    .success(false)
                    .failureCategory(FailureCategory.RUNTIME_ERROR)
                    .errorMessage(execResult.getErrorMessage())
                    .exception(execResult.getException())
                    .executionTimeMs(executionTime)
                    .planValidation(validation)
                    .build();
            }
            
            // Compare results
            ComparisonResult comparison = comparator.compare(
                testCase.getExpectedResult(),
                testCase.getExpectedResultType(),
                execResult.getOutputData(),
                detectResultType(execResult.getOutputData())
            );
            
            if (!comparison.matches()) {
                // Result mismatch
                return EnhancedComplianceResult.builder()
                    .success(false)
                    .failureCategory(determineFailureCategory(comparison))
                    .expectedResult(testCase.getExpectedResult())
                    .expectedResultType(testCase.getExpectedResultType())
                    .actualResult(execResult.getOutputData())
                    .actualResultType(detectResultType(execResult.getOutputData()))
                    .comparisonResult(comparison)
                    .executionTimeMs(executionTime)
                    .planValidation(validation)
                    .build();
            }
            
            // Success!
            return EnhancedComplianceResult.builder()
                .success(true)
                .expectedResult(testCase.getExpectedResult())
                .actualResult(execResult.getOutputData())
                .comparisonResult(comparison)
                .executionTimeMs(executionTime)
                .planValidation(validation)
                .build();
                
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return EnhancedComplianceResult.builder()
                .success(false)
                .failureCategory(FailureCategory.RUNTIME_ERROR)
                .errorMessage(e.getMessage())
                .exception(e)
                .executionTimeMs(executionTime)
                .planValidation(validation)
                .build();
        }
    }
    
    private FailureCategory determineFailureCategory(ComparisonResult comparison) {
        switch (comparison.getComparisonType()) {
            case NO_MATCH:
                // Check if it's a type mismatch or value mismatch
                return comparison.getDetailedMessage().contains("type") ?
                    FailureCategory.TYPE_MISMATCH :
                    FailureCategory.VALUE_MISMATCH;
            default:
                return FailureCategory.COMPARISON_ERROR;
        }
    }
}
```

**Tasks:**
- [ ] Integrate result comparison into test execution
- [ ] Add result type detection logic
- [ ] Implement failure category determination
- [ ] Update result building with comparison details
- [ ] Add logging for comparison details
- [ ] Create integration tests

**Dependencies:** Phase 3.1
**Estimated Time:** 3 days

---

## Phase 4: Testing & Validation (Week 3-4)

### 4.1 Unit Tests

**Files to Create:**
- `sdk/java/src/test/java/io/substrait/compliance/validator/SubstraitPlanValidatorTest.java`
- `sdk/java/src/test/java/io/substrait/compliance/comparison/ResultComparatorTest.java`
- `sdk/java/src/test/java/io/substrait/compliance/comparison/IntegerComparatorTest.java`
- `sdk/java/src/test/java/io/substrait/compliance/comparison/FloatingPointComparatorTest.java`
- `sdk/java/src/test/java/io/substrait/compliance/comparison/SpecialValueHandlerTest.java`

**Test Coverage:**
- [ ] Plan validator with valid plans
- [ ] Plan validator with invalid plans (missing relations, bad references)
- [ ] Plan validator with warnings (best practices)
- [ ] Integer comparison (exact matches, mismatches)
- [ ] Floating-point comparison (epsilon matches, infinity, NaN)
- [ ] Boolean comparison
- [ ] String comparison
- [ ] Special value handling (ERROR, UNDEFINED, NaN, Infinity)
- [ ] Type coercion scenarios
- [ ] Edge cases (null, empty, overflow)

**Estimated Time:** 3 days

### 4.2 Integration Tests

**Files to Create:**
- `sdk/java/src/test/java/io/substrait/compliance/ValidationIntegrationTest.java`
- `sdk/java/src/test/java/io/substrait/compliance/ComparisonIntegrationTest.java`

**Test Scenarios:**
- [ ] End-to-end test with validation and comparison
- [ ] Test with real Substrait plans from test-suites
- [ ] Test with various engines (mock implementations)
- [ ] Test failure scenarios and categorization
- [ ] Performance testing with large test suites

**Estimated Time:** 2 days

### 4.3 Documentation

**Files to Create/Update:**
- `docs/VALIDATION_GUIDE.md`
- `docs/COMPARISON_GUIDE.md`
- `docs/FAILURE_CATEGORIES.md`
- `README.md` (update with new features)

**Content:**
- [ ] How validation works
- [ ] How to interpret validation results
- [ ] Comparison strategies and type handling
- [ ] Special value handling documentation
- [ ] Failure category descriptions
- [ ] Examples and best practices
- [ ] Migration guide from old to new API

**Estimated Time:** 2 days

---

## Phase 5: Remaining Enhancements (Week 4-6)

### 5.1 Engine Capability Declaration System

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/capabilities/EngineCapabilities.java`
- `sdk/java/src/main/java/io/substrait/compliance/capabilities/CapabilityDescriptor.java`
- `sdk/java/src/main/java/io/substrait/compliance/capabilities/CapabilityValidator.java`

**Implementation:**
- [ ] Port engine descriptor model from validation framework
- [ ] Create capability declaration format (JSON/YAML)
- [ ] Implement capability checking before test execution
- [ ] Add capability-based test filtering
- [ ] Create capability validation

**Estimated Time:** 5 days

### 5.2 Failure Categorization System

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/analysis/FailureAnalyzer.java`
- `sdk/java/src/main/java/io/substrait/compliance/analysis/FailureReport.java`

**Implementation:**
- [ ] Implement automatic failure categorization
- [ ] Add root cause analysis
- [ ] Generate fix suggestions
- [ ] Track known issues
- [ ] Create failure pattern detection

**Estimated Time:** 4 days

### 5.3 Data Storage Strategy

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/storage/ResultStorage.java`
- `sdk/java/src/main/java/io/substrait/compliance/storage/PrivateDataStore.java`
- `sdk/java/src/main/java/io/substrait/compliance/storage/PublicDataStore.java`

**Implementation:**
- [ ] Design storage schema for detailed results
- [ ] Implement private data storage (full diagnostics)
- [ ] Implement public data storage (anonymized)
- [ ] Add reproducibility data capture
- [ ] Create data export/import utilities

**Estimated Time:** 5 days

### 5.4 Failure Pattern Analysis

**Files to Create:**
- `sdk/java/src/main/java/io/substrait/compliance/analytics/PatternAnalyzer.java`
- `sdk/java/src/main/java/io/substrait/compliance/analytics/TrendAnalyzer.java`

**Implementation:**
- [ ] Implement failure pattern detection
- [ ] Add trend analysis over time
- [ ] Create common issue identification
- [ ] Generate analytics reports
- [ ] Build dashboard integration

**Estimated Time:** 4 days

---

## Success Criteria

### Phase 2-3 (Immediate Value)
- ✅ Plan validation catches 90%+ of invalid plans before execution
- ✅ Type-aware comparison reduces false positives by 80%+
- ✅ Special value handling (NaN, Infinity) works correctly
- ✅ Detailed diagnostic information available for all failures
- ✅ Performance impact < 10% on test execution time

### Overall Project
- ✅ All 7 enhancement areas implemented
- ✅ Comprehensive test coverage (>85%)
- ✅ Documentation complete and reviewed
- ✅ Successfully tested with real engines
- ✅ Community feedback incorporated

---

## Risk Mitigation

### Technical Risks
1. **Performance Impact**: Validation and comparison add overhead
   - *Mitigation*: Optimize hot paths, add caching, make validation optional
   
2. **Type System Complexity**: Substrait type system is complex
   - *Mitigation*: Start with common types, expand incrementally
   
3. **Backward Compatibility**: Changes may break existing code
   - *Mitigation*: Maintain old API, provide migration path

### Project Risks
1. **Scope Creep**: Additional features requested during implementation
   - *Mitigation*: Stick to plan, document future enhancements separately
   
2. **Resource Availability**: Team members may have other priorities
   - *Mitigation*: Prioritize Phase 2-3, defer others if needed

---

## Next Steps

1. **Review and approve this plan** with stakeholders
2. **Set up development environment** and branch
3. **Begin Phase 1** (Enhanced Result Models)
4. **Daily standups** to track progress
5. **Weekly demos** to show incremental progress

---

## Appendix: File Structure

```
sdk/java/src/main/java/io/substrait/compliance/
├── EnhancedComplianceResult.java          [Phase 1]
├── FailureCategory.java                   [Phase 1]
├── ComparisonResult.java                  [Phase 1]
├── validator/
│   ├── SubstraitPlanValidator.java        [Phase 2]
│   ├── ValidationResult.java              [Phase 2]
│   └── ValidationLevel.java               [Phase 2]
├── comparison/
│   ├── ResultComparator.java              [Phase 3]
│   ├── TypeAwareComparator.java           [Phase 3]
│   ├── IntegerComparator.java             [Phase 3]
│   ├── FloatingPointComparator.java       [Phase 3]
│   ├── BooleanComparator.java             [Phase 3]
│   ├── StringComparator.java              [Phase 3]
│   ├── SpecialValueHandler.java           [Phase 3]
│   └── ComparisonStrategy.java            [Phase 3]
├── capabilities/                          [Phase 5]
│   ├── EngineCapabilities.java
│   ├── CapabilityDescriptor.java
│   └── CapabilityValidator.java
├── analysis/                              [Phase 5]
│   ├── FailureAnalyzer.java
│   └── FailureReport.java
├── storage/                               [Phase 5]
│   ├── ResultStorage.java
│   ├── PrivateDataStore.java
│   └── PublicDataStore.java
└── analytics/                             [Phase 5]
    ├── PatternAnalyzer.java
    └── TrendAnalyzer.java
```

---

**Document Version**: 1.0  
**Last Updated**: 2026-04-22  
**Author**: Bob (Planning Mode)  
**Status**: Ready for Review