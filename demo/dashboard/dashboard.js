// Substrait Compliance Dashboard - JavaScript

let leaderboardData = null;
let passRateChart = null;
let distributionChart = null;
let currentComplexityFilter = 'all';
const AUTO_REFRESH_INTERVAL = 30000; // 30 seconds

// Initialize dashboard on page load
document.addEventListener('DOMContentLoaded', function() {
    console.log('Initializing Substrait Compliance Dashboard...');
    loadData();
    
    // Set up auto-refresh
    setInterval(loadData, AUTO_REFRESH_INTERVAL);
});

// Load leaderboard data
async function loadData() {
    console.log('Loading leaderboard data...');
    
    try {
        const response = await fetch('data/leaderboard.json');
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        leaderboardData = await response.json();
        console.log('Data loaded:', leaderboardData);
        
        updateDashboard();
    } catch (error) {
        console.error('Error loading data:', error);
        showError('Failed to load leaderboard data. Please ensure the demo has been run.');
    }
}

// Update all dashboard components
function updateDashboard() {
    if (!leaderboardData) return;
    
    updateHeaderStats();
    updateLeaderboardTable();
    updateCharts();
    updateDetailedResults();
}

// Update header statistics
function updateHeaderStats() {
    document.getElementById('totalEngines').textContent = leaderboardData.totalEngines || 0;
    document.getElementById('avgPassRate').textContent = 
        (leaderboardData.averagePassRate || 0).toFixed(1) + '%';
    
    const lastUpdated = new Date(leaderboardData.lastUpdated);
    document.getElementById('lastUpdated').textContent = 
        lastUpdated.toLocaleTimeString();
}

// Update leaderboard table
function updateLeaderboardTable() {
    const container = document.getElementById('leaderboardTable');
    
    if (!leaderboardData.engines || leaderboardData.engines.length === 0) {
        container.innerHTML = '<div class="error">No engine data available</div>';
        return;
    }
    
    // Calculate filtered statistics for each engine
    const filteredEngines = leaderboardData.engines.map(engine => {
        if (currentComplexityFilter === 'all') {
            return engine;
        }
        
        // Filter would require loading individual reports - for now show all
        // In a real implementation, we'd filter based on complexity
        return engine;
    });
    
    let html = `
        <table>
            <thead>
                <tr>
                    <th class="rank-cell">Rank</th>
                    <th>Engine</th>
                    <th>Version</th>
                    <th>Pass Rate</th>
                    <th>Passed</th>
                    <th>Failed</th>
                    <th>Skipped</th>
                    <th>Status</th>
                </tr>
            </thead>
            <tbody>
    `;
    
    filteredEngines.forEach(engine => {
        const rankEmoji = getRankEmoji(engine.rank);
        const statusBadge = getStatusBadge(engine.passRate);
        
        html += `
            <tr onclick="showEngineDetails('${engine.engineName}')" style="cursor: pointer;">
                <td class="rank-cell">${rankEmoji}</td>
                <td class="engine-name">${engine.engineName}</td>
                <td>${engine.engineVersion}</td>
                <td class="pass-rate">${engine.passRate.toFixed(1)}%</td>
                <td class="text-success">${engine.passed}/${engine.totalTests}</td>
                <td class="text-danger">${engine.failed}</td>
                <td class="text-warning">${engine.skipped}</td>
                <td>${statusBadge}</td>
            </tr>
        `;
    });
    
    html += `
            </tbody>
        </table>
    `;
    
    if (currentComplexityFilter !== 'all') {
        html += `<p style="margin-top: 10px; color: #666; font-style: italic;">
            Note: Filtering by complexity shows all engines. Click on an engine to see filtered query results.
        </p>`;
    }
    
    container.innerHTML = html;
}

// Update charts
function updateCharts() {
    updatePassRateChart();
    updateDistributionChart();
}

