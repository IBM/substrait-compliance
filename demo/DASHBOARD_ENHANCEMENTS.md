# Dashboard Enhancement Specification

This document outlines planned enhancements to the Substrait Compliance Dashboard based on user requirements.

## Current Dashboard Features (v1.0)

✅ **Implemented:**
- Header with summary statistics
- Leaderboard table with rankings
- Pass rate bar chart
- Test distribution doughnut chart
- Per-engine detail cards
- Color-coded status indicators
- Auto-refresh capability
- Responsive design

## Planned Enhancements (v2.0)

### 1. Query-Level Drill-Down

**Feature**: Click on any engine to see detailed query-by-query results

**Implementation:**
```javascript
// Add click handler to engine rows
function showEngineDetails(engineName) {
    // Fetch individual engine report
    fetch(`../output/${engineName.toLowerCase()}-report.json`)
        .then(r => r.json())
        .then(data => {
            displayQueryModal(data);
        });
}

function displayQueryModal(engineData) {
    // Show modal with:
    // - Query grid (22 queries)
    // - Status for each (✅❌⏭️)
    // - Execution time
    // - Error messages for failures
    // - Complexity indicators
}
```

**UI Components:**
- Modal overlay
- Query grid (7x4 or 11x2 layout)
- Query detail panel
- Close/navigate buttons

### 2. Filter by Query Complexity

**Feature**: Filter leaderboard and results by TPC-H query complexity

**Implementation:**
```javascript
// Add filter dropdown
<select id="complexityFilter" onchange="filterByComplexity()">
    <option value="all">All Queries</option>
    <option value="simple">Simple (Q1, Q6, Q14)</option>
    <option value="medium">Medium (7 queries)</option>
    <option value="complex">Complex (8 queries)</option>
    <option value="very_complex">Very Complex (4 queries)</option>
</select>

function filterByComplexity(level) {
    // Recalculate pass rates based on filtered queries
    // Update all charts and tables
    // Show filtered query count
}
```

**Query Complexity Mapping:**
```javascript
const QUERY_COMPLEXITY = {
    'tpch-q01': 'simple',
    'tpch-q06': 'simple',
    'tpch-q14': 'simple',
    'tpch-q03': 'medium',
    'tpch-q04': 'medium',
    // ... etc
};
```

### 3. Search Functionality

**Feature**: Search/filter engines and queries in real-time

**Implementation:**
```javascript
// Add search box
<input type="text" id="searchBox" 
       placeholder="Search engines or queries..." 
       oninput="performSearch()">

function performSearch() {
    const query = document.getElementById('searchBox').value.toLowerCase();
    
    // Filter leaderboard table
    filterTableRows(query);
    
    // Filter detail cards
    filterDetailCards(query);
    
    // Update result count
    updateSearchResults(query);
}
```

**Search Targets:**
- Engine names
- Engine versions
- Query IDs
- Status (passed/failed/skipped)

### 4. Export Reports

**Feature**: Export data in multiple formats

**Implementation:**

**CSV Export:**
```javascript
function exportToCSV() {
    const csv = [
        ['Rank', 'Engine', 'Version', 'Pass Rate', 'Passed', 'Failed', 'Skipped'],
        ...leaderboardData.engines.map(e => [
            e.rank, e.engineName, e.engineVersion, 
            e.passRate, e.passed, e.failed, e.skipped
        ])
    ].map(row => row.join(',')).join('\n');
    
    downloadFile('compliance-report.csv', csv, 'text/csv');
}
```

**JSON Export:**
```javascript
function exportToJSON() {
    const json = JSON.stringify(leaderboardData, null, 2);
    downloadFile('compliance-report.json', json, 'application/json');
}
```

**PDF Export (using jsPDF):**
```javascript
function exportToPDF() {
    const doc = new jsPDF();
    doc.text('Substrait Compliance Report', 10, 10);
    // Add tables, charts as images
    doc.save('compliance-report.pdf');
}
```

**UI:**
```html
<div class="export-buttons">
    <button onclick="exportToCSV()">📊 Export CSV</button>
    <button onclick="exportToJSON()">📄 Export JSON</button>
    <button onclick="exportToPDF()">📑 Export PDF</button>
    <button onclick="copyToClipboard()">📋 Copy Summary</button>
</div>
```

### 5. Performance Charts

**Feature**: Visualize execution time and performance metrics

**Charts to Add:**

**A. Execution Time Comparison**
```javascript
// Box plot or violin plot showing execution time distribution
new Chart(ctx, {
    type: 'boxplot',
    data: {
        labels: engines.map(e => e.name),
        datasets: [{
            label: 'Execution Time (ms)',
            data: engines.map(e => e.executionTimes)
        }]
    }
});
```

**B. Query Complexity vs Performance**
```javascript
// Scatter plot
new Chart(ctx, {
    type: 'scatter',
    data: {
        datasets: engines.map(engine => ({
            label: engine.name,
            data: engine.queries.map(q => ({
                x: getComplexityScore(q.id),
                y: q.executionTimeMs
            }))
        }))
    }
});
```

