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

package services.applicationassessment

import model.ApplicationStatuses
import model.EvaluationResults._
import model.persisted.SchemeEvaluationResult
import play.api.Logger

trait ApplicationStatusCalculator {

  def determineStatus(result: AssessmentRuleCategoryResult): ApplicationStatuses.EnumVal = result.passedMinimumCompetencyLevel match {
    case Some(false) =>
      ApplicationStatuses.AssessmentCentreFailed
    case _ =>
      calculateStatus(result.overallEvaluation)
  }

  private def calculateStatus(assessmentCentreOverallResults: List[SchemeEvaluationResult]) = {
    def finalStatus(results: List[Result]) = {
      val allReds = results.forall(_ == Red)
      val atLeastOneAmber = results.contains(Amber)

      if (allReds) {
        ApplicationStatuses.AssessmentCentreFailed
      } else if (atLeastOneAmber) {
        ApplicationStatuses.AwaitingAssessmentCentreReevaluation
      } else {
        ApplicationStatuses.AssessmentCentrePassed
      }
    }

    if (assessmentCentreOverallResults.nonEmpty) {
      val results = assessmentCentreOverallResults.map(_.result)
      val status = finalStatus(results)
      Logger.debug(s"Final status for assessment evaluation is $status")
      status
    } else {
      throw new IllegalArgumentException("Cannot determine final application status after assessment evaluation for an empty result list")
    }
  }
}
