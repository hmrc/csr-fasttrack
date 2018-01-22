/*
 * Copyright 2018 HM Revenue & Customs
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

import model.ApplicationStatuses
import model.ApplicationStatuses._
import model.EvaluationResults._
import org.scalatestplus.play.PlaySpec

class ApplicationStatusCalculatorSpec extends PlaySpec {
  val calc = new ApplicationStatusCalculator {}

  "Application status calculator" should {

    "return online test failed when all results are RED" in {
      val evaluated = List(Red, Red, Red)
      calc.determineNewStatus(evaluated, OnlineTestCompleted) mustBe Some(OnlineTestFailed)
    }

    "do not change application status for application status different to: OnlineTestCompleted and AwaitingOnlineTestReevaluation" in {
      val evaluated = List(Red, Green, Red)
      val CanTransition = List(OnlineTestCompleted, AwaitingOnlineTestReevaluation)
      ApplicationStatuses.values.filterNot(s => CanTransition.contains(s)).foreach { status =>
        calc.determineNewStatus(evaluated, status) mustBe None
      }
    }

    "return awaiting allocation when all results are GREEN" in {
      val evaluated = List(Green, Green, Green)
      calc.determineNewStatus(evaluated, OnlineTestCompleted) mustBe Some(AwaitingAllocation)
    }

    "return awaiting online test reevaluation when all results are AMBER" in {
      val evaluated = List(Amber, Amber, Amber)
      calc.determineNewStatus(evaluated, OnlineTestCompleted) mustBe Some(AwaitingOnlineTestReevaluation)
    }

    "return awaiting allocation  when there is at least one GREEN result" in {
      val evaluated = List(Green, Red, Amber)
      calc.determineNewStatus(evaluated, OnlineTestCompleted) mustBe Some(AwaitingAllocation)
    }

    "return awaiting online test reevaluation when there is an AMBER with REDs" in {
      val evaluated = List(Red, Amber, Red, Red)
      calc.determineNewStatus(evaluated, OnlineTestCompleted) mustBe Some(AwaitingOnlineTestReevaluation)
    }

    "throw an exception if there is nothing evaluated" in {
      an[IllegalArgumentException] must be thrownBy {
        calc.determineNewStatus(Nil, OnlineTestCompleted)
      }
    }
  }
}
