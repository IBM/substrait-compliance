//! Error types for compliance testing.

use std::fmt;
use thiserror::Error;

/// Main error type for compliance testing operations.
#[derive(Error, Debug)]
pub enum ComplianceError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    
    #[error("YAML parsing error: {0}")]
    Yaml(#[from] serde_yaml::Error),
    
    #[error("Plan execution error: {0}")]
    Execution(String),
    
    #[error("Plan validation error: {0}")]
    Validation(String),
    
    #[error("Test suite loading error: {0}")]
    Loading(String),
    
    #[error("Data mismatch: {0}")]
    DataMismatch(String),
}

pub type Result<T> = std::result::Result<T, ComplianceError>;
