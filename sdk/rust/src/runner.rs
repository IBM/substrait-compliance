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

        // No expected output means we cannot verify correctness.
        // Return SKIPPED immediately — treating the run as PASSED would give a
        // false impression that the output was validated.
        if test_case.expected_output.is_none() {
            return ComplianceResult::new(&test_case.id, TestStatus::Skipped)
                .with_error("No expected output — cannot verify correctness".to_string())
                .with_execution_time(start.elapsed().as_millis() as u64);
        }

        match self.engine.execute_plan(&test_case.plan_bytes, &test_case.input_data) {
            Ok(mut result) => {
                // Compare against expected output (guaranteed Some by the guard above)
                if let (Some(expected), Some(actual)) =
                    (&test_case.expected_output, &result.output_data)
                {
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
        if actual.row_count() != expected.row_count() {
            return false;
        }
        if actual.column_count() != expected.column_count() {
            return false;
        }
        // Check column types (normalised)
        for (a_col, e_col) in actual.columns.iter().zip(expected.columns.iter()) {
            if normalize_type(a_col.data_type) != normalize_type(e_col.data_type) {
                return false;
            }
        }
        // Check every cell value
        for (a_row, e_row) in actual.rows.iter().zip(expected.rows.iter()) {
            for (a_val, e_val) in a_row.iter().zip(e_row.iter()) {
                if !values_match(a_val, e_val) {
                    return false;
                }
            }
        }
        true
    }
}

/// Canonical type for comparison purposes. Maps aliases to a single name so
/// that, e.g., "fp64" and "double" are treated as equivalent.
fn normalize_type(dt: crate::table_data::DataType) -> &'static str {
    use crate::table_data::DataType::*;
    match dt {
        Integer           => "integer",
        Bigint            => "bigint",
        Double | Decimal  => "double",
        Varchar           => "string",
        Date              => "string",   // dates compared as strings
        Boolean           => "boolean",
    }
}

/// Value-level comparison with epsilon for floating-point strings.
/// TableData stores all values as `String`; parse numerics for comparison.
fn values_match(actual: &str, expected: &str) -> bool {
    if actual == expected {
        return true;
    }
    // Try numeric comparison with epsilon 1e-9
    if let (Ok(a), Ok(e)) = (actual.parse::<f64>(), expected.parse::<f64>()) {
        if a.is_nan() && e.is_nan() {
            return true;
        }
        return (a - e).abs() < 1e-9;
    }
    // Boolean normalisation
    let a_lower = actual.to_lowercase();
    let e_lower = expected.to_lowercase();
    if (a_lower == "true" || a_lower == "false") && (e_lower == "true" || e_lower == "false") {
        return a_lower == e_lower;
    }
    false
}
