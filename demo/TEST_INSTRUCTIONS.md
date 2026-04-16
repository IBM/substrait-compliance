# Testing the Dashboard Features

## Quick Test Steps

### 1. Ensure Demo Data Exists
```bash
cd demo
./runner/run-simple-demo.sh
```

Expected output: Reports generated in `output/` directory

### 2. Start Web Server
```bash
cd dashboard
python3 -m http.server 8080
```

### 3. Open Dashboard
Open browser to: http://localhost:8080

## Feature Testing Checklist

### ✅ Basic Dashboard Loading
- [ ] Dashboard loads without errors
- [ ] Header shows 3 engines, average pass rate, last updated time
- [ ] Leaderboard table shows 3 engines (FastDB, MockDB, CloudDB)
- [ ] Charts display correctly (bar chart and doughnut chart)
- [ ] Detailed results cards show at bottom

### ✅ Query-Level Drill-Down (Modal)
**Test 1: Open Modal**
1. Click on "FastDB" row in leaderboard
2. Modal should open with:
   - Title: "FastDB v2.5.0"
   - Summary stats: Pass Rate, Passed, Failed, Skipped, Avg Time
   - Grid of 22 query cards

**Test 2: Query Cards**
Each card should show:
- Query name (Q01-Q22)
- Complexity badge (colored: green/orange/pink/purple)
- Status indicator
- Execution time
- Error message (for failed queries only)

**Test 3: Close Modal**
- Click X button → Modal closes
- Click outside modal (dark overlay) → Modal closes

### ✅ Filter by Complexity
**Test 1: Visual Feedback**
1. Select "Simple (3 queries)" from dropdown
2. Dropdown should:
   - Change border color to blue
   - Change background to light blue
   - Text becomes bold

**Test 2: Filter in Modal**
1. Keep "Simple" selected
2. Click "FastDB" in leaderboard
3. Modal should show:
   - Blue banner: "Filtered by: SIMPLE queries only (3 queries shown)"
   - Only 3 query cards: Q01, Q06, Q14
   - Summary stats calculated for 3 queries only

**Test 3: Different Complexity Levels**
Try each filter option:
- All Queries → 22 cards
- Simple → 3 cards (Q01, Q06, Q14)
- Medium → 7 cards (Q03, Q04, Q10, Q12, Q13, Q16, Q19)
- Complex → 8 cards (Q05, Q07, Q09, Q11, Q15, Q17, Q18, Q22)
- Very Complex → 4 cards (Q02, Q08, Q20, Q21)

**Test 4: Filter Persistence**
1. Select "Medium"
2. Click FastDB → See 7 queries
3. Close modal
4. Click MockDB → Should still show 7 queries (filter persists)
5. Select "All Queries" → Back to 22 queries

### ✅ Browser Console Check
Open browser console (F12) and verify:
- No JavaScript errors
- Console logs show: "Filtering by complexity: SIMPLE" (when changing filter)
- Console logs show: "Loading details for: FastDB" (when clicking engine)

## Expected Results by Engine

### FastDB (95.5% pass rate)
- **All Queries**: 21 passed, 1 failed (Q20)
- **Simple**: 3 passed, 0 failed
- **Medium**: 7 passed, 0 failed
- **Complex**: 7 passed, 0 failed
- **Very Complex**: 4 passed, 1 failed (Q20)

### MockDB (85.4% pass rate)
- **All Queries**: 19 passed, 3 failed
- **Simple**: 3 passed, 0 failed
- **Medium**: 7 passed, 0 failed
- **Complex**: 6 passed, 1 failed (Q22)
- **Very Complex**: 3 passed, 2 failed (Q20, Q21)

### CloudDB (77.3% pass rate)
- **All Queries**: 17 passed, 5 failed
- **Simple**: 2 passed, 1 failed (Q14)
- **Medium**: 6 passed, 1 failed (Q19)
- **Complex**: 6 passed, 2 failed (Q17, Q18)
- **Very Complex**: 3 passed, 1 failed (Q21)

## Troubleshooting

### Modal Not Opening
**Symptom**: Clicking engine row does nothing

**Check**:
1. Open browser console (F12)
2. Look for errors like "Failed to load report"
3. Verify files exist: `ls demo/output/*.json`
4. Check file names match: `mockdb-report.json`, `fastdb-report.json`, `clouddb-report.json`

**Fix**: Re-run demo: `./runner/run-simple-demo.sh`

### Filter Not Working
**Symptom**: Dropdown changes but modal shows all queries

**Check**:
1. Browser console for JavaScript errors
2. Verify `onchange="filterByComplexity(this.value)"` in HTML
3. Check `currentComplexityFilter` variable in console

**Fix**: Hard refresh browser (Ctrl+Shift+R or Cmd+Shift+R)

### Complexity Data Missing
**Symptom**: All queries show "MEDIUM" or no complexity badge

**Check**:
```bash
cat demo/output/mockdb-report.json | grep complexity
```

**Fix**: 
1. Recompile: `cd demo && javac -d . runner/SimpleDemoRunner.java`
2. Re-run: `./runner/run-simple-demo.sh`

### Charts Not Loading
**Symptom**: Empty chart areas

**Check**:
1. Internet connection (Chart.js loads from CDN)
2. Browser console for CDN errors

**Fix**: Check network tab in browser dev tools

## Visual Verification

### Complexity Badge Colors
- **SIMPLE**: Green background (#e8f5e9), dark green text
- **MEDIUM**: Orange background (#fff3e0), dark orange text
- **COMPLEX**: Pink background (#fce4ec), dark pink text
- **VERY_COMPLEX**: Purple background (#f3e5f5), dark purple text

### Query Card Borders
- **Passed**: Green left border (4px)
- **Failed**: Red left border (4px)
- **Skipped**: Orange left border (4px)

### Modal Animations
- Modal should fade in smoothly
- Modal content should slide down from top
- Hover over query cards → slight lift effect
- Hover over close button → background highlight

## Performance Check
- Modal opens in < 100ms
- Filter change is instant (no delay)
- Smooth scrolling in modal
- No lag when switching between engines

## Success Criteria
✅ All 3 engines clickable and show modal
✅ All 5 filter options work correctly
✅ Query counts match expected values
✅ Complexity badges display with correct colors
✅ Failed queries show error messages
✅ Modal closes properly
✅ No JavaScript errors in console
✅ Visual feedback on filter selection