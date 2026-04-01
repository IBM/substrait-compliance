//! Test runner for executing compliance tests.

use crate::engine::ComplianceEngine;
use crate::error::Result;
use crate::result::{ComplianceReport, ComplianceResult, TestStatus};
use crate::test_suite::{TestCase, TestSuite};
use chrono::Utc;
use std::time::Instant;

/// Executes compliance tests against an engine.
pub struct ComplianceRunner<'a, E: ComplianceEngine> {
    engine: &'a E,
}

impl<'a, E: ComplianceEngine> ComplianceRunner<'a, E> {
    pub fn new(engine: &'a E) -> Self {
        Self { engine }
    }
    
    /// Run all tests in a suite.
    pub fn run_test_suite(&self, suite: &dyn TestSuite) -> ComplianceReport {
        let engine_info = self.engine.get_info();
        let mut report = ComplianceReport::new(suite.get_name(), &engine_info.name);
        
        for test_case in suite.get_test_cases() {
            let result = self.run_test_case(test_case);
            report.results.push(result);
        }
        
        report.end_time = Some(Utc::now());
        report
    }
    
    /// Run a single test case.
    pub fn run_test_case(&self, test_case: &TestCase) -> ComplianceResult {
        let start = Instant::now();
        
        match self.engine.execute_plan(&test_case.plan_bytes, &test_case.input_data) {
            Ok(mut result) => {
                // If expected output provided, compare
                if let (Some(expected), Some(actual)) = 
                    (&test_case.expected_output, &result.output_data) {
                    if !self.compare_results(actual, expected) {
                        result.status = TestStatus::Failed;
                        result.error_message = Some("Output mismatch".to_string());
                    }
                }
                
                result.execution_time_ms = start.elapsed().as_millis() as u64;
                result
            }
            Err(e) => {
                ComplianceResult::new(&test_case.id, TestStatus::Error)
                    .with_error(e.to_string())
                    .with_execution_time(start.elapsed().as_millis() as u64)
            }
        }
    }
    
    fn compare_results(&self, actual: &crate::table_data::TableData, expected: &crate::table_data::TableData) -> bool {
        // Simplified comparison
        actual.row_count() == expected.row_count() && 
        actual.column_count() == expected.column_count()
    }
}
