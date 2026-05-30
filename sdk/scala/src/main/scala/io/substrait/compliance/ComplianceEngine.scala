package io.substrait.compliance

import scala.concurrent.Future

/**
 * Engine metadata information
 */
case class EngineInfo(
  name: String,
  version: String,
  vendor: String,
  description: Option[String] = None
)

/**
 * Engine capability information
 */
case class EngineCapabilities(
  supportedRelations: Seq[String],
  supportedFunctions: Seq[String],
  supportedTypes: Seq[String],
  extensions: Map[String, String] = Map.empty
)

/**
 * Main trait that query engines must implement
 */
trait ComplianceEngine {
  /**
   * Get engine metadata
   */
  def getInfo: EngineInfo

  /**
   * Get engine capabilities
   */
  def getCapabilities: EngineCapabilities

  /**
   * Execute a Substrait plan
   * 
   * @param planBytes Serialized Substrait plan (JSON or binary)
   * @param inputData Map of table names to input data
   * @return Future containing compliance result with output data
   */
  def executePlan(
    planBytes: Array[Byte],
    inputData: Map[String, TableData]
  ): Future[ComplianceResult]

  /**
   * Validate a Substrait plan without executing it
   * 
   * @param planBytes Serialized Substrait plan
   * @return Future containing compliance result indicating validity
   */
  def validatePlan(planBytes: Array[Byte]): Future[ComplianceResult]

  /**
   * Optional: Initialize engine resources
   */
  def initialize(): Future[Unit] = Future.successful(())

  /**
   * Optional: Cleanup engine resources
   */
  def shutdown(): Future[Unit] = Future.successful(())

  /**
   * Optional: Check if engine can handle a specific test
   * 
   * @param testId Test identifier
   * @return True if engine can run the test
   */
  def canRunTest(testId: String): Boolean = true
}

// Made with Bob
