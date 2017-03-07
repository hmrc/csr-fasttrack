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
import model.EvaluationResults.{ Result, _ }

trait ApplicationStatusCalculator {
  private val Failed = OnlineTestFailed
  private val Held = AwaitingOnlineTestReevaluation
  private val Passed = AwaitingAllocation
  private val CanTransition = List(OnlineTestCompleted, AwaitingOnlineTestReevaluation)

  // TODO LT: fix the integration tests for the change to not update online test eval when candidate is in assessment centre
  def determineNewStatus(result: List[Result], currentApplicationStatus: EnumVal): Option[EnumVal] = {
    require(result.nonEmpty, "Cannot determine status from an empty list")

    if (!CanTransition.contains(currentApplicationStatus)) {
      None
    } else if (result.contains(Green)) {
      Some(Passed)
    } else if (result.contains(Amber)) {
      Some(Held)
    } else {
      Some(Failed)
    }
  }
}
