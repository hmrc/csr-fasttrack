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

import factories.DateTimeFactory
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ExerciseScoresAndFeedback }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.applicationassessment.{ AssessmentCentreScoresService, AssessmentCentreService, AssessorAssessmentScoresService, ReviewerAssessmentScoresService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object CandidateScoresController extends CandidateScoresController {
  val assessorAssessmentCentreService = AssessorAssessmentScoresService
  val reviewerAssessmentCentreService = ReviewerAssessmentScoresService
  val dateTimeFactory = DateTimeFactory
}

trait CandidateScoresController extends BaseController {
  val dateTimeFactory: DateTimeFactory

  def assessorAssessmentCentreService: AssessmentCentreScoresService
  def reviewerAssessmentCentreService: AssessmentCentreScoresService

  def getCandidateScores(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessorAssessmentCentreService.getCandidateScores(applicationId).map(scores => Ok(Json.toJson(scores)))
  }

  def saveExerciseScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExerciseScoresAndFeedback] { exerciseScoresAndFeedback =>
      val scoresAndFeedbackWithDate = exerciseScoresAndFeedback.scoresAndFeedback.copy(savedDate = Some(dateTimeFactory.nowLocalTimeZone))
      assessorAssessmentCentreService.saveScoresAndFeedback(applicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = scoresAndFeedbackWithDate)).map { _ =>
        Created
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
      }
    }
  }

  def submitExerciseScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExerciseScoresAndFeedback] { exerciseScoresAndFeedback =>
      val scoresAndFeedbackWithDate = exerciseScoresAndFeedback.scoresAndFeedback.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))
      assessorAssessmentCentreService.saveScoresAndFeedback(applicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = scoresAndFeedbackWithDate)).map { _ =>
      Created
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
      }
    }
  }

  def acceptCandidateScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CandidateScoresAndFeedback] { scoresAndFeedback =>
      assessorAssessmentCentreService.acceptScoresAndFeedback(applicationId, scoresAndFeedback).map { _ =>
        Ok
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
      }
    }
  }
}
