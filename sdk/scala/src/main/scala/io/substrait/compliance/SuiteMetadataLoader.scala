package io.substrait.compliance

import java.nio.file.{Files, Path, Paths}
import java.util.{List => JList, Map => JMap}

import org.yaml.snakeyaml.{LoaderOptions, Yaml}

/**
 * Loads a suite in this repository's metadata format (test-suites/tpch/metadata.yaml and the
 * like): `name`, `version`, `description` and `testCases`, each with `id`, `planBinary`,
 * optional `planJson`, `inputTables` (`name`, `file`) and `expectedOutput`.
 *
 * Inputs and expected outputs are read as scripts/run_plan_compliance.py reads them for the
 * leaderboard:
 *
 *  - Input tables are typed from the plan's read schemas (planJson) and keyed by the table
 *    name the plan reads. A first row that names the plan's columns is a header and skipped.
 *    Values are trimmed; an empty value or `null` is NULL. Without planJson, an input table is
 *    read like an expected output.
 *  - Expected outputs have a typed header (`name:type|...`); values are parsed by type
 *    (integers, floats, booleans), the rest kept as text, as the Java SDK's loader does.
 *  - The decimal scales the plan declares (planJson) are attached to each case.
 *
 * A data file read by several cases with the same schema is loaded once, and the cases
 * share that TableData.
 */
object SuiteMetadataLoader {

  /** Whether the YAML text is in this format (it has a top-level `testCases` key). */
  def isSuiteMetadata(yamlText: String): Boolean = "(?m)^testCases\\s*:".r.findFirstIn(yamlText).isDefined

  def load(path: String): TestSuite = {
    val file = Paths.get(path)
    val base = Option(file.getParent).getOrElse(Paths.get("."))
    val doc = new Yaml(new LoaderOptions()).load[Object](new String(Files.readAllBytes(file), "UTF-8"))
    // Cases of one suite read the same data files: each file is read once (per schema) and
    // the table shared by the cases that read it.
    val tables = scala.collection.mutable.HashMap.empty[(Path, String), TableData]
    val cases = list(get(doc, "testCases")).map(loadCase(_, base, tables))
    TestSuite(
      name = str(get(doc, "name")).getOrElse(file.getFileName.toString),
      description = str(get(doc, "description")),
      version = str(get(doc, "version")).getOrElse("1.0"),
      testCases = cases,
      metadata = Map.empty
    )
  }

  private def loadCase(tc: Any, base: Path, tables: scala.collection.mutable.Map[(Path, String), TableData]): TestCase = {
    val id = str(get(tc, "id")).getOrElse(throw new IllegalArgumentException("test case without id"))
    try {
      val planBinary = str(get(tc, "planBinary")).getOrElse(throw new IllegalArgumentException("planBinary is required"))
      val plan = str(get(tc, "planJson")).map(rel => PlanOutputTypes.parse(read(base.resolve(rel))))
      val schemas = plan.map(PlanOutputTypes.namedTableSchemas).getOrElse(Map.empty)
      val inputs = list(get(tc, "inputTables")).map { t =>
        val name = str(get(t, "name")).getOrElse(throw new IllegalArgumentException("input table without name"))
        val file = base.resolve(str(get(t, "file")).getOrElse(throw new IllegalArgumentException(s"no file for table $name")))
        schemas.find(_._1.equalsIgnoreCase(name)) match {
          case Some((planName, columns)) =>
            planName -> tables.getOrElseUpdate((file.normalize, columns.toString), inputTable(file, columns))
          case None => name -> tables.getOrElseUpdate((file.normalize, ""), typedCsv(file))
        }
      }.toMap
      val expected = str(get(tc, "expectedOutput")).map(base.resolve).filter(Files.isRegularFile(_)).map(typedCsv)
      TestCase(
        name = id,
        description = str(get(tc, "description")),
        planPath = base.resolve(planBinary).toString,
        inputTables = inputs,
        expectedOutput = expected,
        declaredScales = plan.flatMap(p =>
          try Some(PlanOutputTypes.outputTypes(p).map(_.flatMap(get(_, "decimal")).flatMap(get(_, "scale")).collect {
            case n: Number => n.intValue
          }))
          catch { case _: RuntimeException => None })
      )
    } catch {
      case e: RuntimeException => throw new IllegalArgumentException(s"test case $id: ${e.getMessage}", e)
    }
  }

  // ── input tables, typed by the plan ────────────────────────────────────────

  /** A CSV typed by the plan's read schema for the table. */
  def inputTable(file: Path, columns: Seq[(String, Any)]): TableData = {
    val lines = readLines(file)
    if (lines.isEmpty) return TableData(columns.map { case (n, t) => Column(n, columnType(t)) })
    val delimiter = detectDelimiter(lines.head)
    val names = columns.map(_._1.toLowerCase)
    val header = splitLine(lines.head, delimiter).map(_.trim.split(":")(0).toLowerCase)
    val data = if (header == names) lines.tail else lines
    val rows = data.zipWithIndex.collect { case (line, i) if line.trim.nonEmpty =>
      val values = splitLine(line, delimiter)
      if (values.length != columns.length)
        throw new IllegalArgumentException(s"$file row ${i + 1} has ${values.length} values, expected ${columns.length}")
      values.zip(columns).map { case (v, (_, t)) => planValue(v, t) }
    }
    TableData(columns.map { case (n, t) => Column(n, columnType(t)) }, rows)
  }

