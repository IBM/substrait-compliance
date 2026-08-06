package io.substrait.compliance

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future
import java.time.Duration

/**
 * Pass-through integration tests for the Scala ComplianceRunner.
 *
 * Contract verified:
 *   1. PassThroughEngine (returns expected verbatim) → all PASSED
 *   2. MismatchEngine (returns wrong value) → FAILED
 *   3. Epsilon within tolerance (5e-10 < 1e-9) → PASSED
 *   4. Epsilon outside tolerance (2e-9 > 1e-9) → FAILED
 *   5. Missing expected output → SKIPPED, never PASSED
 */
class PassThroughSpec extends AsyncFlatSpec with Matchers {

  // ── helpers ──────────────────────────────────────────────────────────────

  private def doubleColumn(name: String): Column =
    Column(name, ColumnType.Float)

  private def makeTable(v: Double): TableData = {
    val t = TableData(Seq(doubleColumn("v")))
    t.addRow(Seq(v))
    t
  }

  private def makeSuite(
      id: String,
      expectedOutput: Option[TableData] = None
  ): TestSuite = {
    val tc = TestCase(
      name = id,
      planPath = "dummy.bin",
      expectedOutput = expectedOutput
    )
    TestSuite(name = "test_suite", description = None, version = "1.0",
              testCases = Seq(tc), metadata = Map.empty)
  }

  // ── PassThroughEngine: echoes the expected output back ───────────────────

  private class PassThroughEngine(output: Option[TableData]) extends ComplianceEngine {
    override def getInfo: EngineInfo =
      EngineInfo("PassThrough", "1.0", "Test")

    override def getCapabilities: EngineCapabilities =
      EngineCapabilities(Seq.empty, Seq.empty, Seq.empty)

    override def executePlan(
        planBytes: Array[Byte],
        inputData: Map[String, TableData]): Future[EngineResult] =
      Future.successful(output match {
        case Some(td) => EngineResult.success(td)
        case None     => EngineResult.success(TableData.empty)
      })

    override def validatePlan(planBytes: Array[Byte]): Future[EngineResult] =
      Future.successful(EngineResult.success(TableData.empty))
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  "ComplianceRunner" should "PASS when engine returns identical output" in {
    val expected = makeTable(42.0)
    val engine   = new PassThroughEngine(Some(expected))
    val runner   = ComplianceRunner(engine)
    val suite    = makeSuite("t1", Some(makeTable(42.0)))

    runner.runTestSuite(suite).map { result =>
      result.totalTests  shouldBe 1
      result.passedTests shouldBe 1
      result.failedTests shouldBe 0
      result.passRate    shouldBe 100.0
    }
  }

  it should "FAIL when engine returns a different value" in {
    val engine = new PassThroughEngine(Some(makeTable(999.0)))
    val runner = ComplianceRunner(engine)
    val suite  = makeSuite("t2", Some(makeTable(42.0)))

    runner.runTestSuite(suite).map { result =>
      result.totalTests  shouldBe 1
      result.failedTests shouldBe 1
      result.passedTests shouldBe 0
    }
  }

  it should "PASS when double delta is within epsilon (5e-10 < 1e-9)" in {
    val expected = makeTable(1.0)
    val actual   = makeTable(1.0 + 5e-10)
    val engine   = new PassThroughEngine(Some(actual))
    val runner   = ComplianceRunner(engine)
    val suite    = makeSuite("eps_in", Some(expected))

    runner.runTestSuite(suite).map { result =>
      result.passedTests shouldBe 1
    }
  }

  it should "FAIL when double delta is outside epsilon (2e-9 > 1e-9)" in {
    val expected = makeTable(1.0)
    val actual   = makeTable(1.0 + 2e-9)
    val engine   = new PassThroughEngine(Some(actual))
    val runner   = ComplianceRunner(engine)
    val suite    = makeSuite("eps_out", Some(expected))

    runner.runTestSuite(suite).map { result =>
      result.failedTests shouldBe 1
    }
  }

  it should "produce SKIPPED (not PASSED) when expected output is absent" in {
    val engine = new PassThroughEngine(Some(TableData.empty))
    val runner = ComplianceRunner(engine)
    val suite  = makeSuite("no_expected", expectedOutput = None)

    runner.runTestSuite(suite).map { result =>
      result.totalTests  shouldBe 1
      result.passedTests shouldBe 0
      withClue("Missing expected output must NOT count as passed") {
        result.passedTests shouldBe 0
      }
      // The runner records it as either skipped or error, not passed
      (result.skippedTests + result.errorTests) shouldBe 1
    }
  }
}
