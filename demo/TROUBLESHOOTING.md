# Troubleshooting Guide

Common issues and solutions for the Substrait Compliance Demo.

## 📋 Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Demo Execution Issues](#demo-execution-issues)
- [Dashboard Issues](#dashboard-issues)
- [Data and File Issues](#data-and-file-issues)
- [Browser and Display Issues](#browser-and-display-issues)
- [Performance Issues](#performance-issues)
- [Verification Steps](#verification-steps)

---

## Quick Diagnostics

### Run Verification Script

```bash
cd demo
./verify-setup.sh
```

This checks:
- Java version
- Python version
- Required files
- Directory structure
- Permissions

### Manual Quick Check

```bash
# 1. Check Java version (needs 11+)
java -version

# 2. Check Python version (needs 3.8+)
python3 --version

# 3. Verify demo files exist
ls -la runner/run-simple-demo.sh

# 4. Check output directory
ls -la output/

# 5. Check dashboard data
ls -la dashboard/data/
```

---

## Demo Execution Issues

### Issue: Permission Denied on Shell Scripts

**Symptom:**
```
bash: ./runner/run-simple-demo.sh: Permission denied
```

**Solution:**
```bash
# Make script executable
chmod +x runner/run-simple-demo.sh

# Run again
./runner/run-simple-demo.sh
```

**Alternative:**
```bash
# Run with bash explicitly
bash runner/run-simple-demo.sh
```

### Issue: Java Version Not Supported

**Symptom:**
```
Error: Java version 8 is not supported
Requires Java 11 or higher
```

**Solution:**
```bash
# Check current Java version
java -version

# Install Java 11+ (macOS with Homebrew)
brew install openjdk@11

# Install Java 11+ (Ubuntu/Debian)
sudo apt-get install openjdk-11-jdk

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
```

### Issue: SDK Build Fails

**Symptom:**
```
Error: Could not find SDK classes
```

**Solution:**
```bash
# Build SDK first
cd ../sdk/java
./gradlew build

# Return to demo
cd ../../demo

# Run demo again
./runner/run-simple-demo.sh
```

### Issue: Compilation Errors

**Symptom:**
```
javac: error: file not found: SimpleDemoRunner.java
```

**Solution:**
```bash
# Ensure you're in the demo directory
cd demo

# Check if file exists
ls -la runner/SimpleDemoRunner.java

# If missing, check git status
git status

# Restore if needed
git checkout runner/SimpleDemoRunner.java
```

### Issue: Test Data Not Found

**Symptom:**
```
Error: Could not load test suite
```

**Solution:**
```bash
# Check test suite directory
ls -la ../test-suites/

# Verify TPC-H files exist
ls -la ../test-suites/tpch/

# If missing, restore from git
git checkout ../test-suites/
```

---

## Dashboard Issues

### Issue: Dashboard Shows "Failed to Load Data"

**Symptom:**
Dashboard displays error message: "Failed to load leaderboard data"

**Cause:** Demo hasn't been run yet or data files are missing

**Solution:**
```bash
# Step 1: Run the demo
cd demo
./runner/run-simple-demo.sh

# Step 2: Verify files were created
ls -la output/
# Should show: mockdb-report.json, fastdb-report.json, clouddb-report.json, leaderboard.json

ls -la dashboard/data/
# Should show: leaderboard.json

# Step 3: Restart web server
cd dashboard
python3 -m http.server 8080

# Step 4: Refresh browser
```

### Issue: Port Already in Use

**Symptom:**
```
OSError: [Errno 48] Address already in use
```

**Solution:**
```bash
# Option 1: Use different port
python3 -m http.server 8081

# Option 2: Find and kill process using port 8080
lsof -i :8080
kill -9 <PID>

# Option 3: Use alternative port
python3 -m http.server 9000
```

### Issue: Dashboard Shows But No Data Appears

**Symptom:**
Dashboard loads but shows empty charts and tables

**Cause:** Opened as `file://` instead of `http://`

**Solution:**
```bash
# MUST use web server due to CORS restrictions
cd dashboard
python3 -m http.server 8080

# Open in browser:
# http://localhost:8080
# NOT: file:///path/to/dashboard/index.html
```

### Issue: Modal Not Opening

**Symptom:**
Clicking engine row does nothing

**Diagnosis:**
```bash
# 1. Open browser console (F12)
# 2. Look for errors like:
#    "Failed to load report"
#    "404 Not Found"

# 3. Check if report files exist
ls -la demo/output/*.json

# 4. Verify file names match
# Should be: mockdb-report.json, fastdb-report.json, clouddb-report.json
```

**Solution:**
```bash
# Re-run demo to generate reports
cd demo
./runner/run-simple-demo.sh

# Verify files created
ls -la output/

# Hard refresh browser
# Mac: Cmd+Shift+R
# Windows/Linux: Ctrl+Shift+R
```

### Issue: Filter Not Working

**Symptom:**
Dropdown changes but modal shows all queries

**Solution:**
```bash
# 1. Check browser console (F12) for JavaScript errors

# 2. Hard refresh browser
# Mac: Cmd+Shift+R
# Windows/Linux: Ctrl+Shift+R

# 3. Clear browser cache
# Chrome: Settings > Privacy > Clear browsing data

# 4. Try different browser
```

### Issue: Charts Not Displaying

**Symptom:**
Empty chart areas or "Chart.js not loaded" error

**Cause:** Chart.js CDN not accessible

**Solution:**
```bash
# 1. Check internet connection
ping cdn.jsdelivr.net

# 2. Check browser console for CDN errors

# 3. Try different browser

# 4. Check if JavaScript is enabled
```

**Alternative:** Download Chart.js locally
```bash
cd dashboard
curl -o chart.js https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js

# Update index.html to use local file
# Change: <script src="https://cdn.jsdelivr.net/...">
# To: <script src="chart.js">
```

---

## Data and File Issues

### Issue: Reports Not Generated

**Symptom:**
`output/` directory is empty after running demo

**Solution:**
```bash
# Check output directory permissions
ls -ld output/
chmod -R 755 output/

# Check disk space
df -h

# Re-run demo with verbose output
cd demo
bash -x runner/run-simple-demo.sh

# Check for errors in output
```

### Issue: Complexity Data Missing

**Symptom:**
All queries show "MEDIUM" or no complexity badge

**Diagnosis:**
```bash
# Check if complexity field exists in reports
cat demo/output/mockdb-report.json | grep complexity
```

**Solution:**
```bash
# Recompile SimpleDemoRunner
cd demo
javac -d . runner/SimpleDemoRunner.java

# Re-run demo
./runner/run-simple-demo.sh

# Verify complexity data
cat output/mockdb-report.json | grep -A 2 complexity
```

### Issue: Leaderboard File Not Copied

**Symptom:**
`dashboard/data/leaderboard.json` doesn't exist

**Solution:**
```bash
# Manually copy file
cp output/leaderboard.json dashboard/data/

# Or re-run demo (it should copy automatically)
./runner/run-simple-demo.sh
```

### Issue: JSON Parse Errors

**Symptom:**
Browser console shows "Unexpected token" or "JSON parse error"

**Diagnosis:**
```bash
# Validate JSON files
cat output/leaderboard.json | python3 -m json.tool

# Check for syntax errors
cat output/mockdb-report.json | python3 -m json.tool
```

**Solution:**
```bash
# If JSON is invalid, re-run demo
./runner/run-simple-demo.sh

# If problem persists, check SimpleDemoRunner.java for JSON generation bugs
```

---

## Browser and Display Issues

### Issue: Modal Animations Not Working

**Symptom:**
Modal appears instantly without fade-in effect

**Cause:** Browser doesn't support CSS animations or JavaScript disabled

**Solution:**
```bash
# 1. Enable JavaScript in browser settings

# 2. Try different browser (Chrome, Firefox, Safari)

# 3. Check browser version (needs modern browser)
```

### Issue: Complexity Badges Wrong Colors

**Symptom:**
All badges show same color or wrong colors

**Solution:**
```bash
# 1. Hard refresh browser
# Mac: Cmd+Shift+R
# Windows/Linux: Ctrl+Shift+R

# 2. Clear browser cache

# 3. Check styles.css loaded correctly
# Open browser dev tools > Network tab > Look for styles.css
```

### Issue: Responsive Design Not Working

**Symptom:**
Dashboard doesn't resize properly on mobile/tablet

**Solution:**
```bash
# 1. Check viewport meta tag in index.html
# Should have: <meta name="viewport" content="width=device-width, initial-scale=1.0">

# 2. Test in browser responsive mode
# Chrome: F12 > Toggle device toolbar

# 3. Try different screen sizes
```

### Issue: Console Errors About CORS

**Symptom:**
```
Access to fetch at 'file://...' from origin 'null' has been blocked by CORS policy
```

**Cause:** Opening dashboard as local file instead of through web server

**Solution:**
```bash
# MUST use web server
cd dashboard
python3 -m http.server 8080

# Open: http://localhost:8080
# NOT: file:///path/to/index.html
```

---

## Performance Issues

### Issue: Dashboard Loads Slowly

**Symptom:**
Dashboard takes >5 seconds to load

**Diagnosis:**
```bash
# 1. Check file sizes
ls -lh output/*.json
ls -lh dashboard/data/*.json

# 2. Check network tab in browser dev tools

# 3. Check CPU usage
top
```

**Solution:**
```bash
# 1. Reduce number of engines in demo

# 2. Disable auto-refresh temporarily
# Edit dashboard.js, comment out:
# setInterval(loadDashboard, 30000);

# 3. Use local Chart.js instead of CDN
```

### Issue: Modal Opens Slowly

**Symptom:**
Modal takes >1 second to open

**Cause:** Large report files or slow disk I/O

**Solution:**
```bash
# 1. Check report file size
ls -lh output/mockdb-report.json

# 2. Reduce number of test results if possible

# 3. Use SSD instead of HDD

# 4. Close other applications
```

### Issue: Browser Becomes Unresponsive

**Symptom:**
Browser freezes when opening modal or changing filter

**Cause:** Too many DOM elements or memory leak

**Solution:**
```bash
# 1. Close other browser tabs

# 2. Restart browser

# 3. Try different browser

# 4. Check browser console for errors

# 5. Reduce number of queries in test suite
```

---

## Verification Steps

### Success Criteria Checklist

After running demo, verify:

#### ✅ Files Generated
```bash
# Check output files
ls -la output/
# Should show:
# - mockdb-report.json
# - fastdb-report.json
# - clouddb-report.json
# - leaderboard.json

# Check dashboard data
ls -la dashboard/data/
# Should show:
# - leaderboard.json
```

#### ✅ Dashboard Loads
```bash
# Start server
cd dashboard
python3 -m http.server 8080

# Open browser to http://localhost:8080
# Should see:
# - Header with 3 engines
# - Leaderboard table
# - Two charts
# - Detail cards
```

#### ✅ Interactive Features Work
- [ ] Click engine row → Modal opens
- [ ] Modal shows 22 query cards
- [ ] Complexity badges display
- [ ] Filter dropdown works
- [ ] Filtered results show correctly
- [ ] Modal closes properly
- [ ] No console errors

#### ✅ Data Accuracy
```bash
# Verify pass rates
cat output/leaderboard.json | python3 -m json.tool

# Should show:
# FastDB: ~95.5%
# MockDB: ~85.4%
# CloudDB: ~77.3%
```

### Expected Results by Engine

**FastDB (95.5% pass rate):**
- All Queries: 21 passed, 1 failed (Q20)
- Simple: 3 passed, 0 failed
- Medium: 7 passed, 0 failed
- Complex: 7 passed, 0 failed
- Very Complex: 4 passed, 1 failed

**MockDB (85.4% pass rate):**
- All Queries: 19 passed, 3 failed
- Simple: 3 passed, 0 failed
- Medium: 7 passed, 0 failed
- Complex: 6 passed, 1 failed
- Very Complex: 3 passed, 2 failed

**CloudDB (77.3% pass rate):**
- All Queries: 17 passed, 5 failed
- Simple: 2 passed, 1 failed
- Medium: 6 passed, 1 failed
- Complex: 6 passed, 2 failed
- Very Complex: 3 passed, 1 failed

---

## Getting Help

If issues persist after trying these solutions:

1. **Check Documentation:**
   - Main demo README: `demo/README.md`
   - Dashboard guide: `demo/DASHBOARD_GUIDE.md`
   - Main project README: `../README.md`

2. **Check Browser Console:**
   - Press F12 to open developer tools
   - Look for errors in Console tab
   - Check Network tab for failed requests

3. **Verify Setup:**
   - Run verification script: `./verify-setup.sh`
   - Check all prerequisites are met
   - Ensure all files are present

4. **Clean Start:**
   ```bash
   # Remove generated files
   rm -rf output/*.json
   rm -rf dashboard/data/*.json
   
   # Re-run demo
   ./runner/run-simple-demo.sh
   
   # Restart web server
   cd dashboard
   python3 -m http.server 8080
   ```

5. **Report Issues:**
   - Include error messages
   - Include browser console output
   - Include system information (OS, Java version, Python version)
   - Include steps to reproduce

---

**Last Updated**: 2026-05-30