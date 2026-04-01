//! Data structures for tabular data.

use serde::{Deserialize, Serialize};

/// Supported data types.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum DataType {
    Integer,
    Bigint,
    Double,
    Varchar,
    Date,
    Boolean,
    Decimal,
}

/// Represents a table column.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Column {
    pub name: String,
    pub data_type: DataType,
    pub nullable: bool,
}

impl Column {
    pub fn new(name: impl Into<String>, data_type: DataType) -> Self {
        Self {
            name: name.into(),
            data_type,
            nullable: true,
        }
    }
    
    pub fn not_null(mut self) -> Self {
        self.nullable = false;
        self
    }
}

/// Represents tabular data with schema and rows.
#[derive(Debug, Clone)]
pub struct TableData {
    pub columns: Vec<Column>,
    pub rows: Vec<Vec<String>>, // Simplified - would use proper types
}

impl TableData {
    pub fn new(columns: Vec<Column>, rows: Vec<Vec<String>>) -> Self {
        Self { columns, rows }
    }
    
    pub fn row_count(&self) -> usize {
        self.rows.len()
    }
    
    pub fn column_count(&self) -> usize {
        self.columns.len()
    }
    
    pub fn get_column(&self, name: &str) -> Option<&Column> {
        self.columns.iter().find(|c| c.name == name)
    }
}
