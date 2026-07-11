package io.substrait.compliance

import java.time.Duration

/**
 * Test status enumeration
 */
sealed trait TestStatus

object TestStatus {
  case object Passed extends TestStatus
  case object Failed extends TestStatus
  case object Skipped extends TestStatus
  case object Error extends TestStatus
}

/**
 * Result of a single test case execution
 */
case class TestCaseResult(
  testName: String,
  status: TestStatus,
  message: Option[String] = None,
  expected: Option[TableData] = None,
  actual: Option[TableData] = None,
  executionTime: Duration = Duration.ZERO,
  error: Option[Throwable] = None
) {
  
  /**
   * Check if test passed
   */
  def isPassed: Boolean = status == TestStatus.Passed

  /**
   * Check if test failed
   */
  def isFailed: Boolean = status == TestStatus.Failed

  /**
   * Check if test was skipped
   */
  def isSkipped: Boolean = status == TestStatus.Skipped

  /**
   * Check if test had an error
   */
  def isError: Boolean = status == TestStatus.Error

  /**
   * Get formatted message
   */
  def formattedMessage: String = {
    val baseMsg = message.getOrElse("")
    error match {
      case Some(e) => s"$baseMsg\nError: ${e.getMessage}"
      case None => baseMsg
    }
  }
}

/**
 * Result of a test suite execution
 */
case class TestSuiteResult(
  suiteName: String,
  testResults: Seq[TestCaseResult],
  executionTime: Duration = Duration.ZERO
) {
  
  /**
   * Get total number of tests
   */
  def totalTests: Int = testResults.length

  /**
   * Get number of passed tests
   */
  def passedTests: Int = testResults.count(_.isPassed)

  /**
   * Get number of failed tests
   */
  def failedTests: Int = testResults.count(_.isFailed)

  /**
   * Get number of skipped tests
   */
  def skippedTests: Int = testResults.count(_.isSkipped)

  /**
   * Get number of error tests
   */
  def errorTests: Int = testResults.count(_.isError)

  /**
   * Check if all tests passed
   */
  def allPassed: Boolean = testResults.forall(_.isPassed)

  /**
   * Check if any tests failed
   */
  def anyFailed: Boolean = testResults.exists(_.isFailed)

  /**
   * Get pass rate as percentage
   */
  def passRate: Double = {
    if (totalTests == 0) 0.0
    else (passedTests.toDouble / totalTests.toDouble) * 100.0
  }

  /**
   * Get summary string
   */
  def summary: String = {
    s"Suite: $suiteName - Total: $totalTests, Passed: $passedTests, " +
    s"Failed: $failedTests, Skipped: $skippedTests, Errors: $errorTests " +
    s"(${passRate}% pass rate)"
  }
}

/**
 * Overall compliance test results
 */
case class ComplianceResult(
  suiteResults: Seq[TestSuiteResult],
  totalExecutionTime: Duration = Duration.ZERO
) {
  
  /**
   * Get total number of test suites
   */
  def totalSuites: Int = suiteResults.length

  /**
   * Get total number of tests across all suites
   */
  def totalTests: Int = suiteResults.map(_.totalTests).sum

  /**
   * Get total number of passed tests
   */
  def totalPassed: Int = suiteResults.map(_.passedTests).sum

  /**
   * Get total number of failed tests
   */
  def totalFailed: Int = suiteResults.map(_.failedTests).sum

  /**
   * Get total number of skipped tests
   */
  def totalSkipped: Int = suiteResults.map(_.skippedTests).sum

  /**
   * Get total number of error tests
   */
  def totalErrors: Int = suiteResults.map(_.errorTests).sum

  /**
   * Check if all tests passed
   */
  def allPassed: Boolean = suiteResults.forall(_.allPassed)

  /**
   * Check if any tests failed
   */
  def anyFailed: Boolean = suiteResults.exists(_.anyFailed)

  /**
   * Get overall pass rate
   */
  def overallPassRate: Double = {
    if (totalTests == 0) 0.0
    else (totalPassed.toDouble / totalTests.toDouble) * 100.0
  }

  /**
   * Get summary string
   */
  def summary: String = {
    s"Overall Results - Suites: $totalSuites, Tests: $totalTests, " +
    s"Passed: $totalPassed, Failed: $totalFailed, " +
    s"Skipped: $totalSkipped, Errors: $totalErrors " +
    s"(${overallPassRate}% pass rate)"
  }

  /**
   * Get detailed report
   */
  def detailedReport: String = {
    val sb = new StringBuilder
    sb.append(summary).append("\n\n")
    
    suiteResults.foreach { suite =>
      sb.append(suite.summary).append("\n")
      suite.testResults.foreach { test =>
        val statusSymbol = test.status match {
          case TestStatus.Passed => "✓"
          case TestStatus.Failed => "✗"
          case TestStatus.Skipped => "○"
          case TestStatus.Error => "⚠"
        }
        sb.append(s"  $statusSymbol ${test.testName}")
        test.message.foreach(msg => sb.append(s" - $msg"))
        sb.append("\n")
      }
      sb.append("\n")
    }
    
    sb.toString()
  }
}

object ComplianceResult {
  /**
   * Create an empty result
   */
  def empty: ComplianceResult = ComplianceResult(Seq.empty)
}

