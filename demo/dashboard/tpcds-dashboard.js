// TPC-DS Compliance Dashboard - JavaScript

let leaderboardData = null;
let passRateChart = null;
let distributionChart = null;
let sharedSummaryData = null;
const AUTO_REFRESH_INTERVAL = 30000; // 30 seconds

// Initialize dashboard on page load
document.addEventListener('DOMContentLoaded', function() {
    console.log('Initializing TPC-DS Compliance Dashboard...');
    loadData();
    
    // Set up auto-refresh
    setInterval(loadData, AUTO_REFRESH_INTERVAL);
});

// Load leaderboard data
async function loadData() {
    console.log('Loading TPC-DS leaderboard data...');
    
    try {
        const [leaderboardResponse, summaryResponse] = await Promise.all([
            fetch('data/tpcds_leaderboard.json'),
            fetch('data/summary.json')
        ]);
        
        if (!leaderboardResponse.ok) {
            throw new Error(`HTTP error! status: ${leaderboardResponse.status}`);
        }
        
        leaderboardData = await leaderboardResponse.json();
        sharedSummaryData = summaryResponse.ok ? await summaryResponse.json() : null;
        console.log('Data loaded:', leaderboardData);
        
        updateDashboard();
    } catch (error) {
        console.error('Error loading data:', error);
        showError('Failed to load TPC-DS leaderboard data. Please ensure the TPC-DS tests have been run.');
    }
}

// Update all dashboard components
function updateDashboard() {
    if (!leaderboardData) return;
    
    updateHeaderStats();
    updateSuiteSummary();
    updateLeaderboardTable();
    updateCharts();
    updateDetailedResults();
}

// Update header statistics
function updateHeaderStats() {
    document.getElementById('totalEngines').textContent = leaderboardData.totalEngines || 0;
    document.getElementById('avgPassRate').textContent =
        (leaderboardData.averagePassRate || 0).toFixed(1) + '%';
    
    const lastUpdatedValue = (sharedSummaryData && sharedSummaryData.lastUpdated) || leaderboardData.lastUpdated;
    const lastUpdated = new Date(lastUpdatedValue);
    document.getElementById('lastUpdated').textContent =
        isNaN(lastUpdated.getTime()) ? '-' : lastUpdated.toLocaleTimeString();
}

function updateSuiteSummary() {
    const container = document.getElementById('suiteSummaryGrid');
    if (!container) return;
    
    if (!sharedSummaryData || !sharedSummaryData.engines || sharedSummaryData.engines.length === 0) {
        container.innerHTML = '<div class="error">No cross-suite summary available</div>';
        return;
    }
    
    container.innerHTML = sharedSummaryData.engines.map(engine => `
        <div class="detail-card">
            <h3>${engine.engineName}</h3>
            <div class="detail-row">
                <span class="detail-label">TPC-H Pass Rate:</span>
                <span class="detail-value">${Number(engine.tpch?.passRate || 0).toFixed(1)}%</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">TPC-DS Pass Rate:</span>
                <span class="detail-value">${Number(engine.tpcds?.passRate || 0).toFixed(1)}%</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Function Pass Rate:</span>
                <span class="detail-value">${Number(engine.functions?.passRate || 0).toFixed(1)}%</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">TPC-H Tests:</span>
                <span class="detail-value">${engine.tpch?.passed || 0}/${engine.tpch?.totalTests || 0}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">TPC-DS Tests:</span>
                <span class="detail-value">${engine.tpcds?.passed || 0}/${engine.tpcds?.totalTests || 0}</span>
            </div>
            <div class="detail-row">
                <span class="detail-label">Function Tests:</span>
                <span class="detail-value">${engine.functions?.passed || 0}/${engine.functions?.totalTests || 0}</span>
            </div>
        </div>
    `).join('');
}

