# Integration Guide - Substrait Compliance Framework

## Overview
This guide explains how to integrate all phases of the enhanced Substrait Compliance Framework into your testing workflow.

## Quick Start

### 1. Run the Enhanced Demo
```bash
cd demo
./runner/run-enhanced-demo.sh
```

This demonstrates all 10 phases working together:
- ✅ Validation of Substrait plans
- ✅ Type-aware result comparison
- ✅ Failure analysis and categorization
- ✅ Private and public storage
- ✅ Analytics and insights generation

### 2. Build the SDK
```bash
cd sdk/java
./gradlew build
```

### 3. Run Tests
```bash
cd sdk/java
./gradlew test
```

## Demo Output Structure

After running the enhanced demo, you'll find:

```
demo/
├── output/
│   ├── storage/
│   │   ├── private/          # Full data for engine teams
│   │   │   ├── mockdb/
│   │   │   ├── fastdb/
│   │   │   └── clouddb/
│   │   └── public/           # Anonymized data for community
│   │       ├── mockdb/
│   │       ├── fastdb/
│   │       └── clouddb/
│   └── analytics/
│       └── analytics-report.json
└── dashboard/
    └── data/
        └── leaderboard.json
```

## Integration with Your Engine

### Step 1: Create Engine Adapter

```java
public class MyEngineAdapter {
    public Object executeQuery(byte[] substraitPlan) {
        // Your engine-specific execution logic
        return result;
    }
}
```

### Step 2: Integrate Validation

```java
import io.substrait.compliance.validator.SubstraitPlanValidator;
import io.substrait.compliance.validator.ValidationResult;

SubstraitPlanValidator validator = new SubstraitPlanValidator();
ValidationResult validation = validator.validate(plan);

if (!validation.isValid()) {
    System.out.println("Validation errors:");
    validation.getErrors().forEach(System.out::println);
}
```

### Step 3: Use Type-Aware Comparison

```java
import io.substrait.compliance.comparison.TestResultComparator;
import io.substrait.compliance.comparison.ComparisonResult;

TestResultComparator comparator = new TestResultComparator();
ComparisonResult comparison = comparator.compare(actualResult, expectedResult);

if (!comparison.isMatch()) {
    System.out.println("Mismatch: " + comparison.getMessage());
}
```

### Step 4: Analyze Failures

```java
import io.substrait.compliance.analysis.FailureAnalyzer;
import io.substrait.compliance.analysis.FailureReport;

FailureAnalyzer analyzer = new FailureAnalyzer();
FailureReport report = analyzer.analyze(result);

System.out.println("Category: " + report.getCategory());
System.out.println("Suggestions:");
report.getSuggestions().forEach(System.out::println);
```

### Step 5: Store Results

```java
import io.substrait.compliance.storage.*;

// Create storage manager
StorageManager storage = new StorageManager();

// Create test execution record
TestExecutionRecord record = TestExecutionRecord.fromResult(
    enhancedResult,
    testId,
    testName,
    testSuite,
    engineContext,
    substraitPlan,
    testCase
);

// Create engine report
EngineTestReport report = EngineTestReport.builder()
    .engineName("MyEngine")
    .engineVersion("1.0.0")
    .reportTimestamp(Instant.now())
    .addExecutionRecord(record)
    .build();

// Save private report (full data)
storage.savePrivateReport(report);

// Create and save public report (anonymized)
CommunityReport publicReport = DataAnonymizer.anonymize(report);
storage.savePublicReport(publicReport);
```

### Step 6: Generate Analytics

```java
import io.substrait.compliance.analytics.*;

// Load historical reports
List<EngineTestReport> reports = loadReports("MyEngine");

// Generate analytics
AnalyticsEngine analytics = new AnalyticsEngine();
AnalyticsReport analyticsReport = analytics.analyze(reports);

// Display insights
System.out.println("Key Findings:");
analyticsReport.getKeyFindings().forEach(System.out::println);

System.out.println("\nRecommendations:");
analyticsReport.getRecommendations().forEach(System.out::println);
```

## Complete Integration Example

