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

import model.Exceptions.PassMarkSettingsNotFound
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.play.microservice.controller.BaseController
import services.FixDataService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FixDataController extends FixDataController(FixDataService)

abstract class FixDataController(fixDataService: FixDataService) extends BaseController {

  def progressToAssessmentCentre(appId: String): Action[AnyContent] = Action.async { implicit request =>
    fixDataService.progressToAssessmentCentre(appId).map(_ => Ok)
      .recover {
        case _: PassMarkSettingsNotFound => InternalServerError("Pass Mark settings not found")
        case e: IllegalArgumentException => MethodNotAllowed(e.getMessage)
        case e => InternalServerError(e.getMessage)
      }
  }

  def extendExpiredOnlineTests(appId: String, extendDays: Int): Action[AnyContent] = Action.async { implicit request =>
    fixDataService.extendExpiredOnlineTests(appId, extendDays).map(_ => Ok)
  }

  def countNoDateScoresAndFeedback: Action[AnyContent] = Action.async { implicit request =>
    fixDataService.countNoDateScoresAndFeedback.map(Ok(_))
  }

  def fixNoDateScoresAndFeedback: Action[AnyContent] = Action.async { implicit request =>
    fixDataService.fixNoDateScoresAndFeedback.map(Ok(_))
  }

}
