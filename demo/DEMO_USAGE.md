# Demo Usage Guide

## Quick Start

### 1. Run the Demo
```bash
cd demo
./runner/run-simple-demo.sh
```

This generates:
- 3 mock engine reports (MockDB, FastDB, CloudDB)
- Leaderboard with aggregated statistics
- Dashboard data files

### 2. Start the Dashboard
```bash
cd dashboard
python3 -m http.server 8080
```

Open browser to: **http://localhost:8080**

## Using the Dashboard

### Main View
The dashboard opens with:
- **Header Stats**: Total engines, average pass rate, last update time
- **Leaderboard Table**: Ranked list of engines with pass rates
- **Charts**: Visual comparison of engine performance
- **Detailed Results**: Individual engine cards

### New Features ✨

#### 1. Query-Level Drill-Down
**How to use:**
1. Click any row in the leaderboard table
2. A modal opens showing detailed query results
3. Each query displays:
   - Query name (Q01-Q22)
   - Complexity badge (color-coded)
   - Pass/Fail status
   - Execution time
   - Error message (if failed)

**Example:**
```
Click "MockDB" row → Modal shows all 22 TPC-H queries
- Q01 [SIMPLE] ✅ Passed - 263ms
- Q02 [VERY_COMPLEX] ✅ Passed - 122ms
- Q20 [VERY_COMPLEX] ❌ Failed - 281ms
  Error: Query execution failed: simulated failure
```

#### 2. Filter by Complexity
**How to use:**
1. Use the dropdown in the leaderboard section: "Filter by Complexity"
2. Select a complexity level:
   - **All Queries** (default)
   - **Simple** - 3 queries
   - **Medium** - 7 queries
   - **Complex** - 8 queries
   - **Very Complex** - 4 queries
3. Click any engine to see filtered results
4. Modal shows only queries matching selected complexity

**Example:**
```
Select "Simple" → Click "FastDB" → Modal shows only Q01, Q06, Q14
Summary updates: "3 queries shown" instead of "22 queries shown"
```

### Understanding Complexity Levels

#### Simple (3 queries)
- Q01: Pricing Summary Report
- Q06: Forecasting Revenue Change
- Q14: Promotion Effect
- **Use case**: Testing basic aggregation support

#### Medium (7 queries)
- Q03, Q04, Q10, Q12, Q13, Q16, Q19
- **Use case**: Testing join and filter capabilities

#### Complex (8 queries)
- Q05, Q07, Q09, Q11, Q15, Q17, Q18, Q22
- **Use case**: Testing multi-table join performance

#### Very Complex (4 queries)
- Q02, Q08, Q20, Q21
- **Use case**: Testing advanced SQL features (subqueries, correlated queries)

## Demo Scenarios

### Scenario 1: Quick Overview
**Goal**: Get a high-level view of engine compliance

1. Run demo: `./runner/run-simple-demo.sh`
2. Open dashboard
3. Review leaderboard rankings
4. Check pass rate chart

**Expected Result**: FastDB leads with 95.5%, MockDB at 85.4%, CloudDB at 77.3%

### Scenario 2: Investigate Failures
**Goal**: Understand why an engine is failing tests

1. Open dashboard
2. Click on CloudDB (lowest pass rate)
3. Scroll through query cards
4. Identify failed queries (red border)
5. Read error messages

**Expected Result**: See which specific queries failed and why

### Scenario 3: Compare Simple vs Complex Query Support
**Goal**: Determine if an engine handles complex queries well

1. Open dashboard
2. Select "Simple" from complexity filter
3. Click FastDB → Note pass rate for simple queries
4. Select "Very Complex" from filter
5. Click FastDB again → Compare pass rate

**Expected Result**: Most engines perform better on simple queries

### Scenario 4: Performance Analysis
**Goal**: Find the fastest engine for specific query types

1. Open dashboard
2. Select "Medium" complexity
3. Click each engine and note average execution times
4. Compare times across engines

**Expected Result**: Identify which engine executes medium queries fastest

## Interpreting Results

### Status Badges
- 🟢 **Excellent** (95%+): Production-ready
- 🟡 **Good** (85-94%): Minor issues
- 🟠 **Fair** (70-84%): Needs improvement
- 🔴 **Needs Work** (<70%): Significant gaps

### Query Card Colors
- **Green left border**: Passed
- **Red left border**: Failed
- **Orange left border**: Skipped

### Complexity Badge Colors
- **Green**: Simple
- **Orange**: Medium
- **Pink**: Complex
- **Purple**: Very Complex

## Tips & Tricks

### 1. Quick Navigation
- Click anywhere outside modal to close it
- Use X button in modal header to close
- Dropdown persists filter selection

### 2. Comparing Engines
1. Open engine A in modal
2. Note key metrics
3. Close modal
4. Open engine B
5. Compare results

### 3. Finding Patterns
- Filter by complexity to see if failures cluster
- Check execution times for performance bottlenecks
- Look for error message patterns

### 4. Demo Customization
Edit `SimpleDemoRunner.java` to:
- Change pass rates: Modify `shouldPass()` method
- Adjust execution times: Change random ranges
- Add more engines: Add to `engines` array

## Troubleshooting

### Dashboard Not Loading
```bash
# Check if web server is running
lsof -i :8080

# Try different port
python3 -m http.server 8081
```

### Modal Not Opening
- Ensure demo has been run
- Check browser console (F12) for errors
- Verify report files exist: `ls demo/output/`

### Filter Not Working
- Refresh page (F5)
- Clear browser cache
- Re-run demo to regenerate data

### Charts Not Displaying
- Check internet connection (Chart.js loads from CDN)
- Verify JavaScript is enabled
- Try different browser

## Advanced Usage

### Running Multiple Times
```bash
# Run demo multiple times to see different results
./runner/run-simple-demo.sh
# Wait a few seconds
./runner/run-simple-demo.sh
```

### Viewing Raw Data
```bash
# Leaderboard summary
cat output/leaderboard.json | jq

# Individual engine report
cat output/fastdb-report.json | jq

# Filter by complexity in CLI
cat output/mockdb-report.json | jq '.testResults[] | select(.complexity == "SIMPLE")'
```

### Exporting Results
```bash
# Copy reports for sharing
cp -r output/ ~/Desktop/compliance-results/

# Create summary
cat output/leaderboard.json | jq '.engines[] | {name: .engineName, passRate: .passRate}' > summary.json
```

## Next Steps

After exploring the demo:
1. Review `DASHBOARD_FEATURES.md` for detailed feature documentation
2. Check `IMPLEMENTATION_SUMMARY.md` for technical details
3. Explore SDK documentation in `sdk/` directories
4. Try implementing your own engine adapter

## Support

For questions or issues:
- Review documentation in `demo/` directory
- Check browser console for errors
- Verify all files generated correctly
- Ensure web server is running on correct port