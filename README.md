# Substrait Compliance Framework

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?logo=github-actions&logoColor=white)](https://github.ibm.com/rsinha/substrait-compliance/actions)

> **A decentralized compliance testing framework for Substrait implementations**

Enable database engines to self-certify their Substrait compliance through standardized interfaces, pre-packaged test suites, and automated reporting.

---

## 🎯 Overview

The Substrait Compliance Framework transforms how query engines validate their Substrait support. Instead of centralized testing, engines **self-certify** by:

1. **Implementing standard interfaces** - ComplianceEngine trait/interface
2. **Running pre-packaged test suites** - TPC-H with 22 queries
3. **Generating compliance reports** - JSON format with pass/fail results
4. **Publishing results** - Public leaderboard for ecosystem visibility

### Key Benefits

- ✅ **Decentralized** - Engines test themselves, no central bottleneck
- ✅ **Multi-Language** - SDKs for Java, Python, and Rust
- ✅ **Automated** - CI/CD integration with GitHub Actions
- ✅ **Transparent** - Public compliance leaderboard
- ✅ **Comprehensive** - Full TPC-H benchmark (22 queries, 86K rows)

---

## 🚀 Quick Start

### For Engine Developers

**1. Choose Your SDK:**

```bash
# Java
cd sdk/java && ./gradlew build

# Python
cd sdk/python && pip install -e .

# Rust
cd sdk/rust && cargo build
```

**2. Implement ComplianceEngine:**

<details>
<summary><b>Java Example</b></summary>

```java
public class MyEngine implements ComplianceEngine {
    @Override
    public EngineInfo getInfo() {
        return new EngineInfo("MyEngine", "1.0.0", "MyCompany");
    }
    
    @Override
    public ComplianceResult executePlan(byte[] planBytes, Map<String, TableData> inputData) {
        // Load data into your engine
        loadInputData(inputData);
        
        // Execute Substrait plan
        TableData output = executeSubstraitPlan(planBytes);
        
        return new ComplianceResult("test-id", TestStatus.PASSED, output, null, 0);
    }
}
```
</details>

<details>
<summary><b>Python Example</b></summary>

```python
class MyEngine(ComplianceEngine):
    def get_info(self) -> EngineInfo:
        return EngineInfo("MyEngine", "1.0.0", "MyCompany")
    
    def execute_plan(self, plan_bytes: bytes, input_data: Dict[str, TableData]) -> ComplianceResult:
        # Load data into your engine
        self._load_input_data(input_data)
        
        # Execute Substrait plan
        output = self._execute_substrait_plan(plan_bytes)
        
        return ComplianceResult("test-id", TestStatus.PASSED, output_data=output)
```
</details>

<details>
<summary><b>Rust Example</b></summary>

```rust
impl ComplianceEngine for MyEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("MyEngine", "1.0.0", "MyCompany")
    }
    
    fn execute_plan(&self, plan_bytes: &[u8], input_data: &HashMap<String, TableData>) 
        -> Result<ComplianceResult> {
        // Load data into your engine
        self.load_input_data(input_data)?;
        
        // Execute Substrait plan
        let output = self.execute_substrait_plan(plan_bytes)?;
        
        Ok(ComplianceResult::new("test-id", TestStatus::Passed).with_output(output))
    }
}
```
</details>

**3. Run Tests:**

```bash
# Load test suite
YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
TestSuite suite = loader.load("test-suites/tpch/metadata.yaml");

# Run compliance tests
ComplianceRunner runner = new ComplianceRunner(new MyEngine());
ComplianceReport report = runner.runTestSuite(suite);

# View results
System.out.println("Pass Rate: " + report.getPassRate() + "%");
```

**4. Integrate with CI/CD:**

Copy the template workflow to your repository:

```bash
cp .github/workflows/engine-compliance-template.yml \
   your-engine/.github/workflows/substrait-compliance.yml
```

See [examples/](examples/) for complete implementations.

---

## 📦 Repository Structure

```
substrait-compliance/
├── sdk/                           # Multi-language SDKs
│   ├── java/                      # Java SDK (JDK 11+)
│   │   ├── src/main/java/         # Core interfaces
│   │   ├── src/test/java/         # Unit tests (12 passing)
│   │   └── build.gradle           # Gradle build
│   ├── python/                    # Python SDK (3.8+)
│   │   ├── substrait_compliance/  # Package modules
│   │   ├── tests/                 # Unit tests (8 passing)
│   │   └── setup.py               # PyPI setup
│   └── rust/                      # Rust SDK (2021 edition)
│       ├── src/                   # Library modules
│       ├── tests/                 # Integration tests (6 passing)
│       └── Cargo.toml             # Cargo manifest
├── test-suites/                   # Pre-packaged test suites
│   └── tpch/                      # TPC-H benchmark
│       ├── metadata.yaml          # 22 queries with metadata
│       ├── data/                  # 8 CSV files (86,805 rows)
│       └── plans/                 # 44 Substrait plans (bin + json)
├── examples/                      # Example implementations
│   ├── duckdb-java/              # DuckDB compliance engine (Java)
│   └── datafusion-python/        # DataFusion compliance engine (Python)
├── .github/workflows/             # CI/CD automation
│   ├── sdk-build-test.yml        # Test all SDKs
│   ├── release-publish.yml       # Automated releases
│   ├── test-suite-validation.yml # Validate test suites
│   ├── engine-compliance-template.yml  # Template for engines
│   └── compliance-leaderboard.yml      # Public leaderboard
├── scripts/                       # Helper scripts
│   └── generate_leaderboard.py   # Leaderboard generator
└── docs/                          # Documentation
    ├── IMPLEMENTATION_SUMMARY.md  # Framework overview
    ├── CI_CD_IMPLEMENTATION.md    # CI/CD documentation
    └── DEPLOYMENT_GUIDE.md        # Deployment instructions
```

---

## 🏗️ Architecture

### Decentralized Compliance Model

```
┌─────────────────────────────────────────────────────────────┐
│         Compliance Framework (Standards Authority)           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Publishes:                                           │  │
│  │  • SDKs (Java, Python, Rust)                          │  │
│  │  • Test Suites (TPC-H)                                │  │
│  │  • Documentation                                      │  │
│  │  • CI/CD Templates                                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          ↓
              Downloads & Implements
                          ↓
┌─────────────────────────────────────────────────────────────┐
│              Query Engines (Self-Certify)                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  DuckDB    │  │ DataFusion │  │   Spark    │            │
│  │  • Java SDK│  │ • Python   │  │  • Java SDK│            │
│  │  • 90.9%   │  │ • 95.5%    │  │  • 86.4%   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
                          ↓
              Reports Results (Optional)
                          ↓
┌─────────────────────────────────────────────────────────────┐
│         Public Compliance Leaderboard (GitHub Pages)         │
│  • Rankings by pass rate                                    │
│  • Detailed results per engine                              │
│  • Historical trends                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Test Suite

### TPC-H Benchmark

Complete TPC-H benchmark at scale factor 0.01:

| Component | Details |
|-----------|---------|
| **Queries** | 22 (Q1-Q22) |
| **Data Files** | 8 CSV files |
| **Total Rows** | 86,805 |
| **Data Size** | 10.6 MB |
| **Plan Formats** | Binary (.bin) + JSON (.json) |
| **Complexity Levels** | SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX |

**Query Breakdown:**
- **SIMPLE** (3): Q1, Q6, Q14
- **MEDIUM** (7): Q3, Q4, Q10, Q12, Q13, Q16, Q19
- **COMPLEX** (8): Q5, Q7, Q9, Q11, Q15, Q17, Q18, Q22
- **VERY_COMPLEX** (4): Q2, Q8, Q20, Q21

---

## 🔧 SDK Features

### Java SDK

- **Version**: 1.0.0
- **JDK**: 11+
- **Build**: Gradle
- **Tests**: 12 unit tests (100% passing)
- **Coverage**: JaCoCo reports
- **Distribution**: Maven Central (planned)

**Key Classes:**
- `ComplianceEngine` - Main interface
- `ComplianceRunner` - Test executor
- `YamlTestSuiteLoader` - Test suite loader
- `EngineCapabilities` - Feature descriptor

### Python SDK

- **Version**: 1.0.0
- **Python**: 3.8+
- **Build**: setuptools
- **Tests**: 8 integration tests (100% passing)
- **Coverage**: pytest-cov
- **Distribution**: PyPI (planned)

**Key Modules:**
- `engine.py` - ComplianceEngine ABC
- `runner.py` - ComplianceRunner
- `loader.py` - YamlTestSuiteLoader
- `result.py` - Result classes

### Rust SDK

- **Version**: 1.0.0
- **Edition**: 2021
- **Build**: Cargo
- **Tests**: 6 integration tests (100% passing)
- **Coverage**: tarpaulin
- **Distribution**: crates.io (planned)

**Key Modules:**
- `engine.rs` - ComplianceEngine trait
- `runner.rs` - ComplianceRunner
- `loader.rs` - YamlTestSuiteLoader
- `result.rs` - Result types

---

## 🤖 CI/CD Integration

### Automated Workflows

1. **SDK Build & Test** - Tests all SDKs on every commit
2. **Release & Publish** - Automated releases to package registries
3. **Test Suite Validation** - Validates test suite integrity
4. **Compliance Leaderboard** - Aggregates and publishes results

### For Engine Developers

Copy the template workflow to enable automated compliance testing:

```bash
cp .github/workflows/engine-compliance-template.yml \
   .github/workflows/substrait-compliance.yml
```

Customize:
- Engine name and version
- Build commands
- Test execution
- Compliance threshold (default: 80%)

See [.github/workflows/README.md](.github/workflows/README.md) for details.

---

## 📈 Compliance Leaderboard

Public leaderboard showing compliance status across all engines:

- **Rankings** by pass rate
- **Detailed results** per engine
- **Statistics** (average, highest, lowest)
- **Interactive dashboard** (GitHub Pages)

**Example:**

| Rank | Engine | Version | Pass Rate | Queries Passed |
|------|--------|---------|-----------|----------------|
| 🥇 | DataFusion | 35.0.0 | 95.5% | 21/22 |
| 🥈 | DuckDB | 0.10.0 | 90.9% | 20/22 |
| 🥉 | Spark | 3.5.0 | 86.4% | 19/22 |

---

## 📖 Documentation

- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Complete framework overview
- **[CI_CD_IMPLEMENTATION.md](CI_CD_IMPLEMENTATION.md)** - CI/CD architecture and workflows
- **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** - Deployment instructions
- **[.github/workflows/README.md](.github/workflows/README.md)** - Workflow documentation
- **[examples/README.md](examples/README.md)** - Example implementations

---

## 🛠️ Development

### Building from Source

**Java SDK:**
```bash
cd sdk/java
./gradlew build test
```

**Python SDK:**
```bash
cd sdk/python
pip install -e .[dev]
pytest
```

**Rust SDK:**
```bash
cd sdk/rust
cargo build --release
cargo test
```

### Running Examples

**DuckDB (Java):**
```bash
cd examples/duckdb-java
./gradlew run
```

**DataFusion (Python):**
```bash
cd examples/datafusion-python
python datafusion_compliance.py
```

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Areas for Contribution

- Additional test suites (TPC-DS, SSB, etc.)
- More example implementations
- SDK improvements
- Documentation enhancements
- Bug fixes and optimizations

---

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| **Total Lines of Code** | ~5,000 |
| **SDKs** | 3 (Java, Python, Rust) |
| **Tests** | 26 (100% passing) |
| **Test Data Rows** | 86,805 |
| **Workflows** | 5 |
| **Documentation Files** | 5 |
| **Example Implementations** | 2 |

---

## 📜 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Substrait Community** - For the amazing query plan specification
- **TPC-H Benchmark** - For the comprehensive test queries
- **Engine Developers** - For implementing Substrait support

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.ibm.com/rsinha/substrait-compliance/issues)
- **Discussions**: [GitHub Discussions](https://github.ibm.com/rsinha/substrait-compliance/discussions)
- **Documentation**: [docs/](docs/)

---

## 🌐 REST API

A comprehensive REST API is available for programmatic access to compliance results:

- **[REST API Overview](docs/REST_API_SUMMARY.md)** - Project summary and getting started
- **[API Specification](docs/REST_API_PLAN.md)** - Complete API endpoints and specifications
- **[Architecture](docs/REST_API_ARCHITECTURE.md)** - System architecture and diagrams
- **[Implementation Guide](docs/REST_API_IMPLEMENTATION_GUIDE.md)** - Step-by-step implementation
- **[Contributing to API](docs/API_CONTRIBUTING.md)** - Contribution guidelines

### Key Features

- 🔐 **JWT Authentication** - Secure API access with token-based auth
- 📊 **Report Submission** - Submit compliance reports programmatically
- 🔍 **Data Querying** - Query compliance data with filtering and pagination
- 🔔 **Webhooks** - Real-time notifications for compliance events
- ⚡ **Rate Limiting** - Fair usage with configurable limits
- 💾 **Caching** - Optimized performance for frequently accessed data

### Quick Start

```bash
# Run with Podman
cd api
podman-compose up -d

# Access API
curl http://localhost:8080/api/v1/leaderboard
```

See [REST API documentation](docs/REST_API_SUMMARY.md) for details.

---

## 🗺️ Roadmap

- [x] REST API for results (In Progress)
- [ ] Additional test suites (TPC-DS, SSB)
- [ ] Performance benchmarking
- [ ] Compliance badges
- [ ] Historical trend analysis
- [ ] Multi-version testing

---

**Made with ❤️ for the Substrait Community**
