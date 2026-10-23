# Substrait Compliance Framework — Complete Guide

**Version:** 1.0.0
**Last Updated:** July 2026
**Status:** Feature-complete; public release at [github.com/IBM/substrait-compliance](https://github.com/IBM/substrait-compliance)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Framework Overview](#framework-overview)
3. [Architecture](#architecture)
4. [SDK Implementation](#sdk-implementation)
5. [Test Suite Coverage](#test-suite-coverage)
6. [Leaderboard and Submissions](#leaderboard-and-submissions)
7. [CI/CD Automation](#cicd-automation)
8. [Interactive Demo System](#interactive-demo-system)
9. [Getting Started](#getting-started)
10. [Project Statistics](#project-statistics)
11. [Benefits Analysis](#benefits-analysis)

---

## Executive Summary

The **Substrait Compliance Framework** is a decentralized testing ecosystem that enables query engines to self-certify their Substrait support through standardized interfaces, comprehensive test suites, and automated reporting.

### Key Achievements

| Achievement | Description | Impact |
|---|---|---|
| **Multi-Language SDK** | Java, Python, Rust, Go, C++, TypeScript, C#, Scala (8 languages) | Supports diverse engine ecosystems |
| **Comprehensive Test Coverage** | 22 TPC-H + 99 TPC-DS + 140 function test files (5,041 assertions) | Industry-standard benchmarking |
| **Public Leaderboard** | Results submitted by pull request, checked and scored in CI, published on GitHub Pages | Centralized result sharing, no server |
| **Automated CI/CD** | 10 GitHub Actions workflows | Continuous validation |
| **Interactive Demo** | Real-time dashboard and visualization | Easy adoption and exploration |
| **Decentralized Model** | Engine-owned compliance testing | Community-driven validation |

### Current Status

```
✅ Core Framework         — Complete
✅ SDK Implementation     — Complete (8 languages)
✅ Test Suites            — Complete (140 function test files, 5,041 assertions + TPC-H 22 + TPC-DS 99)
✅ Leaderboard            — Published on GitHub Pages; engine teams submit by pull request
✅ CI/CD Pipeline         — 10 workflows, fully automated
✅ Documentation          — Comprehensive
✅ Demo System            — Interactive and functional
```

### Key Metrics

| Metric | Value |
|---|---|
| Function test files | 140 (5,041 assertions across 14 categories) |
| TPC-H queries | 22 |
| TPC-DS queries | 99 |
| SDK languages | 8 |
| CI/CD workflows | 10 |

---

## Framework Overview

### What is the Substrait Compliance Framework?

The framework transforms how query engines validate their Substrait support by providing:

1. **Standard Interfaces** — `ComplianceEngine` trait/interface consistent across all SDKs
2. **Comprehensive Test Suites** — Industry-standard benchmarks (TPC-H, TPC-DS) and function tests
3. **Automated Testing** — Self-service compliance validation
4. **Transparent Reporting** — JSON-based compliance reports
5. **Community Leaderboard** — Optional public compliance rankings

### Core Principles

**Decentralization** — Engines test themselves on their own infrastructure with no central bottleneck. Privacy-preserving: engines control what they publish.

**Standardization** — Consistent interfaces across all SDKs, a standard test suite format, and a uniform reporting structure.

**Transparency** — Open-source test suites, public compliance reports, community-driven governance.

**Extensibility** — Plugin architecture for custom tests, support for engine-specific extensions, flexible reporting options.

### Benefits

**For engine developers:**
- Self-service compliance testing with granular function-level validation
- Performance benchmarking and CI/CD integration
- Gap analysis tools and reference implementations

**For the Substrait ecosystem:**
- Accelerated adoption and improved interoperability
- Community-driven quality assurance and engine comparison

---

## Architecture

### Decentralized Compliance Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Substrait Community                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Compliance Framework (Central)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Test Suites │  │  Leaderboard │  │    Demo      │     │
│  │   (TPC-H +   │  │ (Pages site) │  │  Dashboard   │     │
│  │  Functions)  │  │              │  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Engine A    │      │  Engine B    │      │  Engine C    │
│  SDK (Java)  │      │ SDK (Python) │      │  SDK (Rust)  │
│  CI/CD       │      │  CI/CD       │      │  CI/CD       │
└──────────────┘      └──────────────┘      └──────────────┘
```

### High-Level Compliance Workflow

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ Query Engine │─────▶│     SDK      │─────▶│ Test Suites  │
│              │      │  (8 langs)   │      │  (5,041+)    │
└──────────────┘      └──────────────┘      └──────────────┘
       │                     │                      │
       │                     ▼                      │
       │              ┌──────────────┐              │
       │              │ Test Runner  │◀─────────────┘
       │              └──────────────┘
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Execute    │      │   Validate   │      │   Generate   │
│   Tests      │─────▶│   Results    │─────▶│   Report     │
└──────────────┘      └──────────────┘      └──────────────┘
                                                    │
                                                    ▼
                            ┌────────────────────────────────┐
                            │      Leaderboard (optional)     │
                            │  • Submission by pull request   │
                            │  • Checked and scored in CI     │
                            │  • History in git               │
                            └────────────────────────────────┘
```

### System Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                       ENGINE LAYER                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │ DuckDB   │  │DataFusion│  │  Velox   │  │ Custom Engine│   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                         SDK LAYER                                │
│  Java · Python · Rust · C++ · Go · TypeScript · C# · Scala     │
└─────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    TEST SUITE LAYER                              │
│  TPC-H (22 queries) · TPC-DS (99 queries)                       │
│  Function Tests (140 files, 5,041 assertions, 14 categories)    │
└─────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                   LEADERBOARD LAYER                              │
│  Pull-request submissions — submissions/<engine>/<version>/     │
│  Published leaderboard    — leaderboard.json on GitHub Pages    │
└─────────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technologies | Purpose |
|---|---|---|
| **SDKs** | Java 17+, Python 3.8+, Rust 1.70+, Go 1.21+, C++17, Node.js, .NET 10+, Scala 2.13 | Engine integration |
| **Leaderboard** | Python scripts, GitHub Pages | Result checking and publishing |
| **Testing** | JUnit, pytest, cargo test, go test | Quality assurance |
| **CI/CD** | GitHub Actions | Automation |
| **Demo** | Python, HTML/JS dashboard | Visualization |

### Data Flow

```
Engine Developer
  └─> Integrates SDK into engine codebase
      └─> Runs compliance tests locally
          └─> CI/CD pipeline executes tests automatically
              └─> Results submitted by pull request (optional)
                  └─> CI checks and scores them; a maintainer merges
                      └─> Leaderboard updated
                          └─> Community views progress
```

---

## SDK Implementation

### Design Philosophy

1. **Language idiomatic** — Each SDK follows language-specific best practices
2. **Minimal dependencies** — Lightweight with essential dependencies only
3. **Extensible** — Easy to add custom test cases
4. **Type-safe** — Strong typing where the language supports it
5. **Well-documented** — Comprehensive API documentation

### SDK Feature Matrix

| Feature | Java | Python | Rust | Go | C++ | TypeScript | C# | Scala |
|---|---|---|---|---|---|---|---|---|
| Test execution | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Result validation | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| JSON report output | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Custom tests | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Async support | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Benchmarking | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

### Core Interface (Java)

```java
public interface ComplianceEngine {
    ComplianceResult executeTest(TestCase testCase);
    boolean supportsFeature(String feature);
    EngineMetadata getMetadata();
}

public interface TestCase {
    String getId();
    String getQuery();
    TestType getType();
    Map<String, Object> getParameters();
}
```

### Usage Example (Java)

```java
// 1. Implement the ComplianceEngine interface
public class MySubstraitEngine implements ComplianceEngine {
    @Override
    public ComplianceResult executeTest(TestCase testCase) {
        try {
            SubstraitPlan plan = SubstraitParser.parse(testCase.getQuery());
            QueryResult result = engine.execute(plan);
            return ComplianceResult.success(result, executionTime);
        } catch (Exception e) {
            return ComplianceResult.failure(e.getMessage());
        }
    }

    @Override
    public EngineMetadata getMetadata() {
        return EngineMetadata.builder()
            .name("MyEngine")
            .version("1.0.0")
            .substraitVersion("0.20.0")
            .build();
    }
}

// 2. Run compliance tests
ComplianceTestRunner runner = new ComplianceTestRunner(new MySubstraitEngine());
ComplianceReport report = runner.runTestSuite("tpch");
System.out.println("Passed: " + report.getPassedCount() + "/" + report.getTotalCount());
```

### Usage Example (Python)

```python
from substrait_compliance import ComplianceEngine, TestCase, ComplianceResult, TestStatus

class MySubstraitEngine(ComplianceEngine):
    def execute_test(self, test_case: TestCase) -> ComplianceResult:
        try:
            plan = substrait_parser.parse(test_case.query)
            result = self.engine.execute(plan)
            return ComplianceResult(status=TestStatus.PASSED, data=result)
        except Exception as e:
            return ComplianceResult(status=TestStatus.FAILED, error_message=str(e))

runner = ComplianceTestRunner(MySubstraitEngine())
report = runner.run_test_suite("tpch")
print(f"Passed: {report.passed_count}/{report.total_count}")
```

### Usage Example (Rust)

```rust
use substrait_compliance::{ComplianceEngine, TestCase, ComplianceResult};

struct MyEngine;

impl ComplianceEngine for MyEngine {
    async fn execute_test(&self, test: &TestCase) -> ComplianceResult {
        match self.run_substrait_plan(&test.plan_bytes).await {
            Ok(output) => ComplianceResult::passed(output),
            Err(e)     => ComplianceResult::failed(e.to_string()),
        }
    }
}

let runner = ComplianceRunner::new(MyEngine);
let report = runner.run_suite("tpch").await?;
println!("Score: {:.1}%", report.compliance_score());
```

---

## Test Suite Coverage

### TPC-H Benchmark (22 queries)

Industry-standard OLAP benchmark covering:
- Pricing and revenue analysis
- Supplier and part analysis
- Customer and order analysis
- Market and shipping analysis

Each query ships with a Substrait plan (`.json`) and expected output schema.

### TPC-DS Benchmark (99 queries)

Decision-support workload covering:
- Reporting queries (40)
- Ad-hoc queries (30)
- Iterative OLAP (20)
- Data mining (9)

### Function Test Suite (140 files, 5,041 assertions)

| Category | Files | Assertions | Coverage |
|---|---|---|---|
| Arithmetic | ~15 | ~450 | 95% |
| String | ~25 | ~800 | 92% |
| Comparison | ~20 | ~600 | 98% |
| Datetime | ~12 | ~380 | 88% |
| Aggregate | ~10 | ~300 | 90% |
| Boolean | ~8 | ~240 | 95% |
| Array | ~8 | ~250 | 85% |
| Cast | ~8 | ~250 | 90% |
| Set | ~6 | ~180 | 85% |
| Window | ~8 | ~240 | 85% |
| Geospatial | ~6 | ~180 | 80% |
| JSON | ~5 | ~150 | 75% |
| Map | ~5 | ~150 | 80% |
| Conditional | ~4 | ~120 | 90% |

Tests follow YAML format with explicit input data, expected output, and edge cases (NULL handling, overflow, type boundaries).

---

## Leaderboard and Submissions

Engine teams run the suites in their own CI and submit the reports by pull request into
`submissions/<engine>/<version>/`. The pull-request check (`submission-validation.yml`) uses the
checker, suites and schemas of the base branch, recomputes every score from the per-case
results, and shows where the engine would rank. Once merged, `compliance-leaderboard.yml`
publishes the leaderboard as Markdown, JSON and an HTML page on
[GitHub Pages](https://ibm.github.io/substrait-compliance/), alongside DuckDB and DataFusion,
which this repository runs itself. There is no server or account.

- How to submit: [`submissions/README.md`](../submissions/README.md)
- How scores are computed and published: [`docs/COMPLIANCE_LEADERBOARD.md`](COMPLIANCE_LEADERBOARD.md)
- Machine-readable data: [`leaderboard.json`](https://ibm.github.io/substrait-compliance/leaderboard.json)

---

## CI/CD Automation

10 GitHub Actions workflows provide automated testing, building, and quality assurance.

### Key Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `sdk-build-test.yml` | Push / PR to `sdk/**` | Build and test all 8 SDKs |
| `submission-validation.yml` | PR to `submissions/**` | Check a leaderboard submission |
| `compliance-leaderboard.yml` | Weekly, on merge, after engine runs | Publish the leaderboard |
| `release-publish.yml` | Tag push | Publish SDK packages to registries |

### CI Pipeline

```
PR/Push
  │
  ├── Lint ──── Build ──── Test ──── Security scan
  │
  └── (on release) ──── Package ──── Publish ──── Deploy
```

### Quality Gates

All workflows enforce:
- All tests passing
- Code coverage ≥ 80%
- No critical security vulnerabilities
- Linting clean
- Successful build

---

## Interactive Demo System

The demo (`demo/`) provides a self-contained experience that requires no external dependencies.

### Quick Start

```bash
cd demo
./runner/run-simple-demo.sh

# View dashboard
cd dashboard
python3 -m http.server 8080
# Open http://localhost:8080
```

### What the Demo Shows

- **Leaderboard** — Rankings with fidelity tier badges (VERIFIED / EDGE / BASIC / NONE)
- **Charts** — Pass-rate bar chart and doughnut chart
- **Engine drill-down** — Per-engine test case breakdown
- **Complexity filtering** — SIMPLE / MEDIUM / COMPLEX / VERY_COMPLEX
- **Analytics** — Trend and average compliance across mock engines

### Mock Engines Included

| Engine | Version | TPC-H Score |
|---|---|---|
| FastDB | 2.5.0 | 95.5% (21/22) |
| MockDB | 1.0.0 | 86.4% (19/22) |
| CloudDB | 3.1.0 | 77.3% (17/22) |
| DuckDB | 0.9.0 | 90.9% (20/22) |
| PostgreSQL | 15.0 | 81.8% (18/22) |

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/IBM/substrait-compliance.git
cd substrait-compliance
```

### 2. Run the Demo

```bash
cd demo
./runner/run-simple-demo.sh
```

### 3. Choose Your SDK and Implement `ComplianceEngine`

```bash
# Java (recommended — most complete)
cd sdk/java && ./gradlew build && ./gradlew test

# Python
cd sdk/python && pip install -e . && pytest

# Rust
cd sdk/rust && cargo build && cargo test

# Go
cd sdk/go && go test ./...

# TypeScript
cd sdk/typescript && npm install && npm test

# C++
cd sdk/cpp && mkdir build && cd build && cmake .. && make && ctest

# C#
cd sdk/csharp && dotnet build && dotnet test

# Scala
cd sdk/scala && sbt test
```

### 4. Run Against Test Suites

```bash
# Java — DuckDB integration example
cd examples/duckdb-java && ./gradlew run

# Python — DataFusion integration example
cd examples/datafusion-python && python datafusion_compliance.py
```

### 5. Review Your Report

Reports are written to `output/<engine>-report.json` in JSON format:

```json
{
  "engineName": "MyEngine",
  "engineVersion": "1.0.0",
  "timestamp": "2026-07-22T...",
  "summary": {
    "passed": 20,
    "failed": 2,
    "totalTests": 22
  },
  "complianceScore": 90.9,
  "results": [...]
}
```

---

## Project Statistics

| Metric | Value |
|---|---|
| Function test files | 140 |
| Function test assertions | 5,041 |
| Function categories | 14 |
| TPC-H queries | 22 |
| TPC-DS queries | 99 |
| SDK languages | 8 |
| CI/CD workflows | 10 |
| Example integrations | DuckDB, DataFusion, Velox |

---

## Benefits Analysis

### Decentralized vs. Centralized Testing

| Aspect | Centralized | This Framework (Decentralized) |
|---|---|---|
| Testing bottleneck | Central team required | Engine team self-serves |
| Environment fidelity | Generic environment | Engine's own environment |
| Privacy | Results always public | Engine controls publication |
| Adoption barrier | High (dependency on central infra) | Low (clone and run) |
| Scalability | Limited by central resources | Unlimited (each engine scales independently) |

### For Engine Developers

Running against the test suite provides:
- A pass/fail verdict for each Substrait function
- An overall compliance score suitable for CI badges
- A structured JSON report for trend tracking
- Identification of gaps to prioritize in implementation

---

## Documentation Index

| Document | Description |
|---|---|
| [`README.md`](../README.md) | Quick start, prerequisites, full repo tour |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md) | Contribution guidelines and PR process |
| [`GOVERNANCE.md`](../GOVERNANCE.md) | Project governance, roles, decision-making |
| [`SECURITY.md`](../SECURITY.md) | Security policy and vulnerability reporting |
| [`docs/COMPLIANCE_LEADERBOARD.md`](COMPLIANCE_LEADERBOARD.md) | How the leaderboard is scored and published |
| [`submissions/README.md`](../submissions/README.md) | Submitting results by pull request |
| [`docs/DEPLOYMENT_GUIDE.md`](DEPLOYMENT_GUIDE.md) | Repository setup, releases and contributions |
| [`test-suites/functions/README.md`](../test-suites/functions/README.md) | Function test suite format |

---

*Made with ❤️ for the Substrait community — [github.com/IBM/substrait-compliance](https://github.com/IBM/substrait-compliance)*
