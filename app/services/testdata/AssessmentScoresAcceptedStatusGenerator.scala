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

package services.testdata

import connectors.testdata.ExchangeObjects.DataGenerationResponse
import model.ApplicationStatuses
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import repositories._
import repositories.application.GeneralApplicationRepository
import scheduler.assessment.EvaluateAssessmentScoreJob
import services.applicationassessment.ApplicationAssessmentService
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentScoresAcceptedStatusGenerator extends AssessmentScoresAcceptedStatusGenerator {
  override val previousStatusGenerator = AssessmentScoresEnteredStatusGenerator
  override val aRepository = applicationRepository
  override val aaService = ApplicationAssessmentService
  override val aasRepository = applicationAssessmentScoresRepository
  override val fpRepository = frameworkPreferenceRepository
  override val acssService = AssessmentCentrePassMarkSettingsService
}

trait AssessmentScoresAcceptedStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val aaService: ApplicationAssessmentService
  val aasRepository: ApplicationAssessmentScoresRepository
  val fpRepository: FrameworkPreferenceRepository
  val acssService: AssessmentCentrePassMarkSettingsService

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- aRepository.updateStatus(appId, ApplicationStatuses.AssessmentScoresAccepted)
      scoresAndFeedback <- aasRepository.tryFind(appId)
      preferences <- fpRepository.tryGetPreferences(appId)
      latestPassMarks <- acssService.getLatestVersion
      // Check a pass mark is set for a scheme, otherwise fall over (we need pass marks to accept and score someone)
      schemeThresholds = latestPassMarks.schemes.head.overallPassMarks.getOrElse(throw new Exception("Assessment centre pass marks need " +
        "to be set to generate candidates in this status."))
      _ <- aaService.evaluateAssessmentCandidateScore(
        AssessmentPassmarkPreferencesAndScores(latestPassMarks, preferences.get, scoresAndFeedback.get),
        EvaluateAssessmentScoreJob.minimumCompetencyLevelConfig
      )
      // candidate scores and feedback and 'preferences'
    } yield {
      candidateInPreviousStatus
    }
  }
}
