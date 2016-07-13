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
import model.CandidateScoresCommands.{ CandidateScoreFeedback, CandidateScores, CandidateScoresAndFeedback }
import repositories._
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentScoresEnteredStatusGenerator extends AssessmentScoresEnteredStatusGenerator {
  override val previousStatusGenerator = AllocationStatusGenerator
  override val aRepository = applicationRepository
  override val aasRepository = applicationAssessmentScoresRepository
}

trait AssessmentScoresEnteredStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val aasRepository: ApplicationAssessmentScoresRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {

    def getScoresAndFeedback(applicationId: String): CandidateScoresAndFeedback = {
      CandidateScoresAndFeedback(applicationId, Some(true), false,
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScores(Some(3.5), Some(3.5), Some(3.5)),
        CandidateScoreFeedback(Some("Good interview"), Some("Group exercise excellent"), Some("Written exercise good")))
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- aasRepository.save(getScoresAndFeedback(candidateInPreviousStatus.applicationId.get))
      _ <- aRepository.updateStatus(candidateInPreviousStatus.applicationId.get, ApplicationStatuses.AssessmentScoresEntered)
      _ <- aRepository.updateStatus(candidateInPreviousStatus.applicationId.get, ApplicationStatuses.AssessmentScoresEntered)
    } yield {
      candidateInPreviousStatus
    }
  }
}
