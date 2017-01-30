package services.onlinetesting.evaluation

import model.ApplicationStatuses._
import model.EvaluationResults._
import org.scalatestplus.play.PlaySpec

class ApplicationStatusCalculatorSpec extends PlaySpec {
  val calc = new ApplicationStatusCalculator {}

  "Application status salculator" should {
    "return online test failed when all results are RED" in {
      val evaluated = List(Red, Red, Red)
      calc.determineStatus(evaluated) mustBe OnlineTestFailed
    }

    "return awaiting allocation when all results are GREEN" in {
      val evaluated = List(Green, Green, Green)
      calc.determineStatus(evaluated) mustBe AwaitingAllocation
    }

    "return awaiting online test reevaluation when there is at least one AMBER result" in {
      val evaluated = List(Green, Red, Amber)
      calc.determineStatus(evaluated) mustBe AwaitingOnlineTestReevaluation
    }

    "return online test completed when there is no AMBER and at least one GREEN" in {
      val evaluated = List(Red, Green, Red, Red)
      calc.determineStatus(evaluated) mustBe AwaitingAllocation
    }

    "throw an exception if there is nothing evaluated" in {
      an[IllegalArgumentException] must be thrownBy {
        calc.determineStatus(Nil)
      }
    }
  }
}
