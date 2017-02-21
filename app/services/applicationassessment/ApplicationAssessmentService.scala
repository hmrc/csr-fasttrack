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
import model.ApplicationStatuses
import model.ApplicationStatuses.BSONEnumHandler
import model.AssessmentEvaluationCommands.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import model.EvaluationResults._
import model.Exceptions.IncorrectStatusInApplicationException
import model.PersistedObjects.ApplicationForNotification
import play.api.Logger
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import repositories._
import services.AuditService
import services.evaluation.AssessmentCentrePassmarkRulesEngine
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ApplicationAssessmentService extends ApplicationAssessmentService {

  val appAssessRepository = applicationAssessmentRepository
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

trait ApplicationAssessmentService extends ApplicationStatusCalculator {

  implicit def headerCarrier = new HeaderCarrier()

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appAssessRepository: AssessmentCentreAllocationRepository
  val otRepository: OnlineTestRepository
  val aRepository: GeneralApplicationRepository
  val aasRepository: ApplicationAssessmentScoresRepository
  val fpRepository: FrameworkPreferenceRepository
  val cdRepository: ContactDetailsRepository

  val emailClient: EmailClient

  val auditService: AuditService
  val passmarkService: AssessmentCentrePassMarkSettingsService
  val passmarkRulesEngine: AssessmentCentrePassmarkRulesEngine

  def removeFromApplicationAssessmentSlot(applicationId: String): Future[Unit] = {

    appAssessRepository.delete(applicationId).flatMap { _ =>

      auditService.logEventNoRequest("ApplicationAssessmentDeleted", Map( "applicationId" -> applicationId ))

      otRepository.removeCandidateAllocationStatus(applicationId).map { _ =>
        auditService.logEventNoRequest("ApplicationDeallocated", Map( "applicationId" -> applicationId ))
      }
    }
  }

  def deleteApplicationAssessment(applicationId: String): Future[Unit] = {

    appAssessRepository.delete(applicationId).map { _ =>
      auditService.logEventNoRequest("ApplicationAssessmentDeleted", Map( "applicationId" -> applicationId ))
    }
  }

  def nextAssessmentCandidateReadyForEvaluation: Future[Option[OnlineTestEvaluationAndAssessmentCentreScores]] = {
    passmarkService.getLatestVersion.flatMap {
      case passmark if passmark.schemes.forall(_.overallPassMarks.isDefined) =>
        aRepository.nextApplicationReadyForAssessmentScoreEvaluation(passmark.info.get.version).flatMap {
          case Some(appId) =>
            for {
              scoresOpt <- aasRepository.tryFind(appId)
              prefsWithQualificationsOpt <- fpRepository.tryGetPreferencesWithQualifications(appId)
              otEvaluation <- otRepository.findPassmarkEvaluation(appId)
            } yield {
              for {
                scores <- scoresOpt
                prefsWithQualifications <- prefsWithQualificationsOpt
              } yield {
                val assessmentResult = AssessmentPassmarkPreferencesAndScores(passmark, prefsWithQualifications, scores)
                OnlineTestEvaluationAndAssessmentCentreScores(otEvaluation, assessmentResult)
              }
            }
          case None => Future.successful(None)
        }
      case _ =>
        Logger.warn("Passmark settings are not set for all schemes")
        Future.successful(None)
    }
  }

  def evaluateAssessmentCandidate(onlineTestWithAssessmentCentreScores: OnlineTestEvaluationAndAssessmentCentreScores,
                                  config: AssessmentEvaluationMinimumCompetencyLevel): Future[Unit] = {
    val onlineTestEvaluation = onlineTestWithAssessmentCentreScores.onlineTestEvaluation
    val assessmentScores = onlineTestWithAssessmentCentreScores.assessmentScores
    val assessmentEvaluation = passmarkRulesEngine.evaluate(onlineTestEvaluation, assessmentScores, config)
    val applicationStatus = determineStatus(assessmentEvaluation)

    aRepository.saveAssessmentScoreEvaluation(assessmentScores.scores.applicationId,
      assessmentScores.passmark.info.get.version, assessmentEvaluation, applicationStatus).map { _ =>
      auditNewStatus(assessmentScores.scores.applicationId, applicationStatus)
    }
  }

  def processNextAssessmentCentrePassedOrFailedApplication: Future[Unit] = {
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
    Logger.info(s"$event for $appId. The new status: $newStatus")
    auditService.logEventNoRequest( event, Map("applicationId" -> appId, "applicationStatus" -> newStatus)
    )
  }

  private[applicationassessment] def emailCandidate(application: ApplicationForNotification, emailAddress: String): Future[Unit] = {

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
