# Substrait Compliance Dashboard

Interactive dashboard for visualizing compliance test results.

## 📊 Available Dashboards

### 1. **TPC-H Tests Dashboard** (`index.html`)
- View TPC-H query-level compliance results
- 22 queries across 4 complexity levels
- Engine leaderboard and detailed results
- Visual charts and analytics

### 2. **TPC-DS Tests Dashboard** (`tpcds-tests.html`) 🆕
- View TPC-DS query-level compliance results
- 99 queries testing complex analytical workloads
- Decision Support benchmark scenarios
- Engine leaderboard and detailed results
- Visual charts and analytics

### 3. **Function Tests Dashboard** (`function-tests.html`)
- View function-level compliance results
- 568 tests across 5 categories
- Category breakdown per engine
- Granular pass/fail statistics

## 🚀 Quick Start

### Step 1: Run the Tests

```bash
# Run TPC-H tests
cd ../runner
./run-demo.sh

# Run TPC-DS tests
cd ../runner
./run-tpcds-tests.sh

# Run Function tests
cd ../runner
./run-function-tests-python.sh
```

### Step 2: View the Dashboard

Open in your browser:
```bash
# From the dashboard directory
open index.html              # TPC-H dashboard
open tpcds-tests.html        # TPC-DS dashboard
open function-tests.html     # Function tests dashboard
```

Or use a local server:
```bash
# Python 3
python3 -m http.server 8000

# Then open: http://localhost:8000/
```

## 📁 Data Files

The dashboards read JSON files from the `../output/` directory:

### TPC-H Tests:
- `leaderboard.json`
- `MockDBEngine-report.json`
- `FastDBEngine-report.json`
- `CloudDBEngine-report.json`

### TPC-DS Tests:
- `tpcds_leaderboard.json`
- `MockDBEngine-tpcds-report.json`
- `FastDBEngine-tpcds-report.json`
- `CloudDBEngine-tpcds-report.json`

### Function Tests:
- `function_tests_summary.json`
- `MockDBEngine_function_tests.json`
- `FastDBEngine_function_tests.json`
- `CloudDBEngine_function_tests.json`

### Cross-Suite Summary:
- `summary.json` - Combined metrics for all test suites

## 🎨 Features

### TPC-H Dashboard
- ✅ Leaderboard with rankings
- ✅ Complexity filtering
- ✅ Pass rate comparison charts
- ✅ Detailed query results
- ✅ Status badges

### TPC-DS Dashboard
- ✅ Leaderboard with rankings
- ✅ 99 query results
- ✅ Pass rate comparison charts
- ✅ Detailed query results
- ✅ Status badges
- ✅ Cross-suite integration

### Function Tests Dashboard
- ✅ Engine comparison table
- ✅ Category breakdown cards
- ✅ Progress bars per category
- ✅ Interactive charts
- ✅ Engine selector
- ✅ Navigation between dashboards

## 🔄 Navigation

All dashboards have navigation tabs at the top:
- **TPC-H Tests** - Switch to TPC-H query-level testing (22 queries)
- **TPC-DS Tests** - Switch to TPC-DS query-level testing (99 queries)
- **Function Tests** - Switch to function-level testing (568 tests)

## 📊 Understanding the Results

### Pass Rate Colors

| Color | Range | Meaning |
|-------|-------|---------|
| 🟢 Green | ≥95% | VERIFIED |
| 🔵 Blue | 80-94% | EDGE |
| 🟡 Yellow | 60-79% | BASIC |
| 🔴 Red | <60% | NONE |

### Test Suite Overview

#### TPC-H (22 queries)
- Simple: 3 queries
- Medium: 7 queries
- Complex: 8 queries
- Very Complex: 4 queries

#### TPC-DS (99 queries)
- Comprehensive decision support scenarios
- Complex analytical workloads
- Multi-table joins and aggregations

#### Function Tests (568 tests)
1. **Aggregate** (📊) - COUNT, AVG, SUM, etc.
2. **Window** (🪟) - ROW_NUMBER, RANK, LAG, etc.
3. **Cast** (🔄) - Type conversions
4. **Boolean** (✓) - AND, OR, NOT, XOR
5. **DateTime** (📅) - Date/time operations

## 🛠️ Troubleshooting

### "Error loading data"
- Make sure you've run the test demos first
- Check that JSON files exist in `../output/`
- Verify file permissions

### Charts not displaying
- Ensure internet connection (Chart.js loads from CDN)
- Check browser console for errors
- Try refreshing the page

### Styling issues
- Ensure `styles.css` is in the same directory
- Clear browser cache
- Try a different browser

## 📖 Related Documentation

- [Function Tests Implementation](../../docs/FUNCTION_TESTS_IMPLEMENTATION.md)
- [Test Suite README](../../test-suites/functions/README.md)
- [Demo Usage Guide](../DEMO_USAGE.md)

## 🎯 Example Workflow

1. **Run All Tests**
   ```bash
   cd demo/runner
   ./run-demo.sh                    # TPC-H
   ./run-tpcds-tests.sh             # TPC-DS
   ./run-function-tests-python.sh   # Functions
   ```

2. **View Results**
   ```bash
   cd ../dashboard
   python3 -m http.server 8000
   # Open http://localhost:8000/
   ```

3. **Analyze**
   - Compare engines across all test suites
   - Identify weak areas (queries, categories)
   - Review cross-suite summary
   - Drill down into specific tests

4. **Iterate**
   - Fix identified issues
   - Re-run affected tests
   - Verify improvements in dashboard

## 🌐 Browser Compatibility

Tested on:
- ✅ Chrome/Chromium
- ✅ Firefox
- ✅ Safari
- ✅ Edge

## 📝 Notes

- Dashboards are static HTML/JavaScript
- No server-side processing required
- Data updates when JSON files change
- Refresh page to see new results

---

**Made with ❤️ for the Substrait Community**