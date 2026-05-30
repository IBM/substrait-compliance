using System;
using System.Collections.Generic;
using System.Linq;

namespace Substrait.Compliance
{
    /// <summary>
    /// Column type enumeration
    /// </summary>
    public enum ColumnType
    {
        Integer,
        Float,
        Decimal,
        Boolean,
        String,
        Date,
        Timestamp,
        Time,
        Binary
    }

    /// <summary>
    /// Column definition
    /// </summary>
    public record Column(
        string Name,
        ColumnType Type,
        bool Nullable = true
    );

    /// <summary>
    /// Represents tabular data with schema and rows
    /// </summary>
    public class TableData
    {
        private readonly List<Column> _columns;
        private readonly List<object?[]> _rows;

        /// <summary>
        /// Create an empty table
        /// </summary>
        public TableData()
        {
            _columns = new List<Column>();
            _rows = new List<object?[]>();
        }

        /// <summary>
        /// Create a table with columns
        /// </summary>
        public TableData(IEnumerable<Column> columns)
        {
            _columns = new List<Column>(columns);
            _rows = new List<object?[]>();
        }

        /// <summary>
        /// Create a table with columns and rows
        /// </summary>
        public TableData(IEnumerable<Column> columns, IEnumerable<object?[]> rows)
        {
            _columns = new List<Column>(columns);
            _rows = new List<object?[]>(rows);
        }

        /// <summary>
        /// Get column definitions
        /// </summary>
        public IReadOnlyList<Column> Columns => _columns.AsReadOnly();

        /// <summary>
        /// Get all rows
        /// </summary>
        public IReadOnlyList<object?[]> Rows => _rows.AsReadOnly();

        /// <summary>
        /// Get number of rows
        /// </summary>
        public int RowCount => _rows.Count;

        /// <summary>
        /// Get number of columns
        /// </summary>
        public int ColumnCount => _columns.Count;

        /// <summary>
        /// Check if table is empty
        /// </summary>
        public bool IsEmpty => _rows.Count == 0;

        /// <summary>
        /// Add a row to the table
        /// </summary>
        public void AddRow(params object?[] row)
        {
            if (row.Length != _columns.Count)
            {
                throw new ArgumentException(
                    $"Row has {row.Length} values but table has {_columns.Count} columns");
            }
            _rows.Add(row);
        }

        /// <summary>
        /// Add multiple rows to the table
        /// </summary>
        public void AddRows(IEnumerable<object?[]> rows)
        {
            foreach (var row in rows)
            {
                AddRow(row);
            }
        }

        /// <summary>
        /// Get a specific row by index
        /// </summary>
        public object?[]? GetRow(int index)
        {
            return index >= 0 && index < _rows.Count ? _rows[index] : null;
        }

        /// <summary>
        /// Get a specific cell value
        /// </summary>
        public object? GetCell(int rowIndex, int columnIndex)
        {
            if (rowIndex < 0 || rowIndex >= _rows.Count)
                return null;
            if (columnIndex < 0 || columnIndex >= _columns.Count)
                return null;
            return _rows[rowIndex][columnIndex];
        }

        /// <summary>
        /// Set column definitions
        /// </summary>
        public void SetColumns(IEnumerable<Column> columns)
        {
            _columns.Clear();
            _columns.AddRange(columns);
        }

        /// <summary>
        /// Clear all rows
        /// </summary>
        public void Clear()
        {
            _rows.Clear();
        }

        /// <summary>
        /// Get column by name
        /// </summary>
        public Column? GetColumn(string name)
        {
            return _columns.FirstOrDefault(c => c.Name == name);
        }

        /// <summary>
        /// Get column index by name
        /// </summary>
        public int GetColumnIndex(string name)
        {
            return _columns.FindIndex(c => c.Name == name);
        }
    }
}

// Made with Bob
