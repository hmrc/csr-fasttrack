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

import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.CandidateScoresCommands.Implicits._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.applicationassessment.AssessmentCentreService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object CandidateScoresController extends CandidateScoresController {
  val assessmentCentreService = AssessmentCentreService
}

trait CandidateScoresController extends BaseController {
  def assessmentCentreService: AssessmentCentreService

  def getCandidateScores(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreService.getCandidateScores(applicationId).map(scores =>  Ok(Json.toJson(scores)))
  }

  def createCandidateScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CandidateScoresAndFeedback] { candidateScoresAndFeedback =>
      assessmentCentreService.saveScoresAndFeedback(applicationId, candidateScoresAndFeedback).map { _ =>
        Created
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
      }
    }
  }

  def acceptCandidateScoresAndFeedback(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreService.acceptScoresAndFeedback(applicationId).map( _ => Ok )
  }
}