**C. Performance Distribution**
```javascript
// Histogram
new Chart(ctx, {
    type: 'bar',
    data: {
        labels: ['0-50ms', '50-100ms', '100-200ms', '200-500ms', '500ms+'],
        datasets: [{
            label: 'Query Count',
            data: calculateDistribution(executionTimes)
        }]
    }
});
```

### 6. Comparison Views

**Feature**: Side-by-side engine comparison

**Implementation:**

**A. Comparison Matrix**
```html
<table class="comparison-matrix">
    <thead>
        <tr>
            <th>Query</th>
            <th>MockDB</th>
            <th>FastDB</th>
            <th>CloudDB</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>Q01</td>
            <td class="passed">✅ 127ms</td>
            <td class="passed">✅ 89ms</td>
            <td class="passed">✅ 203ms</td>
        </tr>
        <!-- ... -->
    </tbody>
</table>
```

**B. Capability Radar Chart**
```javascript
new Chart(ctx, {
    type: 'radar',
    data: {
        labels: ['Simple', 'Medium', 'Complex', 'Very Complex', 'Performance'],
        datasets: engines.map(engine => ({
            label: engine.name,
            data: [
                engine.simplePassRate,
                engine.mediumPassRate,
                engine.complexPassRate,
                engine.veryComplexPassRate,
                engine.avgPerformanceScore
            ]
        }))
    }
});
```

**C. Head-to-Head Comparison**
```javascript
function compareEngines(engine1, engine2) {
    return {
        winner: calculateWinner(engine1, engine2),
        metrics: {
            passRate: [engine1.passRate, engine2.passRate],
            avgTime: [engine1.avgTime, engine2.avgTime],
            reliability: [engine1.reliability, engine2.reliability]
        },
        queryByQuery: compareQueries(engine1, engine2)
    };
}
```

### 7. Historical Trends

**Feature**: Track compliance over time

**Data Structure:**
```javascript
// Store in localStorage or backend
const historicalData = {
    'MockDB': [
        { date: '2026-04-01', passRate: 82.5, version: '1.0.0' },
        { date: '2026-04-08', passRate: 84.1, version: '1.0.1' },
        { date: '2026-04-15', passRate: 85.4, version: '1.0.0' }
    ],
    // ...
};
```

**Trend Chart:**
```javascript
new Chart(ctx, {
    type: 'line',
    data: {
        labels: dates,
        datasets: engines.map(engine => ({
            label: engine.name,
            data: engine.historicalPassRates,
            borderColor: engine.color,
            fill: false
        }))
    },
    options: {
        scales: {
            y: {
                min: 0,
                max: 100,
                title: { text: 'Pass Rate (%)' }
            }
        }
    }
});
```

### 8. Share Results

**Feature**: Share compliance results easily

**Implementation:**

**A. Generate Shareable Link**
```javascript
function generateShareLink() {
    const params = new URLSearchParams({
        engines: selectedEngines.join(','),
        date: currentDate,
        view: currentView
    });
    const url = `${window.location.origin}?${params}`;
    copyToClipboard(url);
    showNotification('Link copied to clipboard!');
}
```

**B. Social Media Sharing**
```html
<div class="share-buttons">
    <button onclick="shareTwitter()">🐦 Twitter</button>
    <button onclick="shareLinkedIn()">💼 LinkedIn</button>
    <button onclick="shareEmail()">📧 Email</button>
</div>
```

**C. Embed Code**
```javascript
function generateEmbedCode() {
    return `<iframe src="${embedUrl}" width="800" height="600"></iframe>`;
}
```

## Implementation Priority

### Phase 1 (High Priority)
1. ✅ Query-level drill-down - Most requested
2. ✅ Filter by complexity - Easy to implement
3. ✅ Search functionality - High usability impact
4. ✅ Export to CSV - Useful for analysis

### Phase 2 (Medium Priority)
5. ⏳ Performance charts - Good visualization
6. ⏳ Comparison views - Useful for evaluation
7. ⏳ Export to PDF - Nice to have

### Phase 3 (Lower Priority)
8. ⏳ Historical trends - Requires data persistence
9. ⏳ Share results - Social features
10. ⏳ Advanced filters - Power user features

## Technical Requirements

### Dependencies
```html
<!-- Add to index.html -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-chart-boxplot@4.2.3/dist/chartjs-chart-boxplot.min.js"></script>
```

### Browser Support
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

### Performance Considerations
- Lazy load query details
- Virtualize large tables
- Debounce search input
- Cache computed values
- Optimize chart rendering

## Testing Plan

### Unit Tests
- Filter functions
- Search algorithms
- Export functions
- Data transformations

### Integration Tests
- Modal interactions
- Chart updates
- Filter combinations
- Export formats

### User Acceptance Tests
- Drill-down workflow
- Search usability
- Export functionality
- Mobile responsiveness

## Documentation Updates

- Update README with new features
- Add user guide for advanced features
- Create video tutorials
- Update screenshots

## Estimated Effort

- **Phase 1**: 1-2 weeks
- **Phase 2**: 1-2 weeks
- **Phase 3**: 1 week
- **Total**: 3-5 weeks full-time development

## Success Metrics

- User engagement with drill-down feature
- Export usage statistics
- Search query patterns
- Performance impact on load time
- User feedback scores

---

**This specification provides a complete roadmap for enhancing the dashboard. Implementation can be done incrementally based on priorities and resources.**