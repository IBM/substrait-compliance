use substrait_compliance::*;
use std::collections::HashMap;

struct MockEngine;

impl ComplianceEngine for MockEngine {
    fn get_info(&self) -> EngineInfo {
        EngineInfo::new("MockEngine", "1.0.0", "Test")
    }
    
    fn get_capabilities(&self) -> EngineCapabilities {
        EngineCapabilities::new()
    }
    
    fn execute_plan(
        &self,
        _plan_bytes: &[u8],
        _input_data: &HashMap<String, TableData>,
    ) -> error::Result<ComplianceResult> {
        Ok(ComplianceResult::new("test", TestStatus::Passed))
    }
    
    fn validate_plan(&self, _plan_bytes: &[u8]) -> error::Result<ComplianceResult> {
        Ok(ComplianceResult::new("test", TestStatus::Passed))
    }
}

#[test]
fn test_engine_info() {
    let info = EngineInfo::new("TestEngine", "1.0.0", "TestVendor");
    assert_eq!(info.name, "TestEngine");
    assert_eq!(info.version, "1.0.0");
}

#[test]
fn test_engine_capabilities() {
    let mut caps = EngineCapabilities::new();
    caps.supported_relations.push("read".to_string());
    caps.supported_relations.push("filter".to_string());
    
    assert!(caps.supports_relation("read"));
    assert!(!caps.supports_relation("join"));
}

#[test]
fn test_compliance_result() {
    let result = ComplianceResult::new("q01", TestStatus::Passed);
    assert_eq!(result.test_id, "q01");
    assert!(result.is_success());
}

#[test]
fn test_compliance_report() {
    let mut report = ComplianceReport::new("tpch", "TestEngine");
    report.results.push(ComplianceResult::new("q01", TestStatus::Passed));
    report.results.push(ComplianceResult::new("q02", TestStatus::Failed));
    report.results.push(ComplianceResult::new("q03", TestStatus::Passed));
    
    assert_eq!(report.get_total_count(), 3);
    assert_eq!(report.get_passed_count(), 2);
    assert_eq!(report.get_failed_count(), 1);
    assert!((report.get_pass_rate() - 66.67).abs() < 0.1);
}

#[test]
fn test_table_data() {
    let cols = vec![
        Column::new("id", DataType::Integer).not_null(),
        Column::new("name", DataType::Varchar),
    ];
    let rows = vec![
        vec!["1".to_string(), "test".to_string()],
        vec!["2".to_string(), "data".to_string()],
    ];
    let data = TableData::new(cols, rows);
    
    assert_eq!(data.row_count(), 2);
    assert_eq!(data.column_count(), 2);
}

#[test]
fn test_mock_engine() {
    let engine = MockEngine;
    let info = engine.get_info();
    assert_eq!(info.name, "MockEngine");
    
    let result = engine.execute_plan(&[], &HashMap::new()).unwrap();
    assert!(result.is_success());
}
