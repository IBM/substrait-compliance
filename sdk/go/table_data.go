package compliance

import (
	"fmt"
)

// CellValue represents a single value in a table cell
type CellValue interface{}

// Row represents a single row in a table
type Row []CellValue

// ColumnMetadata describes a table column
type ColumnMetadata struct {
	Name     string
	Type     string
	Nullable bool
}

// TableData represents tabular data with schema information
type TableData struct {
	Columns []ColumnMetadata
	Rows    []Row
}

// NewTableData creates a new TableData instance
func NewTableData(columns []ColumnMetadata) *TableData {
	return &TableData{
		Columns: columns,
		Rows:    make([]Row, 0),
	}
}

// AddRow adds a row to the table
func (t *TableData) AddRow(row Row) {
	t.Rows = append(t.Rows, row)
}

// AddRows adds multiple rows to the table
func (t *TableData) AddRows(rows []Row) {
	t.Rows = append(t.Rows, rows...)
}

// RowCount returns the number of rows
func (t *TableData) RowCount() int {
	return len(t.Rows)
}

// ColumnCount returns the number of columns
func (t *TableData) ColumnCount() int {
	return len(t.Columns)
}

// IsEmpty checks if the table has no rows
func (t *TableData) IsEmpty() bool {
	return len(t.Rows) == 0
}

// GetCell returns the value at the specified row and column
func (t *TableData) GetCell(rowIdx, colIdx int) (CellValue, error) {
	if rowIdx < 0 || rowIdx >= len(t.Rows) {
		return nil, fmt.Errorf("row index %d out of bounds", rowIdx)
	}
	if colIdx < 0 || colIdx >= len(t.Rows[rowIdx]) {
		return nil, fmt.Errorf("column index %d out of bounds", colIdx)
	}
	return t.Rows[rowIdx][colIdx], nil
}

// Clear removes all rows from the table
func (t *TableData) Clear() {
	t.Rows = make([]Row, 0)
}

// IsNull checks if a cell value is NULL
func IsNull(value CellValue) bool {
	return value == nil
}

// CellValueToString converts a cell value to string representation
func CellValueToString(value CellValue) string {
	if value == nil {
		return "NULL"
	}
	return fmt.Sprintf("%v", value)
}

// Made with Bob