// Update leaderboard table
function updateLeaderboardTable() {
    const container = document.getElementById('leaderboardTable');
    
    if (!leaderboardData.engines || leaderboardData.engines.length === 0) {
        container.innerHTML = '<div class="error">No engine data available</div>';
        return;
    }
    
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
    
    leaderboardData.engines.forEach(engine => {
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
    const tpcdsData = engines.map(e => e.passRate);
    const tpchData = engines.map(e => getTpchMetrics(e.engineName).passRate);
    const functionData = engines.map(e => getFunctionMetrics(e.engineName).passRate);
    const colors = engines.map(e => getChartColor(e.passRate));
    
    if (passRateChart) {
        passRateChart.destroy();
    }
    
    passRateChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'TPC-H Pass Rate (%)',
                    data: tpchData,
                    backgroundColor: 'rgba(33, 150, 243, 0.55)',
                    borderColor: 'rgba(33, 150, 243, 1)',
                    borderWidth: 2
                },
                {
                    label: 'TPC-DS Pass Rate (%)',
                    data: tpcdsData,
                    backgroundColor: colors,
                    borderColor: colors.map(c => c.replace('0.7', '1')),
                    borderWidth: 2
                },
                {
                    label: 'Function Pass Rate (%)',
                    data: functionData,
                    backgroundColor: 'rgba(156, 39, 176, 0.55)',
                    borderColor: 'rgba(156, 39, 176, 1)',
                    borderWidth: 2
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            return `${context.dataset.label}: ${context.parsed.y.toFixed(1)}%`;
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
                    'rgba(255, 193, 7, 0.7)',
                    'rgba(156, 39, 176, 0.7)',
                    'rgba(255, 87, 34, 0.7)'
                ],
                borderColor: [
                    'rgba(76, 175, 80, 1)',
                    'rgba(33, 150, 243, 1)',
                    'rgba(255, 193, 7, 1)',
                    'rgba(156, 39, 176, 1)',
                    'rgba(255, 87, 34, 1)'
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
                            const tpchMetrics = getTpchMetrics(engine.engineName);
                            const functionMetrics = getFunctionMetrics(engine.engineName);
                            return [
                                `${context.label}:`,
                                `TPC-H: ${tpchMetrics.passed}/${tpchMetrics.totalTests} (${tpchMetrics.passRate.toFixed(1)}%)`,
                                `TPC-DS: ${engine.passed}/${engine.totalTests} (${engine.passRate.toFixed(1)}%)`,
                                `Functions: ${functionMetrics.passed}/${functionMetrics.totalTests} (${functionMetrics.passRate.toFixed(1)}%)`
                            ];
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
        const tpchMetrics = getTpchMetrics(engine.engineName);
        const functionMetrics = getFunctionMetrics(engine.engineName);
        const combinedPassRate = getCombinedPassRate(engine.engineName, engine.passRate);
        
        html += `
            <div class="detail-card">
                <h3>${engine.engineName} v${engine.engineVersion}</h3>
                <div class="detail-row">
                    <span class="detail-label">TPC-DS Status:</span>
                    <span class="detail-value">${statusBadge}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">TPC-DS Pass Rate:</span>
                    <span class="detail-value pass-rate">${engine.passRate.toFixed(1)}%</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">TPC-DS Tests:</span>
                    <span class="detail-value text-success">${engine.passed}/${engine.totalTests}</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">TPC-H Pass Rate:</span>
                    <span class="detail-value pass-rate">${tpchMetrics.passRate.toFixed(1)}%</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Function Pass Rate:</span>
                    <span class="detail-value pass-rate">${functionMetrics.passRate.toFixed(1)}%</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">Combined Avg Pass Rate:</span>
                    <span class="detail-value">${combinedPassRate.toFixed(1)}%</span>
                </div>
                <div class="detail-row">
                    <span class="detail-label">TPC-DS Failures / Skips:</span>
                    <span class="detail-value"><span class="text-danger">${engine.failed}</span> / <span class="text-warning">${engine.skipped}</span></span>
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

function getTpchMetrics(engineName) {
    if (!sharedSummaryData || !Array.isArray(sharedSummaryData.engines)) {
        return { passRate: 0, totalTests: 0, passed: 0, failed: 0 };
    }
    
    const engineSummary = sharedSummaryData.engines.find(engine => engine.engineName === engineName);
    if (!engineSummary || !engineSummary.tpch) {
        return { passRate: 0, totalTests: 0, passed: 0, failed: 0 };
    }
    
    return {
        passRate: Number(engineSummary.tpch.passRate || 0),
        totalTests: Number(engineSummary.tpch.totalTests || 0),
        passed: Number(engineSummary.tpch.passed || 0),
        failed: Number(engineSummary.tpch.failed || 0)
    };
}

function getFunctionMetrics(engineName) {
    if (!sharedSummaryData || !Array.isArray(sharedSummaryData.engines)) {
        return { passRate: 0, totalTests: 0, passed: 0, failed: 0 };
    }
    
    const engineSummary = sharedSummaryData.engines.find(engine => engine.engineName === engineName);
    if (!engineSummary || !engineSummary.functions) {
        return { passRate: 0, totalTests: 0, passed: 0, failed: 0 };
    }
    
    return {
        passRate: Number(engineSummary.functions.passRate || 0),
        totalTests: Number(engineSummary.functions.totalTests || 0),
        passed: Number(engineSummary.functions.passed || 0),
        failed: Number(engineSummary.functions.failed || 0)
    };
}

function getCombinedPassRate(engineName, tpcdsPassRate) {
    const tpchMetrics = getTpchMetrics(engineName);
    const functionMetrics = getFunctionMetrics(engineName);
    
    let count = 1;
    let total = Number(tpcdsPassRate || 0);
    
    if (tpchMetrics.totalTests > 0) {
        total += tpchMetrics.passRate;
        count++;
    }
    
    if (functionMetrics.totalTests > 0) {
        total += functionMetrics.passRate;
        count++;
    }
    
    return total / count;
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

// Show engine details in modal
async function showEngineDetails(engineName) {
    console.log('=== Loading TPC-DS Engine Details ===');
    console.log('Engine name:', engineName);
    
    const fileName = `${engineName.toLowerCase()}-tpcds-report.json`;
    const filePath = `output/${fileName}`;
    
    console.log('Fetching:', filePath);
    
    try {
        const response = await fetch(filePath);
        console.log('Response status:', response.status);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const engineReport = await response.json();
        console.log('Report loaded successfully!');
        
        displayEngineModal(engineReport);
    } catch (error) {
        console.error('=== ERROR ===');
        console.error('Error details:', error);
        
        alert(`Failed to load detailed TPC-DS results for ${engineName}.\n\nError: ${error.message}\n\nFile path attempted: ${filePath}`);
    }
}

// Display engine details in modal
function displayEngineModal(engineReport) {
    const modal = document.getElementById('queryModal');
    const modalTitle = document.getElementById('modalTitle');
    const queryGrid = document.getElementById('queryGrid');
    
    modalTitle.textContent = `${engineReport.engineName} v${engineReport.engineVersion} - TPC-DS Query Details`;
    
    const filteredResults = engineReport.testResults;
    
    const totalTests = filteredResults.length;
    const passed = filteredResults.filter(t => t.status === 'PASSED').length;
    const failed = filteredResults.filter(t => t.status === 'FAILED').length;
    const skipped = filteredResults.filter(t => t.status === 'SKIPPED').length;
    const passRate = totalTests > 0 ? (passed / totalTests * 100) : 0;
    const avgExecutionTime = filteredResults
        .filter(t => t.executionTimeMs)
        .reduce((sum, t) => sum + t.executionTimeMs, 0) / totalTests;
    
    document.getElementById('modalPassRate').textContent = passRate.toFixed(1) + '%';
    document.getElementById('modalPassed').textContent = passed;
    document.getElementById('modalFailed').textContent = failed;
    document.getElementById('modalAvgTime').textContent = avgExecutionTime.toFixed(0) + 'ms';
    
    let cardsHtml = '';
    
    filteredResults.forEach(test => {
        const statusClass = test.status.toLowerCase();
        const testName = test.testName || test.testId;
        
        cardsHtml += `
            <div class="query-card ${statusClass}">
                <div class="query-card-header">
                    <div class="query-name">${testName}</div>
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

