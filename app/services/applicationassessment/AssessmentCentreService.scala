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

import config.AssessmentEvaluationMinimumCompetencyLevel
import connectors.{ CSREmailClient, EmailClient }
import factories.UUIDFactory
import model.ApplicationStatuses
import model.ApplicationStatuses.BSONEnumHandler
import model.AssessmentEvaluationCommands.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import model.CandidateScoresCommands.{ ApplicationScores, ExerciseScoresAndFeedback, RecordCandidateScores }
import model.EvaluationResults._
import model.Exceptions.IncorrectStatusInApplicationException
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, ApplicationForNotification }
import play.api.Logger
import play.api.mvc.RequestHeader
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository, PersonalDetailsRepository }
import repositories._
import services.AuditService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object AssessmentCentreService extends AssessmentCentreService {

  val assessmentCentreAllocationRepo = assessmentCentreAllocationRepository
  val personalDetailsRepo = personalDetailsRepository
  val otRepository = onlineTestRepository
  val aRepository = applicationRepository
  val aasRepository = applicationAssessmentScoresRepository
  val fpRepository = frameworkPreferenceRepository
  val cdRepository = contactDetailsRepository

  val emailClient = CSREmailClient
  val auditService = AuditService

  val passmarkService = AssessmentCentrePassMarkSettingsService
  val passmarkRulesEngine = AssessmentCentrePassmarkRulesEngine
}

trait AssessmentCentreService extends ApplicationStatusCalculator {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val assessmentCentreAllocationRepo: AssessmentCentreAllocationRepository
  val personalDetailsRepo: PersonalDetailsRepository
  val otRepository: OnlineTestRepository
  val aRepository: GeneralApplicationRepository
  val aasRepository: ApplicationAssessmentScoresRepository
  val fpRepository: FrameworkPreferenceRepository
  val cdRepository: ContactDetailsRepository

  val emailClient: EmailClient

  val auditService: AuditService
  val passmarkService: AssessmentCentrePassMarkSettingsService
  val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine

  def saveScoresAndFeedback(applicationId: String, exerciseScoresAndFeedback: ExerciseScoresAndFeedback)
                           (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    val newStatus = exerciseScoresAndFeedback.scoresAndFeedback.attended match {
      case true => ApplicationStatuses.AssessmentScoresEntered
      case false => ApplicationStatuses.FailedToAttend
    }
    for {
      _ <- aasRepository.save(exerciseScoresAndFeedback)
      _ <- aRepository.updateStatus(applicationId, newStatus)
    } yield {
      auditService.logEvent("ApplicationScoresAndFeedbackSaved", Map("applicationId" -> applicationId))
      auditService.logEvent(s"ApplicationStatusSetTo$newStatus", Map("applicationId" -> applicationId))
    }
  }

  def getCandidateScores(applicationId: String): Future[ApplicationScores] = {
    val assessment = assessmentCentreAllocationRepo.findOne(applicationId)
    val candidate = personalDetailsRepo.find(applicationId)
    val applicationScores = aasRepository.tryFind(applicationId)

    for {
      a <- assessment
      c <- candidate
      as <- applicationScores
    } yield {
      ApplicationScores(RecordCandidateScores(c.firstName, c.lastName, a.venue, a.date), as)
    }
  }

  def acceptScoresAndFeedback(applicationId: String): Future[Unit] = {
    aRepository.updateStatus(applicationId, ApplicationStatuses.AssessmentScoresAccepted)
  }

  def removeFromAssessmentCentreSlot(applicationId: String): Future[Unit] = {
    deleteAssessmentCentreAllocation(applicationId).flatMap { _ =>
      otRepository.removeCandidateAllocationStatus(applicationId).map { _ =>
        auditService.logEventNoRequest("AssessmentCentreAllocationStatusReset", Map("applicationId" -> applicationId))
      }
    }
  }

  def deleteAssessmentCentreAllocation(applicationId: String): Future[Unit] = {
    assessmentCentreAllocationRepo.delete(applicationId).map { _ =>
      auditService.logEventNoRequest("AssessmentCentreAllocationDeleted", Map("applicationId" -> applicationId))
    }
  }

  def nextAssessmentCandidateReadyForEvaluation: Future[Option[OnlineTestEvaluationAndAssessmentCentreScores]] = {
      passmarkService.getLatestVersion2.flatMap {
      case passmark if passmark.schemes.forall(_.overallPassMarks.isDefined) =>
        aRepository.nextApplicationReadyForAssessmentScoreEvaluation(passmark.info.get.version).flatMap {
          case Some(appId) =>
            // scalastyle:off
            println(s"**** <<<<<START>>>>> found candidate ready for assessment evaluation - appId = $appId")
            for {
              assessmentCentreScoresOpt <- aasRepository.tryFind(appId)
              preferredSchemesWithQualificationsOpt <- fpRepository.tryGetPreferencesWithQualifications(appId)
              onlineTestEvaluation <- otRepository.findPassmarkEvaluation(appId)
            } yield {
              for {
                scores <- assessmentCentreScoresOpt
                prefsWithQualifications <- preferredSchemesWithQualificationsOpt
              } yield {
                val assessmentResult = AssessmentPassmarkPreferencesAndScores(passmark, prefsWithQualifications, scores)
                OnlineTestEvaluationAndAssessmentCentreScores(onlineTestEvaluation, assessmentResult)
              }
            }
          case None =>
            println(s"**** no candidate found for assessment evaluation")
            Future.successful(None)
        }
      case _ =>
        Logger.warn("Passmark settings are not set for all schemes")
        Future.successful(None)
    }
  }

