//! The repository's suites load with input tables typed by the plan, as the leaderboard's
//! runner reads them.

use std::path::Path;
use substrait_compliance::{
    load_csv, load_input_table, Column, DataType, TestSuiteLoader, YamlTestSuiteLoader,
};

#[test]
fn tpch_input_tables_are_typed_by_the_plan_and_read_when_the_case_runs() {
    let suite = YamlTestSuiteLoader::new()
        .load(Path::new("../../test-suites/tpch/metadata.yaml"))
        .unwrap();
    let q01 = suite
        .get_test_cases()
        .iter()
        .find(|t| t.id == "q01")
        .unwrap();
    assert!(
        q01.input_data.is_empty(),
        "files are read when the case runs"
    );
    assert_eq!(q01.input_tables.len(), 1);
    assert_eq!(q01.input_tables[0].name, "LINEITEM");
    let inputs = q01.load_input_data().unwrap();
    let lineitem = &inputs["LINEITEM"];
    assert_eq!(lineitem.row_count(), 60175);
    assert_eq!(lineitem.columns[4].name, "L_QUANTITY");
    assert_eq!(lineitem.columns[4].data_type, DataType::Decimal);
    assert_eq!(q01.expected_output.as_ref().unwrap().row_count(), 4);
}

#[test]
fn input_table_skips_header_honours_quotes_and_checks_values() {
    let dir = std::env::temp_dir().join(format!("sc-rs-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let file = dir.join("t.csv");
    std::fs::write(&file, "id,name\n1,\"a, b\"\n2,null\n").unwrap();
    let columns = vec![
        Column::new("ID", DataType::Integer),
        Column::new("NAME", DataType::Varchar),
    ];
    let t = load_input_table(&file, &columns).unwrap();
    assert_eq!(t.rows, vec![vec!["1", "a, b"], vec!["2", "null"]]);

    std::fs::write(&file, "x,y\n").unwrap();
    assert!(
        load_input_table(&file, &columns).is_err(),
        "'x' is not an integer"
    );

    std::fs::write(&file, "n:bigint|s:varchar\n7|\"p|q\"\n").unwrap();
    let e = load_csv(&file).unwrap();
    assert_eq!(e.columns[0].data_type, DataType::Bigint);
    assert_eq!(e.rows, vec![vec!["7", "p|q"]]);
}
