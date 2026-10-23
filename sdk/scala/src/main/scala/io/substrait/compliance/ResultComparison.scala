package io.substrait.compliance

/**
 * Compares an engine's output with the expected output, as the leaderboard's comparators do
 * (sdk/conformance/comparison-vectors.json): same row and column counts, column names
 * case-insensitively, column types by family (all numerics are one family; dates and times
 * compare as text), rows in order, numbers within max(1e-9, 1e-9 * magnitude), everything
 * else as text. NULL matches only NULL; a numeric string matches the number.
 */
object ResultComparison {

  val NumericAbsTolerance: Double = 1e-9
  val NumericRelTolerance: Double = 1e-9

  /**
   * None when the outputs match, otherwise a description of the first difference.
   *
   * @param declaredScales per column, the scale of the decimal type the plan declares
   *                       (None for other columns); such a column also matches within
   *                       10^-scale. Empty when unknown.
   */
  def describeMismatch(expected: TableData, actual: TableData,
                       declaredScales: Seq[Option[Int]] = Seq.empty): Option[String] = {
    val eNames = expected.columns.map(_.name)
    val aNames = actual.columns.map(_.name)
    if (expected.rowCount != actual.rowCount) {
      Some(s"expected ${expected.rowCount} rows, got ${actual.rowCount}")
    } else if (expected.columnCount != actual.columnCount) {
      Some(s"expected ${expected.columnCount} columns ${eNames.mkString("[", ", ", "]")}, " +
        s"got ${actual.columnCount} ${aNames.mkString("[", ", ", "]")}")
    } else if (eNames.zip(aNames).exists { case (e, a) => !e.trim.equalsIgnoreCase(a.trim) }) {
      Some(s"expected column names ${eNames.mkString("[", ", ", "]")}, got ${aNames.mkString("[", ", ", "]")}")
    } else if (expected.columns.zip(actual.columns).exists { case (e, a) => family(e.columnType) != family(a.columnType) }) {
      Some(s"expected column types ${expected.columns.map(_.columnType).mkString("[", ", ", "]")}, " +
        s"got ${actual.columns.map(_.columnType).mkString("[", ", ", "]")}")
    } else {
      val mismatches = for {
        ((eRow, aRow), r) <- expected.rows.iterator.zip(actual.rows.iterator).zipWithIndex
        (e, a, c) <- eRow.iterator.zip(aRow.iterator).zipWithIndex.map { case ((x, y), i) => (x, y, i) }
        if !valuesMatch(e, a, if (c < declaredScales.length) declaredScales(c) else None)
      } yield s"row $r, column ${eNames(c)}: expected ${show(e)}, got ${show(a)}"
      if (mismatches.hasNext) Some(mismatches.next()) else None
    }
  }

  /** The type family a column is compared by. */
  def family(t: ColumnType): String = t match {
    case ColumnType.Integer | ColumnType.Float | ColumnType.Decimal => "numeric"
    case ColumnType.String | ColumnType.Date | ColumnType.Timestamp | ColumnType.Time => "string"
    case ColumnType.Boolean => "boolean"
    case ColumnType.Binary => "binary"
  }

  /**
   * Numbers within the tolerance (and, for a declared decimal, within 10^-scale);
   * NaN matches NaN and infinity only the same infinity.
   */
  def numbersClose(expected: Double, actual: Double, declaredScale: Option[Int] = None): Boolean = {
    if (expected.isNaN || actual.isNaN) expected.isNaN && actual.isNaN
    else if (expected.isInfinite || actual.isInfinite) expected == actual
    else {
      val magnitude = math.max(math.abs(expected), math.abs(actual))
      val base = math.max(NumericAbsTolerance, NumericRelTolerance * magnitude)
      val tolerance = declaredScale match {
        case Some(s) => math.max(base, math.pow(10, -s.toDouble) * (1 + NumericRelTolerance))
        case None => base
      }
      math.abs(expected - actual) <= tolerance
    }
  }

  private def parseNumber(s: String): Option[Double] =
    try Some(s.trim.toDouble) catch { case _: NumberFormatException => None }

  def valuesMatch(expected: Any, actual: Any, declaredScale: Option[Int] = None): Boolean =
    (expected, actual) match {
      case (null, null) => true
      case (null, _) | (_, null) => false
      case (e: java.lang.Boolean, _) => booleanMatch(e, actual)
      case (_, a: java.lang.Boolean) => booleanMatch(expected, a)
      case (e: Number, a: Number) => numbersClose(e.doubleValue, a.doubleValue, declaredScale)
      case (e: Number, a: String) =>
        parseNumber(a).map(numbersClose(e.doubleValue, _, declaredScale)).getOrElse(String.valueOf(e) == a)
      case (e: String, a: Number) =>
        parseNumber(e).map(numbersClose(_, a.doubleValue, declaredScale)).getOrElse(e == String.valueOf(a))
      case _ => String.valueOf(expected) == String.valueOf(actual)
    }

  private def booleanMatch(expected: Any, actual: Any): Boolean =
    java.lang.Boolean.parseBoolean(String.valueOf(expected)) == java.lang.Boolean.parseBoolean(String.valueOf(actual))

  private def show(v: Any): String = v match {
    case null => "NULL"
    case s: String => s"'$s'"
    case b: java.math.BigDecimal => b.toPlainString
    case other => String.valueOf(other)
  }
}
