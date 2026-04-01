//! Test suite loaders for various formats.

use crate::error::{ComplianceError, Result};
use crate::test_suite::{SimpleTestSuite, TestCase, TestSuite, TestSuiteMetadata};
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::Path;

/// Trait for loading test suites.
pub trait TestSuiteLoader {
    fn load(&self, path: &Path) -> Result<Box<dyn TestSuite>>;
    fn supports(&self, path: &Path) -> bool;
}

/// Loads test suites from YAML files.
pub struct YamlTestSuiteLoader;

impl YamlTestSuiteLoader {
    pub fn new() -> Self {
        Self
    }
}

impl Default for YamlTestSuiteLoader {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Debug, Deserialize)]
struct TestSuiteDefinition {
    name: String,
    version: String,
    description: String,
    #[serde(rename = "testCases")]
    test_cases: Vec<TestCaseDefinition>,
}

#[derive(Debug, Deserialize)]
struct TestCaseDefinition {
    id: String,
    description: String,
    #[serde(rename = "planBinary")]
    plan_binary: String,
}

impl TestSuiteLoader for YamlTestSuiteLoader {
    fn load(&self, path: &Path) -> Result<Box<dyn TestSuite>> {
        let content = fs::read_to_string(path)?;
        let def: TestSuiteDefinition = serde_yaml::from_str(&content)?;
        
        let base_dir = path.parent().ok_or_else(|| 
            ComplianceError::Loading("Invalid path".to_string())
        )?;
        
        let mut test_cases = Vec::new();
        for tc_def in def.test_cases {
            let plan_path = base_dir.join(&tc_def.plan_binary);
            let plan_bytes = fs::read(plan_path)?;
            
            let test_case = TestCase::new(
                tc_def.id,
                tc_def.description,
                plan_bytes,
            );
            test_cases.push(test_case);
        }
        
        let metadata = TestSuiteMetadata {
            name: def.name.clone(),
            version: def.version,
            description: def.description,
        };
        
        Ok(Box::new(SimpleTestSuite::new(def.name, test_cases, metadata)))
    }
    
    fn supports(&self, path: &Path) -> bool {
        path.extension()
            .and_then(|ext| ext.to_str())
            .map(|ext| ext == "yaml" || ext == "yml")
            .unwrap_or(false)
    }
}
