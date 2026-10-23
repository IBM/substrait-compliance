package io.substrait.compliance

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

/**
 * Query-output comparison: the vectors shared with the leaderboard's comparators
 * (sdk/conformance/comparison-vectors.json), checked against ResultComparison.describeMismatch.
 */
class ComparisonVectorsSpec extends AnyFlatSpec with Matchers {

  private val vectors: Vector[Json] = {
    val src = Source.fromFile("../conformance/comparison-vectors.json", "UTF-8")
    val text = try src.mkString finally src.close()
    val doc = parse(text).fold(e => throw e, identity)
    doc.hcursor.downField("vectors").focus.flatMap(_.asArray).getOrElse(Vector.empty)
  }

  private def special(o: JsonObject): Any =
    o("double").flatMap(_.asString).map(d => java.lang.Double.valueOf(d): Any)
      .orElse(o("decimal").flatMap(_.asString).map(d => new java.math.BigDecimal(d): Any))
      .getOrElse(throw new IllegalArgumentException(s"unexpected value $o"))

  private def decode(j: Json): Any = j.fold[Any](
    null,
    b => java.lang.Boolean.valueOf(b),
    n => {
      val t = n.toString
      if (t.exists(c => c == '.' || c == 'e' || c == 'E')) java.lang.Double.valueOf(t)
      else java.lang.Long.valueOf(t)
    },
    s => s,
    _ => throw new IllegalArgumentException("unexpected array value"),
    special
  )

  private def strings(j: Option[Json]): Seq[String] =
    j.flatMap(_.asArray).getOrElse(Vector.empty).flatMap(_.asString)

  private def table(j: Json): TableData = {
    val o = j.asObject.getOrElse(throw new IllegalArgumentException("table"))
    val columns = strings(o("names")).zip(strings(o("types"))).map { case (n, t) => Column(n, ColumnType.fromName(t)) }
    val rows = o("rows").flatMap(_.asArray).getOrElse(Vector.empty).map(r => r.asArray.getOrElse(Vector.empty).map(decode).toSeq)
    TableData(columns, rows)
  }

  "ResultComparison.describeMismatch" should "agree with every shared vector" in {
    vectors should not be empty
    val disagreements = vectors.flatMap { v =>
      val o = v.asObject.get
      val scales: Seq[Option[Int]] = o("declaredScales").flatMap(_.asArray).getOrElse(Vector.empty)
        .map(s => s.asNumber.flatMap(_.toInt))
      val mismatch = ResultComparison.describeMismatch(table(o("expected").get), table(o("actual").get), scales)
      val shouldMatch = o("match").flatMap(_.asBoolean).get
      if (mismatch.isEmpty != shouldMatch) Some(s"${o("name").flatMap(_.asString).getOrElse("?")}: $mismatch") else None
    }
    disagreements shouldBe empty
  }
}
