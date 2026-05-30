package io.substrait.compliance

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future

class ComplianceEngineSpec extends AsyncFlatSpec with Matchers {

  // Mock engine for testing
  class MockEngine extends ComplianceEngine {
    override def getInfo(): EngineInfo = {
      EngineInfo("Mock Engine", "1.0.0", Some("Test engine"))
    }

    override def getCapabilities(): EngineCapabilities = {
      EngineCapabilities(
        supportedFormats = Set("json"),
        supportedFunctions = Set("add", "subtract"),
        supportedTypes = Set("i32", "i64"),
        maxParallelism = 2
      )
    }

    override def executePlan(plan: Array[Byte], inputTables: Map[String, TableData]): Future[TableData] = {
      Future.successful(TableData.empty)
    }

    override def validatePlan(plan: Array[Byte]): Future[ValidationResult] = {
      Future.successful(ValidationResult(isValid = true, Seq.empty, Seq.empty))
    }
  }

  "ComplianceEngine" should "return engine information" in {
    val engine = new MockEngine()
    val info = engine.getInfo()
    
    info.name shouldBe "Mock Engine"
    info.version shouldBe "1.0.0"
    info.description shouldBe Some("Test engine")
  }

  it should "return engine capabilities" in {
    val engine = new MockEngine()
    val capabilities = engine.getCapabilities()
    
    capabilities.supportedFormats should contain("json")
    capabilities.supportedFunctions should contain("add")
    capabilities.maxParallelism shouldBe 2
  }

  it should "execute a plan" in {
    val engine = new MockEngine()
    val plan = Array[Byte](1, 2, 3)
    val inputTables = Map.empty[String, TableData]
    
    engine.executePlan(plan, inputTables).map { result =>
      result shouldBe a[TableData]
    }
  }

  it should "validate a plan" in {
    val engine = new MockEngine()
    val plan = Array[Byte](1, 2, 3)
    
    engine.validatePlan(plan).map { result =>
      result.isValid shouldBe true
      result.errors shouldBe empty
    }
  }
}

// Made with Bob
