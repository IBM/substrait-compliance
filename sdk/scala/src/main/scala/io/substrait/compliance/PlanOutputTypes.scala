package io.substrait.compliance

import java.nio.file.{Files, Paths}
import java.util.{List => JList, Map => JMap}

import org.yaml.snakeyaml.{LoaderOptions, Yaml}

/**
 * The output column types a Substrait plan declares, from its JSON form, so decimal columns
 * can be compared at the scale the plan declares (ResultComparison). Mirrors output_types /
 * declared_scales in scripts/run_plan_compliance.py and PlanOutputTypes in the Java SDK.
 *
 * Types are propagated relation by relation: a read's base schema; filter, sort and fetch
 * keep their input's columns, project adds its expressions; an aggregate yields its grouping
 * expressions, then its measures; joins concatenate both sides; emit mappings select and
 * reorder. An expression's type is its declared outputType (functions, aggregates), the
 * referenced column's type, a cast's target or a literal's type.
 *
 * The JSON is read with SnakeYAML (JSON is valid YAML), already a dependency of this SDK.
 */
object PlanOutputTypes {

  private final class Unknown extends RuntimeException

  /**
   * The scale of each output column the plan declares as decimal (None for other columns),
   * or None when the plan's output types cannot be worked out. The JSON may start with
   * // comment lines, as the suites' plan files do.
   */
  def declaredScales(planJson: String): Option[Seq[Option[Int]]] =
    try {
      Some(outputTypes(parse(planJson)).map { t =>
        t.flatMap(field(_, "decimal")).flatMap(field(_, "scale")).collect { case n: Number => n.intValue }
      })
    } catch {
      case _: RuntimeException => None // Unknown, or a plan SnakeYAML cannot read
    }

  /**
   * A plan JSON as nested java.util maps and lists. The JSON may start with // comment lines,
   * as the suites' plan files do.
   */
  def parse(planJson: String): Any = {
    val text = planJson.split("\n").filterNot(_.trim.startsWith("//")).mkString("\n")
    val options = new LoaderOptions()
    options.setCodePointLimit(64 * 1024 * 1024)
    options.setNestingDepthLimit(1000) // plans nest deeply (TPC-DS q95: 63 levels; the default is 50)
    new Yaml(options).load[Object](text)
  }

  /**
   * Every table the plan reads (a ReadRel with a named table and a base schema), by its
   * dotted name: the columns' names and declared types, in order.
   */
  def namedTableSchemas(plan: Any): Map[String, Seq[(String, Any)]] = {
    val found = scala.collection.mutable.LinkedHashMap.empty[String, Seq[(String, Any)]]
    def walk(node: Any): Unit = node match {
      case m: JMap[_, _] =>
        field(m, "read").foreach { read =>
          for {
            named <- field(read, "namedTable")
            schema <- field(read, "baseSchema")
          } {
            val name = list(field(named, "names")).map(String.valueOf).mkString(".")
            val columns = list(field(schema, "names")).map(String.valueOf)
              .zip(list(field(schema, "struct").flatMap(field(_, "types"))))
            if (found.get(name).exists(_.map(_._1) != columns.map(_._1)))
              throw new IllegalArgumentException(s"table $name is read with two different schemas")
            found(name) = columns
          }
        }
        val it = m.values().iterator()
        while (it.hasNext) walk(it.next())
      case l: JList[_] =>
        val it = l.iterator()
        while (it.hasNext) walk(it.next())
      case _ =>
    }
    walk(plan)
    found.toMap
  }

  /**
   * The declared scales of the plan whose binary is at planPath, read from the JSON form
   * beside it (plans/q01.bin -> plans/q01.json), or None if there is none.
   */
  def declaredScalesBeside(planPath: String): Option[Seq[Option[Int]]] = {
    val json = Paths.get(planPath.replaceAll("\\.bin$", "") + ".json")
    if (planPath.endsWith(".bin") && Files.isRegularFile(json))
      declaredScales(new String(Files.readAllBytes(json), "UTF-8"))
    else None
  }

  private def field(v: Any, key: String): Option[Any] = v match {
    case m: JMap[_, _] => Option(m.asInstanceOf[JMap[String, Any]].get(key))
    case _ => None
  }

  private def req(v: Any, key: String): Any = field(v, key).getOrElse(throw new Unknown)

  private def list(v: Option[Any]): Seq[Any] = v match {
    case Some(l: JList[_]) =>
      val it = l.iterator()
      val out = Seq.newBuilder[Any]
      while (it.hasNext) out += it.next()
      out.result()
    case _ => Seq.empty
  }