// Update pass rate comparison chart
function updatePassRateChart() {
    const ctx = document.getElementById('passRateChart');
    
    if (!ctx) return;
    
    const engines = leaderboardData.engines;
    const labels = engines.map(e => e.engineName);
    const data = engines.map(e => e.passRate);
    const colors = engines.map(e => getChartColor(e.passRate));
    
    if (passRateChart) {
        passRateChart.destroy();
    }
    
    passRateChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Pass Rate (%)',
                data: data,
                backgroundColor: colors,
                borderColor: colors.map(c => c.replace('0.7', '1')),
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return `Pass Rate: ${context.parsed.y.toFixed(1)}%`;
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100,
                    ticks: {
                        callback: function(value) {
                            return value + '%';
                        }
                    }
                }
            }
        }
    });
}

// Update test results distribution chart
function updateDistributionChart() {
    const ctx = document.getElementById('distributionChart');
    
    if (!ctx) return;
    
    const engines = leaderboardData.engines;
    
    if (distributionChart) {
        distributionChart.destroy();
    }
    
    distributionChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: engines.map(e => e.engineName),
            datasets: [{
                label: 'Tests Passed',
                data: engines.map(e => e.passed),
                backgroundColor: [
                    'rgba(76, 175, 80, 0.7)',
                    'rgba(33, 150, 243, 0.7)',
                    'rgba(255, 193, 7, 0.7)'
                ],
                borderColor: [
                    'rgba(76, 175, 80, 1)',
                    'rgba(33, 150, 243, 1)',
                    'rgba(255, 193, 7, 1)'
                ],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    position: 'bottom'
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const engine = engines[context.dataIndex];
                            return `${context.label}: ${engine.passed}/${engine.totalTests} (${engine.passRate.toFixed(1)}%)`;
                        }
                    }
                }
            }
        }
    });
}

// Update detailed results section
function updateDetailedResults() {
    const container = document.getElementById('detailsGrid');
    
    if (!leaderboardData.engines || leaderboardData.engines.length === 0) {
        container.innerHTML = '<div class="error">No detailed results available</div>';
        return;
    }
    
    let html = '';
    
    leaderboardData.engines.forEach(engine => {
        const statusBadge = getStatusBadge(engine.passRate);
        const timestamp = new Date(engine.timestamp).toLocaleString();
        
        html += `
            <div class="detail-card">
                <h3>${engine.engineName} v${engine.engineVersion}</h3>
                <div class="detail-row">
                    <span class="detail-label">Status:</span>
                    <span class="detail-value">${statusBadge}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Pass Rate:</span>
                    <span class="detail-value pass-rate">${engine.passRate.toFixed(1)}%</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Tests Passed:</span>
                    <span class="detail-value text-success">${engine.passed}/${engine.totalTests}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Tests Failed:</span>
                    <span class="detail-value text-danger">${engine.failed}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Tests Skipped:</span>
                    <span class="detail-value text-warning">${engine.skipped}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Last Updated:</span>
                    <span class="detail-value">${timestamp}</span>
                </div>
            </div>
        `;
    });
    
    container.innerHTML = html;
}

// Helper function to get rank emoji
function getRankEmoji(rank) {
    switch(rank) {
        case 1: return '🥇';
        case 2: return '🥈';
        case 3: return '🥉';
        default: return rank;
    }
}

// Helper function to get status badge HTML
function getStatusBadge(passRate) {
    let className, emoji, text;
    
    if (passRate >= 95) {
        className = 'excellent';
        emoji = '🟢';
        text = 'Excellent';
    } else if (passRate >= 85) {
        className = 'good';
        emoji = '🟡';
        text = 'Good';
    } else if (passRate >= 70) {
        className = 'fair';
        emoji = '🟠';
        text = 'Fair';
    } else {
        className = 'poor';
        emoji = '🔴';
        text = 'Needs Work';
    }
    
    return `<span class="status-badge ${className}">${emoji} ${text}</span>`;
}

// Helper function to get chart color based on pass rate
function getChartColor(passRate) {
    if (passRate >= 95) return 'rgba(76, 175, 80, 0.7)';
    if (passRate >= 85) return 'rgba(255, 193, 7, 0.7)';
    if (passRate >= 70) return 'rgba(255, 152, 0, 0.7)';
    return 'rgba(244, 67, 54, 0.7)';
}

// Show error message
function showError(message) {
    const containers = [
        'leaderboardTable',
        'detailsGrid'
    ];
    
    containers.forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.innerHTML = `<div class="error">⚠️ ${message}</div>`;
        }
    });
}

