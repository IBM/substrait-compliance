# Quick Start Guide - Substrait Compliance Demo

Get the demo running in 5 minutes!

## Prerequisites

- ✅ Java 11 or higher
- ✅ Python 3.8+ (optional, for enhanced leaderboard)
- ✅ Web browser

## Step 1: Run the Demo

```bash
# Navigate to demo directory
cd demo

# Run the simplified demo script (recommended)
./runner/run-simple-demo.sh
```

The script will:
1. Compile the standalone demo runner
2. Generate mock compliance reports for 3 engines
3. Create leaderboard data
4. Display results

**Note**: This simplified demo generates realistic mock data without requiring full SDK compilation. For a full integration demo with actual SDK usage, see the advanced section below.

## Step 2: View the Dashboard

### ⚠️ Important: Use a Web Server

Due to browser security restrictions (CORS), the dashboard **must** be served via HTTP, not opened as a local file.

### Recommended: Local Web Server
```bash
cd dashboard
python3 -m http.server 8080
```

Then open in your browser: **http://localhost:8080**

### Alternative Ports (if 8080 is in use)
```bash
# Try port 8081
python3 -m http.server 8081

# Try port 9000
python3 -m http.server 9000

# Or any available port
python3 -m http.server 3000
```

### Alternative Web Servers

**Python 2:**
```bash
cd dashboard
python -m SimpleHTTPServer 8080
```

**Node.js (if installed):**
```bash
cd dashboard
npx http-server -p 8080
```

**PHP (if installed):**
```bash
cd dashboard
php -S localhost:8080
```

## What You'll See

### Console Output
```
================================================================================
Substrait Compliance Framework - Demo
================================================================================

📦 Loading TPC-H test suite...
✅ Loaded test suite: TPC-H
   Total test cases: 22

--------------------------------------------------------------------------------
🔧 Testing: MockDB v1.0.0
   Total Tests: 22
   ✅ Passed: 19
   ❌ Failed: 3
   ⏭️  Skipped: 0
   📊 Pass Rate: 86.4%
   💾 Report saved: output/mockdb-report.json

🔧 Testing: FastDB v2.5.0
   Total Tests: 22
   ✅ Passed: 21
   ❌ Failed: 1
   ⏭️  Skipped: 0
   📊 Pass Rate: 95.5%
   💾 Report saved: output/fastdb-report.json

🔧 Testing: CloudDB v3.1.0
   Total Tests: 22
   ✅ Passed: 17
   ❌ Failed: 5
   ⏭️  Skipped: 0
   📊 Pass Rate: 77.3%
   💾 Report saved: output/clouddb-report.json

================================================================================
📊 Demo Summary
================================================================================

Engine          Version    Pass Rate    Status
--------------------------------------------------------------------------------
🥇 FastDB       2.5.0      95.5%        🟢 Excellent
🥈 MockDB       1.0.0      86.4%        🟡 Good
🥉 CloudDB      3.1.0      77.3%        🟠 Fair

✅ Demo completed successfully!
```

### Dashboard Features

1. **Header Stats**
   - Total engines tested
   - Average pass rate
   - Last update time

2. **Leaderboard Table**
   - Rankings with medals (🥇🥈🥉)
   - Pass rates and test counts
   - Status indicators (🟢🟡🟠🔴)

3. **Visual Charts**
   - Bar chart: Pass rate comparison
   - Doughnut chart: Test distribution

4. **Detailed Results**
   - Per-engine breakdown
   - Individual test statistics
   - Timestamps

## Generated Files

```
demo/
├── output/
│   ├── mockdb-report.json      # MockDB compliance report
│   ├── fastdb-report.json      # FastDB compliance report
│   ├── clouddb-report.json     # CloudDB compliance report
│   └── leaderboard.json        # Aggregated leaderboard
└── dashboard/
    └── data/
        └── leaderboard.json    # Dashboard data (copy)
```

## Troubleshooting

### Issue: Permission Denied
```bash
chmod +x runner/run-demo.sh
./runner/run-demo.sh
```

### Issue: SDK Not Found
```bash
# Build SDK first
cd ../sdk/java
./gradlew build
cd ../../demo
```

### Issue: Dashboard Shows "Failed to load"
```bash
# Ensure demo has been run
./runner/run-demo.sh

# Check if data file exists
ls -la dashboard/data/leaderboard.json
```

### Issue: Java Version
```bash
# Check Java version
java -version

# Should be 11 or higher
```

## Next Steps

1. **Explore the Code**
   - Review mock engines: `engines/*.java`
   - Check runner logic: `runner/DemoRunner.java`
   - Examine dashboard: `dashboard/*.html/css/js`

2. **Customize the Demo**
   - Modify pass rates in engine implementations
   - Add more mock engines
   - Change test suite

3. **Integrate Real Engines**
   - Replace mock implementations with real database engines
   - Use actual Substrait plan execution
   - Connect to real databases

## Demo Architecture

```
┌─────────────────┐
│  Mock Engines   │
│  - MockDB       │
│  - FastDB       │
│  - CloudDB      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Compliance SDK  │
│  - Test Runner  │
│  - Report Gen   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  JSON Reports   │
│  - Individual   │
│  - Leaderboard  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Web Dashboard  │
│  - Charts       │
│  - Tables       │
└─────────────────┘
```

## Support

- **Full Documentation**: See `README.md`
- **Main Project**: See `../README.md`
- **SDK Docs**: See `../sdk/*/README.md`

## Tips

- Run the demo multiple times to see consistent results
- Modify engine pass rates to simulate improvements
- Use browser dev tools to inspect dashboard data
- Check console logs for debugging

---

**Ready to start?** Run: `./runner/run-demo.sh`