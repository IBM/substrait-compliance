package io.substrait.compliance

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TableDataSpec extends AnyFlatSpec with Matchers {

  "TableData" should "create an empty table" in {
    val table = TableData.empty
    
    table.isEmpty shouldBe true
    table.rowCount shouldBe 0
    table.columnCount shouldBe 0
  }

  it should "create a table with columns" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns)
    
    table.columnCount shouldBe 2
    table.rowCount shouldBe 0
    table.columns shouldBe columns
  }

  it should "add rows to a table" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns)
    
    table.addRow(Seq(1, "Alice"))
    table.addRow(Seq(2, "Bob"))
    
    table.rowCount shouldBe 2
    table.getRow(0) shouldBe Some(Seq(1, "Alice"))
    table.getRow(1) shouldBe Some(Seq(2, "Bob"))
  }

  it should "reject rows with incorrect column count" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns)
    
    an[IllegalArgumentException] should be thrownBy {
      table.addRow(Seq(1)) // Only 1 value, but 2 columns
    }
  }

  it should "get cell values" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns, Seq(
      Seq(1, "Alice"),
      Seq(2, "Bob")
    ))
    
    table.getCell(0, 0) shouldBe Some(1)
    table.getCell(0, 1) shouldBe Some("Alice")
    table.getCell(1, 0) shouldBe Some(2)
    table.getCell(1, 1) shouldBe Some("Bob")
    table.getCell(2, 0) shouldBe None // Out of bounds
  }

  it should "find columns by name" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns)
    
    table.getColumn("id") shouldBe Some(Column("id", ColumnType.Integer))
    table.getColumn("name") shouldBe Some(Column("name", ColumnType.String))
    table.getColumn("age") shouldBe None
  }

  it should "get column index by name" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns)
    
    table.getColumnIndex("id") shouldBe 0
    table.getColumnIndex("name") shouldBe 1
    table.getColumnIndex("age") shouldBe -1
  }

  it should "map over rows" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("value", ColumnType.Integer)
    )
    val table = TableData(columns, Seq(
      Seq(1, 10),
      Seq(2, 20),
      Seq(3, 30)
    ))
    
    val sums = table.mapRows(row => row(0).asInstanceOf[Int] + row(1).asInstanceOf[Int])
    sums shouldBe Seq(11, 22, 33)
  }

  it should "filter rows" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("value", ColumnType.Integer)
    )
    val table = TableData(columns, Seq(
      Seq(1, 10),
      Seq(2, 20),
      Seq(3, 30)
    ))
    
    val filtered = table.filterRows(row => row(1).asInstanceOf[Int] > 15)
    filtered should have length 2
    filtered shouldBe Seq(Seq(2, 20), Seq(3, 30))
  }

  it should "clear all rows" in {
    val columns = Seq(
      Column("id", ColumnType.Integer),
      Column("name", ColumnType.String)
    )
    val table = TableData(columns, Seq(
      Seq(1, "Alice"),
      Seq(2, "Bob")
    ))
    
    table.rowCount shouldBe 2
    table.clear()
    table.rowCount shouldBe 0
    table.isEmpty shouldBe true
  }
}

