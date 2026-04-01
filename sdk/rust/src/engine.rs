//! Engine interface and metadata types.

use crate::error::Result;
use crate::result::ComplianceResult;
use crate::table_data::TableData;
use std::collections::HashMap;

/// Metadata about a Substrait engine.
#[derive(Debug, Clone)]
pub struct EngineInfo {
    pub name: String,
    pub version: String,
    pub vendor: String,
    pub description: String,
}

impl EngineInfo {
    pub fn new(name: impl Into<String>, version: impl Into<String>, vendor: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            version: version.into(),
            vendor: vendor.into(),
            description: String::new(),
        }
    }
    
    pub fn with_description(mut self, description: impl Into<String>) -> Self {
        self.description = description.into();
        self
    }
}

impl std::fmt::Display for EngineInfo {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{} {} by {}", self.name, self.version, self.vendor)
    }
}

/// Describes what Substrait features an engine supports.
#[derive(Debug, Clone, Default)]
pub struct EngineCapabilities {
    pub supported_relations: Vec<String>,
    pub supported_functions: Vec<String>,
    pub supported_types: Vec<String>,
    pub max_plan_depth: usize,
    pub supports_extensions: bool,
}

impl EngineCapabilities {
    pub fn new() -> Self {
        Self {
            max_plan_depth: 100,
            supports_extensions: true,
            ..Default::default()
        }
    }
    
    pub fn supports_relation(&self, relation_type: &str) -> bool {
        self.supported_relations.iter().any(|r| r == relation_type)
    }
    
    pub fn supports_function(&self, function_name: &str) -> bool {
        self.supported_functions.iter().any(|f| f == function_name)
    }
}

/// Main trait that engines must implement for compliance testing.
pub trait ComplianceEngine {
    /// Return engine metadata.
    fn get_info(&self) -> EngineInfo;
    
    /// Return engine capabilities.
    fn get_capabilities(&self) -> EngineCapabilities;
    
    /// Execute a Substrait plan with given input data.
    fn execute_plan(
        &self,
        plan_bytes: &[u8],
        input_data: &HashMap<String, TableData>,
    ) -> Result<ComplianceResult>;
    
    /// Validate a Substrait plan without executing it.
    fn validate_plan(&self, plan_bytes: &[u8]) -> Result<ComplianceResult>;
}
