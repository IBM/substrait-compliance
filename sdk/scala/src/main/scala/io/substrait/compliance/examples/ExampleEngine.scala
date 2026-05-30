package io.substrait.compliance.examples

import io.substrait.compliance._
import scala.concurrent.{Future, ExecutionContext}

/**
 * Example implementation of a ComplianceEngine for demonstration purposes
 */
class ExampleEngine(implicit ec: ExecutionContext) extends ComplianceEngine {

  override def getInfo(): EngineInfo = {
    EngineInfo(
      name = "Example Engine",
      version = "1.0.0",
      description = Some("Example Substrait compliance engine implementation")
    )
  }

  override def getCapabilities(): EngineCapabilities = {
    EngineCapabilities(
      supportedFormats = Set("json", "binary"),
      supportedFunctions = Set(
        "add", "subtract", "multiply", "divide",
        "equal", "not_equal", "greater_than", "less_than",
        "and", "or", "not"
      ),
      supportedTypes = Set(
        "i32", "i64", "fp32", "fp64", "boolean", "string", "date", "timestamp"
      ),
      maxParallelism = 4
    )
  }

  override def executePlan(
    plan: Array[Byte],
    inputTables: Map[String, TableData]
  ): Future[TableData] = {
    Future {
      // This is a mock implementation that just returns the first input table
      // or an empty table if no inputs are provided
      inputTables.headOption match {
        case Some((_, table)) =>
          // In a real implementation, you would:
          // 1. Parse the Substrait plan
          // 2. Execute the plan against the input tables
          // 3. Return the result
          table
        case None =>
          TableData.empty
      }
    }
  }

  override def validatePlan(plan: Array[Byte]): Future[ValidationResult] = {
    Future {
      // This is a mock implementation
      // In a real implementation, you would parse and validate the plan
      if (plan.isEmpty) {
        ValidationResult(
          isValid = false,
          errors = Seq("Plan is empty"),
          warnings = Seq.empty
        )
      } else {
        ValidationResult(
          isValid = true,
          errors = Seq.empty,
          warnings = Seq.empty
        )
      }
    }
  }
}

object ExampleEngine {
  def apply()(implicit ec: ExecutionContext): ExampleEngine = new ExampleEngine()
}

/**
 * Example usage of the Scala SDK
 */
object ExampleUsage {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Await
  import scala.concurrent.duration._

  def main(args: Array[String]): Unit = {
    // Create an engine instance
    val engine = ExampleEngine()

    // Get engine information
    val info = engine.getInfo()
    println(s"Engine: ${info.name} v${info.version}")

    // Get engine capabilities
    val capabilities = engine.getCapabilities()
    println(s"Supported formats: ${capabilities.supportedFormats.mkString(", ")}")
    println(s"Supported functions: ${capabilities.supportedFunctions.take(5).mkString(", ")}...")

    // Create sample input data
    val inputTable = TableData(
      columns = Seq(
        Column("id", ColumnType.Integer),
        Column("name", ColumnType.String),
        Column("value", ColumnType.Float)
      ),
      rows = Seq(
        Seq(1, "Alice", 100.5),
        Seq(2, "Bob", 200.75),
        Seq(3, "Charlie", 150.25)
      )
    )

    // Execute a plan (mock)
    val planBytes = Array[Byte](1, 2, 3, 4) // Mock plan
    val executionFuture = engine.executePlan(planBytes, Map("input" -> inputTable))

    val result = Await.result(executionFuture, 5.seconds)
    println(s"\nExecution result: ${result.rowCount} rows, ${result.columnCount} columns")

    // Validate a plan
    val validationFuture = engine.validatePlan(planBytes)
    val validation = Await.result(validationFuture, 5.seconds)
    println(s"\nPlan validation: ${if (validation.isValid) "Valid" else "Invalid"}")

    // Load and run test suites
    println("\n--- Running Test Suites ---")
    runTestSuites(engine)
  }

  def runTestSuites(engine: ComplianceEngine): Unit = {
    // Create a sample test suite
    val testSuite = TestSuite(
      name = "Example Test Suite",
      description = Some("Sample test suite for demonstration"),
      testCases = Seq(
        TestCase(
          name = "Simple Addition Test",
          description = Some("Test basic addition operation"),
          planPath = "test-suites/functions/arithmetic/add.bin",
          tags = Set("arithmetic", "basic")
        ),
        TestCase(
          name = "String Concatenation Test",
          description = Some("Test string concatenation"),
          planPath = "test-suites/functions/string/concat.bin",
          tags = Set("string", "basic")
        )
      )
    )

    // Create runner configuration
    val config = RunnerConfig(
      parallelism = 2,
      verbose = true,
      tags = Set("basic")
    )

    // Create and run the compliance runner
    val runner = ComplianceRunner(engine, config)

    val resultFuture = runner.runTestSuite(testSuite)

    try {
      val result = Await.result(resultFuture, 30.seconds)
      println(s"\n${result.summary}")
      println(s"Pass rate: ${result.passRate}%")
    } catch {
      case e: Exception =>
        println(s"Test execution failed: ${e.getMessage}")
    }
  }
}

// Made with Bob
