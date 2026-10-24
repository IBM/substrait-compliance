//! Test suite and test case types.

use crate::table_data::{Column, TableData};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;

/// An input table read from a file when its case runs (see `TestCase::load_input_data`).
#[derive(Debug, Clone)]
pub struct InputTable {
    /// The table name the plan reads.
    pub name: String,
    pub file: PathBuf,
    /// The columns the plan reads the table with, or None to read the file like an
    /// expected output (typed header, or untyped columns).
    pub columns: Option<Vec<Column>>,
}

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
    /// Per output column, the scale of the decimal type the plan declares (None for other
    /// columns), or None when unknown; see `plan_types::declared_scales`.
    pub declared_scales: Option<Vec<Option<u32>>>,
    /// Input tables read from files when the case runs, added to `input_data`. The loader
    /// fills this rather than `input_data` so a suite's data is not held in memory for every
    /// case at once (a suite can read gigabytes of CSV across its cases).
    pub input_tables: Vec<InputTable>,
}

impl TestCase {
    pub fn new(id: impl Into<String>, description: impl Into<String>, plan_bytes: Vec<u8>) -> Self {
        Self {
            id: id.into(),
            description: description.into(),
            plan_bytes,
            input_data: HashMap::new(),
            expected_output: None,
            declared_scales: None,
            input_tables: Vec::new(),
        }
    }

    /// The case's input tables: `input_data`, plus each of `input_tables` read from its file
    /// (`crate::loader::load_input_table`).
    pub fn load_input_data(&self) -> crate::error::Result<HashMap<String, TableData>> {
        let mut tables = self.input_data.clone();
        for t in &self.input_tables {
            let table = match &t.columns {
                Some(columns) => crate::loader::load_input_table(&t.file, columns)?,
                None => crate::loader::load_csv(&t.file)?,
            };
            tables.insert(t.name.clone(), table);
        }
        Ok(tables)
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
