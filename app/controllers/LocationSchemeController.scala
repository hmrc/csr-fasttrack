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

import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.locationschemes.LocationSchemeService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object LocationSchemeController extends LocationSchemeController {
  val locationSchemeService = LocationSchemeService
}

trait LocationSchemeController extends BaseController {
  def locationSchemeService: LocationSchemeService

  def getSchemesAndLocationsByEligibility(hasALevels: Boolean, hasStemALevels: Boolean,
                                                    latitude: Option[Double], longitude: Option[Double]): Action[AnyContent] =
    Action.async { implicit request =>
    locationSchemeService.getSchemesAndLocationsByEligibility(hasALevels, hasStemALevels,
      latitude, longitude).map(r => Ok(Json.toJson(r)))
  }

  def getAvailableSchemesInSelectedLocations(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    locationSchemeService.getAvailableSchemesInSelectedLocations(applicationId).map(r => Ok(Json.toJson(r)))
  }

  def getSchemeLocations(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
        locationSchemeService.getSchemeLocations(applicationId).map { locations => Ok(Json.toJson(locations)) }
    }

  def updateSchemeLocations(applicationId: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[List[String]] { locationIds =>
        locationSchemeService.updateSchemeLocations(applicationId, locationIds).map { _ => Ok }
      }
    }

  def getSchemes(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      locationSchemeService.getSchemes(applicationId).map { schemes => Ok(Json.toJson(schemes)) }
    }

  def updateSchemes(applicationId: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[List[String]] { schemeNames =>
        locationSchemeService.updateSchemes(applicationId, schemeNames).map { _ => Ok }
      }
    }
}
