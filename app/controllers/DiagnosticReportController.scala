/*
 * Copyright 2016 HM Revenue & Customs
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

import model.PersistedObjects
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories._
import repositories.application.DiagnosticReportingRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object DiagnosticReportController extends DiagnosticReportController {
  val drRepository: DiagnosticReportingRepository = diagnosticReportRepository
}

trait DiagnosticReportController extends BaseController {
  import PersistedObjects.Implicits._

  val drRepository: DiagnosticReportingRepository

  def getUserById(userId: String) = Action.async { implicit request =>
    val applicationUser = drRepository.findByUserId(userId)

    applicationUser.map { au =>
      Ok(Json.toJson(au))
    } recover {
      case e => NotFound
    }
  }
}
