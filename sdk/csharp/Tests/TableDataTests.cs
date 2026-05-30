using System;
using System.Linq;
using FluentAssertions;
using Xunit;

namespace Substrait.Compliance.Tests
{
    public class TableDataTests
    {
        [Fact]
        public void Constructor_WithNoArguments_CreatesEmptyTable()
        {
            // Arrange & Act
            var table = new TableData();

            // Assert
            table.RowCount.Should().Be(0);
            table.ColumnCount.Should().Be(0);
            table.IsEmpty.Should().BeTrue();
        }

        [Fact]
        public void Constructor_WithColumnsAndRows_CreatesTable()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer, false),
                new Column("name", ColumnType.String, true)
            };
            var rows = new object?[][]
            {
                new object?[] { 1, "Alice" },
                new object?[] { 2, "Bob" }
            };

            // Act
            var table = new TableData(columns, rows);

            // Assert
            table.RowCount.Should().Be(2);
            table.ColumnCount.Should().Be(2);
            table.IsEmpty.Should().BeFalse();
        }

        [Fact]
        public void AddRow_WithValidRow_AddsRow()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            };
            var table = new TableData(columns);

            // Act
            table.AddRow(1, "test");

            // Assert
            table.RowCount.Should().Be(1);
            table.GetRow(0).Should().BeEquivalentTo(new object[] { 1, "test" });
        }

        [Fact]
        public void AddRow_WithWrongColumnCount_ThrowsException()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            };
            var table = new TableData(columns);

            // Act & Assert
            Action act = () => table.AddRow(1);
            act.Should().Throw<ArgumentException>()
                .WithMessage("*2 columns*");
        }

        [Fact]
        public void AddRows_WithMultipleRows_AddsAllRows()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            };
            var table = new TableData(columns);
            var rows = new object?[][]
            {
                new object?[] { 1, "first" },
                new object?[] { 2, "second" },
                new object?[] { 3, "third" }
            };

            // Act
            table.AddRows(rows);

            // Assert
            table.RowCount.Should().Be(3);
        }

        [Fact]
        public void GetRow_WithValidIndex_ReturnsRow()
        {
            // Arrange
            var table = new TableData();
            table.SetColumns(new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            });
            table.AddRow(1, "test");

            // Act
            var row = table.GetRow(0);

            // Assert
            row.Should().NotBeNull();
            row.Should().BeEquivalentTo(new object[] { 1, "test" });
        }

        [Fact]
        public void GetRow_WithInvalidIndex_ReturnsNull()
        {
            // Arrange
            var table = new TableData();

            // Act
            var row = table.GetRow(0);

            // Assert
            row.Should().BeNull();
        }

        [Fact]
        public void GetCell_WithValidIndices_ReturnsValue()
        {
            // Arrange
            var table = new TableData();
            table.SetColumns(new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            });
            table.AddRow(1, "test");

            // Act
            var value1 = table.GetCell(0, 0);
            var value2 = table.GetCell(0, 1);

            // Assert
            value1.Should().Be(1);
            value2.Should().Be("test");
        }

        [Fact]
        public void GetCell_WithInvalidIndices_ReturnsNull()
        {
            // Arrange
            var table = new TableData();
            table.SetColumns(new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            });
            table.AddRow(1, "test");

            // Act & Assert
            table.GetCell(1, 0).Should().BeNull(); // Invalid row
            table.GetCell(0, 2).Should().BeNull(); // Invalid column
        }

        [Fact]
        public void Clear_RemovesAllRows()
        {
            // Arrange
            var table = new TableData();
            table.SetColumns(new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("value", ColumnType.String)
            });
            table.AddRow(1, "first");
            table.AddRow(2, "second");

            // Act
            table.Clear();

            // Assert
            table.RowCount.Should().Be(0);
            table.IsEmpty.Should().BeTrue();
        }

        [Fact]
        public void GetColumn_WithValidName_ReturnsColumn()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("name", ColumnType.String)
            };
            var table = new TableData(columns);

            // Act
            var column = table.GetColumn("name");

            // Assert
            column.Should().NotBeNull();
            column!.Name.Should().Be("name");
            column.Type.Should().Be(ColumnType.String);
        }

        [Fact]
        public void GetColumn_WithInvalidName_ReturnsNull()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer)
            };
            var table = new TableData(columns);

            // Act
            var column = table.GetColumn("nonexistent");

            // Assert
            column.Should().BeNull();
        }

        [Fact]
        public void GetColumnIndex_WithValidName_ReturnsIndex()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer),
                new Column("name", ColumnType.String),
                new Column("value", ColumnType.Float)
            };
            var table = new TableData(columns);

            // Act
            var index = table.GetColumnIndex("name");

            // Assert
            index.Should().Be(1);
        }

        [Fact]
        public void GetColumnIndex_WithInvalidName_ReturnsNegativeOne()
        {
            // Arrange
            var columns = new[]
            {
                new Column("id", ColumnType.Integer)
            };
            var table = new TableData(columns);

            // Act
            var index = table.GetColumnIndex("nonexistent");

            // Assert
            index.Should().Be(-1);
        }
    }
}

// Made with Bob