  private def kind(t: Any): String = t match {
    case m: JMap[_, _] if !m.isEmpty => String.valueOf(m.keySet().iterator().next())
    case _ => "string"
  }

  private def columnType(t: Any): ColumnType = kind(t) match {
    case "i8" | "i16" | "i32" | "i64" => ColumnType.Integer
    case "fp32" | "fp64" => ColumnType.Float
    case "decimal" => ColumnType.Decimal
    case "bool" => ColumnType.Boolean
    case "date" => ColumnType.Date
    case "timestamp" | "timestampTz" | "precisionTimestamp" | "precisionTimestampTz" => ColumnType.Timestamp
    case "time" | "precisionTime" => ColumnType.Time
    case "binary" | "fixedBinary" => ColumnType.Binary
    case _ => ColumnType.String
  }

  private def planValue(raw: String, t: Any): Any = {
    val v = raw.trim
    if (v.isEmpty || v.equalsIgnoreCase("null")) null
    else kind(t) match {
      case "i8" | "i16" | "i32" => java.lang.Integer.valueOf(v)
      case "i64" => java.lang.Long.valueOf(v)
      case "fp32" => java.lang.Float.valueOf(v)
      case "fp64" => java.lang.Double.valueOf(v)
      case "decimal" => new java.math.BigDecimal(v)
      case "bool" => java.lang.Boolean.valueOf(v.equalsIgnoreCase("true"))
      case _ => v
    }
  }

  // ── expected outputs (typed header, as the Java SDK's loader reads them) ─────

  /** A CSV with a typed header (`name:type|...`), or untyped columns column_1.. of text. */
  def typedCsv(file: Path): TableData = {
    val lines = readLines(file)
    if (lines.isEmpty) throw new IllegalArgumentException(s"CSV file is empty: $file")
    val delimiter = detectDelimiter(lines.head)
    val first = splitLine(lines.head, delimiter)
    val typed = first.forall { h => val i = h.trim.indexOf(':'); i > 0 && i < h.trim.length - 1 }
    val (names, types, data) =
      if (typed) {
        val parts = first.map { h => val i = h.trim.indexOf(':'); (h.trim.substring(0, i).trim, h.trim.substring(i + 1).trim.toLowerCase) }
        (parts.map(_._1), parts.map(_._2), lines.tail)
      } else ((1 to first.length).map(i => s"column_$i"), first.map(_ => "string"), lines)
    val rows = data.zipWithIndex.collect { case (line, i) if line.trim.nonEmpty =>
      val values = splitLine(line, delimiter)
      if (values.length != names.length)
        throw new IllegalArgumentException(s"CSV row ${i + 1} in $file has ${values.length} values but expected ${names.length}")
      values.zip(types).map { case (v, t) => headerValue(v, t) }
    }
    TableData(names.zip(types).map { case (n, t) => Column(n, ColumnType.fromName(t)) }, rows)
  }

  private def headerValue(raw: String, t: String): Any = {
    val v = raw.trim
    if (v.isEmpty || v.equalsIgnoreCase("null")) null
    else try {
      t match {
        case "int" | "integer" | "i8" | "i16" | "i32" => java.lang.Integer.valueOf(v)
        case "long" | "bigint" | "i64" => java.lang.Long.valueOf(v)
        case "float" | "fp32" => java.lang.Float.valueOf(v)
        case "double" | "fp64" | "decimal" => java.lang.Double.valueOf(v)
        case "bool" | "boolean" => java.lang.Boolean.valueOf(v.equalsIgnoreCase("true"))
        case _ => v
      }
    } catch { case _: NumberFormatException => v }
  }

  // ── CSV and YAML helpers ─────────────────────────────────────────────────────

  private def readLines(file: Path): Seq[String] = {
    val text = read(file)
    text.split("\\r?\\n", -1).toSeq.reverse.dropWhile(_.isEmpty).reverse
  }

  private def read(file: Path): String = new String(Files.readAllBytes(file), "UTF-8")

  private def detectDelimiter(line: String): Char = if (line.count(_ == '|') > line.count(_ == ',')) '|' else ','

  /** One line, split on the delimiter outside double quotes; "" inside quotes is a quote. */
  private def splitLine(line: String, delimiter: Char): Seq[String] = {
    val values = Seq.newBuilder[String]
    val current = new StringBuilder
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val ch = line.charAt(i)
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length && line.charAt(i + 1) == '"') { current.append('"'); i += 1 }
        else inQuotes = !inQuotes
      } else if (ch == delimiter && !inQuotes) {
        values += current.toString.trim; current.clear()
      } else current.append(ch)
      i += 1
    }
    values += current.toString.trim
    values.result().map(v => if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) v.substring(1, v.length - 1) else v)
  }

  private def get(v: Any, key: String): Option[Any] = v match {
    case m: JMap[_, _] => Option(m.asInstanceOf[JMap[String, Any]].get(key))
    case _ => None
  }

  private def str(v: Option[Any]): Option[String] = v.map(String.valueOf)

  private def list(v: Option[Any]): Seq[Any] = v match {
    case Some(l: JList[_]) =>
      val it = l.iterator()
      val out = Seq.newBuilder[Any]
      while (it.hasNext) out += it.next()
      out.result()
    case _ => Seq.empty
  }
}