  def evaluateAssessmentCandidate(onlineTestWithAssessmentCentreScores: OnlineTestEvaluationAndAssessmentCentreScores,
                                  config: AssessmentEvaluationMinimumCompetencyLevel
                                  ): Future[Unit] = {

    val onlineTestEvaluation = onlineTestWithAssessmentCentreScores.onlineTestEvaluation
    //scalastyle:off
    println("**** <<<<<START EVALUATE CANDIDATE>>>>>")
    println(s"**** onlineTestEvaluation = $onlineTestEvaluation")
    val assessmentScores = onlineTestWithAssessmentCentreScores.assessmentScores
    println(s"**** assessmentScores = $assessmentScores")

    val assessmentEvaluation = passmarkRulesEngine.evaluate(onlineTestEvaluation, assessmentScores, config)

    println(s"**** assessmentEvaluation = $assessmentEvaluation")
    val applicationStatus = determineStatus(assessmentEvaluation)
    println(s"**** overall applicationStatus = $applicationStatus")

    aRepository.saveAssessmentScoreEvaluation(
      assessmentScores.scores.applicationId,
      assessmentScores.passmark.info.get.version, assessmentEvaluation, applicationStatus
    ).map { _ =>
      auditNewStatus(assessmentScores.scores.applicationId, applicationStatus)
    }
  }

  def processNextAssessmentCentrePassedOrFailedApplication(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    aRepository.nextAssessmentCentrePassedOrFailedApplication().flatMap {
      case Some(application) =>
        Logger.debug(s"processAssessmentCentrePassedOrFailedApplication() with application id [${application.applicationId}] " +
          s"and status [${application.applicationStatus}]")
        for {
          emailAddress <- candidateEmailAddress(application.userId)
          _ <- emailCandidate(application, emailAddress)
          _ <- commitNotifiedStatus(application)
        } yield ()
      case None => Future.successful(())
    }
  }

  private def auditNewStatus(appId: String, newStatus: ApplicationStatuses.EnumVal): Unit = {
    val event = newStatus match {
      case ApplicationStatuses.AssessmentCentrePassedNotified => "ApplicationAssessmentPassedNotified"
      case ApplicationStatuses.AssessmentCentreFailedNotified => "ApplicationAssessmentFailedNotified"
      case ApplicationStatuses.AssessmentCentreFailed | ApplicationStatuses.AssessmentCentrePassed |
        ApplicationStatuses.AwaitingAssessmentCentreReevaluation => "ApplicationAssessmentEvaluated"
    }
    Logger.info(s"$event for $appId. New application status = $newStatus")
    auditService.logEventNoRequest(event, Map("applicationId" -> appId, "applicationStatus" -> newStatus))
  }

  private[applicationassessment] def emailCandidate(
    application: ApplicationForNotification,
    emailAddress: String
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {

    application.applicationStatus match {
      case ApplicationStatuses.AssessmentCentrePassed =>
        emailClient.sendAssessmentCentrePassed(emailAddress, application.preferredName).map { _ =>
          auditNotified("AssessmentCentrePassedEmailed", application, Some(emailAddress))
        }
      case ApplicationStatuses.AssessmentCentreFailed =>
        emailClient.sendAssessmentCentreFailed(emailAddress, application.preferredName).map { _ =>
          auditNotified("AssessmentCentreFailedEmailed", application, Some(emailAddress))
        }
      case _ =>
        Logger.warn(s"We cannot send email to candidate for application [${application.applicationId}] because its status is " +
          s"[${application.applicationStatus}].")
        Future.failed(IncorrectStatusInApplicationException(
          "Application should have been in ASSESSMENT_CENTRE_FAILED or ASSESSMENT_CENTRE_PASSED status"
        ))
    }
  }

  private def commitNotifiedStatus(application: ApplicationForNotification): Future[Unit] = {
    val notifiedStatus = if (application.applicationStatus == ApplicationStatuses.AssessmentCentreFailed) {
      ApplicationStatuses.AssessmentCentreFailedNotified
    } else {
      ApplicationStatuses.AssessmentCentrePassedNotified
    }

    aRepository.updateStatus(application.applicationId, notifiedStatus).map { _ =>
      auditNewStatus(application.applicationId, notifiedStatus)
    }
  }
  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  private def auditNotified(event: String, application: ApplicationForNotification, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user ${application.userId}")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> application.userId) ++ emailAddress.map("email" -> _).toMap
    )
  }
}
