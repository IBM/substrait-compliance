package io.substrait.compliance

import scala.collection.mutable

/**
 * Column type enumeration
 */
sealed trait ColumnType

object ColumnType {
  case object Integer extends ColumnType
  case object Float extends ColumnType
  case object Decimal extends ColumnType
  case object Boolean extends ColumnType
  case object String extends ColumnType
  case object Date extends ColumnType
  case object Timestamp extends ColumnType
  case object Time extends ColumnType
  case object Binary extends ColumnType
}

/**
 * Column definition
 */
case class Column(
  name: String,
  columnType: ColumnType,
  nullable: Boolean = true
)

/**
 * Represents tabular data with schema and rows
 */
class TableData(
  private var _columns: Seq[Column] = Seq.empty,
  private val _rows: mutable.Buffer[Seq[Any]] = mutable.Buffer.empty
) {
  
  /**
   * Get column definitions
   */
  def columns: Seq[Column] = _columns

  /**
   * Get all rows
   */
  def rows: Seq[Seq[Any]] = _rows.toSeq

  /**
   * Get number of rows
   */
  def rowCount: Int = _rows.length

  /**
   * Get number of columns
   */
  def columnCount: Int = _columns.length

  /**
   * Check if table is empty
   */
  def isEmpty: Boolean = _rows.isEmpty

  /**
   * Add a row to the table
   */
  def addRow(row: Seq[Any]): Unit = {
    require(row.length == _columns.length,
      s"Row has ${row.length} values but table has ${_columns.length} columns")
    _rows += row
  }

  /**
   * Add multiple rows to the table
   */
  def addRows(rows: Seq[Seq[Any]]): Unit = {
    rows.foreach(addRow)
  }

  /**
   * Get a specific row by index
   */
  def getRow(index: Int): Option[Seq[Any]] = {
    if (index >= 0 && index < _rows.length) Some(_rows(index))
    else None
  }

  /**
   * Get a specific cell value
   */
  def getCell(rowIndex: Int, columnIndex: Int): Option[Any] = {
    for {
      row <- getRow(rowIndex)
      if columnIndex >= 0 && columnIndex < row.length
    } yield row(columnIndex)
  }

  /**
   * Set column definitions
   */
  def setColumns(columns: Seq[Column]): Unit = {
    _columns = columns
  }

  /**
   * Clear all rows
   */
  def clear(): Unit = {
    _rows.clear()
  }

  /**
   * Get column by name
   */
  def getColumn(name: String): Option[Column] = {
    _columns.find(_.name == name)
  }

  /**
   * Get column index by name
   */
  def getColumnIndex(name: String): Int = {
    _columns.indexWhere(_.name == name)
  }

  /**
   * Map over rows
   */
  def mapRows[B](f: Seq[Any] => B): Seq[B] = {
    _rows.map(f).toSeq
  }

  /**
   * Filter rows
   */
  def filterRows(f: Seq[Any] => Boolean): Seq[Seq[Any]] = {
    _rows.filter(f).toSeq
  }

  /**
   * Fold over rows
   */
  def foldRows[B](z: B)(f: (B, Seq[Any]) => B): B = {
    _rows.foldLeft(z)(f)
  }
}

object TableData {
  /**
   * Create a table with columns
   */
  def apply(columns: Seq[Column]): TableData = {
    new TableData(columns)
  }

  /**
   * Create a table with columns and rows
   */
  def apply(columns: Seq[Column], rows: Seq[Seq[Any]]): TableData = {
    val table = new TableData(columns)
    table.addRows(rows)
    table
  }

  /**
   * Create an empty table
   */
  def empty: TableData = new TableData()
}

// Made with Bob
