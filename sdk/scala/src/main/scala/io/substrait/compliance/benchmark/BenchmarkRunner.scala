package io.substrait.compliance.benchmark

import io.substrait.compliance._
import scala.concurrent.{Future, ExecutionContext}
import java.time.{Duration, Instant}
import scala.collection.mutable

/**
 * Performance metrics for a single operation
 */
case class OperationMetrics(
  operationName: String,
  executionTime: Duration,
  memoryUsed: Long,
  timestamp: Instant
)

/**
 * Benchmark statistics
 */
case class BenchmarkStats(
  operationName: String,
  totalRuns: Int,
  minTime: Duration,
  maxTime: Duration,
  avgTime: Duration,
  medianTime: Duration,
  p95Time: Duration,
  p99Time: Duration,
  stdDev: Double,
  throughput: Double // operations per second
) {
  def summary: String = {
    f"""
       |Operation: $operationName
       |Total Runs: $totalRuns
       |Min Time: ${minTime.toMillis}ms
       |Max Time: ${maxTime.toMillis}ms
       |Avg Time: ${avgTime.toMillis}ms
       |Median Time: ${medianTime.toMillis}ms
       |P95 Time: ${p95Time.toMillis}ms
       |P99 Time: ${p99Time.toMillis}ms
       |Std Dev: ${stdDev}%.2fms
       |Throughput: ${throughput}%.2f ops/sec
       |""".stripMargin
  }
}

/**
 * Complete benchmark result
 */
case class BenchmarkResult(
  engineName: String,
  benchmarkName: String,
  stats: Seq[BenchmarkStats],
  totalDuration: Duration,
  timestamp: Instant
) {
  def summary: String = {
    val sb = new StringBuilder
    sb.append(s"Benchmark: $benchmarkName\n")
    sb.append(s"Engine: $engineName\n")
    sb.append(s"Total Duration: ${totalDuration.toMillis}ms\n")
    sb.append(s"Timestamp: $timestamp\n\n")
    
    stats.foreach { stat =>
      sb.append(stat.summary)
      sb.append("\n")
    }
    
    sb.toString()
  }
  
  def toCSV: String = {
    val header = "Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n"
    val rows = stats.map { stat =>
      s"$engineName,$benchmarkName,${stat.operationName},${stat.totalRuns}," +
      s"${stat.minTime.toMillis},${stat.maxTime.toMillis},${stat.avgTime.toMillis}," +
      s"${stat.medianTime.toMillis},${stat.p95Time.toMillis},${stat.p99Time.toMillis}," +
      s"${stat.stdDev},${stat.throughput}"
    }.mkString("\n")
    
    header + rows
  }
}

/**
 * Configuration for benchmark execution
 */
case class BenchmarkConfig(
  warmupRuns: Int = 5,
  measurementRuns: Int = 100,
  parallelism: Int = 1,
  collectMemoryStats: Boolean = true,
  verbose: Boolean = false
)

/**
 * Runs performance benchmarks on compliance engines
 */
