# Substrait Compliance Dashboard

Interactive dashboard for visualizing compliance test results.

## 📊 Available Dashboards

### 1. **TPC-H Tests Dashboard** (`index.html`)
- View TPC-H query-level compliance results
- 22 queries across 4 complexity levels
- Engine leaderboard and detailed results
- Visual charts and analytics

### 2. **Function Tests Dashboard** (`function-tests.html`) 🆕
- View function-level compliance results
- 568 tests across 5 categories
- Category breakdown per engine
- Granular pass/fail statistics

## 🚀 Quick Start

### Step 1: Run the Tests

```bash
# Run TPC-H tests (if available)
cd ../runner
./run-demo.sh

# Run Function tests
cd ../runner
./run-function-tests-python.sh
```

### Step 2: View the Dashboard

Open in your browser:
```bash
# From the dashboard directory
open index.html              # TPC-H dashboard
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
- `MockDBEngine.json`
- `FastDBEngine.json`
- `CloudDBEngine.json`

### Function Tests:
- `MockDBEngine_function_tests.json`
- `FastDBEngine_function_tests.json`
- `CloudDBEngine_function_tests.json`
- `function_tests_summary.json`

## 🎨 Features

### TPC-H Dashboard
- ✅ Leaderboard with rankings
- ✅ Complexity filtering
- ✅ Pass rate comparison charts
- ✅ Detailed query results
- ✅ Status badges

### Function Tests Dashboard
- ✅ Engine comparison table
- ✅ Category breakdown cards
- ✅ Progress bars per category
- ✅ Interactive charts
- ✅ Engine selector
- ✅ Navigation between dashboards

## 🔄 Navigation

Both dashboards have navigation tabs at the top:
- **TPC-H Tests** - Switch to query-level testing
- **Function Tests** - Switch to function-level testing

## 📊 Understanding the Results

### Pass Rate Colors

| Color | Range | Meaning |
|-------|-------|---------|
| 🟢 Green | ≥95% | Excellent |
| 🟡 Yellow | 85-94% | Good |
| 🟠 Orange | 70-84% | Fair |
| 🔴 Red | <70% | Needs Improvement |

### Function Test Categories

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

1. **Run Tests**
   ```bash
   cd demo/runner
   ./run-function-tests-python.sh
   ```

2. **View Results**
   ```bash
   cd ../dashboard
   open function-tests.html
   ```

3. **Analyze**
   - Compare engines
   - Identify weak categories
   - Drill down into specific functions

4. **Iterate**
   - Fix identified issues
   - Re-run tests
   - Verify improvements

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