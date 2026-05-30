package io.substrait.compliance.benchmark

import io.substrait.compliance._
import io.substrait.compliance.examples.ExampleEngine
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
import java.nio.file.{Files, Paths}

/**
 * Example demonstrating performance benchmarking
 */
object BenchmarkExample {
  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    println("=== Substrait Compliance Benchmarking Example ===\n")

    // Create engine instance
    val engine = new ExampleEngine()

    // Run different benchmark scenarios
    runBasicBenchmark(engine)
    runPlanExecutionBenchmark(engine)
    runComparisonBenchmark()
    runScalabilityBenchmark(engine)

    println("\n=== Benchmarking Complete ===")
  }

  /**
   * Basic benchmark example
   */
  def runBasicBenchmark(engine: ComplianceEngine): Unit = {
    println("1. Basic Operation Benchmark")
    println("-" * 50)

    val config = BenchmarkConfig(
      warmupRuns = 10,
      measurementRuns = 100,
      verbose = true
    )

    val runner = BenchmarkRunner(engine, config)

    val operations = Seq(
      ("getInfo", () => Future.successful(engine.getInfo())),
      ("getCapabilities", () => Future.successful(engine.getCapabilities()))
    )

    val resultFuture = runner.runBenchmark("Basic Operations", operations)
    val result = Await.result(resultFuture, 1.minute)

    println(result.summary)
    println()
  }

  /**
   * Plan execution benchmark
   */
  def runPlanExecutionBenchmark(engine: ComplianceEngine): Unit = {
    println("2. Plan Execution Benchmark")
    println("-" * 50)

    // Create sample data
    val inputTable = TableData(
      columns = Seq(
        Column("id", ColumnType.Integer),
        Column("value", ColumnType.Float)
      ),
      rows = (1 to 1000).map(i => Seq(i, i * 1.5))
    )

    val plan = Array[Byte](1, 2, 3, 4, 5) // Mock plan

    val config = BenchmarkConfig(
      warmupRuns = 5,
      measurementRuns = 50,
      collectMemoryStats = true,
      verbose = true
    )

    val runner = BenchmarkRunner(engine, config)

    val operations = Seq(
      ("executePlan_1k_rows", () => 
        engine.executePlan(plan, Map("input" -> inputTable))),
      ("validatePlan", () => 
        engine.validatePlan(plan))
    )

    val resultFuture = runner.runBenchmark("Plan Execution", operations)
    val result = Await.result(resultFuture, 2.minutes)

    println(result.summary)
    
    // Export to CSV
    val csvPath = "benchmark_results.csv"
    Files.write(Paths.get(csvPath), result.toCSV.getBytes)
    println(s"Results exported to: $csvPath\n")
  }

  /**
   * Comparison benchmark between multiple engines
   */
  def runComparisonBenchmark(): Unit = {
    println("3. Engine Comparison Benchmark")
    println("-" * 50)

    // Create multiple engine instances
    val engines = Seq(
      new ExampleEngine(),
      new ExampleEngine(), // Could be different implementations
      new ExampleEngine()
    )

    val config = BenchmarkConfig(
      warmupRuns = 5,
      measurementRuns = 50,
      verbose = false
    )

    val runner = BenchmarkRunner(engines.head, config)

    val operations = Seq[(String, ComplianceEngine => Future[_])](
      ("getInfo", eng => Future.successful(eng.getInfo())),
      ("getCapabilities", eng => Future.successful(eng.getCapabilities()))
    )

    val resultsFuture = runner.compareBenchmark(
      "Engine Comparison",
      engines,
      operations
    )

    val results = Await.result(resultsFuture, 2.minutes)

    // Print comparison
    results.foreach { result =>
      println(s"\nEngine: ${result.engineName}")
      result.stats.foreach { stat =>
        println(f"  ${stat.operationName}: ${stat.avgTime.toMillis}ms avg, " +
                f"${stat.throughput}%.2f ops/sec")
      }
    }
    println()
  }

  /**
   * Scalability benchmark with varying data sizes
   */
  def runScalabilityBenchmark(engine: ComplianceEngine): Unit = {
    println("4. Scalability Benchmark")
    println("-" * 50)

    val dataSizes = Seq(100, 1000, 10000, 100000)
    val plan = Array[Byte](1, 2, 3, 4, 5)

    val config = BenchmarkConfig(
      warmupRuns = 3,
      measurementRuns = 20,
      verbose = false
    )

    println(f"${"Data Size"}%-15s ${"Avg Time"}%-15s ${"Throughput"}%-15s")
    println("-" * 50)

    dataSizes.foreach { size =>
      val inputTable = TableData(
        columns = Seq(
          Column("id", ColumnType.Integer),
          Column("value", ColumnType.Float)
        ),
        rows = (1 to size).map(i => Seq(i, i * 1.5))
      )

      val statsFuture = BenchmarkRunner.quickBenchmark(
        engine,
        s"executePlan_${size}_rows",
        () => engine.executePlan(plan, Map("input" -> inputTable)),
        runs = 20
      )

      val stats = Await.result(statsFuture, 1.minute)
      println(f"$size%-15d ${stats.avgTime.toMillis}%-15dms ${stats.throughput}%-15.2f ops/sec")
    }
    println()
  }

  /**
   * Parallel execution benchmark
   */
  def runParallelBenchmark(engine: ComplianceEngine): Unit = {
    println("5. Parallel Execution Benchmark")
    println("-" * 50)

    val parallelismLevels = Seq(1, 2, 4, 8)
    val plan = Array[Byte](1, 2, 3, 4, 5)

    val inputTable = TableData(
      columns = Seq(Column("id", ColumnType.Integer)),
      rows = (1 to 1000).map(i => Seq(i))
    )

    println(f"${"Parallelism"}%-15s ${"Avg Time"}%-15s ${"Throughput"}%-15s")
    println("-" * 50)

    parallelismLevels.foreach { parallelism =>
      val config = BenchmarkConfig(
        warmupRuns = 3,
        measurementRuns = 20,
        parallelism = parallelism,
        verbose = false
      )

      val runner = BenchmarkRunner(engine, config)

      val operations = Seq(
        ("executePlan", () => 
          engine.executePlan(plan, Map("input" -> inputTable)))
      )

      val resultFuture = runner.runBenchmark(
        s"Parallel_${parallelism}",
        operations
      )

      val result = Await.result(resultFuture, 2.minutes)
      val stats = result.stats.head

      println(f"$parallelism%-15d ${stats.avgTime.toMillis}%-15dms " +
              f"${stats.throughput}%-15.2f ops/sec")
    }
    println()
  }

  /**
   * Memory usage benchmark
   */
  def runMemoryBenchmark(engine: ComplianceEngine): Unit = {
    println("6. Memory Usage Benchmark")
    println("-" * 50)

    val config = BenchmarkConfig(
      warmupRuns = 5,
      measurementRuns = 50,
      collectMemoryStats = true,
      verbose = false
    )

    val runner = BenchmarkRunner(engine, config)

    val dataSizes = Seq(1000, 10000, 100000)

    dataSizes.foreach { size =>
      val inputTable = TableData(
        columns = Seq(
          Column("id", ColumnType.Integer),
          Column("data", ColumnType.String)
        ),
        rows = (1 to size).map(i => Seq(i, s"data_$i"))
      )

      val plan = Array[Byte](1, 2, 3, 4, 5)

      val statsFuture = runner.benchmarkOperation(
        s"memory_${size}_rows",
        () => engine.executePlan(plan, Map("input" -> inputTable))
      )

      val stats = Await.result(statsFuture, 2.minutes)
      println(f"Data size: $size%-10d rows, Avg time: ${stats.avgTime.toMillis}ms")
    }
    println()
  }
}

// Made with Bob