class BenchmarkRunner(
  engine: ComplianceEngine,
  config: BenchmarkConfig = BenchmarkConfig()
)(implicit ec: ExecutionContext) {

  private val runtime = Runtime.getRuntime

  /**
   * Run a complete benchmark suite
   */
  def runBenchmark(
    benchmarkName: String,
    operations: Seq[(String, () => Future[_])]
  ): Future[BenchmarkResult] = {
    val startTime = Instant.now()
    
    if (config.verbose) {
      println(s"Starting benchmark: $benchmarkName")
      println(s"Warmup runs: ${config.warmupRuns}")
      println(s"Measurement runs: ${config.measurementRuns}")
    }

    // Run benchmarks for each operation
    val statsFuture = Future.sequence(operations.map { case (name, op) =>
      benchmarkOperation(name, op)
    })

    statsFuture.map { stats =>
      val endTime = Instant.now()
      val totalDuration = Duration.between(startTime, endTime)
      
      BenchmarkResult(
        engineName = engine.getInfo.name,
        benchmarkName = benchmarkName,
        stats = stats,
        totalDuration = totalDuration,
        timestamp = startTime
      )
    }
  }

  /**
   * Benchmark a single operation
   */
  def benchmarkOperation(
    operationName: String,
    operation: () => Future[_]
  ): Future[BenchmarkStats] = {
    if (config.verbose) {
      println(s"  Benchmarking: $operationName")
    }

    // Warmup phase
    val warmupFuture = if (config.warmupRuns > 0) {
      if (config.verbose) println(s"    Warmup: ${config.warmupRuns} runs")
      Future.sequence((1 to config.warmupRuns).map(_ => operation()))
    } else {
      Future.successful(Seq.empty)
    }

    warmupFuture.flatMap { _ =>
      // Measurement phase
      if (config.verbose) println(s"    Measuring: ${config.measurementRuns} runs")
      
      val metrics = mutable.Buffer[OperationMetrics]()
      
      def runMeasurement(remaining: Int): Future[Unit] = {
        if (remaining <= 0) {
          Future.successful(())
        } else {
          measureOperation(operationName, operation).flatMap { metric =>
            metrics += metric
            runMeasurement(remaining - 1)
          }
        }
      }

      runMeasurement(config.measurementRuns).map { _ =>
        calculateStats(operationName, metrics.toSeq)
      }
    }
  }

  /**
   * Measure a single operation execution
   */
  private def measureOperation(
    operationName: String,
    operation: () => Future[_]
  ): Future[OperationMetrics] = {
    val startTime = Instant.now()
    val startMemory = if (config.collectMemoryStats) {
      runtime.totalMemory() - runtime.freeMemory()
    } else 0L

    operation().map { _ =>
      val endTime = Instant.now()
      val endMemory = if (config.collectMemoryStats) {
        runtime.totalMemory() - runtime.freeMemory()
      } else 0L

      OperationMetrics(
        operationName = operationName,
        executionTime = Duration.between(startTime, endTime),
        memoryUsed = endMemory - startMemory,
        timestamp = startTime
      )
    }
  }

  /**
   * Calculate statistics from metrics
   */
  private def calculateStats(
    operationName: String,
    metrics: Seq[OperationMetrics]
  ): BenchmarkStats = {
    val times = metrics.map(_.executionTime.toNanos).sorted
    val totalRuns = times.length

    val minTime = Duration.ofNanos(times.head)
    val maxTime = Duration.ofNanos(times.last)
    val avgNanos = times.sum / totalRuns
    val avgTime = Duration.ofNanos(avgNanos)

    val medianTime = Duration.ofNanos(times(totalRuns / 2))
    val p95Time = Duration.ofNanos(times((totalRuns * 0.95).toInt))
    val p99Time = Duration.ofNanos(times((totalRuns * 0.99).toInt))

    // Calculate standard deviation
    val variance = times.map(t => math.pow(t - avgNanos, 2)).sum / totalRuns
    val stdDev = math.sqrt(variance) / 1_000_000.0 // Convert to milliseconds

    // Calculate throughput (ops/sec)
    val totalTimeSeconds = times.sum / 1_000_000_000.0
    val throughput = totalRuns / totalTimeSeconds

    BenchmarkStats(
      operationName = operationName,
      totalRuns = totalRuns,
      minTime = minTime,
      maxTime = maxTime,
      avgTime = avgTime,
      medianTime = medianTime,
      p95Time = p95Time,
      p99Time = p99Time,
      stdDev = stdDev,
      throughput = throughput
    )
  }

  /**
   * Run a comparison benchmark between multiple engines
   */
  def compareBenchmark(
    benchmarkName: String,
    engines: Seq[ComplianceEngine],
    operations: Seq[(String, ComplianceEngine => Future[_])]
  ): Future[Seq[BenchmarkResult]] = {
    Future.sequence(engines.map { eng =>
      val runner = new BenchmarkRunner(eng, config)
      val engineOps = operations.map { case (name, op) =>
        (name, () => op(eng))
      }
      runner.runBenchmark(benchmarkName, engineOps)
    })
  }
}

object BenchmarkRunner {
  /**
   * Create a new benchmark runner
   */
  def apply(engine: ComplianceEngine, config: BenchmarkConfig = BenchmarkConfig())
           (implicit ec: ExecutionContext): BenchmarkRunner = {
    new BenchmarkRunner(engine, config)
  }

  /**
   * Quick benchmark helper
   */
  def quickBenchmark(
    engine: ComplianceEngine,
    operationName: String,
    operation: () => Future[_],
    runs: Int = 100
  )(implicit ec: ExecutionContext): Future[BenchmarkStats] = {
    val config = BenchmarkConfig(warmupRuns = 5, measurementRuns = runs)
    val runner = new BenchmarkRunner(engine, config)
    runner.benchmarkOperation(operationName, operation)
  }
}

