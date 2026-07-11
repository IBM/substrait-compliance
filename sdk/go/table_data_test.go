package compliance

import (
	"testing"
)

func TestTableDataCreation(t *testing.T) {
	columns := []ColumnMetadata{
		{Name: "id", Type: "INTEGER", Nullable: false},
		{Name: "name", Type: "STRING", Nullable: true},
	}
	table := NewTableData(columns)

	if table.ColumnCount() != 2 {
		t.Errorf("Expected 2 columns, got %d", table.ColumnCount())
	}
	if !table.IsEmpty() {
		t.Error("Expected table to be empty")
	}
}

func TestTableDataAddRow(t *testing.T) {
	table := NewTableData([]ColumnMetadata{
		{Name: "id", Type: "INTEGER"},
	})

	table.AddRow(Row{1})
	table.AddRow(Row{2})

	if table.RowCount() != 2 {
		t.Errorf("Expected 2 rows, got %d", table.RowCount())
	}
	if table.IsEmpty() {
		t.Error("Expected table to not be empty")
	}
}

func TestTableDataGetCell(t *testing.T) {
	table := NewTableData([]ColumnMetadata{
		{Name: "value", Type: "INTEGER"},
	})
	table.AddRow(Row{42})

	cell, err := table.GetCell(0, 0)
	if err != nil {
		t.Fatalf("GetCell failed: %v", err)
	}

	if cell != 42 {
		t.Errorf("Expected cell value 42, got %v", cell)
	}
}

func TestTableDataGetCellOutOfBounds(t *testing.T) {
	table := NewTableData([]ColumnMetadata{
		{Name: "value", Type: "INTEGER"},
	})
	table.AddRow(Row{42})

	_, err := table.GetCell(1, 0)
	if err == nil {
		t.Error("Expected error for out of bounds row index")
	}

	_, err = table.GetCell(0, 1)
	if err == nil {
		t.Error("Expected error for out of bounds column index")
	}
}

func TestTableDataClear(t *testing.T) {
	table := NewTableData([]ColumnMetadata{
		{Name: "id", Type: "INTEGER"},
	})
	table.AddRow(Row{1})
	table.AddRow(Row{2})

	table.Clear()

	if !table.IsEmpty() {
		t.Error("Expected table to be empty after clear")
	}
	if table.RowCount() != 0 {
		t.Errorf("Expected 0 rows after clear, got %d", table.RowCount())
	}
}

func TestIsNull(t *testing.T) {
	if !IsNull(nil) {
		t.Error("Expected nil to be null")
	}

	if IsNull(42) {
		t.Error("Expected 42 to not be null")
	}

	if IsNull("test") {
		t.Error("Expected 'test' to not be null")
	}
}

func TestCellValueToString(t *testing.T) {
	tests := []struct {
		value    CellValue
		expected string
	}{
		{nil, "NULL"},
		{42, "42"},
		{"hello", "hello"},
		{3.14, "3.14"},
		{true, "true"},
	}

	for _, tt := range tests {
		result := CellValueToString(tt.value)
		if result != tt.expected {
			t.Errorf("Expected '%s', got '%s'", tt.expected, result)
		}
	}
}