// Filter by query complexity
function filterByComplexity(complexity) {
    currentComplexityFilter = complexity;
    console.log('Filtering by complexity:', complexity);
    
    // Update the dropdown value
    const dropdown = document.getElementById('complexityFilter');
    if (dropdown) {
        dropdown.value = complexity;
        
        // Add visual feedback for active filter
        if (complexity !== 'all') {
            dropdown.style.borderColor = '#2196f3';
            dropdown.style.backgroundColor = '#e3f2fd';
            dropdown.style.fontWeight = 'bold';
        } else {
            dropdown.style.borderColor = '';
            dropdown.style.backgroundColor = '';
            dropdown.style.fontWeight = '';
        }
    }
    
    // Show filter notification
    if (complexity !== 'all') {
        const filterLabel = complexity.replace('_', ' ');
        console.log(`✓ Filter active: ${filterLabel} queries`);
    }
    
    // Note: Leaderboard table doesn't change, but modal will show filtered results
    // This is intentional - we want to see all engines, but drill-down shows filtered queries
}

// Show engine details in modal
async function showEngineDetails(engineName) {
    console.log('=== Loading Engine Details ===');
    console.log('Engine name:', engineName);
    console.log('Current URL:', window.location.href);
    
    const fileName = `${engineName.toLowerCase()}-report.json`;
    const filePath = `output/${fileName}`;
    
    console.log('Fetching:', filePath);
    
    try {
        const response = await fetch(filePath);
        console.log('Response status:', response.status);
        console.log('Response OK:', response.ok);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const engineReport = await response.json();
        console.log('Report loaded successfully!');
        console.log('Test results count:', engineReport.testResults.length);
        
        displayEngineModal(engineReport);
    } catch (error) {
        console.error('=== ERROR ===');
        console.error('Error details:', error);
        console.error('Stack:', error.stack);
        
        alert(`Failed to load detailed results for ${engineName}.\n\nError: ${error.message}\n\nPlease ensure:\n1. Web server is running from dashboard directory\n2. Symlink exists: dashboard/output -> ../output\n3. Run: cd demo/dashboard && ls -la output/\n\nFile path attempted: ${filePath}`);
    }
}