```java
public class IntegratedComplianceRunner {
    private final SubstraitPlanValidator validator;
    private final TestResultComparator comparator;
    private final FailureAnalyzer failureAnalyzer;
    private final StorageManager storage;
    private final MyEngineAdapter engine;
    
    public void runComplianceTests(List<TestCase> testCases) {
        List<TestExecutionRecord> records = new ArrayList<>();
        
        for (TestCase testCase : testCases) {
            // 1. Validate plan
            ValidationResult validation = validator.validate(testCase.getPlan());
            
            // 2. Execute test
            Object actualResult = engine.executeQuery(testCase.getPlan());
            
            // 3. Compare results
            ComparisonResult comparison = comparator.compare(
                actualResult, 
                testCase.getExpectedResult()
            );
            
            // 4. Analyze failures
            FailureCategory category = null;
            if (!comparison.isMatch()) {
                FailureReport failureReport = failureAnalyzer.analyze(
                    createEnhancedResult(testCase, actualResult, comparison, validation)
                );
                category = failureReport.getCategory();
            }
            
            // 5. Create execution record
            EnhancedComplianceResult result = EnhancedComplianceResult.builder()
                .success(comparison.isMatch())
                .actualResult(actualResult)
                .expectedResult(testCase.getExpectedResult())
                .planValidation(validation)
                .comparisonResult(comparison)
                .failureCategory(category)
                .build();
            
            TestExecutionRecord record = TestExecutionRecord.fromResult(
                result, testCase.getId(), testCase.getName(),
                testCase.getSuite(), getEngineContext(),
                testCase.getPlan(), testCase
            );
            
            records.add(record);
        }
        
        // 6. Create and store report
        EngineTestReport report = EngineTestReport.builder()
            .engineName("MyEngine")
            .engineVersion("1.0.0")
            .reportTimestamp(Instant.now())
            .executionRecords(records)
            .build();
        
        storage.savePrivateReport(report);
        
        // 7. Create public report
        CommunityReport publicReport = DataAnonymizer.anonymize(report);
        storage.savePublicReport(publicReport);
        
        // 8. Generate analytics
        List<EngineTestReport> historicalReports = loadHistoricalReports();
        historicalReports.add(report);
        
        AnalyticsEngine analytics = new AnalyticsEngine();
        AnalyticsReport analyticsReport = analytics.analyze(historicalReports);
        
        // 9. Display results
        displayResults(report, analyticsReport);
    }
}
```

## Configuration

### Storage Configuration

```java
StorageConfig config = StorageConfig.builder()
    .storageRoot("./compliance-data")
    .retentionDays(90)
    .enableCompression(false)
    .enableAnonymization(true)
    .build();

StorageManager storage = new StorageManager(config);
```

### Comparison Configuration

```java
// Default epsilon for floating-point comparison
TestResultComparator comparator = new TestResultComparator();

// Custom epsilon
FloatingPointComparator floatComparator = new FloatingPointComparator(1e-9);
```

## Best Practices

### 1. Always Validate Plans
```java
ValidationResult validation = validator.validate(plan);
if (!validation.isValid()) {
    // Handle validation errors before execution
    logValidationErrors(validation);
}
```

### 2. Use Type-Aware Comparison
```java
// Don't use simple equality
// if (actual.equals(expected)) { ... }

// Use type-aware comparison
ComparisonResult comparison = comparator.compare(actual, expected);
if (comparison.isMatch()) { ... }
```

### 3. Store All Results
```java
// Always store both private and public reports
storage.savePrivateReport(report);
CommunityReport publicReport = DataAnonymizer.anonymize(report);
storage.savePublicReport(publicReport);
```

### 4. Generate Regular Analytics
```java
// Run analytics daily or after each test run
AnalyticsReport analytics = analyticsEngine.analyze(recentReports);
if (analytics.getPerformance().isHasRegression()) {
    alertTeam("Performance regression detected!");
}
```

### 5. Create Reproduction Packages
```java
// For all failures, create reproduction packages
for (TestExecutionRecord failure : report.getFailures()) {
    ReproductionPackage pkg = ReproductionPackager.createPackage(failure);
    pkg.saveToFile(Paths.get("reproductions/" + failure.getTestId() + ".zip"));
}
```

## Troubleshooting

### Build Issues

If the SDK build fails:
```bash
cd sdk/java
./gradlew clean build --refresh-dependencies
```

### Demo Issues

If the demo fails:
```bash
cd demo
rm -rf output
./runner/run-enhanced-demo.sh
```

### Missing Dependencies

Add to your `build.gradle`:
```gradle
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2'
}
```

## Next Steps

1. **Run the Demo**: `./demo/runner/run-enhanced-demo.sh`
2. **Review Output**: Check `demo/output/` for generated reports
3. **Integrate**: Follow the integration examples above
4. **Customize**: Adapt to your engine's specific needs
5. **Contribute**: Share your results with the community

## Support

For questions or issues:
1. Check the documentation in the project root
2. Review the demo code in `demo/runner/EnhancedDemoRunner.java`
3. Examine the test cases in `sdk/java/src/test/`
4. Refer to the phase implementation summaries

## Summary

The enhanced Substrait Compliance Framework provides:
- ✅ Complete validation and comparison
- ✅ Systematic failure analysis
- ✅ Comprehensive data storage
- ✅ Powerful analytics
- ✅ Privacy-protected sharing
- ✅ Full reproducibility

All phases are integrated and working together in the demo!