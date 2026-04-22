# Phase 7: Data Storage Implementation Plan

## Overview
Design and implement a comprehensive storage strategy for test results, failure data, and reproducibility information. The system must support both private (engine team) and public (community) data storage with appropriate filtering and anonymization.

## Goals
1. **Reproducibility**: Store all data needed to reproduce test failures
2. **Privacy**: Separate private (engine-specific) and public (community) data
3. **Efficiency**: Optimize storage format and size
4. **Queryability**: Enable efficient data retrieval and analysis
5. **Versioning**: Track changes over time

## Storage Architecture

### 1. Data Models

#### TestExecutionRecord
Complete record of a single test execution:
- Test metadata (ID, name, suite, version)
- Execution context (timestamp, engine info, environment)
- Input data (Substrait plan, test case)
- Output data (actual result, expected result)
- Diagnostics (validation results, comparison details)
- Failure information (category, analysis, suggestions)
- Performance metrics (execution time, resource usage)

#### EngineTestReport
Aggregated report for an engine:
- Engine information
- Test execution records
- Summary statistics
- Capability coverage
- Failure patterns

#### CommunityReport
Anonymized public report:
- Test outcomes (pass/fail counts)
- Failure categories distribution
- Performance benchmarks
- Compliance scores
- Known issues

### 2. Storage Formats

#### JSON Format
- Human-readable
- Easy to parse and query
- Good for individual records
- Supports nested structures

#### Parquet Format (Future)
- Columnar storage
- Efficient compression
- Fast analytics queries
- Good for large datasets

### 3. Storage Layers

#### Layer 1: Private Storage (Engine Teams)
**Location**: `storage/private/{engine-name}/{timestamp}/`
**Contents**:
- Full test execution records
- Complete diagnostic information
- Substrait plans (binary and text)
- Stack traces and logs
- Environment details
- Sensitive performance data

**Files**:
- `execution-records.json` - All test executions
- `failure-details.json` - Detailed failure analysis
- `plans/` - Directory of Substrait plans
- `logs/` - Execution logs
- `metadata.json` - Run metadata

#### Layer 2: Public Storage (Community)
**Location**: `storage/public/{engine-name}/{timestamp}/`
**Contents**:
- Anonymized test outcomes
- Failure categories (no sensitive details)
- Compliance scores
- Performance benchmarks (aggregated)
- Known issues

**Files**:
- `summary.json` - High-level summary
- `compliance-report.json` - Compliance scores
- `failure-categories.json` - Failure distribution
- `benchmarks.json` - Performance data

### 4. Storage Components

#### StorageManager
Main interface for storage operations:
- `savePrivateReport(EngineTestReport)` - Save full report
- `savePublicReport(CommunityReport)` - Save anonymized report
- `loadPrivateReport(engineName, timestamp)` - Load full report
- `loadPublicReport(engineName, timestamp)` - Load public report
- `listReports(engineName)` - List available reports
- `exportForReproduction(testId)` - Export reproduction package

#### DataAnonymizer
Anonymize sensitive data for public storage:
- Remove stack traces
- Aggregate performance metrics
- Remove environment details
- Sanitize error messages
- Keep failure categories and patterns

#### ReproductionPackager
Create self-contained reproduction packages:
- Test case definition
- Substrait plan
- Expected result
- Execution instructions
- Environment requirements

#### StorageConfig
Configuration for storage behavior:
- Storage root directory
- Retention policies
- Compression settings
- Anonymization rules
- Export formats

## Implementation Steps

### Step 1: Core Data Models
- [x] TestExecutionRecord
- [x] EngineTestReport
- [x] CommunityReport
- [x] StorageMetadata

### Step 2: Storage Manager
- [x] StorageManager interface and implementation
- [x] JSON serialization/deserialization
- [x] Directory structure management
- [x] File operations

### Step 3: Data Anonymization
- [x] DataAnonymizer implementation
- [x] Sensitive data filtering
- [x] Aggregation logic
- [x] Sanitization rules

### Step 4: Reproduction Support
- [x] ReproductionPackager implementation
- [x] Package format definition
- [x] Export functionality
- [x] Import/replay support

### Step 5: Configuration
- [x] StorageConfig implementation
- [x] Default configurations
- [x] Validation logic

