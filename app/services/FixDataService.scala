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

package services

import config.DataFixupConfig
import controllers.OnlineTestExtension
import model.ApplicationStatuses
import model.EvaluationResults.Green
import model.Exceptions.{ InvalidStatusException, PassMarkSettingsNotFound }
import model.persisted.SchemeEvaluationResult
import play.api.libs.json.{ JsString, JsValue, Json }
import play.api.mvc.{ RequestHeader, Result, Results }
import repositories._
import repositories.application._
import services.onlinetesting.OnlineTestExtensionService
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.mvc.Results._
import services.applicationassessment.{ AssessorAssessmentCentreScoresService, AssessorAssessmentScoresService }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FixDataService extends FixDataService {
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository = onlineTestPassMarkSettingsRepository
  val onlineTestingRepo: OnlineTestRepository = onlineTestRepository
  val onlineTestExtensionService: OnlineTestExtensionService = OnlineTestExtensionService
  val assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository = assessorAssessmentScoresRepository
  val auditService = AuditService
  val progressToAssessmentCentreConfig = config.MicroserviceAppConfig.progressToAssessmentCentreConfig
}

trait FixDataService {
  def appRepo: GeneralApplicationRepository
  def passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository
  def onlineTestingRepo: OnlineTestRepository
  def onlineTestExtensionService: OnlineTestExtensionService
  def assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository
  def auditService: AuditService
  def progressToAssessmentCentreConfig: DataFixupConfig

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
}
