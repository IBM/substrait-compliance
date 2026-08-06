//! Substrait Compliance Testing SDK for Rust
//!
//! This crate provides interfaces and utilities for engines to test
//! their Substrait compliance in a decentralized manner.

pub mod engine;
pub mod runner;
pub mod result;
pub mod test_suite;
pub mod table_data;
pub mod loader;
pub mod error;
pub mod benchmark;

pub use engine::{ComplianceEngine, EngineInfo, EngineCapabilities};
pub use runner::ComplianceRunner;
pub use result::{ComplianceResult, ComplianceReport, TestStatus};
pub use test_suite::{TestSuite, SimpleTestSuite, TestCase, TestSuiteMetadata};
pub use table_data::{TableData, Column, DataType};
pub use loader::{TestSuiteLoader, YamlTestSuiteLoader, load_csv, load_input_data};
pub use error::ComplianceError;
pub use benchmark::{BenchmarkRunner, BenchmarkConfig, BenchmarkResult, BenchmarkStats};
