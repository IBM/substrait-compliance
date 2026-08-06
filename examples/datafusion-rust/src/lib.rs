//! DataFusion Substrait compliance engine library.
//!
//! Exports [`DataFusionComplianceEngine`] plus the data types used by `main.rs`.

use datafusion::arrow;
use datafusion::prelude::*;
use datafusion_substrait::logical_plan::consumer::from_substrait_plan;
use datafusion_substrait::serializer;
use std::collections::HashMap;
use substrait::proto::Plan;

// ── Value type ────────────────────────────────────────────────────────────────

/// A single cell value in a [`TableData`] row.
#[derive(Debug, Clone)]
pub enum Value {
    Null,
    Boolean(bool),
    Int8(i8),
    Int16(i16),
    Int32(i32),
    Int64(i64),
    UInt8(u8),
    UInt16(u16),
    UInt32(u32),
    UInt64(u64),
    Float32(f32),
    Float64(f64),
    String(String),
    Binary(Vec<u8>),
}

// ── TableData ─────────────────────────────────────────────────────────────────

/// An in-memory result table.
#[derive(Debug, Clone)]
pub struct TableData {
    pub column_names: Vec<String>,
    pub column_types: Vec<String>,
    pub rows: Vec<Vec<Value>>,
}

// ── ComplianceResult ──────────────────────────────────────────────────────────

/// The outcome of one `execute_plan` call.
#[derive(Debug)]
pub struct ComplianceResult {
    pub success: bool,
    pub output: Option<TableData>,
    pub error: Option<String>,
    pub duration_ms: u64,
}

// ── PlanValidationResult ──────────────────────────────────────────────────────

/// Whether a plan is supported.
#[derive(Debug)]
pub struct PlanValidationResult {
    pub is_supported: bool,
    pub reasons: Vec<String>,
}

// ── DataFusionComplianceEngine ────────────────────────────────────────────────

/// DataFusion engine that executes Substrait plans via
/// `datafusion-substrait`'s `from_substrait_plan` consumer.
pub struct DataFusionComplianceEngine {
    ctx: SessionContext,
}

impl DataFusionComplianceEngine {
    pub fn new() -> Self {
        Self {
            ctx: SessionContext::new(),
        }
    }

    pub fn get_engine_info(&self) -> EngineInfo {
        EngineInfo {
            name: "DataFusion".to_string(),
            version: "54.1".to_string(),
            vendor: "Apache Software Foundation".to_string(),
            substrait_version: "0.64".to_string(),
            description: "Fast, extensible query engine with native Substrait support"
                .to_string(),
        }
    }

    pub fn get_capabilities(&self) -> EngineCapabilities {
        EngineCapabilities {
            supported_relations: ["read", "filter", "project", "aggregate",
                "join", "sort", "limit", "union"]
                .iter().map(|s| s.to_string()).collect(),
            supported_functions: ["add", "subtract", "multiply", "divide",
                "sum", "count", "avg", "min", "max",
                "concat", "substring", "upper", "lower"]
                .iter().map(|s| s.to_string()).collect(),
            supported_types: ["integer", "bigint", "double", "varchar",
                "date", "timestamp", "boolean"]
                .iter().map(|s| s.to_string()).collect(),
            max_plan_depth: 100,
            supports_extensions: true,
        }
    }

    pub fn validate_plan(&self, plan: &Plan) -> PlanValidationResult {
        if plan.relations.is_empty() {
            return PlanValidationResult {
                is_supported: false,
                reasons: vec!["Plan has no relations".to_string()],
            };
        }
        PlanValidationResult {
            is_supported: true,
            reasons: vec![],
        }
    }

