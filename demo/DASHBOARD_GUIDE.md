# Dashboard Guide

Complete guide to using the Substrait Compliance Dashboard features.

## 📋 Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Core Features](#core-features)
- [Query Complexity Classification](#query-complexity-classification)
- [Usage Tips](#usage-tips)
- [Technical Details](#technical-details)
- [Customization](#customization)
- [Testing Checklist](#testing-checklist)

---

## Overview

The Substrait Compliance Dashboard provides an interactive interface for viewing and analyzing database engine compliance test results. It features real-time rankings, query-level drill-down, complexity filtering, and visual charts.

---

## Getting Started

### Prerequisites
- Demo must be run first to generate data
- Web server required (CORS restrictions)
- Modern browser with JavaScript enabled

### Launch Dashboard

```bash
# From demo directory
cd dashboard
python3 -m http.server 8080

# Open browser to:
# http://localhost:8080
```

---

## Core Features

### 1. Leaderboard View

**What it shows:**
- Real-time rankings of engines by pass rate
- Key metrics: Pass rate, tests passed/failed/skipped
- Status indicators with color coding
- Interactive rows (clickable)

**Status Badges:**
- 🟢 **VERIFIED** (95%+): Meets the highest fidelity tier
- 🔵 **EDGE** (80-94%): Strong coverage with some gaps
- 🟡 **BASIC** (60-79%): Foundational coverage only
- 🔴 **NONE** (<60%): Significant gaps remain

**How to use:**
- Click any engine row to see detailed results
- Rankings update automatically with new data
- Medals (🥇🥈🥉) show top 3 performers

### 2. Query-Level Drill-Down ✨

**What it shows:**
Click any engine to open a modal displaying:
- **Summary Statistics**: Pass rate, counts, average execution time
- **Individual Query Cards**: All 22 TPC-H queries with:
  - Query name (Q01-Q22)
  - Complexity badge (color-coded)
  - Status (Passed ✅ / Failed ❌ / Skipped ⏭️)
  - Execution time in milliseconds
  - Error messages (for failed tests)

**How to use:**
1. Click any engine row in the leaderboard table
2. Modal opens with detailed query-by-query results
3. Scroll through query cards to see individual results
4. Click X button or outside modal to close

**Visual Indicators:**
- **Green left border**: Query passed
- **Red left border**: Query failed
- **Orange left border**: Query skipped
- **Complexity badges**: Color-coded by difficulty

**Example:**
```
Click "MockDB" row → Modal shows all 22 TPC-H queries
- Q01 [SIMPLE] ✅ Passed - 263ms
- Q02 [VERY_COMPLEX] ✅ Passed - 122ms
- Q20 [VERY_COMPLEX] ❌ Failed - 281ms
  Error: Query execution failed: simulated failure
```

### 3. Filter by Query Complexity ✨

**What it does:**
Filters results to show only queries of selected complexity level.

**Complexity Levels:**
- **All Queries** (default): Shows all 22 TPC-H queries
- **Simple** (3 queries): Q1, Q6, Q14
- **Medium** (7 queries): Q3, Q4, Q10, Q12, Q13, Q16, Q19
- **Complex** (8 queries): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22
- **Very Complex** (4 queries): Q2, Q8, Q20, Q21

**How to use:**
1. Use the dropdown in the leaderboard section header
2. Select a complexity level
3. Dropdown turns blue to show active filter
4. Click any engine to see filtered results
5. Modal shows only matching queries
6. Summary statistics update automatically

**Visual Feedback:**
- Active filter: Blue border and background
- Filter banner in modal: "Filtered by: SIMPLE queries only (3 queries shown)"
- Query count updates in summary

**Example:**
```
Select "Simple" → Click "FastDB" → Modal shows only Q01, Q06, Q14
Summary updates: "3 queries shown" instead of "22 queries shown"
```

### 4. Visual Charts

**Pass Rate Comparison (Bar Chart):**
- Horizontal bars showing pass rates
- Color-coded by performance level
- Easy visual comparison across engines

**Test Distribution (Doughnut Chart):**
- Shows proportion of tests passed per engine
- Color-coded segments
- Interactive hover tooltips

**Color Coding:**
- Green: 95%+ (VERIFIED)
- Blue: 80-94% (EDGE)
- Yellow: 60-79% (BASIC)
- Red: <60% (NONE)

### 5. Detailed Results Cards

**What they show:**
Individual cards for each engine displaying:
- Engine name and version
- Status badge
- Pass rate percentage
- Test counts (passed/failed/skipped)
- Last update timestamp

**Location:**
Bottom section of dashboard, below charts

### 6. Auto-Refresh

**Behavior:**
- Dashboard automatically refreshes every 30 seconds
- Checks for new data files
- Updates all visualizations
- No manual refresh needed

**Configuration:**
Edit `dashboard.js` to change refresh interval:
```javascript
setInterval(loadDashboard, 30000); // 30 seconds
```

---

## Query Complexity Classification

### Simple Queries (3 queries)

**Queries:** Q1, Q6, Q14

**Characteristics:**
- Single table scans
- Basic aggregations (SUM, COUNT, AVG)
- Simple filters (WHERE clauses)
- No joins or subqueries

**Use Case:** Testing basic aggregation support

**Examples:**
- Q1: Pricing Summary Report - Simple aggregation with GROUP BY
- Q6: Forecasting Revenue Change - Single table with filter
- Q14: Promotion Effect - Basic percentage calculation

### Medium Queries (7 queries)

**Queries:** Q3, Q4, Q10, Q12, Q13, Q16, Q19

**Characteristics:**
- 2-3 table joins
- Moderate aggregations
- Multiple filters
- Some subqueries

**Use Case:** Testing join and filter capabilities

**Examples:**
- Q3: Shipping Priority - 3-table join with ORDER BY
- Q10: Returned Item Reporting - Multiple joins with aggregation
- Q12: Shipping Modes - Join with CASE expressions

### Complex Queries (8 queries)

**Queries:** Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22

**Characteristics:**
- 4+ table joins
- Complex aggregations
- Nested queries
- Window functions

**Use Case:** Testing multi-table join performance

**Examples:**
- Q5: Local Supplier Volume - 6-table join
- Q9: Product Type Profit - Complex aggregation with multiple joins
- Q15: Top Supplier - View creation with aggregation

### Very Complex Queries (4 queries)

**Queries:** Q2, Q8, Q20, Q21

**Characteristics:**
- Correlated subqueries
- Multiple levels of nesting
- Advanced SQL features (EXISTS, NOT EXISTS)
- Complex join conditions

**Use Case:** Testing advanced SQL features

**Examples:**
- Q2: Minimum Cost Supplier - Correlated subquery with MIN
- Q20: Potential Part Promotion - EXISTS with nested subqueries
- Q21: Suppliers Who Kept Orders Waiting - Multiple NOT EXISTS clauses

---

## Usage Tips

### 1. Quick Navigation
- Click anywhere outside modal to close it
- Use X button in modal header to close
- Dropdown persists filter selection across engines

### 2. Comparing Engines
1. Open engine A in modal
2. Note key metrics (pass rate, failed queries)
3. Close modal
4. Open engine B
5. Compare results

### 3. Finding Patterns
- Filter by complexity to see if failures cluster
- Check execution times for performance bottlenecks
- Look for error message patterns across engines
- Compare simple vs complex query performance

### 4. Performance Analysis
1. Select "Simple" complexity
2. Click each engine and note pass rates
3. Select "Very Complex"
4. Compare pass rates - most engines drop on complex queries

### 5. Identifying Issues
1. Click engine with low pass rate
2. Scroll through failed queries (red border)
3. Read error messages
4. Note which complexity levels have most failures

---

## Technical Details

### Data Sources

**Leaderboard Data:**
- File: `data/leaderboard.json`
- Contains: Aggregated statistics for all engines
- Updated: When demo runs

**Engine Reports:**
- Files: `../output/{enginename}-report.json`
- Contains: Detailed test results for each engine
- Loaded: On-demand when modal opens

### File Structure

```
demo/dashboard/
├── index.html          # Dashboard structure
├── styles.css          # Styling and layout
├── dashboard.js        # Interactive functionality
└── data/
    └── leaderboard.json  # Aggregated results
```

### Browser Requirements

- **Modern Browser**: Chrome 90+, Firefox 88+, Safari 14+, Edge 90+
- **JavaScript**: ES6+ support required
- **Dependencies**: Chart.js (loaded via CDN)
- **Server**: Local web server (CORS restrictions)

### Performance

**Load Times:**
- Initial dashboard load: <1s (loads leaderboard.json only)
- Modal open: <100ms (fetches single engine report)
- Filter change: Instant (client-side filtering)

**File Sizes:**
- leaderboard.json: ~1KB (summary data only)
- Individual reports: ~3-5KB each (detailed results)
- Total dashboard assets: ~15KB (HTML + CSS + JS)

### Data Flow

```
SimpleDemoRunner.java
    ↓
Generates test results with complexity field
    ↓
Saves to output/{engine}-report.json
    ↓
Creates leaderboard.json
    ↓
Copies to dashboard/data/leaderboard.json
    ↓
Dashboard loads and displays
```

---

## Customization

### Adding New Complexity Levels

**1. Update SimpleDemoRunner.java:**
```java
private static String getQueryComplexity(String queryName) {
    // Add your custom complexity mappings
    switch (queryName) {
        case "Q01": return "TRIVIAL";
        case "Q02": return "EXTREME";
        // ...
    }
}
```

**2. Add option to HTML dropdown:**
```html
<option value="trivial">Trivial (X queries)</option>
<option value="extreme">Extreme (Y queries)</option>
```

**3. Add CSS class for badge color:**
```css
.complexity-trivial {
    background-color: #e0f7fa;
    color: #006064;
}
```

**4. Update documentation**

### Modifying Modal Appearance

**Modal Size and Position:**
```css
.modal-content {
    width: 90%;           /* Adjust width */
    max-width: 1200px;    /* Maximum width */
    max-height: 85vh;     /* Maximum height */
}
```

**Query Card Styling:**
```css
.query-card {
    padding: 15px;        /* Card padding */
    border-radius: 8px;   /* Corner radius */
    margin-bottom: 12px;  /* Spacing between cards */
}
```

**Complexity Badge Colors:**
```css
.complexity-simple {
    background-color: #e8f5e9;  /* Light green */
    color: #2e7d32;             /* Dark green */
}
```

### Adjusting Filter Behavior

**Modify filter logic in dashboard.js:**
```javascript
function filterByComplexity(complexity) {
    currentComplexityFilter = complexity;
    
    // Add custom logic here
    console.log(`Filtering by: ${complexity}`);
    
    // Update visual feedback
    updateFilterVisuals();
}
```

**Change modal display logic:**
```javascript
function displayEngineModal(engineReport) {
    let filteredResults = engineReport.testResults;
    
    // Custom filtering logic
    if (currentComplexityFilter !== 'all') {
        filteredResults = engineReport.testResults.filter(test => {
            // Your custom filter condition
            return matchesFilter(test, currentComplexityFilter);
        });
    }
    
    // Display filtered results
    renderQueryCards(filteredResults);
}
```

### Changing Refresh Interval

**Edit dashboard.js:**
```javascript
// Change from 30 seconds to 60 seconds
setInterval(loadDashboard, 60000);

// Or disable auto-refresh
// Comment out the setInterval line
```

---

## Testing Checklist

### ✅ Basic Dashboard Loading
- [ ] Dashboard loads without errors
- [ ] Header shows correct engine count and average pass rate
- [ ] Leaderboard table displays all engines
- [ ] Charts render correctly
- [ ] Detailed results cards appear at bottom

### ✅ Query-Level Drill-Down
- [ ] Clicking engine row opens modal
- [ ] Modal shows correct engine name and version
- [ ] Summary statistics display correctly
- [ ] All 22 query cards appear
- [ ] Complexity badges show correct colors
- [ ] Status indicators (✅❌⏭️) display correctly
- [ ] Execution times shown
- [ ] Error messages appear for failed queries
- [ ] X button closes modal
- [ ] Clicking outside modal closes it

### ✅ Filter by Complexity
- [ ] Dropdown shows all 5 options
- [ ] Selecting filter changes dropdown appearance (blue)
- [ ] Modal shows filtered queries only
- [ ] Query count updates in summary
- [ ] Filter banner appears in modal
- [ ] Filter persists across different engines
- [ ] "All Queries" resets to show all 22 queries

### ✅ Browser Console
- [ ] No JavaScript errors
- [ ] Console logs show filter changes
- [ ] Console logs show engine loading

### ✅ Visual Verification
- [ ] Complexity badges have correct colors
- [ ] Query cards have correct border colors
- [ ] Modal animations work smoothly
- [ ] Hover effects work on cards and buttons

### ✅ Performance
- [ ] Modal opens quickly (<100ms)
- [ ] Filter changes are instant
- [ ] Smooth scrolling in modal
- [ ] No lag when switching engines

---

## Support

For issues or questions:
- Check the main demo README: `demo/README.md`
- Review browser console for errors (F12)
- Verify demo has been run: `./runner/run-simple-demo.sh`
- Ensure web server is running on correct port
- Check that data files exist in `output/` and `dashboard/data/`

---

**Last Updated**: 2026-05-30