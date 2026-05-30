package io.substrait.compliance

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import java.time.{Duration, Instant}
import java.nio.file.{Files, Paths}

/**
 * Configuration for compliance runner
 */
case class RunnerConfig(
  parallelism: Int = 1,
  stopOnFirstFailure: Boolean = false,
  verbose: Boolean = false,
  tags: Set[String] = Set.empty,
  excludeTags: Set[String] = Set.empty
)

/**
 * Runs compliance tests against an engine
 */
class ComplianceRunner(
  engine: ComplianceEngine,
  config: RunnerConfig = RunnerConfig()
)(implicit ec: ExecutionContext) {

  /**
   * Run a single test suite
   */
  def runTestSuite(suite: TestSuite): Future[TestSuiteResult] = {
    val startTime = Instant.now()

    // Filter test cases based on tags
    val filteredTests = filterTestCases(suite.testCases)

    if (config.verbose) {
      println(s"Running test suite: ${suite.name} (${filteredTests.length} tests)")
    }

    // Run tests based on parallelism setting
    val testResultsFuture = if (config.parallelism > 1) {
      runTestsParallel(filteredTests)
    } else {
      runTestsSequential(filteredTests)
    }

    testResultsFuture.map { testResults =>
      val endTime = Instant.now()
      val executionTime = Duration.between(startTime, endTime)
      TestSuiteResult(suite.name, testResults, executionTime)
    }
  }

  /**
   * Run multiple test suites
   */
  def runTestSuites(suites: Seq[TestSuite]): Future[ComplianceResult] = {
    val startTime = Instant.now()

    if (config.verbose) {
      println(s"Running ${suites.length} test suites")
    }

    val suiteResultsFuture = Future.sequence(suites.map(runTestSuite))

    suiteResultsFuture.map { suiteResults =>
      val endTime = Instant.now()
      val executionTime = Duration.between(startTime, endTime)
      ComplianceResult(suiteResults, executionTime)
    }
  }

  /**
   * Run tests from a directory
   */
  def runFromDirectory(directoryPath: String, recursive: Boolean = false): Future[ComplianceResult] = {
    TestSuiteLoader.loadFromDirectory(directoryPath, recursive) match {
      case Success(suites) =>
        if (suites.isEmpty) {
          Future.successful(ComplianceResult.empty)
        } else {
          runTestSuites(suites)
        }
      case Failure(e) =>
        Future.failed(new RuntimeException(s"Failed to load test suites: ${e.getMessage}", e))
    }
  }

  /**
   * Run a single test case
   */
  private def runTestCase(testCase: TestCase): Future[TestCaseResult] = {
    val startTime = Instant.now()

    if (config.verbose) {
      println(s"  Running test: ${testCase.name}")
    }

    // Read plan file
    val planFuture = readPlanFile(testCase.planPath)

    planFuture.flatMap { plan =>
      // Execute plan
      engine.executePlan(plan, testCase.inputTables).map { actualOutput =>
        val endTime = Instant.now()
        val executionTime = Duration.between(startTime, endTime)

        // Compare results
        val result = testCase.expectedOutput match {
          case Some(expected) =>
            if (compareTableData(expected, actualOutput)) {
              TestCaseResult(
                testName = testCase.name,
                status = TestStatus.Passed,
                executionTime = executionTime
              )
            } else {
              TestCaseResult(
                testName = testCase.name,
                status = TestStatus.Failed,
                message = Some("Output does not match expected result"),
                expected = Some(expected),
                actual = Some(actualOutput),
                executionTime = executionTime
              )
            }
          case None =>
            // No expected output, just check if execution succeeded
            TestCaseResult(
              testName = testCase.name,
              status = if (testCase.shouldFail) TestStatus.Failed else TestStatus.Passed,
              message = if (testCase.shouldFail) Some("Test should have failed but succeeded") else None,
              actual = Some(actualOutput),
              executionTime = executionTime
            )
        }

        if (config.verbose) {
          val statusSymbol = if (result.isPassed) "✓" else "✗"
          println(s"    $statusSymbol ${testCase.name}")
        }

        result
      }.recover {
        case e: Exception =>
          val endTime = Instant.now()
          val executionTime = Duration.between(startTime, endTime)

          if (testCase.shouldFail) {
            // Test was expected to fail
            TestCaseResult(
              testName = testCase.name,
              status = TestStatus.Passed,
              message = Some("Test failed as expected"),
              executionTime = executionTime
            )
          } else {
            // Unexpected error
            TestCaseResult(
              testName = testCase.name,
              status = TestStatus.Error,
              message = Some(s"Test execution failed: ${e.getMessage}"),
              error = Some(e),
              executionTime = executionTime
            )
          }
      }
    }
  }

  /**
   * Run tests sequentially
   */
  private def runTestsSequential(testCases: Seq[TestCase]): Future[Seq[TestCaseResult]] = {
    testCases.foldLeft(Future.successful(Seq.empty[TestCaseResult])) { (accFuture, testCase) =>
      accFuture.flatMap { results =>
        if (config.stopOnFirstFailure && results.exists(_.isFailed)) {
          Future.successful(results)
        } else {
          runTestCase(testCase).map(result => results :+ result)
        }
      }
    }
  }

  /**
   * Run tests in parallel
   */
  private def runTestsParallel(testCases: Seq[TestCase]): Future[Seq[TestCaseResult]] = {
    Future.sequence(testCases.map(runTestCase))
  }

  /**
   * Filter test cases based on tags
   */
  private def filterTestCases(testCases: Seq[TestCase]): Seq[TestCase] = {
    testCases.filter { testCase =>
      val includeByTags = if (config.tags.isEmpty) {
        true
      } else {
        testCase.hasAnyTag(config.tags)
      }

      val excludeByTags = if (config.excludeTags.isEmpty) {
        false
      } else {
        testCase.hasAnyTag(config.excludeTags)
      }

      includeByTags && !excludeByTags
    }
  }

  /**
   * Read plan file
   */
  private def readPlanFile(planPath: String): Future[Array[Byte]] = {
    Future {
      val path = Paths.get(planPath)
      if (!Files.exists(path)) {
        throw new IllegalArgumentException(s"Plan file not found: $planPath")
      }
      Files.readAllBytes(path)
    }
  }

  /**
   * Compare two TableData objects
   */
  private def compareTableData(expected: TableData, actual: TableData): Boolean = {
    if (expected.columnCount != actual.columnCount) return false
    if (expected.rowCount != actual.rowCount) return false

    // Compare column definitions
    if (expected.columns != actual.columns) return false

    // Compare rows
    expected.rows.zip(actual.rows).forall { case (expectedRow, actualRow) =>
      expectedRow.zip(actualRow).forall { case (expectedCell, actualCell) =>
        compareValues(expectedCell, actualCell)
      }
    }
  }

  /**
   * Compare two values with null handling
   */
  private def compareValues(expected: Any, actual: Any): Boolean = {
    (expected, actual) match {
      case (null, null) => true
      case (null, _) => false
      case (_, null) => false
      case (e: Double, a: Double) => math.abs(e - a) < 1e-9
      case (e: Float, a: Float) => math.abs(e - a) < 1e-6
      case _ => expected == actual
    }
  }
}

object ComplianceRunner {
  /**
   * Create a new compliance runner
   */
  def apply(engine: ComplianceEngine, config: RunnerConfig = RunnerConfig())
           (implicit ec: ExecutionContext): ComplianceRunner = {
    new ComplianceRunner(engine, config)
  }
}

// Made with Bob
