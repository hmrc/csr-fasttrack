/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import model.ApplicationStatuses
import model.EvaluationResults.{ CompetencyAverageResult, PerSchemeEvaluation, Result }

case class AssessmentScoreEvaluationTestExpectation(applicationStatus: ApplicationStatuses.EnumVal,
                                                    passmarkVersion: Option[String], passedMinimumCompetencyLevel: Option[Boolean],
                                                    leadingAndCommunicatingAverage: Option[Double],
                                                    collaboratingAndPartneringAverage: Option[Double],
                                                    deliveringAtPaceAverage: Option[Double],
                                                    makingEffectiveDecisionsAverage: Option[Double],
                                                    changingAndImprovingAverage: Option[Double],
                                                    buildingCapabilityForAllAverage: Option[Double],
                                                    motivationFitAverage: Option[Double],
                                                    overallScore: Option[Double],
                                                    schemesEvaluation: Option[String],
                                                    overallEvaluation: Option[String]) {

  def competencyAverage: Option[CompetencyAverageResult] = {
    val allResults = List(leadingAndCommunicatingAverage, collaboratingAndPartneringAverage,
      deliveringAtPaceAverage, makingEffectiveDecisionsAverage, changingAndImprovingAverage,
      buildingCapabilityForAllAverage, motivationFitAverage)

    require(allResults.forall(_.isDefined) || allResults.forall(_.isEmpty), "all competency or none of them must be defined")

    if (allResults.forall(_.isDefined)) {
      Some(CompetencyAverageResult(
        leadingAndCommunicatingAverage.get,
        collaboratingAndPartneringAverage.get,
        deliveringAtPaceAverage.get,
        makingEffectiveDecisionsAverage.get,
        changingAndImprovingAverage.get,
        buildingCapabilityForAllAverage.get,
        motivationFitAverage.get,
        overallScore.get))
    } else {
      None
    }
  }

  def allSchemesEvaluationExpectations: Option[List[PerSchemeEvaluation]] = commonSchemeEvaluationExpectations(schemesEvaluation)

  def overallSchemesEvaluationExpectations: Option[List[PerSchemeEvaluation]] = commonSchemeEvaluationExpectations(overallEvaluation)

  private def commonSchemeEvaluationExpectations(schemesEvaluation: Option[String]) = schemesEvaluation.map { s =>
    s.split("\\|").map { schemeAndResult =>
      val Array(scheme, result) = schemeAndResult.split(":")
      PerSchemeEvaluation(scheme, Result(result))
    }.toList
  }
}
