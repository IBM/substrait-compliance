package io.substrait.compliance

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Paths}
import scala.concurrent.Future

/**
 * This repository's suite metadata (test-suites/tpch/metadata.yaml), loaded as
 * scripts/run_plan_compliance.py loads it, and run end to end.
 */
class SuiteMetadataLoaderSpec extends AsyncFlatSpec with Matchers {

  private val tpch = "../../test-suites/tpch/metadata.yaml"

  "SuiteMetadataLoader" should "load every TPC-H case with its inputs, expected output and scales" in {
    val suite = SuiteMetadataLoader.load(tpch)
    suite.testCases should have length 22
    val q01 = suite.testCases.head
    q01.name shouldBe "q01"
    q01.inputTables.keySet shouldBe Set("LINEITEM") // the name the plan reads
    q01.inputTables("LINEITEM").rowCount shouldBe 60175
    q01.inputTables("LINEITEM").columnCount shouldBe 16
    q01.inputTables("LINEITEM").columns(4).columnType shouldBe ColumnType.Decimal
    q01.expectedOutput.map(t => (t.rowCount, t.columnCount)) shouldBe Some((4, 10))
    q01.declaredScales.map(_(6)) shouldBe Some(Some(2))
    Files.isRegularFile(Paths.get(q01.planPath)) shouldBe true
  }

  it should "be what TestSuiteLoader.loadFromFile returns for suite metadata" in {
    TestSuiteLoader.loadFromFile(tpch).get.testCases.map(_.name) shouldBe
      SuiteMetadataLoader.load(tpch).testCases.map(_.name)
  }

  it should "share a data file between the cases that read it" in {
    val suite = SuiteMetadataLoader.load(tpch)
    val lineitem = suite.testCases.flatMap(_.inputTables.get("LINEITEM"))
    lineitem.size should be > 1
    lineitem.forall(_ eq lineitem.head) shouldBe true
  }

  /** Returns each case's expected output, found by the plan it is given. */
  private class EchoEngine(byPlan: Map[Seq[Byte], TableData], tweak: TableData => TableData) extends ComplianceEngine {
    override def getInfo: EngineInfo = EngineInfo("Echo", "1.0", "Test")
    override def getCapabilities: EngineCapabilities = EngineCapabilities(Seq.empty, Seq.empty, Seq.empty)
    override def executePlan(planBytes: Array[Byte], inputData: Map[String, TableData]): Future[EngineResult] =
      Future.successful(EngineResult.success(tweak(byPlan(planBytes.toSeq))))
    override def validatePlan(planBytes: Array[Byte]): Future[EngineResult] =
      Future.successful(EngineResult.success(TableData.empty))
  }

  private def q01Only(f: TableData => TableData)(t: TableData): TableData =
    if (t.columns.lastOption.map(_.name).contains("count_order")) f(t) else t

  private def run(tweak: TableData => TableData) = {
    val suite = SuiteMetadataLoader.load(tpch)
    val byPlan = suite.testCases.map(tc => Files.readAllBytes(Paths.get(tc.planPath)).toSeq -> tc.expectedOutput.get).toMap
    ComplianceRunner(new EchoEngine(byPlan, tweak)).runTestSuite(suite)
  }

  "ComplianceRunner on TPC-H" should "pass every query when the engine returns the expected output" in {
    run(identity).map(_.passedTests shouldBe 22)
  }

  it should "accept q01's averages at the plan's declared scale (decimal(15,2))" in {
    val rounded = q01Only(t => TableData(t.columns, t.rows.map(_.zipWithIndex.map {
      case (v, i) if i >= 6 && i <= 8 => new java.math.BigDecimal(String.valueOf(v)).setScale(2, java.math.RoundingMode.HALF_UP)
      case (v, _) => v
    }))) _
    run(rounded).map(_.passedTests shouldBe 22)
  }

  it should "fail a renamed column and say so" in {
    val renamed = q01Only(t => TableData(t.columns.updated(9, Column("COUNT", t.columns(9).columnType)), t.rows)) _
    run(renamed).map { r =>
      r.passedTests shouldBe 21
      r.testResults.find(_.testName == "q01").flatMap(_.message).getOrElse("") should include("expected column names")
    }
  }
}
