//! Test suite and test case types.

use crate::table_data::TableData;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Metadata about a test suite.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TestSuiteMetadata {
    pub name: String,
    pub version: String,
    pub description: String,
}

impl std::fmt::Display for TestSuiteMetadata {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{} v{}", self.name, self.version)
    }
}

/// A single test case with plan and data.
#[derive(Debug, Clone)]
pub struct TestCase {
    pub id: String,
    pub description: String,
    pub plan_bytes: Vec<u8>,
    pub input_data: HashMap<String, TableData>,
    pub expected_output: Option<TableData>,
}

impl TestCase {
    pub fn new(
        id: impl Into<String>,
        description: impl Into<String>,
        plan_bytes: Vec<u8>,
    ) -> Self {
        Self {
            id: id.into(),
            description: description.into(),
            plan_bytes,
            input_data: HashMap::new(),
            expected_output: None,
        }
    }
}

/// Trait for test suites.
pub trait TestSuite {
    fn get_name(&self) -> &str;
    fn get_test_cases(&self) -> &[TestCase];
    fn get_metadata(&self) -> &TestSuiteMetadata;
}

/// Simple in-memory test suite implementation.
#[derive(Debug, Clone)]
pub struct SimpleTestSuite {
    name: String,
    test_cases: Vec<TestCase>,
    metadata: TestSuiteMetadata,
}

impl SimpleTestSuite {
    pub fn new(
        name: impl Into<String>,
        test_cases: Vec<TestCase>,
        metadata: TestSuiteMetadata,
    ) -> Self {
        Self {
            name: name.into(),
            test_cases,
            metadata,
        }
    }
}

impl TestSuite for SimpleTestSuite {
    fn get_name(&self) -> &str {
        &self.name
    }
    
    fn get_test_cases(&self) -> &[TestCase] {
        &self.test_cases
    }
    
    fn get_metadata(&self) -> &TestSuiteMetadata {
        &self.metadata
    }
}
