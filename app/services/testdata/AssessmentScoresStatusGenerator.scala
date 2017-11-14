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
import factories.{ DateTimeFactory, UUIDFactory }
import model.{ ApplicationStatuses, AssessmentExercise }
import model.AssessmentExercise._
import model.CandidateScoresCommands._
import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.faker.DataFaker.Random

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AssessmentScoresEnteredStatusGenerator extends AssessmentScoresStatusGenerator {
  val previousStatusGenerator = AllocationStatusGenerator
  val aRepository = applicationRepository
  val aasRepository = assessorAssessmentScoresRepository
  val newStatus = ApplicationStatuses.AssessmentScoresEntered
  val scores = (gc: GeneratorConfig) => gc.assessmentScores
  val isSubmitted = false
}

object AssessmentScoresSubmittedStatusGenerator extends AssessmentScoresStatusGenerator {
  val previousStatusGenerator = AssessmentScoresEnteredStatusGenerator
  val aRepository = applicationRepository
  val aasRepository = assessorAssessmentScoresRepository
  val newStatus = ApplicationStatuses.AssessmentScoresEntered
  val scores = (gc: GeneratorConfig) => {
    gc.assessmentScores.map { assessmentScores =>
      val newInterviewScores = assessmentScores.interview.map { interview =>
        interview.copy(submittedDate = Some(DateTimeFactory.nowLocalTimeZone))
      }
      val newGroupExerciseScores = assessmentScores.groupExercise.map { groupExercise =>
        groupExercise.copy(submittedDate = Some(DateTimeFactory.nowLocalTimeZone))
      }
      val newWrittenExerciseScores = assessmentScores.writtenExercise.map { writtenExercise =>
        writtenExercise.copy(submittedDate = Some(DateTimeFactory.nowLocalTimeZone))
      }
      assessmentScores.copy(interview = newInterviewScores, groupExercise = newGroupExerciseScores, writtenExercise = newWrittenExerciseScores)
    }
  }
  val isSubmitted = true
}

object AssessmentScoresAcceptedStatusGenerator extends AssessmentScoresStatusGenerator {
  val previousStatusGenerator = AssessmentScoresSubmittedStatusGenerator
  val aRepository = applicationRepository
  val aasRepository = reviewerAssessmentScoresRepository
  val newStatus = ApplicationStatuses.AssessmentScoresAccepted
  val scores = (gc: GeneratorConfig) => gc.reviewerAssessmentScores
  val isSubmitted = true
}

trait AssessmentScoresStatusGenerator extends ConstructiveGenerator {
  def aRepository: GeneralApplicationRepository
  def aasRepository: ApplicationAssessmentScoresRepository
  def newStatus: ApplicationStatuses.EnumVal
  def scores: (GeneratorConfig) => Option[CandidateScoresAndFeedback]
  def isSubmitted: Boolean

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier): Future[DataGenerationResponse] = {
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- saveScores(candidateInPreviousStatus.applicationId.get, generatorConfig)
      _ <- aRepository.updateStatus(candidateInPreviousStatus.applicationId.get, newStatus)
    } yield {
      candidateInPreviousStatus.copy(applicationStatus = newStatus)
    }
  }

  private def saveScores(appId: String, generatorConfig: GeneratorConfig) = {
    val toInsert = scores(generatorConfig) match {
      case None =>
        val interviewScores = randomScoresAndFeedback(appId, AssessmentExercise.interview, isSubmitted)
        val groupScores = randomScoresAndFeedback(appId, AssessmentExercise.groupExercise, isSubmitted)
        val writtenScores = randomScoresAndFeedback(appId, AssessmentExercise.writtenExercise, isSubmitted)
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

  private def randomScoresAndFeedback(applicationId: String, assessmentExercise: AssessmentExercise,
                                      isSubmitted: Boolean = false): ExerciseScoresAndFeedback = {
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
      updatedBy = UUIDFactory.generateUUID(),
      savedDate = Some(DateTimeFactory.nowLocalTimeZone),
      submittedDate = if (isSubmitted) { Some(DateTimeFactory.nowLocalTimeZone) } else { None }
    )

    ExerciseScoresAndFeedback(applicationId, assessmentExercise, scoresAndFeedback)
  }
}
