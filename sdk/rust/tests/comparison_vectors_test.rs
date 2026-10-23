//! Query-output comparison: the vectors shared with the leaderboard's comparators
//! (sdk/conformance/comparison-vectors.json), checked against `describe_mismatch`.

use serde_json::Value;
use substrait_compliance::{describe_mismatch, parse_data_type, Column, TableData};

/// TableData holds values as text; NULL is an empty value.
fn text(v: &Value) -> String {
    match v {
        Value::Null => String::new(),
        Value::String(s) => s.clone(),
        Value::Bool(b) => b.to_string(),
        Value::Number(n) => n.to_string(),
        Value::Object(o) => o
            .get("double")
            .or_else(|| o.get("decimal"))
            .and_then(Value::as_str)
            .expect("{\"double\"|\"decimal\": \"...\"}")
            .to_string(),
        Value::Array(_) => panic!("unexpected array value"),
    }
}

fn table(t: &Value) -> TableData {
    let names = t["names"].as_array().unwrap();
    let types = t["types"].as_array().unwrap();
    let columns = names
        .iter()
        .zip(types)
        .map(|(n, ty)| Column::new(n.as_str().unwrap(), parse_data_type(ty.as_str().unwrap())))
        .collect();
    let rows = t["rows"]
        .as_array()
        .unwrap()
        .iter()
        .map(|r| r.as_array().unwrap().iter().map(text).collect())
        .collect();
    TableData::new(columns, rows)
}

#[test]
fn comparison_vectors() {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../conformance/comparison-vectors.json"
    );
    let doc: Value =
        serde_json::from_str(&std::fs::read_to_string(path).expect("read vectors")).unwrap();
    let vectors = doc["vectors"].as_array().unwrap();
    assert!(!vectors.is_empty(), "no vectors read");
    let mut failures = Vec::new();
    for v in vectors {
        let scales: Option<Vec<Option<u32>>> = v.get("declaredScales").map(|s| {
            s.as_array()
                .unwrap()
                .iter()
                .map(|x| x.as_u64().map(|n| n as u32))
                .collect()
        });
        let mismatch = describe_mismatch(
            &table(&v["expected"]),
            &table(&v["actual"]),
            scales.as_deref(),
        );
        if mismatch.is_none() != v["match"].as_bool().unwrap() {
            failures.push(format!("{}: {:?}", v["name"].as_str().unwrap(), mismatch));
        }
    }
    assert!(
        failures.is_empty(),
        "vectors that disagree:\n{}",
        failures.join("\n")
    );
}