### Step 6: Testing
- [ ] Unit tests for all components
- [ ] Integration tests
- [ ] Storage performance tests

## Data Flow

```
Test Execution
    ↓
EnhancedComplianceResult
    ↓
TestExecutionRecord (created)
    ↓
    ├─→ Private Storage (full data)
    │   └─→ storage/private/{engine}/{timestamp}/
    │
    └─→ DataAnonymizer
        ↓
        CommunityReport (anonymized)
        ↓
        Public Storage
        └─→ storage/public/{engine}/{timestamp}/
```

## Storage Schema

### TestExecutionRecord Schema
```json
{
  "testId": "string",
  "testName": "string",
  "testSuite": "string",
  "timestamp": "ISO-8601",
  "engineInfo": {
    "name": "string",
    "version": "string",
    "capabilities": {}
  },
  "input": {
    "substraitPlan": "base64-encoded",
    "testCase": {}
  },
  "output": {
    "actualResult": {},
    "expectedResult": {},
    "comparisonResult": {}
  },
  "diagnostics": {
    "validationResults": [],
    "failureCategory": "string",
    "failureAnalysis": {}
  },
  "metrics": {
    "executionTimeMs": "number",
    "memoryUsedBytes": "number"
  }
}
```

### CommunityReport Schema
```json
{
  "engineName": "string",
  "reportTimestamp": "ISO-8601",
  "summary": {
    "totalTests": "number",
    "passed": "number",
    "failed": "number",
    "skipped": "number"
  },
  "failureCategories": {
    "VALIDATION_ERROR": "number",
    "TYPE_MISMATCH": "number",
    ...
  },
  "complianceScore": {
    "overall": "number",
    "byCategory": {}
  },
  "knownIssues": []
}
```

## Usage Examples

### Saving Test Results
```java
// Create storage manager
StorageManager storage = new StorageManager(config);

// Execute tests and collect results
List<EnhancedComplianceResult> results = executeTests();

// Create test execution records
List<TestExecutionRecord> records = results.stream()
    .map(TestExecutionRecord::fromResult)
    .collect(Collectors.toList());

// Create engine report
EngineTestReport report = EngineTestReport.builder()
    .engineInfo(engineInfo)
    .executionRecords(records)
    .build();

// Save private report (full data)
storage.savePrivateReport(report);

// Create and save public report (anonymized)
CommunityReport publicReport = DataAnonymizer.anonymize(report);
storage.savePublicReport(publicReport);
```

### Loading and Analyzing Results
```java
// Load private report
EngineTestReport report = storage.loadPrivateReport("MyEngine", timestamp);

// Analyze failures
List<TestExecutionRecord> failures = report.getFailures();
Map<FailureCategory, Long> distribution = failures.stream()
    .collect(Collectors.groupingBy(
        r -> r.getDiagnostics().getFailureCategory(),
        Collectors.counting()
    ));

// Export reproduction package for specific failure
ReproductionPackage pkg = storage.exportForReproduction(testId);
pkg.saveToFile("reproduction-package.zip");
```

### Querying Public Data
```java
// Load public report
CommunityReport report = storage.loadPublicReport("MyEngine", timestamp);

// Get compliance score
double score = report.getComplianceScore().getOverall();

// Get failure distribution
Map<FailureCategory, Integer> failures = report.getFailureCategories();

// Compare with previous runs
List<CommunityReport> history = storage.listPublicReports("MyEngine");
```

## Benefits

1. **Complete Reproducibility**: All data needed to reproduce failures is stored
2. **Privacy Protection**: Sensitive data stays private, community gets useful insights
3. **Trend Analysis**: Historical data enables pattern detection
4. **Debugging Support**: Full diagnostic information for engine teams
5. **Community Transparency**: Public reports build trust and collaboration
6. **Efficient Storage**: Optimized formats and compression
7. **Easy Integration**: Simple API for storage operations

## Next Steps After Implementation

1. Integrate with test execution pipeline (Phase 9)
2. Build analytics on stored data (Phase 8)
3. Create visualization dashboards
4. Implement retention policies
5. Add cloud storage support (S3, GCS, Azure)
6. Create data export/import tools