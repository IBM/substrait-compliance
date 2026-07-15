#!/usr/bin/env node
/**
 * Dashboard population tests — runnable with plain Node.js (no extra deps).
 *
 * Tests three things:
 *   1. Unit — tier / badge / colour helpers in dashboard.js
 *   2. Data integrity — JSON files the dashboard fetches are well-formed
 *   3. Round-trip — re-run function_test_demo.py and verify summary.json is updated
 *
 * Usage (from repo root or demo/):
 *   node demo/dashboard/test-dashboard.js
 *   # or from demo/dashboard/
 *   node test-dashboard.js
 */

'use strict';

const fs   = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// ── locate repo root ──────────────────────────────────────────────────────────
const HERE     = __dirname;                        // demo/dashboard
const DEMO_DIR = path.resolve(HERE, '..');         // demo/
const ROOT_DIR = path.resolve(DEMO_DIR, '..');     // repo root

// ── minimal test harness ──────────────────────────────────────────────────────
let passed = 0, failed = 0;
const failures = [];

function test(label, fn) {
    try {
        fn();
        console.log(`  ✅  ${label}`);
        passed++;
    } catch (e) {
        console.log(`  ❌  ${label}`);
        console.log(`       ${e.message}`);
        failures.push({ label, error: e.message });
        failed++;
    }
}

function assert(cond, msg) {
    if (!cond) throw new Error(msg || 'assertion failed');
}

