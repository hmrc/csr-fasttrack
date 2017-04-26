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
import model.AssessmentExercise
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ExerciseScoresAndFeedback }
import model.Exceptions.ApplicationNotFound
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.applicationassessment.AssessorAssessmentScoresService.{ AssessorScoresExistForExerciseException, ReviewerScoresExistForExerciseException }
import services.applicationassessment._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessorScoresController extends AssessorScoresController {
  val dateTimeFactory = DateTimeFactory
  val assessmentCentreScoresRemovalService: AssessmentCentreScoresRemovalService = AssessmentCentreScoresRemovalService
  val assessmentCentreService: AssessmentCentreService = AssessmentCentreService
  val assessmentCentreScoresService: AssessmentCentreScoresService = AssessorAssessmentScoresService
}

object ReviewerScoresController extends ReviewerScoresController {
  val assessmentCentreScoresService: AssessmentCentreScoresService = ReviewerAssessmentScoresService
  val assessmentCentreScoresRemovalService: AssessmentCentreScoresRemovalService = AssessmentCentreScoresRemovalService
  val assessmentCentreService: AssessmentCentreService = AssessmentCentreService
  val dateTimeFactory = DateTimeFactory
}

object EvaluatedAssessmentCentreScoresController extends EvaluatedAssessmentCentreScoresController {
  val assessmentCentreService: AssessmentCentreService = AssessmentCentreService
  val dateTimeFactory = DateTimeFactory
}

trait EvaluatedAssessmentCentreScoresController extends BaseController {
  val dateTimeFactory: DateTimeFactory
  def assessmentCentreService: AssessmentCentreService

  def getCompetencyAverageResult(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreService.getCompetencyAverageResult(applicationId).map {
      case None => NotFound(s"Competency average result for $applicationId could not be found")
      case Some(result) => Ok(Json.toJson(result))
    }
  }
}

trait AssessmentCentreScoresController extends BaseController {
  val dateTimeFactory: DateTimeFactory

  val assessmentCentreScoresService: AssessmentCentreScoresService
  val assessmentCentreScoresRemovalService: AssessmentCentreScoresRemovalService
  val assessmentCentreService: AssessmentCentreService

  def getCandidateScores(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreScoresService.getCandidateScores(applicationId).map(scores => Ok(Json.toJson(scores)))
  }

  def getCandidateScoresAndFeedback(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreScoresService.getCandidateScoresAndFeedback(applicationId).map(scores => Ok(Json.toJson(scores)))
  }

  def saveExerciseScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExerciseScoresAndFeedback] { exerciseScoresAndFeedback =>
      val scoresAndFeedbackWithDate = exerciseScoresAndFeedback.scoresAndFeedback.copy(savedDate = Some(dateTimeFactory.nowLocalTimeZone))
      assessmentCentreScoresService.saveScoresAndFeedback(applicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = scoresAndFeedbackWithDate)).map { _ =>
        Created
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
        case e: AssessorScoresExistForExerciseException => Conflict(e.getMessage)
        case e: ReviewerScoresExistForExerciseException => MethodNotAllowed(e.getMessage)
      }
    }
  }
}

trait AssessorScoresController extends AssessmentCentreScoresController {
  def submitExerciseScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ExerciseScoresAndFeedback] { exerciseScoresAndFeedback =>
      val scoresAndFeedbackWithDate = exerciseScoresAndFeedback.scoresAndFeedback.copy(submittedDate = Some(dateTimeFactory.nowLocalTimeZone))
      assessmentCentreScoresService.saveScoresAndFeedback(applicationId,
        exerciseScoresAndFeedback.copy(scoresAndFeedback = scoresAndFeedbackWithDate)).map { _ =>
      Created
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
        case e: AssessorScoresExistForExerciseException => Conflict(e.getMessage)
        case e: ReviewerScoresExistForExerciseException => MethodNotAllowed(e.getMessage)
      }
    }
  }
}

trait ReviewerScoresController extends AssessmentCentreScoresController {
  def acceptCandidateScoresAndFeedback(applicationId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CandidateScoresAndFeedback] { scoresAndFeedback =>
      assessmentCentreScoresService.acceptScoresAndFeedback(applicationId, scoresAndFeedback).map { _ =>
        Ok
      }.recover {
        case e: IllegalStateException => BadRequest(s"${e.getMessage} for applicationId $applicationId")
      }
    }
  }

  def unlockExercise(applicationId: String, exercise: String): Action[AnyContent] = Action.async { implicit request =>
    assessmentCentreScoresRemovalService.removeScoresAndFeedback(applicationId, AssessmentExercise.withName(exercise)).map { _ =>
      Ok
    }.recover {
      case e: NoSuchElementException => BadRequest(s"No such exercise '$exercise'")
      case e: ApplicationNotFound => BadRequest(s"No such application '$applicationId'")
    }
  }
}
