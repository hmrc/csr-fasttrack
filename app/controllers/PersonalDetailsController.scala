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

import model.Commands._
import model.Exceptions.{ CannotUpdateContactDetails, CannotUpdateRecord, ContactDetailsNotFound, PersonalDetailsNotFound }
import model.PersistedObjects.{ ContactDetails, PersonalDetails }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.application.PersonalDetailsService
import model.persisted.PersonalDetails
import repositories._
import repositories.application.PersonalDetailsRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object PersonalDetailsController extends PersonalDetailsController {
  val personalDetailsService = PersonalDetailsService
}

trait PersonalDetailsController extends BaseController {

  import model.Commands.Implicits._

  def personalDetailsService: PersonalDetailsService

  def personalDetails(userId: String, applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UpdateGeneralDetails] { req =>
      val personalDetails = PersonalDetails(req.firstName, req.lastName, req.preferredName, req.dateOfBirth, req.aLevel,
        req.stemLevel, req.civilServant, req.department)
      val contactDetails = ContactDetails(req.address, req.postCode, req.email, req.phone)

      personalDetailsService.update(userId, applicationId, personalDetails, contactDetails).map { _ =>
        Created
      } recover {
        case e: CannotUpdateContactDetails => BadRequest(s"cannot update contact details for user: ${e.userId}")
        case e: CannotUpdateRecord => BadRequest(s"cannot update personal details record with applicationId: ${e.applicationId}")
      }
    }
  }

  def find(userId: String, applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    personalDetailsService.find(userId, applicationId) map { details =>
      Ok(Json.toJson(details))
    } recover {
      case e: ContactDetailsNotFound => NotFound(s"cannot find contact details for userId: ${e.userId}")
      case e: PersonalDetailsNotFound => NotFound(s"cannot find personal details for applicationId: ${e.applicationId}")
    }
  }
}
