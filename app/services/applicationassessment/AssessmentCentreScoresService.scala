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

import model.{ ApplicationStatuses, AssessmentExercise }
import model.CandidateScoresCommands.{ ApplicationScores, CandidateScoresAndFeedback, ExerciseScoresAndFeedback, RecordCandidateScores }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application._
import services.AuditService
import services.applicationassessment.AssessorAssessmentScoresService.{ AssessorScoresExistForExerciseException,
ReviewerScoresExistForExerciseException }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


object AssessorAssessmentScoresService extends AssessorAssessmentCentreScoresService {
  case class AssessorScoresExistForExerciseException(m: String) extends Exception(m)
  case class ReviewerScoresExistForExerciseException(m: String) extends Exception(m)
  val assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository = assessorAssessmentScoresRepository
  val reviewerScoresRepo: ReviewerApplicationAssessmentScoresMongoRepository = reviewerAssessmentScoresRepository
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsMongoRepository = personalDetailsRepository
  val auditService = AuditService

}

object ReviewerAssessmentScoresService extends ReviewerAssessmentScoresService {
  val assessmentScoresRepo: ReviewerApplicationAssessmentScoresMongoRepository = reviewerAssessmentScoresRepository
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsMongoRepository = personalDetailsRepository
  val auditService = AuditService
}

trait AssessorAssessmentCentreScoresService extends AssessmentCentreScoresService {
  val reviewerScoresRepo: ApplicationAssessmentScoresRepository

  def saveScoresAndFeedback(applicationId: String, exerciseScoresAndFeedback: ExerciseScoresAndFeedback)
                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val newStatus = ApplicationStatuses.AssessmentScoresEntered

    for {
      _ <- reviewerScoresRepo.tryFind(applicationId).map(_.map(_ =>
        throw ReviewerScoresExistForExerciseException(
          s"Reviewer scores already exist for $applicationId ${exerciseScoresAndFeedback.exercise}"
        )
      ))
      _ <- assessmentScoresRepo.tryFind(applicationId).map(_.foreach( scoresAndFeedback =>
        if (doesAnotherScoreExist(scoresAndFeedback, exerciseScoresAndFeedback.exercise,
          exerciseScoresAndFeedback.scoresAndFeedback.updatedBy)
        ) {
          throw AssessorScoresExistForExerciseException(
            s"Assessor scores already exist for $applicationId ${exerciseScoresAndFeedback.exercise}"
          )
        }
      ))
      _ <- assessmentScoresRepo.save(exerciseScoresAndFeedback)
      _ <- appRepo.updateStatus(applicationId, newStatus)
    } yield {
      auditService.logEvent("ApplicationScoresAndFeedbackSaved", Map("applicationId" -> applicationId))
      auditService.logEvent(s"ApplicationStatusSetTo$newStatus", Map("applicationId" -> applicationId))
    }
  }

  private def doesAnotherScoreExist(existingScoresAndFeedback: CandidateScoresAndFeedback, exercise: AssessmentExercise.Value,
    updatedBy: String) = exercise match {
    case AssessmentExercise.interview => existingScoresAndFeedback.interview.exists(_.updatedBy != updatedBy)
    case AssessmentExercise.groupExercise => existingScoresAndFeedback.groupExercise.exists(_.updatedBy != updatedBy)
    case AssessmentExercise.writtenExercise => existingScoresAndFeedback.writtenExercise.exists(_.updatedBy != updatedBy)
  }
}

trait ReviewerAssessmentScoresService extends AssessmentCentreScoresService {
  def saveScoresAndFeedback(applicationId: String, exerciseScoresAndFeedback: ExerciseScoresAndFeedback)
                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val newStatus = ApplicationStatuses.AssessmentScoresEntered
    for {
      _ <- assessmentScoresRepo.save(exerciseScoresAndFeedback)
      _ <- appRepo.updateStatus(applicationId, newStatus)
    } yield {
      auditService.logEvent("ApplicationScoresAndFeedbackSaved", Map("applicationId" -> applicationId))
      auditService.logEvent(s"ApplicationStatusSetTo$newStatus", Map("applicationId" -> applicationId))
    }
  }
}

trait AssessmentCentreScoresService {
  def assessmentScoresRepo: ApplicationAssessmentScoresRepository
  def appRepo: GeneralApplicationRepository
  def assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository
  def personalDetailsRepo: PersonalDetailsRepository
  def auditService: AuditService

  def saveScoresAndFeedback(applicationId: String, exerciseScoresAndFeedback: ExerciseScoresAndFeedback)
                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit]

  def acceptScoresAndFeedback(applicationId: String, scoresAndFeedback: CandidateScoresAndFeedback)
                             (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val newStatus = determineStatus(scoresAndFeedback)
    for {
      _ <- assessmentScoresRepo.saveAll(scoresAndFeedback)
      _ <- appRepo.updateStatus(applicationId, newStatus)
    } yield {
      auditService.logEvent("ApplicationScoresAndFeedbackAccepted", Map("applicationId" -> applicationId))
      auditService.logEvent(s"ApplicationStatusSetTo$newStatus", Map("applicationId" -> applicationId))
    }
  }

  def getNonSubmittedCandidateScores(assessorId: String): Future[List[ApplicationScores]] = {
    def getApplicationScores(candidateScores: CandidateScoresAndFeedback) = {
      val assessmentCentreAllocationFut = assessmentCentreAllocationRepo.findOne(candidateScores.applicationId)
      val personalDetailsFut = personalDetailsRepo.find(candidateScores.applicationId)
      for {
        a <- assessmentCentreAllocationFut
        c <- personalDetailsFut
      } yield {
        ApplicationScores(RecordCandidateScores(c.firstName, c.lastName, a.venue, a.date), Some(candidateScores))
      }
    }
    assessmentScoresRepo.findNonSubmittedScores(assessorId).flatMap { candidateScores =>
      Future.traverse(candidateScores)(getApplicationScores)
    }
  }

  def getCandidateScores(applicationId: String): Future[ApplicationScores] = {
    val assessment = assessmentCentreAllocationRepo.findOne(applicationId)
    val candidate = personalDetailsRepo.find(applicationId)
    val applicationScores = assessmentScoresRepo.tryFind(applicationId)

    for {
      a <- assessment
      c <- candidate
      as <- applicationScores
    } yield {
      ApplicationScores(RecordCandidateScores(c.firstName, c.lastName, a.venue, a.date), as)
    }
  }

  def getCandidateScoresAndFeedback(applicationId: String): Future[Option[CandidateScoresAndFeedback]] = {
    assessmentScoresRepo.tryFind(applicationId)
  }

  private def determineStatus(scoresAndFeedback: CandidateScoresAndFeedback) = {
    val exerciseAttendList = List(
      scoresAndFeedback.interview.map(_.attended),
      scoresAndFeedback.groupExercise.map(_.attended),
      scoresAndFeedback.writtenExercise.map(_.attended)
    ).map(_.getOrElse(throw new IllegalStateException("Cannot accept scores with empty attend field")))

    if (exerciseAttendList.contains(true)) {
      ApplicationStatuses.AssessmentScoresAccepted
    } else {
      ApplicationStatuses.FailedToAttend
    }
  }
}