    /// Execute a Substrait plan using DataFusion's native consumer.
    ///
    /// Input tables are registered as in-memory Arrow MemTables before the
    /// plan runs.  Results are collected into a [`TableData`].
    pub async fn execute_plan(
        &mut self,
        plan: &Plan,
        input_data: HashMap<String, TableData>,
    ) -> ComplianceResult {
        let start = std::time::Instant::now();
        match self.execute_internal(plan, input_data).await {
            Ok(output) => ComplianceResult {
                success: true,
                output: Some(output),
                error: None,
                duration_ms: start.elapsed().as_millis() as u64,
            },
            Err(e) => ComplianceResult {
                success: false,
                output: None,
                error: Some(e.to_string()),
                duration_ms: start.elapsed().as_millis() as u64,
            },
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    async fn execute_internal(
        &mut self,
        plan: &Plan,
        input_data: HashMap<String, TableData>,
    ) -> Result<TableData, Box<dyn std::error::Error>> {
        use arrow::array::*;
        use arrow::datatypes::{Field, Schema};
        use datafusion::datasource::MemTable;
        use std::sync::Arc;

        // Register each input table as an in-memory Arrow MemTable.
        for (table_name, data) in &input_data {
            if data.rows.is_empty() {
                continue;
            }
            let fields: Vec<Field> = data.column_names.iter()
                .zip(data.column_types.iter())
                .map(|(name, ty)| Field::new(name, substrait_type_to_arrow(ty), true))
                .collect();
            let schema = Arc::new(Schema::new(fields));
            let num_cols = data.column_names.len();
            let num_rows = data.rows.len();

            let arrays: Vec<Arc<dyn Array>> = (0..num_cols)
                .map(|col_idx| {
                    build_arrow_column(schema.field(col_idx).data_type(), &data.rows, col_idx, num_rows)
                })
                .collect();

            let batch = arrow::record_batch::RecordBatch::try_new(schema.clone(), arrays)?;
            let mem_table = MemTable::try_new(schema, vec![vec![batch]])?;
            self.ctx.register_table(table_name.as_str(), Arc::new(mem_table))?;
        }

        // Serialize Plan to bytes; prost::Message must be in scope for encode_to_vec.
        let plan_bytes: Vec<u8> = {
            use prost::Message;
            plan.encode_to_vec()
        };

        // datafusion-substrait serializer: deserialize_bytes → SubstraitPlan
        let substrait_plan = serializer::deserialize_bytes(plan_bytes).await?;

        // from_substrait_plan needs a &SessionState, not &SessionContext
        let state = self.ctx.state();
        let logical_plan = from_substrait_plan(&state, &substrait_plan).await?;

        let df = self.ctx.execute_logical_plan(logical_plan).await?;
        let batches = df.collect().await?;

        if batches.is_empty() {
            return Ok(TableData { column_names: vec![], column_types: vec![], rows: vec![] });
        }

        let schema = batches[0].schema();
        let column_names: Vec<String> = schema.fields().iter().map(|f| f.name().clone()).collect();
        let column_types: Vec<String> = schema.fields().iter()
            .map(|f| arrow_type_to_substrait(f.data_type()))
            .collect();

        let mut rows: Vec<Vec<Value>> = Vec::new();
        for batch in &batches {
            for row_idx in 0..batch.num_rows() {
                let mut row = Vec::with_capacity(batch.num_columns());
                for col_idx in 0..batch.num_columns() {
                    row.push(extract_value(batch.column(col_idx).as_ref(), row_idx));
                }
                rows.push(row);
            }
        }

        Ok(TableData { column_names, column_types, rows })
    }
}

impl Default for DataFusionComplianceEngine {
    fn default() -> Self { Self::new() }
}

// ── Metadata types ────────────────────────────────────────────────────────────

pub struct EngineInfo {
    pub name: String,
    pub version: String,
    pub vendor: String,
    pub substrait_version: String,
    pub description: String,
}

pub struct EngineCapabilities {
    pub supported_relations: Vec<String>,
    pub supported_functions: Vec<String>,
    pub supported_types: Vec<String>,
    pub max_plan_depth: usize,
    pub supports_extensions: bool,
}

// ── Arrow / Substrait type helpers ────────────────────────────────────────────

fn substrait_type_to_arrow(ty: &str) -> arrow::datatypes::DataType {
    use arrow::datatypes::DataType;
    match ty.to_lowercase().as_str() {
        "i8"  | "tinyint"   => DataType::Int8,
        "i16" | "smallint"  => DataType::Int16,
        "i32" | "integer" | "int" => DataType::Int32,
        "i64" | "bigint"    => DataType::Int64,
        "fp32"| "float" | "real"  => DataType::Float32,
        "fp64"| "double"    => DataType::Float64,
        "bool"| "boolean"   => DataType::Boolean,
        "date"              => DataType::Date32,
        _                   => DataType::Utf8,
    }
}

fn arrow_type_to_substrait(dt: &arrow::datatypes::DataType) -> String {
    use arrow::datatypes::DataType;
    match dt {
        DataType::Int8    => "i8",
        DataType::Int16   => "i16",
        DataType::Int32   => "integer",
        DataType::Int64   => "bigint",
        DataType::Float32 => "fp32",
        DataType::Float64 => "double",
        DataType::Boolean => "boolean",
        DataType::Date32 | DataType::Date64 => "date",
        _                 => "string",
    }.to_string()
}

fn build_arrow_column(
    dt: &arrow::datatypes::DataType,
    rows: &[Vec<Value>],
    col_idx: usize,
    num_rows: usize,
) -> std::sync::Arc<dyn arrow::array::Array> {
    use arrow::array::*;
    use arrow::datatypes::DataType;
    use std::sync::Arc;

    macro_rules! build_primitive {
        ($builder:ident, $variant:ident, $cast:ty) => {{
            let mut b = $builder::with_capacity(num_rows);
            for row in rows {
                match &row[col_idx] {
                    Value::$variant(v) => b.append_value(*v as $cast),
                    _ => b.append_null(),
                }
            }
            Arc::new(b.finish()) as Arc<dyn Array>
        }};
    }

    match dt {
        DataType::Int8    => build_primitive!(Int8Builder,    Int8,    i8),
        DataType::Int16   => build_primitive!(Int16Builder,   Int16,   i16),
        DataType::Int32   => build_primitive!(Int32Builder,   Int32,   i32),
        DataType::Int64   => build_primitive!(Int64Builder,   Int64,   i64),
        DataType::Float32 => build_primitive!(Float32Builder, Float32, f32),
        DataType::Float64 => build_primitive!(Float64Builder, Float64, f64),
        DataType::Boolean => {
            let mut b = BooleanBuilder::with_capacity(num_rows);
            for row in rows {
                match &row[col_idx] {
                    Value::Boolean(v) => b.append_value(*v),
                    _ => b.append_null(),
                }
            }
            Arc::new(b.finish())
        }
        _ => {
            let mut b = StringBuilder::with_capacity(num_rows, num_rows * 8);
            for row in rows {
                match &row[col_idx] {
                    Value::Null => b.append_null(),
                    Value::String(s) => b.append_value(s),
                    other => b.append_value(format!("{:?}", other)),
                }
            }
            Arc::new(b.finish())
        }
    }
}

fn extract_value(array: &dyn arrow::array::Array, idx: usize) -> Value {
    use arrow::array::*;
    use arrow::datatypes::DataType;

    if array.is_null(idx) { return Value::Null; }

    match array.data_type() {
        DataType::Boolean => Value::Boolean(
            array.as_any().downcast_ref::<BooleanArray>().unwrap().value(idx)),
        DataType::Int8  => Value::Int8(
            array.as_any().downcast_ref::<Int8Array>().unwrap().value(idx)),
        DataType::Int16 => Value::Int16(
            array.as_any().downcast_ref::<Int16Array>().unwrap().value(idx)),
        DataType::Int32 => Value::Int32(
            array.as_any().downcast_ref::<Int32Array>().unwrap().value(idx)),
        DataType::Int64 => Value::Int64(
            array.as_any().downcast_ref::<Int64Array>().unwrap().value(idx)),
        DataType::Float32 => Value::Float32(
            array.as_any().downcast_ref::<Float32Array>().unwrap().value(idx)),
        DataType::Float64 => Value::Float64(
            array.as_any().downcast_ref::<Float64Array>().unwrap().value(idx)),
        DataType::Utf8 => Value::String(
            array.as_any().downcast_ref::<StringArray>().unwrap().value(idx).to_string()),
        DataType::LargeUtf8 => Value::String(
            array.as_any().downcast_ref::<LargeStringArray>().unwrap().value(idx).to_string()),
        _ => Value::String(format!("<{:?}>", array.data_type())),
    }
}
