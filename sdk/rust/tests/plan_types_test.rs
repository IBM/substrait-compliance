//! The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out.

use substrait_compliance::plan_types::declared_scales;
use substrait_compliance::{TestSuiteLoader, YamlTestSuiteLoader};

fn plan(rel: &str) -> String {
    let path = format!(
        "{}/../../test-suites/tpch/{rel}",
        env!("CARGO_MANIFEST_DIR")
    );
    std::fs::read_to_string(path).expect("read plan")
}

#[test]
fn q01_declares_decimal_aggregates() {
    let s = declared_scales(&plan("plans/q01.json")).expect("scales");
    assert_eq!(
        s,
        vec![
            None,
            None,
            Some(2),
            Some(2),
            Some(4),
            Some(6),
            Some(2),
            Some(2),
            Some(2),
            None
        ]
    );
}

#[test]
fn both_encodings_declare_the_same_scales() {
    for q in 1..=22 {
        let name = format!("q{q:02}.json");
        let current = declared_scales(&plan(&format!("plans/{name}")));
        assert!(current.is_some(), "{name}");
        assert_eq!(
            current,
            declared_scales(&plan(&format!("plans-substrait-0.81/{name}"))),
            "{name}"
        );
    }
}

#[test]
fn unknown_relation_gives_no_scales() {
    assert_eq!(
        declared_scales(r#"{"relations":[{"root":{"input":{"exchange":{}}}}]}"#),
        None
    );
}

#[test]
fn the_loader_attaches_scales_to_each_case() {
    let path = format!(
        "{}/../../test-suites/tpch/metadata.yaml",
        env!("CARGO_MANIFEST_DIR")
    );
    let suite = YamlTestSuiteLoader::new()
        .load(std::path::Path::new(&path))
        .expect("load");
    let q01 = &suite.get_test_cases()[0];
    assert_eq!(q01.id, "q01");
    assert_eq!(q01.declared_scales.as_ref().map(|s| s[6]), Some(Some(2)));
}
