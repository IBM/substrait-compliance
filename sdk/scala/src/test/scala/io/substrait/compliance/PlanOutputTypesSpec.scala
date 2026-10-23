package io.substrait.compliance

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Paths}

/** The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out. */
class PlanOutputTypesSpec extends AnyFlatSpec with Matchers {

  private val tpch = "../../test-suites/tpch/"

  private def plan(rel: String): String = new String(Files.readAllBytes(Paths.get(tpch + rel)), "UTF-8")

  "PlanOutputTypes.declaredScales" should "give q01's decimal aggregates" in {
    PlanOutputTypes.declaredScales(plan("plans/q01.json")) shouldBe
      Some(Seq(None, None, Some(2), Some(2), Some(4), Some(6), Some(2), Some(2), Some(2), None))
  }

  it should "give the same scales for both TPC-H encodings" in {
    for (q <- 1 to 22) {
      val name = f"q$q%02d.json"
      val current = PlanOutputTypes.declaredScales(plan("plans/" + name))
      withClue(name) {
        current should not be None
        PlanOutputTypes.declaredScales(plan("plans-substrait-0.81/" + name)) shouldBe current
      }
    }
  }

  it should "read deeply nested plans (TPC-H q20)" in {
    PlanOutputTypes.declaredScales(plan("plans/q20.json")) shouldBe Some(Seq(None, None))
  }

  it should "give None for a relation it does not know" in {
    PlanOutputTypes.declaredScales("""{"relations":[{"root":{"input":{"exchange":{}}}}]}""") shouldBe None
  }

  "PlanOutputTypes.declaredScalesBeside" should "read the JSON next to a plan binary" in {
    PlanOutputTypes.declaredScalesBeside(tpch + "plans/q01.bin").map(_(6)) shouldBe Some(Some(2))
    PlanOutputTypes.declaredScalesBeside(tpch + "plans/no-such-plan.bin") shouldBe None
  }
}
