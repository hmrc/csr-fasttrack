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

package services.testdata

import common.FutureEx
import connectors.testdata.ExchangeObjects.DataGenerationResponse
import factories.UUIDFactory
import model.{ ApplicationStatuses, AssessmentExercise }
import model.AssessmentExercise._
import model.CandidateScoresCommands._
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentScoresEnteredStatusGenerator extends AssessmentScoresEnteredStatusGenerator {
  override val previousStatusGenerator = AllocationStatusGenerator
  override val aRepository = applicationRepository
  override val aasRepository = assessorAssessmentScoresRepository
}

trait AssessmentScoresEnteredStatusGenerator extends ConstructiveGenerator {
  val aRepository: GeneralApplicationRepository
  val aasRepository: ApplicationAssessmentScoresRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- saveScores(candidateInPreviousStatus.applicationId.get, generatorConfig)
      _ <- aRepository.updateStatus(candidateInPreviousStatus.applicationId.get, ApplicationStatuses.AssessmentScoresEntered)
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = ApplicationStatuses.AssessmentScoresEntered)
    }
  }

  private def saveScores(appId: String, generatorConfig: GeneratorConfig) = {
    val toInsert = generatorConfig.assessmentScores match {
      case None =>
        val interviewScores = randomScoresAndFeedback(appId, AssessmentExercise.interview)
        val groupScores = randomScoresAndFeedback(appId, AssessmentExercise.groupExercise)
        val writtenScores = randomScoresAndFeedback(appId, AssessmentExercise.writtenExercise)
        List(interviewScores, groupScores, writtenScores)
      case Some(scores) =>
        List(
          scores.interview.map(s => ExerciseScoresAndFeedback(appId, AssessmentExercise.interview, s)),
          scores.groupExercise.map(s => ExerciseScoresAndFeedback(appId, AssessmentExercise.groupExercise, s)),
          scores.writtenExercise.map(s => ExerciseScoresAndFeedback(appId, AssessmentExercise.writtenExercise, s))
        ).flatten
    }

    FutureEx.traverseSerial(toInsert)(aasRepository.save(_))
  }

  private def randomScoresAndFeedback(applicationId: String, assessmentExercise: AssessmentExercise): ExerciseScoresAndFeedback = {
    def randScore = Some(Random.randDouble(1, 4))

    val scoresAndFeedback = ScoresAndFeedback(
      attended = true,
      assessmentIncomplete = false,
      leadingAndCommunicating     = randScore,
      collaboratingAndPartnering  = randScore,
      deliveringAtPace            = randScore,
      makingEffectiveDecisions    = randScore,
      changingAndImproving        = randScore,
      buildingCapabilityForAll    = randScore,
      motivationFit               = randScore,
      feedback = Some("Good interview"),
      updatedBy = UUIDFactory.generateUUID())

    ExerciseScoresAndFeedback(applicationId, assessmentExercise, scoresAndFeedback)
  }
}