// Display engine details in modal
function displayEngineModal(engineReport) {
    const modal = document.getElementById('queryModal');
    const modalTitle = document.getElementById('modalTitle');
    const queryGrid = document.getElementById('queryGrid');
    
    // Set modal title
    modalTitle.textContent = `${engineReport.engineName} v${engineReport.engineVersion} - Query Details`;
    
    // Filter test results by complexity if needed
    let filteredResults = engineReport.testResults;
    if (currentComplexityFilter !== 'all') {
        filteredResults = engineReport.testResults.filter(t =>
            (t.complexity || 'MEDIUM').toUpperCase() === currentComplexityFilter.toUpperCase()
        );
    }
    
    // Calculate summary statistics
    const totalTests = filteredResults.length;
    const passed = filteredResults.filter(t => t.status === 'PASSED').length;
    const failed = filteredResults.filter(t => t.status === 'FAILED').length;
    const skipped = filteredResults.filter(t => t.status === 'SKIPPED').length;
    const passRate = totalTests > 0 ? (passed / totalTests * 100) : 0;
    const avgExecutionTime = filteredResults
        .filter(t => t.executionTimeMs)
        .reduce((sum, t) => sum + t.executionTimeMs, 0) / totalTests;
    
    // Build modal content
    let html = `
        <div class="engine-summary">
            <div class="summary-item">
                <div class="label">Pass Rate</div>
                <div class="value" style="color: ${getChartColor(passRate).replace('0.7', '1')}">${passRate.toFixed(1)}%</div>
            </div>
            <div class="summary-item">
                <div class="label">Passed</div>
                <div class="value" style="color: #4caf50">${passed}</div>
            </div>
            <div class="summary-item">
                <div class="label">Failed</div>
                <div class="value" style="color: #f44336">${failed}</div>
            </div>
            <div class="summary-item">
                <div class="label">Skipped</div>
                <div class="value" style="color: #ff9800">${skipped}</div>
            </div>
            <div class="summary-item">
                <div class="label">Avg Time</div>
                <div class="value">${avgExecutionTime.toFixed(0)}ms</div>
            </div>
        </div>
        
        <h3 style="margin: 20px 0 15px 0; color: #333;">Query Results</h3>
        <div class="query-grid">
    `;
    
    // Add query cards (filtered)
    filteredResults.forEach(test => {
        const statusClass = test.status.toLowerCase();
        const complexity = test.complexity || 'MEDIUM';
        const complexityClass = `complexity-${complexity.toLowerCase().replace('_', '-')}`;
        
        html += `
            <div class="query-card ${statusClass}">
                <div class="query-card-header">
                    <div class="query-name">${test.testName}</div>
                    <span class="query-complexity ${complexityClass}">${complexity}</span>
                </div>
                <div class="query-details">
                    <div class="detail-row">
                        <span class="detail-label">Status</span>
                        <span class="detail-value">${getStatusBadge(test.status === 'PASSED' ? 100 : 0)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Execution Time</span>
                        <span class="detail-value execution-time">${test.executionTimeMs || 0}ms</span>
                    </div>
        `;
        
        if (test.errorMessage) {
            html += `
                    <div class="error-message">
                        <strong>Error:</strong> ${escapeHtml(test.errorMessage)}
                    </div>
            `;
        }
        
        html += `
                </div>
            </div>
        `;
    });
    
    html += '</div>';
    
    // Add filter info if active
    if (currentComplexityFilter !== 'all') {
        const filterLabel = currentComplexityFilter.replace('_', ' ');
        html = `<div style="background: #e3f2fd; padding: 10px; border-radius: 6px; margin-bottom: 20px; text-align: center;">
            <strong>Filtered by:</strong> ${filterLabel} queries only (${totalTests} queries shown)
        </div>` + html;
    }
    
    // Update the stats in the existing HTML structure
    document.getElementById('modalPassRate').textContent = passRate.toFixed(1) + '%';
    document.getElementById('modalPassed').textContent = passed;
    document.getElementById('modalFailed').textContent = failed;
    document.getElementById('modalAvgTime').textContent = avgExecutionTime.toFixed(0) + 'ms';
    
    // Build query cards HTML
    let cardsHtml = '';
    
    // Add filter info if active
    if (currentComplexityFilter !== 'all') {
        const filterLabel = currentComplexityFilter.replace('_', ' ');
        cardsHtml += `<div style="background: #e3f2fd; padding: 10px; border-radius: 6px; margin-bottom: 20px; text-align: center;">
            <strong>Filtered by:</strong> ${filterLabel} queries only (${totalTests} queries shown)
        </div>`;
    }
    
    // Add query cards
    filteredResults.forEach(test => {
        const statusClass = test.status.toLowerCase();
        const complexity = test.complexity || 'MEDIUM';
        const complexityClass = `complexity-${complexity.toLowerCase().replace('_', '-')}`;
        const testName = test.testName || test.testId;
        
        cardsHtml += `
            <div class="query-card ${statusClass}">
                <div class="query-card-header">
                    <div class="query-name">${testName}</div>
                    <span class="query-complexity ${complexityClass}">${complexity}</span>
                </div>
                <div class="query-details">
                    <div class="detail-row">
                        <span class="detail-label">Status</span>
                        <span class="detail-value">${test.status}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Execution Time</span>
                        <span class="detail-value execution-time">${test.executionTimeMs || 0}ms</span>
                    </div>
        `;
        
        if (test.errorMessage) {
            cardsHtml += `
                    <div class="error-message">
                        <strong>Error:</strong> ${escapeHtml(test.errorMessage)}
                    </div>
            `;
        }
        
        cardsHtml += `
                </div>
            </div>
        `;
    });
    
    queryGrid.innerHTML = cardsHtml;
    modal.style.display = 'block';
}

// Close modal
function closeQueryModal() {
    const modal = document.getElementById('queryModal');
    modal.style.display = 'none';
}

// Close modal when clicking outside
window.onclick = function(event) {
    const modal = document.getElementById('queryModal');
    if (event.target === modal) {
        closeQueryModal();
    }
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Export functions for testing
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        loadData,
        updateDashboard,
        getRankEmoji,
        getStatusBadge,
        getChartColor
    };
}

// Made with Bob
