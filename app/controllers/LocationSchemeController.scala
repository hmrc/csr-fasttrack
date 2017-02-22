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

import model.Exceptions.{ LocationPreferencesNotFound, SchemePreferencesNotFound }
import model.Scheme.Scheme
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.locationschemes.LocationSchemeService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LocationSchemeController extends LocationSchemeController {
  val locationSchemeService = LocationSchemeService
}

trait LocationSchemeController extends BaseController {
  def locationSchemeService: LocationSchemeService

  def getEligibleSchemes(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.getEligibleSchemes(applicationId).map(r => Ok(Json.toJson(r)))
  }

  def getSchemes(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.getSchemes(applicationId).map {
      schemes => Ok(Json.toJson(schemes))
    }.recover {
      case ex: SchemePreferencesNotFound => NotFound("Schemes not found")
    }
  }

  def getAvailableSchemes: Action[AnyContent] = Action { implicit request =>
    Ok(Json.toJson(locationSchemeService.getAvailableSchemes))
  }

  def getEligibleSchemeLocations(
    applicationId: String,
    latitude: Option[Double], longitude: Option[Double]
  ): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.getEligibleSchemeLocations(applicationId, latitude, longitude).map(r => Ok(Json.toJson(r)))
  }

  def getSchemeLocations(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.getSchemeLocations(applicationId).map {
      locations => Ok(Json.toJson(locations))
    }.recover {
      case ex: LocationPreferencesNotFound => NotFound("Locations not found")
    }
  }

  def updateSchemeLocations(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[List[String]] { locationIds =>
      locationSchemeService.updateSchemeLocations(applicationId, locationIds).map { _ => Ok }
    }
  }

  def updateSchemes(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[List[Scheme]] { schemeNames =>
      locationSchemeService.updateSchemes(applicationId, schemeNames).map { _ => Ok }
    }
  }

  def removeSchemes(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.removeSchemes(applicationId).map { _ => Ok }
  }

  def removeSchemeLocations(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.removeSchemeLocations(applicationId).map { _ => Ok }
  }
}
