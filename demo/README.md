# Substrait Compliance Framework - Interactive Demo

This demo showcases the framework-backed workflow for testing database engines for Substrait compliance and publishing results to an interactive dashboard.

> Status: pre-release demo surface. This demo is intended for local evaluation, contributor walkthroughs, and documentation support. It should not be presented as a benchmark, certification program, or production monitoring system.

## 📋 Table of Contents

- [Quick Start (5 Minutes)](#quick-start-5-minutes)
- [Demo Overview](#demo-overview)
- [Dashboard Features](#dashboard-features)
- [Demo Components](#demo-components)
- [Usage Scenarios](#usage-scenarios)
- [Mock Engine Characteristics](#mock-engine-characteristics)
- [Function Tests](#function-tests)
- [Customization](#customization)
- [Troubleshooting](#troubleshooting)
- [Next Steps](#next-steps)

---

## Support Boundary

This demo is intended for:
- local walkthroughs of the framework-backed execution flow
- deterministic sample report generation
- dashboard UX evaluation
- contributor documentation and experimentation

This demo is **not** intended to serve as:
- an official certification result for any engine
- a substitute for production observability or SLO monitoring
- a statistically rigorous benchmark harness
- a guarantee of real engine behavior outside the deterministic demo adapters in [`demo/engines`](demo/engines)

## Quick Start (5 Minutes)

### Prerequisites

- ✅ Java 11 or higher
- ✅ Python 3.8+ (for web server)
- ✅ Web browser

### Step 1: Run the Demo

```bash
# Navigate to demo directory
cd demo

# Run the framework-backed demo script
./runner/run-simple-demo.sh
```

**Expected Output:**
```
================================================================================
Substrait Compliance Framework - Demo
================================================================================

📦 Loading TPC-H test suite...
✅ Loaded test suite: tpch
   Total test cases: 22

🔧 Testing: MockDB v1.0.0
   ✅ Passed: 22/22 (100.0%)
   💾 Report saved: output/mockdb-report.json

🔧 Testing: FastDB v2.5.0
   ✅ Passed: 22/22 (100.0%)
   💾 Report saved: output/fastdb-report.json

🔧 Testing: CloudDB v3.1.0
   ✅ Passed: 22/22 (100.0%)
   💾 Report saved: output/clouddb-report.json

🔧 Testing: DuckDB v0.10.0
   ✅ Passed: 22/22 (100.0%)
   💾 Report saved: output/duckdb-report.json

🔧 Testing: PostgreSQL v16.0
   ✅ Passed: 22/22 (100.0%)
   💾 Report saved: output/postgresql-report.json

📈 Generating leaderboard...
   💾 Leaderboard saved: output/leaderboard.json
   💾 Dashboard data updated: dashboard/data/leaderboard.json

✅ Demo completed successfully!
```

### Step 2: Verify Files Were Created

```bash
# Check that reports were generated
ls -la output/
# Should show: mockdb-report.json, fastdb-report.json, clouddb-report.json, duckdb-report.json, postgresql-report.json, leaderboard.json

# Check that dashboard data was created
ls -la dashboard/data/
# Should show: leaderboard.json
```

### Step 3: Start Web Server

⚠️ **Important:** Due to browser security (CORS), you **must** use a web server, not open files directly.

```bash
# Navigate to dashboard directory
cd dashboard

# Start Python web server
python3 -m http.server 8080
```

**If port 8080 is in use, try:**
```bash
python3 -m http.server 8081
# or
python3 -m http.server 9000
```

### Step 4: Open Dashboard

Open your web browser and go to:
```
http://localhost:8080
```

---

## Demo Overview

The demo runs five deterministic demo engines (MockDB, FastDB, CloudDB, DuckDB, and PostgreSQL) against the framework-backed TPC-H suite and publishes the generated results to a web dashboard.

All five engines in this directory are demo adapters with deterministic behavior. They are useful for validating the framework flow and dashboard rendering, but they are not evidence of certified behavior for upstream database products.

### What You'll See

#### In the Dashboard:
- **Header**: Shows the participating engines and aggregate pass-rate metrics
- **Leaderboard Table**: Rankings with 🥇🥈🥉 medals
- **Bar Chart**: Pass rate comparison
- **Doughnut Chart**: Test distribution
- **Detail Cards**: Per-engine breakdowns

#### Rankings:
```
🥇 MockDB / FastDB / CloudDB / DuckDB / PostgreSQL
🥈 Rankings depend on the generated framework-backed reports
🥉 Leaderboard data is written to dashboard JSON files after each run
```

### Demo Workflow

1. **Demo Engines Execute Tests**
   - Loads the TPC-H YAML suite and associated plan/data files
   - Executes 22 Substrait query plans through the Java compliance framework
   - Produces deterministic compliance report JSON for each engine

2. **Reports Aggregation**
   - Collects all engine reports
   - Calculates rankings
   - Generates leaderboard markdown and JSON

3. **Dashboard Display**
   - Shows rankings and statistics
   - Provides interactive visualizations
   - Enables query-level drill-down

### Production-Style Validation Before External Use

If you want to adapt this demo for a public showcase, staging environment, or downstream distribution, validate the following first:

- replace demo adapters with real engine integrations or clearly label them as simulated
- verify generated reports against the packaged framework artifacts you intend to distribute
- confirm dashboard hosting, caching, and refresh behavior in your target environment
- document data provenance for every displayed result
- define who owns support for demo failures, stale data, and broken visualizations

---

## Dashboard Features

### 1. Leaderboard View
- **Real-time Rankings**: Engines ranked by pass rate
- **Key Metrics**: Pass rate, tests passed/failed/skipped
- **Status Indicators**: Visual badges (Excellent 🟢, Good 🟡, Fair 🟠, Needs Work 🔴)
- **Interactive Rows**: Click any engine row to view detailed results

### 2. Query-Level Drill-Down ✨

Click on any engine in the leaderboard to open a detailed modal showing:
- **Summary Statistics**: Pass rate, passed/failed/skipped counts, average execution time
- **Individual Query Results**: Each TPC-H query displayed as a card with:
  - Query name (Q01-Q22)
  - Complexity badge (Simple, Medium, Complex, Very Complex)
  - Status (Passed/Failed/Skipped)
  - Execution time in milliseconds
  - Error messages (for failed tests)

**How to Use:**
1. Click any engine row in the leaderboard table
2. Modal opens with detailed query-by-query results
3. Scroll through query cards to see individual results
4. Click the X or outside the modal to close

### 3. Filter by Query Complexity ✨

Filter results to focus on specific query complexity levels:
- **All Queries** (default): Shows all 22 TPC-H queries
- **Simple**: 3 queries (Q1, Q6, Q14) - Basic aggregations
- **Medium**: 7 queries (Q3, Q4, Q10, Q12, Q13, Q16, Q19) - Joins and filters
- **Complex**: 8 queries (Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22) - Multi-table joins
- **Very Complex**: 4 queries (Q2, Q8, Q20, Q21) - Subqueries and advanced operations

**How to Use:**
1. Use the dropdown in the leaderboard section header
2. Select a complexity level
3. Click any engine to see filtered results in the modal
4. Modal shows only queries matching the selected complexity
5. Summary statistics update to reflect filtered queries only

### 4. Visual Charts
- **Pass Rate Comparison**: Bar chart comparing engine pass rates
- **Test Distribution**: Doughnut chart showing tests passed per engine
- **Color-coded Performance**: Green (95%+), Yellow (85-94%), Orange (70-84%), Red (<70%)

### 5. Auto-Refresh
- Dashboard automatically refreshes every 30 seconds
- Ensures latest test results are always displayed
- No manual refresh needed

---

## Demo Components

```
demo/
├── README.md                    # This file
├── engines/                     # Mock database engines
│   ├── MockDBEngine.java       # Mock database implementation
│   ├── FastDBEngine.java       # Fast database implementation
│   ├── CloudDBEngine.java      # Cloud database implementation
│   ├── DuckDBEngine.java       # DuckDB demo implementation
│   └── PostgreSQLEngine.java   # PostgreSQL demo implementation
├── runner/                      # Demo execution scripts
│   ├── DemoRunner.java         # Main demo runner
│   ├── EnhancedDemoRunner.java # Integrated end-to-end demo runner
│   ├── FunctionTestDemo.java   # Functional test demo runner
│   ├── run-demo.sh             # Basic demo runner script
│   ├── run-enhanced-demo.sh    # Integrated TPC-H + function dashboard demo
│   ├── run-simple-demo.sh      # Simplified demo (recommended)
│   └── run-function-tests.sh   # Functional test runner
├── dashboard/                   # Web dashboard
│   ├── index.html              # Dashboard UI
│   ├── styles.css              # Dashboard styles
│   ├── dashboard.js            # Dashboard logic
│   └── data/                   # Generated reports
│       └── .gitkeep
└── output/                      # Demo output
    └── .gitkeep
```

---

## Usage Scenarios

### Scenario 1: Quick Overview
**Goal**: Get a high-level view of engine compliance

1. Run demo: `./runner/run-simple-demo.sh`
2. Open dashboard
3. Review leaderboard rankings
4. Check pass rate chart

**Expected Result**: A deterministic leaderboard is generated from the current demo adapters and written to the dashboard data directory.

### Scenario 2: Investigate Failures
**Goal**: Understand why an engine is failing tests

1. Open dashboard
2. Click on CloudDB (lowest pass rate)
3. Scroll through query cards
4. Identify failed queries (red border)
5. Read error messages

**Expected Result**: See which specific queries failed and why in the generated demo report set.

### Scenario 3: Compare Simple vs Complex Query Support
**Goal**: Determine if an engine handles complex queries well

1. Open dashboard
2. Select "Simple" from complexity filter
3. Click FastDB → Note pass rate for simple queries
4. Select "Very Complex" from filter
5. Click FastDB again → Compare pass rate

**Expected Result**: Compare how the deterministic demo adapters present different complexity buckets in the dashboard.

### Scenario 4: Performance Analysis
**Goal**: Find the fastest engine for specific query types

1. Open dashboard
2. Select "Medium" complexity
3. Click each engine and note average execution times
4. Compare times across engines

**Expected Result**: Compare the demo adapters' reported execution-time estimates in the generated dashboard output.

---

## Demo Engine Characteristics

### MockDB (Baseline)
- **Mode**: Deterministic baseline engine
- **Behavior**: Returns deterministic output derived from loaded input tables
- **Use Case**: Baseline framework validation
- **Support Boundary**: Demo-only adapter, not a real product integration

### FastDB (High Performance)
- **Mode**: Deterministic optimized engine
- **Behavior**: Returns deterministic output with lower estimated execution times
- **Use Case**: Optimized-engine demo comparison
- **Support Boundary**: Fictional/demo adapter used to illustrate leaderboard variation

### CloudDB (Cloud-Native)
- **Mode**: Deterministic cloud-oriented engine
- **Behavior**: Returns deterministic output with higher estimated execution times
- **Use Case**: Cloud-style demo comparison
- **Support Boundary**: Fictional/demo adapter used to illustrate leaderboard variation

### DuckDB
- **Mode**: Deterministic analytical engine
- **Behavior**: Returns deterministic output derived from loaded input tables
- **Use Case**: Analytical-engine demo comparison
- **Support Boundary**: Demo adapter in this repository, not an official upstream certification result

### PostgreSQL
- **Mode**: Deterministic relational engine
- **Behavior**: Returns deterministic output derived from loaded input tables
- **Use Case**: Relational-engine demo comparison
- **Support Boundary**: Demo adapter in this repository, not an official upstream certification result

---

## Function Tests

The demo includes comprehensive function-level compliance tests across **7 major categories**:

### Test Categories (33 test files)

1. **Advanced Math Functions** (11 tests)
   - round, ceil, floor, trunc, log, log10, ln, sign, mod, radians, degrees

2. **Array/List Functions** (6 tests)
   - array_construct, array_element, array_length, array_concat, array_contains, array_position

3. **Struct/Map Functions** (5 tests)
   - struct_construct, struct_extract, map_construct, map_extract, map_keys

4. **JSON Functions** (2 tests)
   - json_extract, json_parse

5. **Conditional Functions** (2 tests)
   - case_when, if_then_else

6. **Set Operations** (3 tests)
   - union, intersect, except

7. **Geospatial Functions** (4 tests)
   - st_distance, st_contains, st_intersects, st_area

### Running Function Tests

```bash
cd demo/runner
./run-function-tests-python.sh
```

This will:
- Execute tests for all function categories
- Generate JSON reports in `demo/output/`
- Create a summary report
- Display results in a formatted table

---

## Customization

### Add More Engines

Create a new engine class:

```java
public class YourDBEngine implements ComplianceEngine {
    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo("YourDB", "1.0.0", "0.20.0");
    }
    
    @Override
    public ComplianceResult executePlan(Plan plan, Map<String, TableData> inputData) {
        // Your implementation
    }
    
    // ... other methods
}
```

Add to `DemoRunner.java`:
```java
engines.add(new YourDBEngine());
```

### Adjust Deterministic Behavior

Modify the demo engine implementations to change deterministic output shaping or execution-time estimation.

### Change Test Suite

Update `DemoRunner.java` to use different test suites:
```java
TestSuite suite = loader.load("path/to/your/suite.yaml");
```

### Modify Dashboard Appearance

Edit `demo/dashboard/styles.css`:
- `.modal-content`: Modal size and positioning
- `.query-card`: Individual query card styling
- `.complexity-*`: Complexity badge colors

---

## Troubleshooting

### Dashboard Not Loading Data

**Problem**: Dashboard shows "Failed to load leaderboard data"

**Solution**:
```bash
# Ensure demo has been run
./runner/run-simple-demo.sh

# Check if data file exists
ls -la dashboard/data/leaderboard.json

# Use web server instead of file://
cd dashboard && python3 -m http.server 8080
```

### Modal Not Opening

**Problem**: Clicking engine row does nothing

**Solution**:
1. Open browser console (F12)
2. Look for errors like "Failed to load report"
3. Verify files exist: `ls demo/output/*.json`
4. Check file names match: `mockdb-report.json`, `fastdb-report.json`, `clouddb-report.json`
5. Re-run demo: `./runner/run-simple-demo.sh`

### Filter Not Working

**Problem**: Dropdown changes but modal shows all queries

**Solution**:
1. Check browser console for JavaScript errors
2. Verify `onchange="filterByComplexity(this.value)"` in HTML
3. Hard refresh browser (Ctrl+Shift+R or Cmd+Shift+R)

### Port Already in Use

**Problem**: "Address already in use" error

**Solution**:
```bash
# Use a different port
python3 -m http.server 8081
# or
python3 -m http.server 9000
```

### Permission Denied on Scripts

**Problem**: Cannot execute run-simple-demo.sh

**Solution**:
```bash
chmod +x runner/run-simple-demo.sh
./runner/run-simple-demo.sh
```

### Charts Not Displaying

**Problem**: Empty chart areas

**Solution**:
- Check internet connection (Chart.js loads from CDN)
- Verify JavaScript is enabled
- Try different browser
- Check browser console for CDN errors

### Compilation Errors

**Problem**: Java compilation fails

**Solution**:
```bash
# Ensure SDK is built first
cd ../sdk/java
./gradlew build
cd ../../demo
```

### Reports Not Generated

**Problem**: Output directory is empty

**Solution**:
```bash
# Check output directory permissions
chmod -R 755 demo/output

# Re-run demo
./runner/run-simple-demo.sh
```

---

## Output Files

After running the framework-backed demo:

```
demo/output/
├── analytics/analytics-report.json         # Analytics summary
├── storage/                                # Private/public report storage
├── mockdb-report.json                      # MockDB TPC-H report
├── fastdb-report.json                      # FastDB TPC-H report
├── clouddb-report.json                     # CloudDB TPC-H report
├── duckdb-report.json                      # DuckDB TPC-H report
├── postgresql-report.json                  # PostgreSQL TPC-H report
└── function_tests_summary.json             # Functional test summary

demo/dashboard/data/
├── leaderboard.json                        # TPC-H dashboard leaderboard
└── summary.json                            # Cross-suite dashboard summary
```

---

## Next Steps

### For Demo Purposes
1. ✅ Run the demo: `./runner/run-simple-demo.sh`
2. ✅ View dashboard: Open `http://localhost:8080`
3. ✅ Explore reports: `cat output/*.json`
4. ✅ Present to stakeholders

### For Production Use
1. Replace demo engines with real engine integrations
2. Connect execution to actual databases or query runtimes
3. Deploy dashboard to a hosted web server
4. Set up CI/CD for automated testing
5. Extend report publication and storage workflows

### Explore Further
1. **Review Code**: Examine mock engine implementations
2. **Modify Engines**: Change pass rates and behaviors
3. **Customize Dashboard**: Update styles and charts
4. **Real Integration**: Replace mocks with actual database engines

---

## Support

For questions or issues:
- Review main project README: `../README.md`
- Check SDK documentation: `../sdk/*/README.md`
- See examples: `../examples/README.md`
- Check browser console for errors (F12)
- Verify all files generated correctly

---

## Technical Details

### Mock Data Generation
- Simulates realistic query execution times (50-500ms)
- Generates deterministic pass/fail patterns
- Creates valid TableData structures

### Report Format
```json
{
  "engineName": "MockDB",
  "engineVersion": "1.0.0",
  "substraitVersion": "0.20.0",
  "timestamp": "2026-04-15T22:50:00Z",
  "testSuiteName": "TPC-H",
  "totalTests": 22,
  "passed": 19,
  "failed": 2,
  "skipped": 1,
  "passRate": 86.4,
  "testResults": [...]
}
```

### Dashboard Technology
- Pure HTML/CSS/JavaScript (no frameworks)
- Chart.js for visualizations
- Responsive design
- Works offline (after initial load)

### Browser Requirements
- Modern browser with JavaScript enabled
- Chart.js library (loaded via CDN)
- Local web server (due to CORS restrictions)

---

**Demo Purpose**: Educational demonstration of the Substrait Compliance Framework workflow. Mock engines simulate real database behavior for testing and visualization purposes.

**Last Updated**: 2026-05-30