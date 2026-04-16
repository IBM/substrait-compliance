# Substrait Compliance Framework - End-to-End Demo

This demo showcases the complete workflow of testing database engines for Substrait compliance and publishing results to an interactive dashboard.

## Demo Overview

The demo simulates three database engines (MockDB, FastDB, and CloudDB) running compliance tests against the TPC-H benchmark and publishing results to a web dashboard.

## Demo Components

```
demo/
├── README.md                    # This file
├── engines/                     # Mock database engines
│   ├── MockDBEngine.java       # Mock database implementation
│   ├── FastDBEngine.java       # Fast database implementation
│   └── CloudDBEngine.java      # Cloud database implementation
├── runner/                      # Demo execution scripts
│   ├── DemoRunner.java         # Main demo runner
│   └── run-demo.sh             # Shell script to run demo
├── dashboard/                   # Web dashboard
│   ├── index.html              # Dashboard UI
│   ├── styles.css              # Dashboard styles
│   ├── dashboard.js            # Dashboard logic
│   └── data/                   # Generated reports
│       └── .gitkeep
└── output/                      # Demo output
    └── .gitkeep
```

## Quick Start

### Prerequisites

- Java 11+
- Python 3.8+
- Web browser (for dashboard)

### Run the Demo

```bash
# From the demo directory
cd demo

# Run the complete demo
./runner/run-demo.sh
```

This will:
1. ✅ Compile mock database engines
2. ✅ Run compliance tests for 3 engines
3. ✅ Generate compliance reports (JSON)
4. ✅ Create leaderboard
5. ✅ Launch dashboard in browser

## Demo Workflow

### Step 1: Mock Engines Execute Tests

Each mock engine:
- Loads TPC-H test data (simulated)
- Executes 22 Substrait query plans
- Generates pass/fail results with realistic patterns
- Produces compliance report JSON

### Step 2: Reports Aggregation

The `generate_leaderboard.py` script:
- Collects all engine reports
- Calculates rankings
- Generates leaderboard markdown and JSON

### Step 3: Dashboard Display

The web dashboard shows:
- **Rankings Table**: Engines sorted by pass rate
- **Pass Rate Chart**: Visual comparison
- **Detailed Results**: Per-engine breakdown
- **Query Analysis**: Which queries passed/failed

## Mock Engine Characteristics

### MockDB (Baseline)
- Pass Rate: ~85%
- Strengths: Simple queries, basic aggregations
- Weaknesses: Complex joins, subqueries

### FastDB (High Performance)
- Pass Rate: ~95%
- Strengths: All query types, optimized execution
- Weaknesses: Some edge cases

### CloudDB (Cloud-Native)
- Pass Rate: ~78%
- Strengths: Scalable queries, distributed execution
- Weaknesses: Complex analytical queries

## Dashboard Features

### Interactive Elements
- 📊 **Bar Chart**: Visual pass rate comparison
- 📈 **Trend Lines**: Historical performance (simulated)
- 🔍 **Query Details**: Click engine to see query-level results
- 🎨 **Color Coding**: Green (≥95%), Yellow (85-94%), Orange (70-84%), Red (<70%)

### Real-Time Updates
- Auto-refresh every 30 seconds (configurable)
- Live status indicators
- Timestamp of last update

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

### Adjust Pass Rates

Modify the mock engine implementations to simulate different compliance levels.

### Change Test Suite

Update `DemoRunner.java` to use different test suites:
```java
TestSuite suite = loader.load("path/to/your/suite.yaml");
```

## Output Files

After running the demo:

```
demo/output/
├── mockdb-report.json          # MockDB compliance report
├── fastdb-report.json          # FastDB compliance report
├── clouddb-report.json         # CloudDB compliance report
├── leaderboard.md              # Markdown leaderboard
└── leaderboard.json            # JSON leaderboard

demo/dashboard/data/
└── leaderboard.json            # Copy for dashboard
```

## Demo Scenarios

### Scenario 1: Initial Compliance Check
Run all engines and see baseline compliance.

### Scenario 2: Engine Improvement
Modify an engine's pass rate and re-run to show improvement.

### Scenario 3: New Engine Addition
Add a new engine and see how it ranks.

### Scenario 4: Dashboard Monitoring
Keep dashboard open and run demo multiple times to see updates.

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
- Works offline

## Troubleshooting

### Issue: Compilation Errors
```bash
# Ensure SDK is built first
cd ../sdk/java
./gradlew build
```

### Issue: Dashboard Not Loading
```bash
# Use a local web server
cd demo/dashboard
python3 -m http.server 8000
# Open http://localhost:8000
```

### Issue: Reports Not Generated
```bash
# Check output directory permissions
chmod -R 755 demo/output
```

## Next Steps

1. **Explore Code**: Review mock engine implementations
2. **Modify Engines**: Change pass rates and behaviors
3. **Customize Dashboard**: Update styles and charts
4. **Real Integration**: Replace mocks with actual database engines

## Support

For questions or issues:
- Review main project README: `../README.md`
- Check SDK documentation: `../sdk/*/README.md`
- See examples: `../examples/README.md`

---

**Demo Purpose**: Educational demonstration of the Substrait Compliance Framework workflow. Mock engines simulate real database behavior for testing and visualization purposes.