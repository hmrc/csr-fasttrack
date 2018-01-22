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

package model.testdata

import model.ApplicationStatuses
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.EvaluationResults.Result
import model.Scheme.Scheme
import model.exchange.testdata.OnlineTestScoresRequest

case class OnlineTestScores(numericalTScore: Option[Double], verbalTScore: Option[Double], situationalTScore: Option[Double],
  competencyTScore: Option[Double])

object OnlineTestScores {
  def apply(onlineTestScoresRequest: OnlineTestScoresRequest): OnlineTestScores = {
    OnlineTestScores(
      numericalTScore = onlineTestScoresRequest.numerical.map(_.toDouble),
      verbalTScore = onlineTestScoresRequest.verbal.map(_.toDouble),
      situationalTScore = onlineTestScoresRequest.situational.map(_.toDouble),
      competencyTScore = onlineTestScoresRequest.competency.map(_.toDouble)
    )
  }
}

case class GeneratorConfig(
  personalData: PersonalData = PersonalData(),
  assistanceDetails: AssistanceDetailsData = AssistanceDetailsData(),
  schemesData: SchemesData = SchemesData(),
  schemeLocationsData: SchemeLocationsData = SchemeLocationsData(),
  setGis: Boolean = false,
  cubiksUrl: String,
  region: Option[String] = None,
  loc1scheme1Passmark: Option[Result] = None,
  loc1scheme2Passmark: Option[Result] = None,
  previousStatus: Option[String] = None,
  confirmedAllocation: Boolean = true,
  testScores: Option[OnlineTestScores] = None,
  assessmentScores: Option[CandidateScoresAndFeedback] = None,
  reviewerAssessmentScores: Option[CandidateScoresAndFeedback] = None
)

object GeneratorConfig {
  def apply(cubiksUrlFromConfig: String, request: model.exchange.testdata.CreateCandidateInStatusRequest)(generatorId: Int): GeneratorConfig = {

    val statusData = StatusData(request.statusData)
    GeneratorConfig(
      personalData = request.personalData.map(PersonalData(_, generatorId)).getOrElse(PersonalData()),
      assistanceDetails = request.assistanceDetailsData.map(AssistanceDetailsData(_, generatorId)).getOrElse(AssistanceDetailsData()),
      schemesData = request.schemesData.map(SchemesData(_, generatorId)).getOrElse(SchemesData()),
      schemeLocationsData = request.schemeLocationsData.map(SchemeLocationsData(_, generatorId)).getOrElse(SchemeLocationsData()),
      previousStatus = statusData.previousApplicationStatus,
      cubiksUrl = cubiksUrlFromConfig,
      region = request.region,
      loc1scheme1Passmark = request.loc1scheme1EvaluationResult.map(Result.apply),
      loc1scheme2Passmark = request.loc1scheme2EvaluationResult.map(Result.apply),
      confirmedAllocation = statusData.applicationStatus == ApplicationStatuses.AllocationConfirmed.name,
      testScores = request.onlineTestScores.map(OnlineTestScores.apply),
      assessmentScores = request.assessmentScores,
      reviewerAssessmentScores = request.reviewerAssessmentScores
    )
  }
}
