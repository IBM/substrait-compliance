# 🚀 START HERE - Demo Instructions

## ⚠️ IMPORTANT: Run Demo First!

Before viewing the dashboard, you **must** run the demo to generate the data files.

## Step-by-Step Instructions

### Step 1: Run the Demo (REQUIRED)
```bash
# Make sure you're in the demo directory
cd demo

# Run the demo script
./runner/run-simple-demo.sh
```

**Expected Output:**
```
================================================================================
Substrait Compliance Framework - Demo
================================================================================

🔧 Testing: MockDB v1.0.0
   ✅ Passed: 19/22 (85.4%)
   💾 Report saved: output/mockdb-report.json

🔧 Testing: FastDB v2.5.0
   ✅ Passed: 21/22 (95.5%)
   💾 Report saved: output/fastdb-report.json

🔧 Testing: CloudDB v3.1.0
   ✅ Passed: 17/22 (77.3%)
   💾 Report saved: output/clouddb-report.json

📈 Generating leaderboard...
   💾 Leaderboard saved: output/leaderboard.json
   💾 Dashboard data updated: dashboard/data/leaderboard.json

✅ Demo completed successfully!
```

### Step 2: Verify Files Were Created
```bash
# Check that reports were generated
ls -la output/
# Should show: mockdb-report.json, fastdb-report.json, clouddb-report.json, leaderboard.json

# Check that dashboard data was created
ls -la dashboard/data/
# Should show: leaderboard.json
```

### Step 3: Start Web Server
```bash
# Navigate to dashboard directory
cd dashboard

# Start Python web server
python3 -m http.server 8080
```

**If port 8080 is in use, try:**
```bash
python3 -m http.server 8081
# or
python3 -m http.server 9000
```

### Step 4: Open Dashboard
Open your web browser and go to:
```
http://localhost:8080
```

(Or whatever port you used in Step 3)

## ✅ What You Should See

### In the Dashboard:
- **Header**: Shows 3 engines, average pass rate ~86%
- **Leaderboard Table**: Rankings with 🥇🥈🥉 medals
- **Bar Chart**: Pass rate comparison
- **Doughnut Chart**: Test distribution
- **Detail Cards**: Per-engine breakdowns

### Rankings:
```
🥇 FastDB  - 95.5% (🟢 Excellent)
🥈 MockDB  - 85.4% (🟡 Good)
🥉 CloudDB - 77.3% (🟠 Fair)
```

## 🔧 Troubleshooting

### Problem: "Failed to load leaderboard data"
**Cause**: Demo wasn't run yet
**Solution**: Go back to Step 1 and run `./runner/run-simple-demo.sh`

### Problem: "Address already in use"
**Cause**: Port 8080 is already being used
**Solution**: Use a different port (8081, 9000, 3000, etc.)

### Problem: Dashboard shows but no data
**Cause**: Opened as file:// instead of http://
**Solution**: Must use web server (Step 3)

### Problem: Permission denied on run-simple-demo.sh
**Solution**: 
```bash
chmod +x runner/run-simple-demo.sh
./runner/run-simple-demo.sh
```

## 📁 Expected File Structure After Running

```
demo/
├── output/
│   ├── mockdb-report.json      ✅ Generated
│   ├── fastdb-report.json      ✅ Generated
│   ├── clouddb-report.json     ✅ Generated
│   └── leaderboard.json        ✅ Generated
└── dashboard/
    └── data/
        └── leaderboard.json    ✅ Generated
```

## 🎯 Quick Commands

```bash
# Full workflow in one go:
cd demo
./runner/run-simple-demo.sh
cd dashboard
python3 -m http.server 8080
# Then open http://localhost:8080 in browser
```

## 📚 More Information

- **QUICKSTART.md** - 5-minute guide
- **README.md** - Full documentation
- **DEMO_SUCCESS.md** - Success verification guide

---

**Remember**: Always run the demo script BEFORE trying to view the dashboard!