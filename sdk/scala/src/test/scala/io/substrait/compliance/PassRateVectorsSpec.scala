package io.substrait.compliance

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

/** The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv). */
class PassRateVectorsSpec extends AnyFlatSpec with Matchers {

  private val rows: Seq[Array[String]] = {
    val src = Source.fromFile("../conformance/pass-rate-vectors.csv")
    try src.getLines().map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).drop(1).map(_.split(",")).toList
    finally src.close()
  }

  "TestSuiteResult.passRate" should "match the shared vectors" in {
    rows should not be empty
    for (f <- rows if f(4).toInt == 0) { // no UNSUPPORTED status in the Scala SDK
      val statuses = Seq(TestStatus.Passed, TestStatus.Failed, TestStatus.Skipped, TestStatus.Error)
      val results = statuses.zip(f.take(4).map(_.toInt)).flatMap { case (status, n) =>
        (0 until n).map(i => TestCaseResult(s"$status-$i", status))
      }
      TestSuiteResult("suite", results).passRate shouldBe f(5).toDouble +- 1e-9
    }
  }
}
