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

package services

import config.DataFixupConfig
import connectors.{ CSREmailClient, EmailClient }
import controllers.OnlineTestExtension
import model.{ ApplicationStatuses, ProgressStatuses }
import model.EvaluationResults.Green
import model.Exceptions.{ ApplicationNotFound, InvalidStatusException, PassMarkSettingsNotFound }
import model.persisted.SchemeEvaluationResult
import play.api.libs.json.{ JsString, JsValue, Json }
import play.api.mvc.{ RequestHeader, Result, Results }
import repositories._
import repositories.application._
import services.onlinetesting.OnlineTestExtensionService
import play.api.mvc.Results._
import services.FixDataService.ApplicationHasAlreadyBeenEmailedException
import services.applicationassessment.{ AssessorAssessmentCentreScoresService, AssessorAssessmentScoresService }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object FixDataService extends FixDataService {
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository = onlineTestPassMarkSettingsRepository
  val onlineTestingRepo: OnlineTestRepository = onlineTestRepository
  val onlineTestExtensionService: OnlineTestExtensionService = OnlineTestExtensionService
  val assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository = assessorAssessmentScoresRepository
  val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = assessmentCentreAllocationRepository
  val auditService: AuditService.type = AuditService
  val progressToAssessmentCentreConfig: DataFixupConfig = config.MicroserviceAppConfig.progressToAssessmentCentreConfig
  val emailClient: CSREmailClient.type = CSREmailClient
  val cdRepo: ContactDetailsRepository = contactDetailsRepository

  case class ApplicationHasAlreadyBeenEmailedException(message: String) extends Exception(message)
}

trait FixDataService {
  def appRepo: GeneralApplicationRepository
  def passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository
  def onlineTestingRepo: OnlineTestRepository
  def onlineTestExtensionService: OnlineTestExtensionService
  def assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository
  def assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository
  def auditService: AuditService
  def progressToAssessmentCentreConfig: DataFixupConfig
  def emailClient: EmailClient
  def cdRepo: ContactDetailsRepository

  def progressToAssessmentCentre(appId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    if (progressToAssessmentCentreConfig.isValid(appId)) {
      for {
        latestPassmarkSettings <- passmarkSettingsRepo.tryGetLatestVersion()
        schemes <- appRepo.getSchemes(appId)
        fakeSchemeEvaluation = schemes.map { s => SchemeEvaluationResult(s, Green) }
        version = latestPassmarkSettings.getOrElse(throw new PassMarkSettingsNotFound).version
        _ <- appRepo.progressToAssessmentCentre(appId, fakeSchemeEvaluation, version)
      } yield {
        auditService.logEvent("CandidatePromotedToAwaitingAllocation", Map("applicationId" -> appId))
      }
    } else {
      Future.failed(new IllegalArgumentException(s"$appId does not match configured application Id for this action"))
    }
  }

  def extendExpiredOnlineTests(appId: String, extendDays: Int)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Result] = {
      onlineTestingRepo.getOnlineTestApplication(appId).flatMap {
        case Some(onlineTestApp) =>
          onlineTestExtensionService.extendExpiryTimeForExpiredTests(onlineTestApp, extendDays).map { _ => Ok }
        case _ => Future.successful(NotFound)
      }
  }

  def countNoDateScoresAndFeedback(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[String]] = {
    assessmentScoresRepo.noDateScoresAndFeedback.map { resultList =>
      ("Count: " + resultList.length) :: resultList
    }
  }

  def fixNoDateScoresAndFeedback(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[String]] = {
    assessmentScoresRepo.noDateScoresAndFeedback.map { brokenSandFList =>
      brokenSandFList.map { applicationId =>
        assessmentScoresRepo.fixNoDateScoresAndFeedback(applicationId)
      }
      ("Count: " + brokenSandFList.length) :: brokenSandFList
    }
  }

  def forcePassmarkReevaluationForOnlineTestComplete(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    onlineTestingRepo.getOnlineTestApplication(applicationId).map { testRecord =>
      if (testRecord.get.applicationStatus == ApplicationStatuses.OnlineTestCompleted) {
        onlineTestingRepo.findPassmarkEvaluation(applicationId).map { passmarkEvaluation =>
          onlineTestingRepo.savePassMarkScore(applicationId, "NOVERSION", passmarkEvaluation.result, None).map(_ => ())
        }
      } else {
          throw new Exception("User was not in the required state")
      }
    }
  }

  def findAdminWithdrawnApplicationsNotEmailed()(implicit hc: HeaderCarrier, rh: RequestHeader): Future[List[String]] = {
    appRepo.findAdminWithdrawnApplicationsNotEmailed
  }

  def emailAdminWithdrawnApplicationNotEmailed(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      verifyUnemailed <- this.findAdminWithdrawnApplicationsNotEmailed().map(_.filter(_ == applicationId))
      _ = if (verifyUnemailed.isEmpty) throw ApplicationHasAlreadyBeenEmailedException(applicationId)
      candidateOpt <- appRepo.find(applicationId)
      candidate = candidateOpt.getOrElse(throw ApplicationNotFound(applicationId))
      contactDetails <- cdRepo.find(candidate.userId)
      _ <- emailClient.sendAdminWithdrawnEmail(contactDetails.email, candidate.nameForEmail)
      _ <- appRepo.markAdminWithdrawnApplicationAsEmailed(applicationId)
    } yield ()
  }

  def listCollections: Future[String] = {
    appRepo.listCollections.map(_.mkString("\n"))
  }

  def removeCollection(name: String): Future[Unit] = {
    appRepo.removeCollection(name)
  }

  def setAssessmentCentrePassedNotified(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      _ <- appRepo.updateStatus(applicationId, ApplicationStatuses.AssessmentCentrePassedNotified)
    } yield ()
  }

  def rollbackToAwaitingAllocationNotifiedFromFailedToAttend(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus])
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    for {
      _ <- assessmentScoresRepo.removeDocument(applicationId)
      _ <- assessmentCentreAllocationRepo.deleteNoCheck(applicationId)
      _ <- appRepo.removeProgressStatuses(applicationId, progressStatuses)
      _ <- appRepo.removeProgressStatusDates(applicationId, progressStatuses)
      _ <- appRepo.removeAllocationExpiryDateNoCheck(applicationId)
      _ <- appRepo.updateStatus(applicationId, ApplicationStatuses.AwaitingAllocationNotified)
    } yield ()
  }
}
