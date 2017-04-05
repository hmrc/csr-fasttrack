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
import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands.{ ApplicationScores, CandidateScoresAndFeedback, ExerciseScoresAndFeedback, RecordCandidateScores }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application._
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessorAssessmentScoresService extends AssessmentCentreScoresService {
  val assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository = assessorAssessmentScoresRepository
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsMongoRepository = personalDetailsRepository
  val auditService = AuditService
}

object ReviewerAssessmentScoresService extends AssessmentCentreScoresService {
  val assessmentScoresRepo: ReviewerApplicationAssessmentScoresMongoRepository = reviewerAssessmentScoresRepository
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsMongoRepository = personalDetailsRepository
  val auditService = AuditService
}

trait AssessmentCentreScoresService {
  val assessmentScoresRepo: ApplicationAssessmentScoresRepository
  val appRepo: GeneralApplicationRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsRepository
  val auditService: AuditService

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
