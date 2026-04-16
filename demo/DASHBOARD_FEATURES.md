# Dashboard Features Guide

## Overview
The Substrait Compliance Dashboard provides an interactive interface for viewing and analyzing database engine compliance test results.

## Core Features

### 1. Leaderboard View
- **Real-time Rankings**: Engines ranked by pass rate
- **Key Metrics**: Pass rate, tests passed/failed/skipped
- **Status Indicators**: Visual badges (Excellent 🟢, Good 🟡, Fair 🟠, Needs Work 🔴)
- **Interactive Rows**: Click any engine row to view detailed results

### 2. Query-Level Drill-Down ✨ NEW
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

### 3. Filter by Query Complexity ✨ NEW
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

### 5. Detailed Results Cards
Individual cards for each engine showing:
- Engine name and version
- Status badge
- Pass rate percentage
- Test counts (passed/failed/skipped)
- Last update timestamp

### 6. Auto-Refresh
- Dashboard automatically refreshes every 30 seconds
- Ensures latest test results are always displayed
- No manual refresh needed

## Query Complexity Classification

### Simple Queries (3 queries)
- **Q1**: Pricing Summary Report
- **Q6**: Forecasting Revenue Change
- **Q14**: Promotion Effect

**Characteristics**: Single table scans, basic aggregations, simple filters

### Medium Queries (7 queries)
- **Q3**: Shipping Priority
- **Q4**: Order Priority Checking
- **Q10**: Returned Item Reporting
- **Q12**: Shipping Modes and Order Priority
- **Q13**: Customer Distribution
- **Q16**: Parts/Supplier Relationship
- **Q19**: Discounted Revenue

**Characteristics**: 2-3 table joins, moderate aggregations, multiple filters

### Complex Queries (8 queries)
- **Q5**: Local Supplier Volume
- **Q7**: Volume Shipping
- **Q9**: Product Type Profit Measure
- **Q11**: Important Stock Identification
- **Q15**: Top Supplier
- **Q17**: Small-Quantity-Order Revenue
- **Q18**: Large Volume Customer
- **Q22**: Global Sales Opportunity

**Characteristics**: 4+ table joins, complex aggregations, nested queries

### Very Complex Queries (4 queries)
- **Q2**: Minimum Cost Supplier
- **Q8**: National Market Share
- **Q20**: Potential Part Promotion
- **Q21**: Suppliers Who Kept Orders Waiting

**Characteristics**: Correlated subqueries, multiple levels of nesting, advanced SQL features

## Technical Details

### Data Sources
- **Leaderboard Data**: `data/leaderboard.json` - Aggregated statistics
- **Engine Reports**: `../output/{enginename}-report.json` - Detailed test results

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
- Modern browser with JavaScript enabled
- Chart.js library (loaded via CDN)
- Local web server (due to CORS restrictions)

### Running the Dashboard
```bash
# From demo/dashboard directory
python3 -m http.server 8080

# Open browser to:
http://localhost:8080
```

## Customization

### Adding New Complexity Levels
Edit `SimpleDemoRunner.java`:
```java
private static String getQueryComplexity(String queryName) {
    // Add your custom complexity mappings
}
```

### Modifying Modal Appearance
Edit `demo/dashboard/styles.css`:
- `.modal-content`: Modal size and positioning
- `.query-card`: Individual query card styling
- `.complexity-*`: Complexity badge colors

### Adjusting Filter Behavior
Edit `demo/dashboard/dashboard.js`:
- `filterByComplexity()`: Filter logic
- `displayEngineModal()`: Modal display logic

## Future Enhancements

Planned features (not yet implemented):
- [ ] Search functionality for queries
- [ ] Export reports to PDF/CSV
- [ ] Performance trend charts over time
- [ ] Side-by-side engine comparison
- [ ] Custom query grouping
- [ ] Historical data tracking

## Troubleshooting

### Modal Not Opening
- Ensure demo has been run: `./run-simple-demo.sh`
- Check browser console for errors
- Verify report files exist in `demo/output/`

### Filter Not Working
- Ensure SimpleDemoRunner includes complexity data
- Recompile and run demo if needed
- Check browser console for JavaScript errors

### Charts Not Displaying
- Verify Chart.js CDN is accessible
- Check internet connection
- Look for console errors

## Support

For issues or questions:
1. Check the demo documentation in `demo/START_HERE.md`
2. Review the implementation guide in `IMPLEMENTATION_SUMMARY.md`
3. Examine browser console for error messages