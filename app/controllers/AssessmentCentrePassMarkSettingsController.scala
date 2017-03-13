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

package controllers

import model.persisted.AssessmentCentrePassMarkSettings
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import services.AuditService
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentCentrePassMarkSettingsController extends AssessmentCentrePassMarkSettingsController {
  val assessmentCentrePassMarkRepository = assessmentCentrePassMarkSettingsRepository
  val assessmentCentrePassmarkService = AssessmentCentrePassMarkSettingsService
  val auditService = AuditService
}

trait AssessmentCentrePassMarkSettingsController extends BaseController {
  val assessmentCentrePassMarkRepository: AssessmentCentrePassMarkSettingsRepository
  val assessmentCentrePassmarkService: AssessmentCentrePassMarkSettingsService
  val auditService: AuditService

  def getLatestVersion = Action.async { implicit request =>
    assessmentCentrePassmarkService.getLatestVersion.map { passmarkOpt =>
      val passmark = passmarkOpt.map(_.toPassmarkResponse).getOrElse(AssessmentCentrePassMarkSettings.EmptyPassmarkResponse)
      Ok(Json.toJson(passmark))
    }
  }

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[AssessmentCentrePassMarkSettings] { settings =>
      assessmentCentrePassMarkRepository.create(settings).map { _ =>
        auditService.logEvent("AssessmentCentrePassMarkSettingsCreated", Map(
          "Version" -> settings.info.version,
          "CreatedByUserId" -> settings.info.createdByUser,
          "StoredCreateDate" -> settings.info.createDate.toString
        ))
        Created
      }
    }
  }
}
