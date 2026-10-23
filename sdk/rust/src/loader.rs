//! Test suite loaders for various formats.
//!
//! The YAML loader reads a `metadata.yaml` that lists test-case entries, in this
//! repository's format (test-suites/tpch/metadata.yaml and the like). Each entry names a
//! plan binary, optionally its JSON form, input tables and an expected-output CSV.
//!
//! Inputs and expected outputs are read as scripts/run_plan_compliance.py reads them for the
//! leaderboard:
//!
//! - Input tables are typed from the plan's read schemas (`planJson`) and keyed by the table
//!   name the plan reads. A first row that names the plan's columns is a header and skipped.
//!   Values are trimmed and checked against the column type; an empty value or `null` is NULL.
//!   Without `planJson`, an input table is read like an expected output. The files are read
//!   when the case runs (`TestCase::input_tables`), not when the suite loads.
//! - Expected CSV files use typed headers in the form `colname:type`; they default to
//!   `expected/<id>.csv` next to the metadata file.
//! - The decimal scales the plan declares (`planJson`) are attached to each case.

use crate::error::{ComplianceError, Result};
use crate::table_data::{Column, DataType, TableData};
use crate::test_suite::{InputTable, SimpleTestSuite, TestCase, TestSuite, TestSuiteMetadata};
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
    pub fn new() -> Self {
        Self
    }
}

impl Default for YamlTestSuiteLoader {
    fn default() -> Self {
        Self::new()
    }
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
    /// Optional relative path to the plan's JSON form, used for the declared output types.
    #[serde(rename = "planJson")]
    plan_json: Option<String>,
    /// Optional relative path to the expected-output CSV.
    /// Defaults to `expected/<id>.csv` when omitted.
    #[serde(rename = "expectedOutput")]
    expected_output: Option<String>,
    #[serde(rename = "inputTables", default)]
    input_tables: Vec<InputTableDefinition>,
}

#[derive(Debug, Deserialize)]
struct InputTableDefinition {
    name: String,
    file: String,
}

// ── Loader impl ──────────────────────────────────────────────────────────────

