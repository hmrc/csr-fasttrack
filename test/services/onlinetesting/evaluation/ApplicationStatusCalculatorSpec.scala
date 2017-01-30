/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
