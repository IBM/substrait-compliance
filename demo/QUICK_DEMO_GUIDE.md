# Quick Demo Guide - 2 Minute Setup

## Start the Demo (30 seconds)

```bash
# 1. Generate test data
cd demo
./runner/run-simple-demo.sh

# 2. Start web server
cd dashboard
python3 -m http.server 8080

# 3. Open browser
# Go to: http://localhost:8080
```

## Demo the Features (90 seconds)

### Feature 1: Dashboard Overview (15 seconds)
**What to show:**
- "Here's the compliance leaderboard showing 3 database engines"
- "FastDB leads with 95.5% pass rate"
- "Charts show visual comparison of performance"

### Feature 2: Query-Level Drill-Down (45 seconds)
**What to do:**
1. Click "FastDB" row in the table
2. Modal opens showing detailed results

**What to say:**
- "Click any engine to see detailed query-by-query results"
- "Each of the 22 TPC-H queries is shown as a card"
- "Green border means passed, red means failed"
- "You can see execution times and error messages"
- "Complexity badges show query difficulty"

**What to point out:**
- Summary stats at top (pass rate, counts, avg time)
- Query cards with status and timing
- Error messages on failed queries (scroll to Q20)
- Complexity badges (Simple/Medium/Complex/Very Complex)

### Feature 3: Filter by Complexity (30 seconds)
**What to do:**
1. Close the modal
2. Select "Simple (3 queries)" from dropdown
3. Notice dropdown turns blue (visual feedback)
4. Click "FastDB" again

**What to say:**
- "You can filter by query complexity"
- "Let's see how FastDB handles simple queries"
- "Now the modal shows only 3 simple queries"
- "All 3 passed - 100% success rate on simple queries"

**What to try:**
1. Select "Very Complex (4 queries)"
2. Click "CloudDB" (lowest performer)
3. Show that it struggles with complex queries

## Key Talking Points

### Problem Statement
"Database engines need to support Substrait, but compliance testing is manual and inconsistent"

### Solution
"This framework automates compliance testing and provides visual dashboards for results"

### Value Proposition
1. **Automated Testing**: Run 22 TPC-H queries automatically
2. **Visual Results**: Interactive dashboard, not just logs
3. **Detailed Analysis**: Drill down to individual query failures
4. **Complexity Insights**: See which query types cause problems
5. **Easy Comparison**: Compare multiple engines side-by-side

## Demo Scenarios

### Scenario A: "Which engine should we use?"
1. Show leaderboard rankings
2. Click top engine (FastDB) - 95.5% pass rate
3. Click bottom engine (CloudDB) - 77.3% pass rate
4. Compare their failed queries

**Conclusion**: "FastDB is more mature, CloudDB needs work on complex queries"

### Scenario B: "Why is our engine failing?"
1. Select "Very Complex" filter
2. Click the failing engine
3. Show specific failed queries (Q20, Q21)
4. Read error messages

**Conclusion**: "The engine struggles with correlated subqueries - that's our focus area"

### Scenario C: "Are we production-ready?"
1. Show overall pass rate
2. Filter by "Simple" - should be 100%
3. Filter by "Medium" - should be high
4. Filter by "Complex" - acceptable failures

**Conclusion**: "Ready for simple/medium workloads, complex queries need more work"

## Common Questions & Answers

**Q: Is this real data?**
A: "This demo uses simulated data, but the framework works with real engines. We have SDKs for Java, Python, and Rust."

**Q: How long does testing take?**
A: "The demo runs in seconds. Real testing depends on engine speed - typically minutes for 22 queries."

**Q: Can we add custom queries?**
A: "Yes! The framework supports custom test suites. TPC-H is just the default benchmark."

**Q: What about other databases?**
A: "Any engine that supports Substrait can be tested. You implement a simple adapter interface."

**Q: Can we export these results?**
A: "The reports are JSON files that can be processed, shared, or integrated into CI/CD pipelines."

## Technical Details (If Asked)

### Architecture
- **Test Runner**: Java application that executes queries
- **Reports**: JSON format with detailed results
- **Dashboard**: Static HTML/CSS/JS (no backend needed)
- **Data Flow**: Runner → JSON files → Dashboard reads files

### Complexity Classification
- **Simple** (3): Single table, basic aggregations
- **Medium** (7): 2-3 table joins, moderate complexity
- **Complex** (8): 4+ table joins, advanced SQL
- **Very Complex** (4): Correlated subqueries, nested queries

### Extensibility
- Add new engines: Implement adapter interface
- Add new queries: Create test suite YAML files
- Customize dashboard: Edit HTML/CSS/JS files
- Integrate CI/CD: Run from scripts, parse JSON output

## Troubleshooting During Demo

### Dashboard won't load
```bash
# Check if server is running
lsof -i :8080

# Try different port
python3 -m http.server 8081
```

### Modal won't open
```bash
# Verify reports exist
ls demo/output/*.json

# Re-run demo if needed
./runner/run-simple-demo.sh
```

### Filter doesn't work
- Hard refresh browser: Cmd+Shift+R (Mac) or Ctrl+Shift+R (Windows)
- Check browser console (F12) for errors

## After the Demo

### Next Steps for Audience
1. Review documentation in `demo/` directory
2. Try running the demo themselves
3. Explore SDK examples in `sdk/` directories
4. Consider implementing adapter for their engine

### Follow-up Materials
- `DASHBOARD_FEATURES.md` - Complete feature documentation
- `DEMO_USAGE.md` - Detailed usage scenarios
- `TEST_INSTRUCTIONS.md` - Testing checklist
- `IMPLEMENTATION_SUMMARY.md` - Technical details

## Pro Tips

1. **Practice the flow**: Run through once before presenting
2. **Have backup**: Keep screenshots in case of technical issues
3. **Know your data**: Memorize which queries fail for each engine
4. **Be ready to improvise**: Audience might want to see specific features
5. **Keep it simple**: Don't dive too deep into technical details unless asked

## Time Variations

### 1-Minute Version
- Show leaderboard
- Click one engine
- Show modal with query results
- Done!

### 5-Minute Version
- Show leaderboard (30s)
- Drill down on best engine (1m)
- Drill down on worst engine (1m)
- Demo complexity filter (1m)
- Show different complexity levels (1m)
- Q&A (30s)

### 10-Minute Version
- Full walkthrough of all features
- Multiple engine comparisons
- All complexity levels
- Discuss architecture
- Show JSON reports
- Q&A

## Success Metrics

After the demo, audience should understand:
- ✅ What the framework does (compliance testing)
- ✅ How it works (automated query execution)
- ✅ Why it's useful (visual results, detailed analysis)
- ✅ How to use it (run script, view dashboard)
- ✅ What's next (implement adapter for their engine)