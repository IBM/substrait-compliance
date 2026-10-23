//! Test runner for executing compliance tests.

use crate::engine::ComplianceEngine;
use crate::result::{ComplianceReport, ComplianceResult, TestStatus};
use crate::table_data::{DataType, TableData};
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

        // Input tables named by the suite are read now, one case at a time.
        let loaded;
        let inputs = if test_case.input_tables.is_empty() {
            &test_case.input_data
        } else {
            match test_case.load_input_data() {
                Ok(tables) => {
                    loaded = tables;
                    &loaded
                }
                Err(e) => {
                    return ComplianceResult::new(&test_case.id, TestStatus::Error)
                        .with_error(e.to_string())
                        .with_execution_time(start.elapsed().as_millis() as u64)
                }
            }
        };

        match self.engine.execute_plan(&test_case.plan_bytes, inputs) {
            Ok(mut result) => {
                // Compare against expected output (guaranteed Some by the guard above)
                if let (Some(expected), Some(actual)) =
                    (&test_case.expected_output, &result.output_data)
                {
                    if let Some(mismatch) =
                        describe_mismatch(expected, actual, test_case.declared_scales.as_deref())
                    {
                        result.status = TestStatus::Failed;
                        result.error_message = Some(format!("Output mismatch: {mismatch}"));
                    }
                }

                result.execution_time_ms = start.elapsed().as_millis() as u64;
                result
            }
            Err(e) => ComplianceResult::new(&test_case.id, TestStatus::Error)
                .with_error(e.to_string())
                .with_execution_time(start.elapsed().as_millis() as u64),
        }
    }
}

/// Absolute tolerance near zero; relative tolerance for larger magnitudes.
pub const NUMERIC_ABS_TOLERANCE: f64 = 1e-9;
pub const NUMERIC_REL_TOLERANCE: f64 = 1e-9;

/// Compare an engine's output with the expected output, as the leaderboard's comparators
/// do (`sdk/conformance/comparison-vectors.json`): same row and column counts, column
/// names case-insensitively, column types by family (all numerics are one family; dates
/// and times compare as text), rows in order, numbers within max(1e-9, 1e-9 * magnitude),
/// everything else as text. NULL (an empty value or `null`) matches only NULL.
///
/// `declared_scales`, when given, holds per column the scale of the decimal type the plan
/// declares (None for other columns); such a column also matches within 10^-scale.
///
/// Returns None when the outputs match, otherwise a description of the first difference.
pub fn describe_mismatch(
    expected: &TableData,
    actual: &TableData,
    declared_scales: Option<&[Option<u32>]>,
) -> Option<String> {
    if expected.row_count() != actual.row_count() {
        return Some(format!(
            "expected {} rows, got {}",
            expected.row_count(),
            actual.row_count()
        ));
    }
    let names = |t: &TableData| t.columns.iter().map(|c| c.name.clone()).collect::<Vec<_>>();
    if expected.column_count() != actual.column_count() {
        return Some(format!(
            "expected {} columns {:?}, got {} {:?}",
            expected.column_count(),
            names(expected),
            actual.column_count(),
            names(actual)
        ));
    }
    let same_names = expected
        .columns
        .iter()
        .zip(&actual.columns)
        .all(|(e, a)| e.name.trim().eq_ignore_ascii_case(a.name.trim()));
    if !same_names {
        return Some(format!(
            "expected column names {:?}, got {:?}",
            names(expected),
            names(actual)
        ));
    }
    let same_types = expected
        .columns
        .iter()
        .zip(&actual.columns)
        .all(|(e, a)| type_family(e.data_type) == type_family(a.data_type));
    if !same_types {
        let types = |t: &TableData| t.columns.iter().map(|c| c.data_type).collect::<Vec<_>>();
        return Some(format!(
            "expected column types {:?}, got {:?}",
            types(expected),
            types(actual)
        ));
    }
    for (r, (e_row, a_row)) in expected.rows.iter().zip(&actual.rows).enumerate() {
        for (c, (e, a)) in e_row.iter().zip(a_row).enumerate() {
            let scale = declared_scales.and_then(|s| s.get(c).copied().flatten());
            let numeric = type_family(expected.columns[c].data_type) == "numeric";
            if !values_match(e, a, scale, numeric) {
                return Some(format!(
                    "row {r}, column {}: expected {e:?}, got {a:?}",
                    expected.columns[c].name
                ));
            }
        }
    }
    None
}

/// The type family a column is compared by.
fn type_family(dt: DataType) -> &'static str {
    match dt {
        DataType::Integer | DataType::Bigint | DataType::Double | DataType::Decimal => "numeric",
        DataType::Varchar | DataType::Date => "string",
        DataType::Boolean => "boolean",
    }
}

fn is_null(v: &str) -> bool {
    let v = v.trim();
    v.is_empty() || v.eq_ignore_ascii_case("null")
}

/// Numbers within the tolerance (and, for a declared decimal, within 10^-scale);
/// NaN matches NaN and infinity only the same infinity.
pub fn numbers_close(expected: f64, actual: f64, declared_scale: Option<u32>) -> bool {
    if expected.is_nan() || actual.is_nan() {
        return expected.is_nan() && actual.is_nan();
    }
    if expected.is_infinite() || actual.is_infinite() {
        return expected == actual;
    }
    let magnitude = expected.abs().max(actual.abs());
    let mut tolerance = NUMERIC_ABS_TOLERANCE.max(NUMERIC_REL_TOLERANCE * magnitude);
    if let Some(s) = declared_scale {
        tolerance = tolerance.max(10f64.powi(-(s as i32)) * (1.0 + NUMERIC_REL_TOLERANCE));
    }
    (expected - actual).abs() <= tolerance
}

/// TableData holds every value as text: in a numeric column numbers are parsed and compared
/// numerically; booleans compare case-insensitively, everything else exactly (so "001" and "1"
/// in a text column stay different).
fn values_match(expected: &str, actual: &str, declared_scale: Option<u32>, numeric: bool) -> bool {
    if is_null(expected) || is_null(actual) {
        return is_null(expected) && is_null(actual);
    }
    if numeric {
        if let (Ok(e), Ok(a)) = (expected.trim().parse::<f64>(), actual.trim().parse::<f64>()) {
            return numbers_close(e, a, declared_scale);
        }
    }
    let (e, a) = (expected.to_lowercase(), actual.to_lowercase());
    if (e == "true" || e == "false") && (a == "true" || a == "false") {
        return e == a;
    }
    expected == actual
}
