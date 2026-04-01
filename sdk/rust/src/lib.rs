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

pub use engine::{ComplianceEngine, EngineInfo, EngineCapabilities};
pub use runner::ComplianceRunner;
pub use result::{ComplianceResult, ComplianceReport, TestStatus};
pub use test_suite::{TestSuite, TestCase, TestSuiteMetadata};
pub use table_data::{TableData, Column, DataType};
pub use loader::{TestSuiteLoader, YamlTestSuiteLoader};
pub use error::ComplianceError;
