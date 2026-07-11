package io.substrait.compliance

/**
 * Status of engine execution
 */
sealed trait EngineResultStatus

object EngineResultStatus {
  case object Success extends EngineResultStatus
  case object Failed extends EngineResultStatus
  case object Error extends EngineResultStatus
}

/**
 * Result of executing a plan on an engine
 */
case class EngineResult(
  status: EngineResultStatus,
  outputData: Option[TableData] = None,
  message: Option[String] = None,
  error: Option[Throwable] = None
) {
  
  /**
   * Check if execution was successful
   */
  def isSuccess: Boolean = status == EngineResultStatus.Success
  
  /**
   * Check if execution failed
   */
  def isFailed: Boolean = status == EngineResultStatus.Failed
  
  /**
   * Check if execution had an error
   */
  def isError: Boolean = status == EngineResultStatus.Error
}

object EngineResult {
  /**
   * Create a successful result
   */
  def success(output: TableData): EngineResult = {
    EngineResult(EngineResultStatus.Success, Some(output))
  }
  
  /**
   * Create a failed result
   */
  def failed(message: String): EngineResult = {
    EngineResult(EngineResultStatus.Failed, None, Some(message))
  }
  
  /**
   * Create an error result
   */
  def error(message: String, throwable: Throwable): EngineResult = {
    EngineResult(EngineResultStatus.Error, None, Some(message), Some(throwable))
  }
}