  private def onlyEntry(node: Any): (String, Any) = node match {
    case m: JMap[_, _] if !m.isEmpty =>
      val e = m.entrySet().iterator().next()
      (String.valueOf(e.getKey), e.getValue)
    case _ => throw new Unknown
  }

  private def kindType(kind: String): Any = {
    val m = new java.util.LinkedHashMap[String, Any]()
    m.put(kind, new java.util.LinkedHashMap[String, Any]())
    m
  }

  def outputTypes(plan: Any): Seq[Option[Any]] = {
    val root = list(field(plan, "relations")).flatMap(r => field(r, "root")).headOption.getOrElse(throw new Unknown)
    relTypes(req(root, "input"))
  }

  private def relTypes(node: Any): Seq[Option[Any]] = {
    val (kind, rel) = onlyEntry(node)
    val cols: Seq[Option[Any]] = kind match {
      case "read" =>
        val types = req(req(req(rel, "baseSchema"), "struct"), "types")
        if (!types.isInstanceOf[JList[_]]) throw new Unknown
        list(Some(types)).map(Some(_))
      case "filter" | "sort" | "fetch" => relTypes(req(rel, "input"))
      case "project" =>
        val base = relTypes(req(rel, "input"))
        base ++ list(field(rel, "expressions")).map(exprType(_, base))
      case "aggregate" =>
        val base = relTypes(req(rel, "input"))
        val groupings = list(field(rel, "groupings"))
        val level = list(field(rel, "groupingExpressions"))
        val keys =
          if (level.nonEmpty) level // 0.107: aggregate-level expressions
          else if (groupings.length <= 1) groupings.headOption.map(g => list(field(g, "groupingExpressions"))).getOrElse(Seq.empty)
          else throw new Unknown // grouping sets in the older encoding
        keys.map(exprType(_, base)) ++ list(field(rel, "measures")).map(m => field(req(m, "measure"), "outputType"))
      case "join" | "cross" => relTypes(req(rel, "left")) ++ relTypes(req(rel, "right"))
      case _ => throw new Unknown
    }
    field(rel, "common").flatMap(field(_, "emit")).flatMap(field(_, "outputMapping")) match {
      case Some(mapping: JList[_]) =>
        list(Some(mapping)).map {
          case n: Number if n.intValue >= 0 && n.intValue < cols.length => cols(n.intValue)
          case _ => throw new Unknown
        }
      case Some(_) => throw new Unknown
      case None => cols
    }
  }

  private def exprType(e: Any, inputs: Seq[Option[Any]]): Option[Any] = {
    def has(k: String) = field(e, k).isDefined
    if (has("selection")) {
      val sel = req(e, "selection")
      val structField = field(sel, "directReference").flatMap(field(_, "structField"))
      if (field(sel, "outerReference").isDefined || structField.isEmpty || structField.flatMap(field(_, "child")).isDefined) None
      else {
        val i = structField.flatMap(field(_, "field")).collect { case n: Number => n.intValue }.getOrElse(0)
        if (i < inputs.length) inputs(i) else None
      }
    } else if (has("scalarFunction")) field(req(e, "scalarFunction"), "outputType")
    else if (has("windowFunction")) field(req(e, "windowFunction"), "outputType")
    else if (has("literal")) literalType(req(e, "literal"))
    else if (has("cast")) field(req(e, "cast"), "type")
    else if (has("ifThen")) {
      val ifThen = req(e, "ifThen")
      list(field(ifThen, "ifs")).headOption match {
        case Some(first) => exprType(req(first, "then"), inputs)
        case None => exprType(req(ifThen, "else"), inputs)
      }
    } else if (has("singularOrList")) Some(kindType("bool"))
    else if (has("subquery")) {
      val sq = req(e, "subquery")
      field(sq, "scalar") match {
        case Some(scalar) => relTypes(req(scalar, "input")).headOption.flatten
        case None => Some(kindType("bool"))
      }
    } else None
  }

  private def literalType(lit: Any): Option[Any] = {
    val (key, value) = onlyEntry(lit)
    key match {
      case "boolean" => Some(kindType("bool"))
      case "i8" | "i16" | "i32" | "i64" | "fp32" | "fp64" | "string" | "date" | "timestamp" => Some(kindType(key))
      case "decimal" =>
        val d = new java.util.LinkedHashMap[String, Any]()
        d.put("precision", req(value, "precision"))
        d.put("scale", req(value, "scale"))
        val m = new java.util.LinkedHashMap[String, Any]()
        m.put("decimal", d)
        Some(m)
      case "varChar" => Some(kindType("varchar"))
      case "fixedChar" => Some(kindType("fixedChar"))
      case "null" => Some(value)
      case _ => None
    }
  }
}