function assertEqual(a, b, msg) {
    if (a !== b) throw new Error(msg || `expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);
}

// ── load dashboard helpers (works because module.exports is present) ──────────
// Stub the browser globals dashboard.js references at module level
global.document = {
    addEventListener: () => {},
    getElementById:   () => null,
};
global.window = {};
global.Chart = function() {};

const dash = require('./dashboard.js');
const { getTier, getStatusBadge, getChartColor } = dash;

// ─────────────────────────────────────────────────────────────────────────────
// 1. UNIT TESTS — tier helper
// ─────────────────────────────────────────────────────────────────────────────
console.log('\n── 1. Fidelity tier thresholds ──────────────────────────────────');

test('getTier: 100% → verified',  () => assertEqual(getTier(100), 'verified'));
test('getTier: 95% → verified',   () => assertEqual(getTier(95),  'verified'));
test('getTier: 94% → edge',       () => assertEqual(getTier(94),  'edge'));
test('getTier: 80% → edge',       () => assertEqual(getTier(80),  'edge'));
test('getTier: 79% → basic',      () => assertEqual(getTier(79),  'basic'));
test('getTier: 60% → basic',      () => assertEqual(getTier(60),  'basic'));
test('getTier: 59% → none',       () => assertEqual(getTier(59),  'none'));
test('getTier: 0% → none',        () => assertEqual(getTier(0),   'none'));

// ── status badge ──────────────────────────────────────────────────────────────
console.log('\n── 2. Status badge HTML ──────────────────────────────────────────');

test('badge 100% contains VERIFIED',  () => assert(getStatusBadge(100).includes('VERIFIED')));
test('badge 100% contains tier-verified', () => assert(getStatusBadge(100).includes('tier-verified')));
test('badge 85% contains EDGE',       () => assert(getStatusBadge(85).includes('EDGE')));
test('badge 70% contains BASIC',      () => assert(getStatusBadge(70).includes('BASIC')));
test('badge 45% contains NONE',       () => assert(getStatusBadge(45).includes('NONE')));
test('badge is a <span>',             () => assert(getStatusBadge(90).startsWith('<span')));

// ── chart colour ──────────────────────────────────────────────────────────────
console.log('\n── 3. Chart colour mapping ───────────────────────────────────────');

test('colour 95% → green (verified)',  () => assert(getChartColor(95).includes('76, 175, 80')));
test('colour 85% → blue (edge)',       () => assert(getChartColor(85).includes('33, 150, 243')));
test('colour 65% → yellow (basic)',    () => assert(getChartColor(65).includes('255, 193, 7')));
test('colour 50% → red (none)',        () => assert(getChartColor(50).includes('244, 67, 54')));

// ─────────────────────────────────────────────────────────────────────────────
// 2. DATA INTEGRITY — JSON files the dashboard loads
// ─────────────────────────────────────────────────────────────────────────────
console.log('\n── 4. Data file integrity ────────────────────────────────────────');

function loadJSON(rel) {
    const full = path.resolve(DEMO_DIR, rel);
    assert(fs.existsSync(full), `file missing: ${full}`);
    return JSON.parse(fs.readFileSync(full, 'utf8'));
}

test('dashboard/data/leaderboard.json is valid JSON', () => {
    const lb = loadJSON('dashboard/data/leaderboard.json');
    assert(Array.isArray(lb.engines), 'engines must be an array');
    assert(lb.engines.length > 0, 'must have at least one engine');
    lb.engines.forEach(e => {
        assert(typeof e.engineName  === 'string', 'engineName must be a string');
        assert(typeof e.passRate    === 'number', 'passRate must be a number');
        assert(typeof e.passed      === 'number', 'passed must be a number');
        assert(typeof e.totalTests  === 'number', 'totalTests must be a number');
        assert(e.passRate >= 0 && e.passRate <= 100, 'passRate must be 0–100');
    });
});

test('dashboard/data/summary.json is valid JSON', () => {
    const s = loadJSON('dashboard/data/summary.json');
    assert(Array.isArray(s.engines), 'engines must be an array');
    assert(s.engines.length > 0, 'must have at least one engine');
    s.engines.forEach(e => {
        assert(typeof e.engineName === 'string', 'engineName missing');
        assert(e.tpch,      'tpch block missing');
        assert(e.functions, 'functions block missing');
    });
});

test('output/function_tests_summary.json is valid JSON', () => {
    const s = loadJSON('output/function_tests_summary.json');
    assert(Array.isArray(s.engines), 'engines must be an array');
    s.engines.forEach(e => {
        assert(typeof e.engine        === 'string', 'engine name missing');
        assert(typeof e.totalTests    === 'number', 'totalTests missing');
        assert(typeof e.totalPassed   === 'number', 'totalPassed missing');
        assert(typeof e.overallPassRate === 'number', 'overallPassRate missing');
        assert(typeof e.categories    === 'object',  'categories missing');
        Object.entries(e.categories).forEach(([cat, stats]) => {
            assert(typeof stats.passRate === 'number', `passRate missing for ${cat}`);
            assert(stats.passRate >= 0 && stats.passRate <= 100, `passRate out of range for ${cat}`);
        });
    });
});

test('engine reports exist for all 5 engines', () => {
    ['mockdb','fastdb','clouddb','duckdb','postgresql'].forEach(name => {
        const p = path.resolve(DEMO_DIR, `output/${name}-report.json`);
        assert(fs.existsSync(p), `missing: ${name}-report.json`);
        const r = JSON.parse(fs.readFileSync(p, 'utf8'));
        assert(typeof r.passRate === 'number', `passRate missing in ${name}-report.json`);
        assert(Array.isArray(r.testResults), `testResults missing in ${name}-report.json`);
    });
});

test('leaderboard ranks are contiguous from 1', () => {
    const lb = loadJSON('dashboard/data/leaderboard.json');
    const ranks = lb.engines.map(e => e.rank).sort((a, b) => a - b);
    ranks.forEach((r, i) => assertEqual(r, i + 1, `rank gap at position ${i + 1}`));
});

test('summary and leaderboard have the same engine names', () => {
    const lb  = loadJSON('dashboard/data/leaderboard.json');
    const sum = loadJSON('dashboard/data/summary.json');
    const lbNames  = lb.engines.map(e => e.engineName).sort();
    const sumNames = sum.engines.map(e => e.engineName).sort();
    assertEqual(JSON.stringify(lbNames), JSON.stringify(sumNames),
        `leaderboard engines: ${lbNames} | summary engines: ${sumNames}`);
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. ROUND-TRIP — regenerate data, verify summary.json is updated
// ─────────────────────────────────────────────────────────────────────────────
console.log('\n── 5. Round-trip: regenerate function test data ──────────────────');

test('python3 function_test_demo.py runs without error', () => {
    const script = path.resolve(DEMO_DIR, 'runner/function_test_demo.py');
    try {
        execSync(`python3 "${script}"`, { cwd: DEMO_DIR, stdio: 'pipe', timeout: 60000 });
    } catch (e) {
        throw new Error(`Demo script failed:\n${e.stderr?.toString().trim() || e.message}`);
    }
});

test('function_tests_summary.json is refreshed after demo run', () => {
    const summaryPath = path.resolve(DEMO_DIR, 'output/function_tests_summary.json');
    const stat = fs.statSync(summaryPath);
    const ageMs = Date.now() - stat.mtimeMs;
    assert(ageMs < 30000, `file not updated — last modified ${(ageMs/1000).toFixed(1)}s ago`);
});

test('dashboard/data/summary.json is refreshed after demo run', () => {
    const summaryPath = path.resolve(DEMO_DIR, 'dashboard/data/summary.json');
    const stat = fs.statSync(summaryPath);
    const ageMs = Date.now() - stat.mtimeMs;
    assert(ageMs < 30000, `file not updated — last modified ${(ageMs/1000).toFixed(1)}s ago`);
});

test('all engines present in refreshed function_tests_summary.json', () => {
    const s = loadJSON('output/function_tests_summary.json');
    const names = s.engines.map(e => e.engine);
    ['MockDB','FastDB','CloudDB','DuckDB','PostgreSQL'].forEach(name =>
        assert(names.includes(name), `${name} missing from function_tests_summary.json`)
    );
});

// ─────────────────────────────────────────────────────────────────────────────
// RESULTS
// ─────────────────────────────────────────────────────────────────────────────
console.log('\n' + '─'.repeat(60));
console.log(`Results: ${passed} passed, ${failed} failed`);
if (failures.length) {
    console.log('\nFailed tests:');
    failures.forEach(f => console.log(`  • ${f.label}\n    ${f.error}`));
}
console.log('─'.repeat(60));
process.exit(failed > 0 ? 1 : 0);