impl TestSuiteLoader for YamlTestSuiteLoader {
    fn load(&self, path: &Path) -> Result<Box<dyn TestSuite>> {
        let content = fs::read_to_string(path)?;
        let def: TestSuiteDefinition = serde_yaml::from_str(&content)?;

        let base_dir = path
            .parent()
            .ok_or_else(|| ComplianceError::Loading("Invalid path".to_string()))?;

        let mut test_cases = Vec::new();
        for tc_def in def.test_cases {
            let plan_path = base_dir.join(&tc_def.plan_binary);
            let plan_bytes = fs::read(&plan_path).map_err(|e| {
                ComplianceError::Loading(format!("plan {}: {}", plan_path.display(), e))
            })?;

            let mut tc = TestCase::new(tc_def.id.clone(), tc_def.description, plan_bytes);
            let mut schemas = HashMap::new();
            if let Some(rel) = &tc_def.plan_json {
                let json = fs::read_to_string(base_dir.join(rel)).map_err(|e| {
                    ComplianceError::Loading(format!("test case {}: {}: {}", tc_def.id, rel, e))
                })?;
                tc.declared_scales = crate::plan_types::declared_scales(&json);
                schemas = crate::plan_types::parse_plan_json(&json)
                    .and_then(|plan| crate::plan_types::named_table_schemas(&plan))
                    .map_err(|e| {
                        ComplianceError::Loading(format!("test case {}: {}", tc_def.id, e))
                    })?;
            }
            for t in &tc_def.input_tables {
                let plan_table = schemas
                    .iter()
                    .find(|(n, _)| n.eq_ignore_ascii_case(&t.name));
                tc.input_tables.push(InputTable {
                    name: plan_table
                        .map(|(n, _)| n.clone())
                        .unwrap_or_else(|| t.name.clone()),
                    file: base_dir.join(&t.file),
                    columns: plan_table.map(|(_, cols)| {
                        cols.iter()
                            .map(|(name, ty)| Column::new(name.clone(), plan_data_type(ty)))
                            .collect()
                    }),
                });
            }

            // Resolve expected-output path: explicit field or default location.
            let csv_path = match &tc_def.expected_output {
                Some(rel) => base_dir.join(rel),
                None => base_dir.join("expected").join(format!("{}.csv", tc_def.id)),
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

        Ok(Box::new(SimpleTestSuite::new(
            def.name, test_cases, metadata,
        )))
    }

    fn supports(&self, path: &Path) -> bool {
        path.extension()
            .and_then(|e| e.to_str())
            .map(|e| e == "yaml" || e == "yml")
            .unwrap_or(false)
    }
}

// ── CSV / expected-output parsing ────────────────────────────────────────────

/// Parse a CSV (pipe- or comma-delimited, detected from the first line) with an optional
/// typed header row; values are trimmed, and double quotes may enclose a delimiter.
///
/// Typed header format: `colname:type|colname:type|...`
/// Untyped header: absent — columns are named `column_1`, `column_2`, …
pub fn load_csv(path: &Path) -> Result<TableData> {
    let lines = read_lines(path)?;
    if lines.is_empty() {
        return Ok(TableData::new(vec![], vec![]));
    }
    let delimiter = detect_delimiter(&lines[0]);
    let fields = split_line(&lines[0], delimiter);
    // Typed header: every field has the form name:type
    let has_typed_header = fields
        .iter()
        .all(|f| matches!(f.find(':'), Some(i) if i > 0 && i < f.len() - 1));

    let (columns, data_start): (Vec<Column>, usize) = if has_typed_header {
        let cols = fields
            .iter()
            .map(|f| {
                let (name, type_str) = f.split_once(':').unwrap_or((f, "varchar"));
                Column::new(name.trim(), parse_data_type(type_str.trim()))
            })
            .collect();
        (cols, 1)
    } else {
        // No header: default column names, all varchar
        let cols = (1..=fields.len())
            .map(|i| Column::new(format!("column_{}", i), DataType::Varchar))
            .collect();
        (cols, 0)
    };

    let mut rows: Vec<Vec<String>> = Vec::new();
    for (n, line) in lines[data_start..].iter().enumerate() {
        if line.trim().is_empty() {
            continue;
        }
        let row = split_line(line, delimiter);
        if row.len() != columns.len() {
            return Err(ComplianceError::Loading(format!(
                "CSV row {} in {} has {} values, expected {}",
                n + 1 + data_start,
                path.display(),
                row.len(),
                columns.len()
            )));
        }
        rows.push(row);
    }

    Ok(TableData::new(columns, rows))
}

/// Read a CSV typed by the columns the plan reads it with. A first row that names the
/// plan's columns is a header and skipped. Values are trimmed and must parse as the column's
/// type (integers, floats, decimals); an empty value or `null` is NULL.
pub fn load_input_table(path: &Path, columns: &[Column]) -> Result<TableData> {
    let lines = read_lines(path)?;
    let mut rows: Vec<Vec<String>> = Vec::new();
    if let Some(first) = lines.first() {
        let delimiter = detect_delimiter(first);
        let header = split_line(first, delimiter);
        let is_header = header.len() == columns.len()
            && header.iter().zip(columns).all(|(h, c)| {
                h.split(':')
                    .next()
                    .unwrap_or_default()
                    .eq_ignore_ascii_case(&c.name)
            });
        for (n, line) in lines.iter().enumerate().skip(usize::from(is_header)) {
            if line.trim().is_empty() {
                continue;
            }
            let row = split_line(line, delimiter);
            if row.len() != columns.len() {
                return Err(ComplianceError::Loading(format!(
                    "{} row {} has {} values, expected {}",
                    path.display(),
                    n + 1,
                    row.len(),
                    columns.len()
                )));
            }
            for (v, c) in row.iter().zip(columns) {
                let null = v.is_empty() || v.eq_ignore_ascii_case("null");
                let ok = null
                    || match c.data_type {
                        DataType::Integer | DataType::Bigint => v.parse::<i64>().is_ok(),
                        DataType::Double | DataType::Decimal => v.parse::<f64>().is_ok(),
                        _ => true,
                    };
                if !ok {
                    return Err(ComplianceError::Loading(format!(
                        "{} row {}, column {}: '{}' is not a {:?}",
                        path.display(),
                        n + 1,
                        c.name,
                        v,
                        c.data_type
                    )));
                }
            }
            rows.push(row);
        }
    }
    Ok(TableData::new(columns.to_vec(), rows))
}

/// The DataType for a Substrait type in a plan's JSON form ({"i32": {...}}, ...).
fn plan_data_type(ty: &serde_yaml::Value) -> DataType {
    let kind = ty
        .as_mapping()
        .and_then(|m| m.keys().next())
        .and_then(|k| k.as_str())
        .unwrap_or("string");
    match kind {
        "i8" | "i16" | "i32" => DataType::Integer,
        "i64" => DataType::Bigint,
        "fp32" | "fp64" => DataType::Double,
        "decimal" => DataType::Decimal,
        "bool" => DataType::Boolean,
        "date"
        | "time"
        | "timestamp"
        | "timestampTz"
        | "precisionTime"
        | "precisionTimestamp"
        | "precisionTimestampTz" => DataType::Date,
        _ => DataType::Varchar,
    }
}

fn read_lines(path: &Path) -> Result<Vec<String>> {
    let text = fs::read_to_string(path)
        .map_err(|e| ComplianceError::Loading(format!("CSV {}: {}", path.display(), e)))?;
    let mut lines: Vec<String> = text.lines().map(str::to_string).collect();
    while lines.last().is_some_and(|l| l.is_empty()) {
        lines.pop();
    }
    Ok(lines)
}

fn detect_delimiter(line: &str) -> char {
    if line.matches('|').count() > line.matches(',').count() {
        '|'
    } else {
        ','
    }
}

/// One line, split on the delimiter outside double quotes ("" inside quotes is a quote),
/// each value trimmed and stripped of surrounding quotes.
fn split_line(line: &str, delimiter: char) -> Vec<String> {
    let mut values = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut chars = line.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '"' {
            if in_quotes && chars.peek() == Some(&'"') {
                current.push('"');
                chars.next();
            } else {
                in_quotes = !in_quotes;
            }
        } else if ch == delimiter && !in_quotes {
            values.push(current.trim().to_string());
            current.clear();
        } else {
            current.push(ch);
        }
    }
    values.push(current.trim().to_string());
    values
        .into_iter()
        .map(|v| {
            if v.len() >= 2 && v.starts_with('"') && v.ends_with('"') {
                v[1..v.len() - 1].to_string()
            } else {
                v
            }
        })
        .collect()
}

/// Map a type name from a typed CSV header (or an engine's result) to a DataType. Type
/// parameters are ignored: `decimal(15,2)` is a decimal, `varchar(25)` a varchar.
pub fn parse_data_type(s: &str) -> DataType {
    let lower = s.trim().to_lowercase();
    let base = lower.split('(').next().unwrap_or("").trim();
    match base {
        "integer" | "int" | "int32" | "i32" | "i8" | "i16" | "smallint" | "tinyint" | "int2"
        | "int4" => DataType::Integer,
        "bigint" | "long" | "int64" | "i64" | "int8" | "hugeint" => DataType::Bigint,
        "double" | "fp64" | "float8" | "float" | "fp32" | "real" | "float4" | "number" => {
            DataType::Double
        }
        "decimal" | "numeric" => DataType::Decimal,
        "boolean" | "bool" => DataType::Boolean,
        "date" | "timestamp" | "timestamptz" | "time" => DataType::Date,
        _ => DataType::Varchar,
    }
}

// ── Input-data CSV ────────────────────────────────────────────────────────────

/// Load a directory of named input CSVs into a `HashMap<name, TableData>`.
/// Each file `tablename.csv` becomes the entry keyed by `tablename`.
pub fn load_input_data(dir: &Path) -> Result<HashMap<String, TableData>> {
    let mut map = HashMap::new();
    if !dir.exists() {
        return Ok(map);
    }
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) == Some("csv") {
            let name = path
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("unknown")
                .to_string();
            map.insert(name, load_csv(&path)?);
        }
    }
    Ok(map)
}
