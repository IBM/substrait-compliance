package io.substrait.compliance

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future

class ComplianceEngineSpec extends AsyncFlatSpec with Matchers {

  // Mock engine for testing
  class MockEngine extends ComplianceEngine {
    override def getInfo: EngineInfo = {
      EngineInfo("Mock Engine", "1.0.0", "Test Vendor", Some("Test engine"))
    }

    override def getCapabilities: EngineCapabilities = {
      EngineCapabilities(
        supportedRelations = Seq("read"),
        supportedFunctions = Seq("add", "subtract"),
        supportedTypes = Seq("i32", "i64"),
        extensions = Map("format" -> "json", "maxParallelism" -> "2")
      )
    }

    override def executePlan(plan: Array[Byte], inputTables: Map[String, TableData]): Future[EngineResult] = {
      Future.successful(EngineResult.success(TableData.empty))
    }

    override def validatePlan(plan: Array[Byte]): Future[EngineResult] = {
      Future.successful(EngineResult.success(TableData.empty))
    }
  }

  "ComplianceEngine" should "return engine information" in {
    val engine = new MockEngine()
    val info = engine.getInfo
    
    info.name shouldBe "Mock Engine"
    info.version shouldBe "1.0.0"
    info.description shouldBe Some("Test engine")
  }

  it should "return engine capabilities" in {
    val engine = new MockEngine()
    val capabilities = engine.getCapabilities
    
    capabilities.supportedRelations should contain("read")
    capabilities.supportedFunctions should contain("add")
    capabilities.extensions should contain("format" -> "json")
  }

  it should "execute a plan" in {
    val engine = new MockEngine()
    val plan = Array[Byte](1, 2, 3)
    val inputTables = Map.empty[String, TableData]
    
    engine.executePlan(plan, inputTables).map { result =>
      result.isSuccess shouldBe true
      result.outputData.isDefined shouldBe true
      result.outputData.get.isEmpty shouldBe true
    }
  }

  it should "validate a plan" in {
    val engine = new MockEngine()
    val plan = Array[Byte](1, 2, 3)
    
    engine.validatePlan(plan).map { result =>
      result.isSuccess shouldBe true
      result.isError shouldBe false
    }
  }
}

