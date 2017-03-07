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

trait ApplicationStatusCalculator {

  def determineStatus(result: AssessmentRuleCategoryResult): ApplicationStatuses.EnumVal = result.passedMinimumCompetencyLevel match {
    case Some(false) =>
      ApplicationStatuses.AssessmentCentreFailed
    case _ =>
      val allResultsOrderedByPreferences = List(result.location1Scheme1, result.location1Scheme2,
        result.location2Scheme1, result.location2Scheme2, result.alternativeScheme).flatten
      statusBasedOnFirstNonRedResult(allResultsOrderedByPreferences)
  }

  private def statusBasedOnFirstNonRedResult(allResultsInPreferenceOrder: List[Result]) = {
    val amberOrGreenOnly = allResultsInPreferenceOrder filterNot (_ == Red)

    amberOrGreenOnly.headOption match {
      case Some(Green) => ApplicationStatuses.AssessmentCentrePassed
      case Some(Amber) => ApplicationStatuses.AwaitingAssessmentCentreReevaluation
      case _ => ApplicationStatuses.AssessmentCentreFailed
    }
  }

  // Determines the overall result for the candidate based on the overall evaluation results
  // if the candidate has passed the minimum competency check
  def determineStatusNEW(result: AssessmentRuleCategoryResultNEW): ApplicationStatuses.EnumVal = result.passedMinimumCompetencyLevel match {
    case Some(false) =>
      ApplicationStatuses.AssessmentCentreFailed
    case _ =>
      val assessmentCentreOverallResults = result.overallEvaluation
      calculateStatus(assessmentCentreOverallResults)
  }

  private def calculateStatus(assessmentCentreOverallResults: List[PerSchemeEvaluation]): ApplicationStatuses.EnumVal = {
    if (assessmentCentreOverallResults.count(_.result == Red) == assessmentCentreOverallResults.size) {
      //scalastyle:off
      println(s"**** ApplicationStatusCalculator - all schemes are Red so setting status to ${ApplicationStatuses.AssessmentCentreFailed}")
      ApplicationStatuses.AssessmentCentreFailed
    } else if (assessmentCentreOverallResults.count(r => r.result == Red || r.result == Green) == assessmentCentreOverallResults.size) {
      println(s"**** ApplicationStatusCalculator - all schemes are Red/Green so setting status to ${ApplicationStatuses.AssessmentCentrePassed}")
      ApplicationStatuses.AssessmentCentrePassed
    } else {
      println(s"**** ApplicationStatusCalculator - schemes are not all Red/Green (some are Amber) so setting status to ${ApplicationStatuses.AwaitingAssessmentCentreReevaluation}")
      ApplicationStatuses.AwaitingAssessmentCentreReevaluation
    }
  }
}
