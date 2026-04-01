"""Data structures for tabular data."""

from dataclasses import dataclass
from typing import List, Any, Optional
from enum import Enum


class DataType(Enum):
    """Supported data types."""
    INTEGER = "integer"
    BIGINT = "bigint"
    DOUBLE = "double"
    VARCHAR = "varchar"
    DATE = "date"
    BOOLEAN = "boolean"
    DECIMAL = "decimal"


@dataclass
class Column:
    """Represents a table column."""
    
    name: str
    data_type: DataType
    nullable: bool = True
    
    def __str__(self) -> str:
        null_str = "NULL" if self.nullable else "NOT NULL"
        return f"{self.name} {self.data_type.value} {null_str}"


@dataclass
class TableData:
    """Represents tabular data with schema and rows."""
    
    columns: List[Column]
    rows: List[List[Any]]
    
    def row_count(self) -> int:
        """Return number of rows."""
        return len(self.rows)
    
    def column_count(self) -> int:
        """Return number of columns."""
        return len(self.columns)
    
    def get_column(self, name: str) -> Optional[Column]:
        """Get column by name."""
        for col in self.columns:
            if col.name == name:
                return col
        return None
    
    def __str__(self) -> str:
        return f"TableData({self.column_count()} cols, {self.row_count()} rows)"
