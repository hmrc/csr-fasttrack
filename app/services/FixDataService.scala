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
import model.ApplicationStatuses
import model.EvaluationResults.Green
import model.Exceptions.{ InvalidStatusException, PassMarkSettingsNotFound }
import model.persisted.SchemeEvaluationResult
import play.api.mvc.RequestHeader
import repositories._
import repositories.application._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FixDataService extends FixDataService {
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository = onlineTestPassMarkSettingsRepository
  val auditService = AuditService
  val progressToAssessmentCentreConfig = config.MicroserviceAppConfig.progressToAssessmentCentreConfig
}

trait FixDataService {
  def appRepo: GeneralApplicationRepository
  def passmarkSettingsRepo: OnlineTestPassMarkSettingsRepository
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
}
