package io.substrait.compliance.benchmark

import io.substrait.compliance._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future
import java.time.Duration

class BenchmarkRunnerSpec extends AsyncFlatSpec with Matchers {

  // Mock engine for testing
  class MockEngine extends ComplianceEngine {
    override def getInfo(): EngineInfo = {
      EngineInfo("Mock Engine", "1.0.0", Some("Test engine"))
    }

    override def getCapabilities(): EngineCapabilities = {
      EngineCapabilities(
        supportedFormats = Set("json"),
        supportedFunctions = Set("add"),
        supportedTypes = Set("i32"),
        maxParallelism = 2
      )
    }

    override def executePlan(plan: Array[Byte], inputTables: Map[String, TableData]): Future[TableData] = {
      // Simulate some work
      Future {
        Thread.sleep(10)
        TableData.empty
      }
    }

    override def validatePlan(plan: Array[Byte]): Future[ValidationResult] = {
      Future.successful(ValidationResult(isValid = true, Seq.empty, Seq.empty))
    }
  }

  "BenchmarkRunner" should "run a simple benchmark" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 2, measurementRuns = 5, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operations = Seq(
      ("test_op", () => Future.successful("result"))
    )

    runner.runBenchmark("Test Benchmark", operations).map { result =>
      result.benchmarkName shouldBe "Test Benchmark"
      result.engineName shouldBe "Mock Engine"
      result.stats should have length 1
      result.stats.head.operationName shouldBe "test_op"
      result.stats.head.totalRuns shouldBe 5
    }
  }

  it should "calculate correct statistics" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 0, measurementRuns = 10, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operation = () => Future {
      Thread.sleep(10)
      "result"
    }

    runner.benchmarkOperation("test_op", operation).map { stats =>
      stats.totalRuns shouldBe 10
      stats.minTime.toMillis should be >= 0L
      stats.maxTime.toMillis should be >= stats.minTime.toMillis
      stats.avgTime.toMillis should be >= stats.minTime.toMillis
      stats.avgTime.toMillis should be <= stats.maxTime.toMillis
      stats.throughput should be > 0.0
    }
  }

  it should "handle warmup runs" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 3, measurementRuns = 5, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operation = () => Future.successful("result")

    runner.benchmarkOperation("test_op", operation).map { stats =>
      // Only measurement runs should be counted
      stats.totalRuns shouldBe 5
    }
  }

  it should "calculate percentiles correctly" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 0, measurementRuns = 100, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operation = () => Future {
      Thread.sleep(5)
      "result"
    }

    runner.benchmarkOperation("test_op", operation).map { stats =>
      stats.p95Time.toMillis should be >= stats.medianTime.toMillis
      stats.p99Time.toMillis should be >= stats.p95Time.toMillis
      stats.maxTime.toMillis should be >= stats.p99Time.toMillis
    }
  }

  it should "export results to CSV format" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 1, measurementRuns = 5, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operations = Seq(
      ("op1", () => Future.successful("result1")),
      ("op2", () => Future.successful("result2"))
    )

    runner.runBenchmark("CSV Test", operations).map { result =>
      val csv = result.toCSV
      csv should include("Engine,Benchmark,Operation")
      csv should include("Mock Engine")
      csv should include("CSV Test")
      csv should include("op1")
      csv should include("op2")
    }
  }

  it should "generate summary report" in {
    val engine = new MockEngine()
    val config = BenchmarkConfig(warmupRuns = 1, measurementRuns = 5, verbose = false)
    val runner = BenchmarkRunner(engine, config)

    val operations = Seq(
      ("test_op", () => Future.successful("result"))
    )

    runner.runBenchmark("Summary Test", operations).map { result =>
      val summary = result.summary
      summary should include("Benchmark: Summary Test")
      summary should include("Engine: Mock Engine")
      summary should include("Operation: test_op")
      summary should include("Total Runs: 5")
    }
  }

  "BenchmarkRunner.quickBenchmark" should "run a quick benchmark" in {
    val engine = new MockEngine()
    val operation = () => Future.successful("result")

    BenchmarkRunner.quickBenchmark(engine, "quick_test", operation, runs = 10).map { stats =>
      stats.operationName shouldBe "quick_test"
      stats.totalRuns shouldBe 10
      stats.throughput should be > 0.0
    }
  }
}

// Made with Bob
