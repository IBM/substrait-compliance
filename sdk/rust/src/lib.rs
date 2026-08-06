//! Substrait Compliance Testing SDK for Rust
//!
//! This crate provides interfaces and utilities for engines to test
//! their Substrait compliance in a decentralized manner.

pub mod benchmark;
pub mod engine;
pub mod error;
pub mod loader;
pub mod result;
pub mod runner;
pub mod table_data;
pub mod test_suite;

pub use benchmark::{BenchmarkConfig, BenchmarkResult, BenchmarkRunner, BenchmarkStats};
pub use engine::{ComplianceEngine, EngineCapabilities, EngineInfo};
pub use error::ComplianceError;
pub use loader::{load_csv, load_input_data, TestSuiteLoader, YamlTestSuiteLoader};
pub use result::{ComplianceReport, ComplianceResult, TestStatus};
pub use runner::ComplianceRunner;
pub use table_data::{Column, DataType, TableData};
pub use test_suite::{SimpleTestSuite, TestCase, TestSuite, TestSuiteMetadata};
