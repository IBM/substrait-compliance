//! Result types for compliance testing.

use crate::table_data::TableData;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// Test execution status.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum TestStatus {
    Passed,
    Failed,
    Skipped,
    Error,
}

/// Result of executing a single test case.
#[derive(Debug, Clone)]
pub struct ComplianceResult {
    pub test_id: String,
    pub status: TestStatus,
    pub output_data: Option<TableData>,
    pub error_message: Option<String>,
    pub execution_time_ms: u64,
}

impl ComplianceResult {
    pub fn new(test_id: impl Into<String>, status: TestStatus) -> Self {
        Self {
            test_id: test_id.into(),
            status,
            output_data: None,
            error_message: None,
            execution_time_ms: 0,
        }
    }
    
    pub fn with_output(mut self, output: TableData) -> Self {
        self.output_data = Some(output);
        self
    }
    
    pub fn with_error(mut self, error: impl Into<String>) -> Self {
        self.error_message = Some(error.into());
        self
    }
    
    pub fn with_execution_time(mut self, time_ms: u64) -> Self {
        self.execution_time_ms = time_ms;
        self
    }
    
    pub fn is_success(&self) -> bool {
        self.status == TestStatus::Passed
    }
}

/// Aggregated report of test suite execution.
#[derive(Debug, Clone)]
pub struct ComplianceReport {
    pub suite_name: String,
    pub engine_name: String,
    pub results: Vec<ComplianceResult>,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
}

impl ComplianceReport {
    pub fn new(suite_name: impl Into<String>, engine_name: impl Into<String>) -> Self {
        Self {
            suite_name: suite_name.into(),
            engine_name: engine_name.into(),
            results: Vec::new(),
            start_time: Utc::now(),
            end_time: None,
        }
    }
    
    pub fn get_passed_count(&self) -> usize {
        self.results.iter().filter(|r| r.status == TestStatus::Passed).count()
    }
    
    pub fn get_failed_count(&self) -> usize {
        self.results.iter().filter(|r| r.status == TestStatus::Failed).count()
    }
    
    pub fn get_skipped_count(&self) -> usize {
        self.results.iter().filter(|r| r.status == TestStatus::Skipped).count()
    }
    
    pub fn get_error_count(&self) -> usize {
        self.results.iter().filter(|r| r.status == TestStatus::Error).count()
    }
    
    pub fn get_total_count(&self) -> usize {
        self.results.len()
    }
    
    pub fn get_pass_rate(&self) -> f64 {
        let total = self.get_total_count();
        if total == 0 {
            return 0.0;
        }
        (self.get_passed_count() as f64 / total as f64) * 100.0
    }
}

impl std::fmt::Display for ComplianceReport {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "ComplianceReport({} on {}: {}/{} passed, {:.1}%)",
            self.suite_name,
            self.engine_name,
            self.get_passed_count(),
            self.get_total_count(),
            self.get_pass_rate()
        )
    }
}
