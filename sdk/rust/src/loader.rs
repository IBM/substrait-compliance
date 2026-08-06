//! Test suite loaders for various formats.
//!
//! The YAML loader reads a `metadata.yaml` that lists test-case entries.
//! Each entry names a plan binary and optional input/expected CSV files.
//! Expected CSV files use typed headers in the form `colname:type` and are
//! located in an `expected/` subdirectory next to the metadata file.

use crate::error::{ComplianceError, Result};
use crate::table_data::{Column, DataType, TableData};
use crate::test_suite::{SimpleTestSuite, TestCase, TestSuite, TestSuiteMetadata};
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use std::path::Path;

/// Trait for loading test suites.
pub trait TestSuiteLoader {
    fn load(&self, path: &Path) -> Result<Box<dyn TestSuite>>;
    fn supports(&self, path: &Path) -> bool;
}

/// Loads test suites from YAML metadata files.
pub struct YamlTestSuiteLoader;

impl YamlTestSuiteLoader {
    pub fn new() -> Self { Self }
}

impl Default for YamlTestSuiteLoader {
    fn default() -> Self { Self::new() }
}

// ── YAML schema ─────────────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
struct TestSuiteDefinition {
    name: String,
    version: String,
    #[serde(default)]
    description: String,
    #[serde(rename = "testCases", default)]
    test_cases: Vec<TestCaseDefinition>,
}

#[derive(Debug, Deserialize)]
struct TestCaseDefinition {
    id: String,
    #[serde(default)]
    description: String,
    /// Relative path to the binary Substrait plan file.
    #[serde(rename = "planBinary")]
    plan_binary: String,
    /// Optional relative path to the expected-output CSV.
    /// Defaults to `expected/<id>.csv` when omitted.
    #[serde(rename = "expectedOutput")]
    expected_output: Option<String>,
}

// ── Loader impl ──────────────────────────────────────────────────────────────

impl TestSuiteLoader for YamlTestSuiteLoader {
    fn load(&self, path: &Path) -> Result<Box<dyn TestSuite>> {
        let content = fs::read_to_string(path)?;
        let def: TestSuiteDefinition = serde_yaml::from_str(&content)?;

        let base_dir = path.parent().ok_or_else(||
            ComplianceError::Loading("Invalid path".to_string())
        )?;

        let mut test_cases = Vec::new();
        for tc_def in def.test_cases {
            let plan_path = base_dir.join(&tc_def.plan_binary);
            let plan_bytes = fs::read(&plan_path).map_err(|e| {
                ComplianceError::Loading(format!("plan {}: {}", plan_path.display(), e))
            })?;

            let mut tc = TestCase::new(tc_def.id.clone(), tc_def.description, plan_bytes);

            // Resolve expected-output path: explicit field or default location.
            let csv_path = match &tc_def.expected_output {
                Some(rel) => base_dir.join(rel),
                None      => base_dir.join("expected").join(format!("{}.csv", tc_def.id)),
            };
            if csv_path.exists() {
                tc.expected_output = Some(load_csv(&csv_path)?);
            }

            test_cases.push(tc);
        }

        let metadata = TestSuiteMetadata {
            name: def.name.clone(),
            version: def.version,
            description: def.description,
        };

        Ok(Box::new(SimpleTestSuite::new(def.name, test_cases, metadata)))
    }

    fn supports(&self, path: &Path) -> bool {
        path.extension()
            .and_then(|e| e.to_str())
            .map(|e| e == "yaml" || e == "yml")
            .unwrap_or(false)
    }
}

// ── CSV / expected-output parsing ────────────────────────────────────────────

/// Parse a pipe-delimited CSV with an optional typed header row.
///
/// Typed header format: `colname:type|colname:type|...`
/// Untyped header: absent — columns are named `column_1`, `column_2`, …
pub fn load_csv(path: &Path) -> Result<TableData> {
    let text = fs::read_to_string(path).map_err(|e| {
        ComplianceError::Loading(format!("expected CSV {}: {}", path.display(), e))
    })?;

    let lines: Vec<&str> = text.lines().collect();
    if lines.is_empty() {
        return Ok(TableData::new(vec![], vec![]));
    }

    // Detect typed header: every field must contain ':'
    let first = lines[0];
    let fields: Vec<&str> = first.split('|').collect();
    let has_typed_header = fields.iter().all(|f| f.contains(':'));

    let (columns, data_start) = if has_typed_header {
        let cols = fields.iter().map(|f| {
            let (name, type_str) = f.split_once(':').unwrap_or((f, "varchar"));
            Column::new(name.trim(), parse_data_type(type_str.trim()))
        }).collect();
        (cols, 1)
    } else {
        // No header: default column names, all varchar
        let ncols = fields.len();
        let cols = (1..=ncols)
            .map(|i| Column::new(format!("column_{}", i), DataType::Varchar))
            .collect();
        (cols, 0)
    };

    let mut rows: Vec<Vec<String>> = Vec::new();
    for line in &lines[data_start..] {
        let line = line.trim();
        if line.is_empty() { continue; }
        let row: Vec<String> = line.split('|').map(|v| v.trim().to_string()).collect();
        rows.push(row);
    }

    Ok(TableData::new(columns, rows))
}

fn parse_data_type(s: &str) -> DataType {
    match s.to_lowercase().as_str() {
        "integer" | "int" | "int32" | "i32" | "smallint" | "int4" => DataType::Integer,
        "bigint" | "int64" | "i64"                                  => DataType::Bigint,
        "double" | "fp64" | "float8" | "numeric" | "decimal"        => DataType::Double,
        "boolean" | "bool"                                           => DataType::Boolean,
        "date" | "timestamp"                                         => DataType::Date,
        _                                                            => DataType::Varchar,
    }
}

// ── Input-data CSV ────────────────────────────────────────────────────────────

/// Load a directory of named input CSVs into a `HashMap<name, TableData>`.
/// Each file `tablename.csv` becomes the entry keyed by `tablename`.
pub fn load_input_data(dir: &Path) -> Result<HashMap<String, TableData>> {
    let mut map = HashMap::new();
    if !dir.exists() { return Ok(map); }
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) == Some("csv") {
            let name = path.file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("unknown")
                .to_string();
            map.insert(name, load_csv(&path)?);
        }
    }
    Ok(map)
}
