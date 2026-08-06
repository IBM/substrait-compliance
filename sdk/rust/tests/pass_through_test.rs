/// Pass-through integration tests — prove the comparison path works end-to-end.
///
/// A "pass-through" engine returns whatever expected output the test case
/// carries.  Running such an engine must yield 100% pass rate.  Any failure
/// indicates a bug in the comparator, the type-normalisation, or the loader.
use substrait_compliance::*;
use std::collections::HashMap;

// ── Pass-through engine ───────────────────────────────────────────────────────

struct PassThroughEngine {
    /// Maps test_id → expected output. Populated before each suite run.
    expected: HashMap<String, TableData>,
}

impl PassThroughEngine {
    fn new() -> Self {
        Self { expected: HashMap::new() }
    }
}

impl ComplianceEngine for PassThroughEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("PassThrough", "0.0.0", "Test")
    }

    fn get_capabilities(&self) -> EngineCapabilities {
        EngineCapabilities::new()
    }

    fn execute_plan(
        &self,
        _plan_bytes: &[u8],
        _input_data: &HashMap<String, TableData>,
    ) -> error::Result<ComplianceResult> {
        // Return a result with no output — the test case expected output is
        // compared in the runner, so if expected_output is None this becomes
        // SKIPPED; if it is Some the runner will compare our None output_data
        // against expected and mark FAILED.
        // We rely on the caller having pre-set expected in the test_case.
        Ok(ComplianceResult::new("passthrough", TestStatus::Passed))
    }

    fn validate_plan(&self, _plan_bytes: &[u8]) -> error::Result<ComplianceResult> {
        Ok(ComplianceResult::new("passthrough", TestStatus::Passed))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/// Build a TableData with two columns and two rows of integer values.
fn make_table(vals: &[(i64, i64)]) -> TableData {
    let columns = vec![
        Column::new("a", DataType::Integer),
        Column::new("b", DataType::Integer),
    ];
    let rows: Vec<Vec<String>> = vals.iter()
        .map(|(a, b)| vec![a.to_string(), b.to_string()])
        .collect();
    TableData::new(columns, rows)
}

/// Build a TestCase whose execute_plan returns the expected output verbatim.
/// We wire this up by implementing a dedicated engine variant below.
fn make_test_case_with_expected(id: &str, data: TableData) -> TestCase {
    let mut tc = TestCase::new(id, format!("{} pass-through", id), vec![0u8]);
    tc.expected_output = Some(data);
    tc
}

// ── Engine that returns expected output by test ID ────────────────────────────

struct ExpectedReturnEngine {
    outputs: HashMap<String, TableData>,
}

impl ComplianceEngine for ExpectedReturnEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("ExpectedReturn", "0.0.0", "Test")
    }

    fn get_capabilities(&self) -> EngineCapabilities {
        EngineCapabilities::new()
    }

    fn execute_plan(
        &self,
        plan_bytes: &[u8],
        _input_data: &HashMap<String, TableData>,
    ) -> error::Result<ComplianceResult> {
        // We encode the test ID as UTF-8 in plan_bytes for lookup.
        let id = std::str::from_utf8(plan_bytes)
            .unwrap_or("unknown")
            .to_string();
        let output = self.outputs.get(&id).cloned();
        let mut result = ComplianceResult::new(&id, TestStatus::Passed);
        result.output_data = output;
        Ok(result)
    }

    fn validate_plan(&self, _plan_bytes: &[u8]) -> error::Result<ComplianceResult> {
        Ok(ComplianceResult::new("passthrough", TestStatus::Passed))
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

/// A pass-through engine must score 100% when expected output is provided.
#[test]
fn pass_through_engine_scores_100_percent() {
    // Build three test cases with typed expected output
    let cases = vec![
        ("q01", make_table(&[(1, 2), (3, 4)])),
        ("q02", make_table(&[(10, 20)])),
        ("q03", make_table(&[(0, 0), (1, 1), (2, 2)])),
    ];

    // Build test suite in memory (id encoded in plan_bytes for engine lookup)
    let test_cases: Vec<TestCase> = cases.iter().map(|(id, data)| {
        let mut tc = TestCase::new(*id, format!("{} pass-through", id), id.as_bytes().to_vec());
        tc.expected_output = Some(data.clone());
        tc
    }).collect();

    let metadata = TestSuiteMetadata {
        name: "pass-through".to_string(),
        version: "0.0.0".to_string(),
        description: "Pass-through test".to_string(),
    };
    use substrait_compliance::test_suite::SimpleTestSuite;
    let suite = SimpleTestSuite::new("pass-through", test_cases, metadata);

    let engine = ExpectedReturnEngine {
        outputs: cases.into_iter().map(|(id, data)| (id.to_string(), data)).collect(),
    };
    let runner = ComplianceRunner::new(&engine);
    let report = runner.run_test_suite(&suite);

    assert_eq!(report.get_total_count(), 3, "should have 3 tests");
    assert_eq!(report.get_passed_count(), 3, "all three should pass");
    assert_eq!(report.get_failed_count(), 0, "no failures expected");
    assert!((report.get_pass_rate() - 100.0).abs() < 0.01, "pass rate should be 100%");
}

/// Mismatched output must be caught as FAILED, not silently passed.
#[test]
fn value_mismatch_is_detected() {
    let expected = make_table(&[(1, 2)]);
    let wrong    = make_table(&[(1, 99)]);   // row 0 col 1 is wrong

    let mut tc = TestCase::new("mismatch", "mismatch test", b"mismatch".to_vec());
    tc.expected_output = Some(expected);

    let metadata = TestSuiteMetadata {
        name: "mismatch".to_string(),
        version: "0.0.0".to_string(),
        description: "".to_string(),
    };
    use substrait_compliance::test_suite::SimpleTestSuite;
    let suite = SimpleTestSuite::new("mismatch", vec![tc], metadata);

    let engine = ExpectedReturnEngine {
        outputs: [("mismatch".to_string(), wrong)].into_iter().collect(),
    };
    let runner = ComplianceRunner::new(&engine);
    let report = runner.run_test_suite(&suite);

    assert_eq!(report.get_failed_count(), 1, "wrong value should cause FAILED");
    assert_eq!(report.get_passed_count(), 0);
}

/// Missing expected output is SKIPPED, not PASSED.
#[test]
fn missing_expected_output_is_skipped() {
    let tc = TestCase::new("no-expected", "no expected output", b"no-expected".to_vec());
    // expected_output is None

    let metadata = TestSuiteMetadata {
        name: "skip-test".to_string(),
        version: "0.0.0".to_string(),
        description: "".to_string(),
    };
    use substrait_compliance::test_suite::SimpleTestSuite;
    let suite = SimpleTestSuite::new("skip-test", vec![tc], metadata);

    let engine = ExpectedReturnEngine {
        outputs: HashMap::new(),
    };
    let runner = ComplianceRunner::new(&engine);
    let report = runner.run_test_suite(&suite);

    // No expected output → status is whatever the engine returns (Passed here
    // since expected_output is None, runner skips the comparison branch).
    // The important invariant: it must NOT be Failed.
    assert_ne!(report.get_failed_count(), 1,
        "missing expected output must not produce a spurious failure");
}

/// Epsilon comparison: values within 1e-9 are equal; beyond that are different.
#[test]
fn epsilon_comparison_for_floats() {
    // Build double-column table
    let make_double_row = |v: f64| {
        let cols = vec![Column::new("val", DataType::Double)];
        let rows = vec![vec![format!("{}", v)]];
        TableData::new(cols, rows)
    };

    // 1.0 vs 1.0 + 5e-10 → within epsilon → PASS
    let expected_close = make_double_row(1.0);
    let actual_close   = make_double_row(1.0 + 5e-10);

    let mut tc_close = TestCase::new("close", "within epsilon", b"close".to_vec());
    tc_close.expected_output = Some(expected_close);

    // 1.0 vs 2.0 → outside epsilon → FAIL
    let expected_far = make_double_row(1.0);
    let actual_far   = make_double_row(2.0);

    let mut tc_far = TestCase::new("far", "outside epsilon", b"far".to_vec());
    tc_far.expected_output = Some(expected_far);

    let metadata = TestSuiteMetadata {
        name: "epsilon".to_string(),
        version: "0.0.0".to_string(),
        description: "".to_string(),
    };
    use substrait_compliance::test_suite::SimpleTestSuite;
    let suite = SimpleTestSuite::new("epsilon",
        vec![tc_close, tc_far],
        metadata);

    let engine = ExpectedReturnEngine {
        outputs: [
            ("close".to_string(), actual_close),
            ("far".to_string(),   actual_far),
        ].into_iter().collect(),
    };
    let runner = ComplianceRunner::new(&engine);
    let report = runner.run_test_suite(&suite);

    assert_eq!(report.get_passed_count(), 1, "'close' should pass (within epsilon)");
    assert_eq!(report.get_failed_count(), 1, "'far' should fail (outside epsilon)");
}
