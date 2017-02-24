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

import model.Exceptions.{ AssistanceDetailsNotFound, CannotUpdateAssistanceDetails }
import model.exchange.AssistanceDetails
import play.api.libs.json.Json
import play.api.mvc.Action
import services.AuditService
import services.assistancedetails.AssistanceDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AssistanceDetailsController extends AssistanceDetailsController {
  val assistanceDetailsService = AssistanceDetailsService
  val auditService = AuditService
}

trait AssistanceDetailsController extends BaseController {
  val assistanceDetailsService: AssistanceDetailsService
  val auditService: AuditService

  def update(userId: String, applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[AssistanceDetails] { req =>
      (for {
        _ <- assistanceDetailsService.update(applicationId, userId, req)
      } yield {
        auditService.logEvent("AssistanceDetailsSaved")
        Created
      }).recover {
        case e: CannotUpdateAssistanceDetails => BadRequest(s"cannot update assistance details for user: ${e.userId}")
      }
    }
  }

  def find(userId: String, applicationId: String) = Action.async { implicit request =>
    (for {
      ad <- assistanceDetailsService.find(applicationId, userId)
    } yield {
      Ok(Json.toJson(ad))
    }).recover {
      case e: AssistanceDetailsNotFound => NotFound(s"cannot find assistance details for application: ${e.id}")
    }
  }
}
