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

package model

import model.AssessmentExercise.AssessmentExercise
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, Json }

object CandidateScoresCommands {

  case class RecordCandidateScores(firstName: String, lastName: String, venueName: String, date: LocalDate)

  case class CandidateScoresAndFeedback(
      applicationId: String,
      interview: Option[ScoresAndFeedback] = None,
      groupExercise: Option[ScoresAndFeedback] = None,
      writtenExercise: Option[ScoresAndFeedback] = None
  ) {
    def allVersionsEmpty: Boolean = List(
      interview.flatMap(_.version),
      groupExercise.flatMap(_.version),
      writtenExercise.flatMap(_.version)
    ).forall(_.isEmpty)

    def setVersion(newVersion: Option[String]): CandidateScoresAndFeedback = {
      def updateVersion(sOpt: Option[ScoresAndFeedback]): Option[ScoresAndFeedback] = {
        sOpt.map(_.copy(version = newVersion))
      }

      this.copy(
        interview = updateVersion(interview),
        groupExercise = updateVersion(groupExercise),
        writtenExercise = updateVersion(writtenExercise)
      )
    }

    def leadingAndCommunicatingAvg: Double = {
      average(List(interview, groupExercise, writtenExercise).flatMap(_.flatMap(_.leadingAndCommunicating)), 3)
    }

    def deliveringAtPaceAvg: Double = {
      average(List(interview, writtenExercise).flatMap(_.flatMap(_.deliveringAtPace)), 2)
    }

    def changingAndImprovingAvg: Double = {
      average(List(interview, writtenExercise).flatMap(_.flatMap(_.changingAndImproving)), 2)
    }

    def buildingCapabilityForAllAvg: Double = {
      average(List(interview, groupExercise).flatMap(_.flatMap(_.buildingCapabilityForAll)), 2)
    }

    def collaboratingAndPartneringAvg: Double = {
      average(List(groupExercise, writtenExercise).flatMap(_.flatMap(_.collaboratingAndPartnering)), 2)
    }

    def makingEffectiveDecisionsAvg: Double = {
      average(List(groupExercise, writtenExercise).flatMap(_.flatMap(_.makingEffectiveDecisions)), 2)
    }

    // It has weight equals 2 - no need to divide by 2
    // sum * 2 / 2 = sum
    def motivationalFitDoubledAvg: Double = {
      average(List(interview, groupExercise).flatMap(_.flatMap(_.motivationFit)), 1)
    }

    private def average(list: List[Double], mandatoryNumberOfElements: Int) = {
      (list.map(BigDecimal(_)).sum / mandatoryNumberOfElements).toDouble
    }
  }

  case class ExerciseScoresAndFeedback(
    applicationId: String,
    exercise: AssessmentExercise,
    scoresAndFeedback: ScoresAndFeedback
  )

  case class ScoresAndFeedback(attended: Boolean,
                               assessmentIncomplete: Boolean,
                               leadingAndCommunicating: Option[Double] = None,
                               collaboratingAndPartnering: Option[Double] = None,
                               deliveringAtPace: Option[Double] = None,
                               makingEffectiveDecisions: Option[Double] = None,
                               changingAndImproving: Option[Double] = None,
                               buildingCapabilityForAll: Option[Double] = None,
                               motivationFit: Option[Double] = None,
                               feedback: Option[String] = None,
                               updatedBy: String,
                               savedDate: Option[DateTime] = None,
                               submittedDate: Option[DateTime] = None,
                               version: Option[String] = None
                              )

  case class ApplicationScores(candidate: RecordCandidateScores, scoresAndFeedback: Option[CandidateScoresAndFeedback])

  object Implicits {
    implicit val RecordCandidateScoresFormats: Format[RecordCandidateScores] = Json.format[RecordCandidateScores]
    implicit val scoresAndFeedbackFormats: Format[ScoresAndFeedback] = Json.format[ScoresAndFeedback]
    implicit val exerciseScoresAndFeedbackFormats: Format[ExerciseScoresAndFeedback] = Json.format[ExerciseScoresAndFeedback]
    implicit val CandidateScoresAndFeedbackFormats: Format[CandidateScoresAndFeedback] = Json.format[CandidateScoresAndFeedback]
    implicit val ApplicationScoresFormats: Format[ApplicationScores] = Json.format[ApplicationScores]
  }
}
