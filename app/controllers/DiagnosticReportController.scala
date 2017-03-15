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

import akka.stream.scaladsl.Source
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.{ Action, AnyContent }
import model.PersistedObjects.Implicits.candidateTestReportFormats
import model.CandidateScoresCommands.Implicits.CandidateScoresAndFeedbackFormats
import play.api.Logger
import play.api.libs.streams.Streams
import repositories._
import repositories.application.DiagnosticReportingRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object DiagnosticReportController extends DiagnosticReportController {
  val drRepository: DiagnosticReportingRepository = diagnosticReportRepository
  val trRepository: TestReportRepository = testReportRepository
  val assessmentScoresRepo: ApplicationAssessmentScoresRepository = applicationAssessmentScoresRepository
}

trait DiagnosticReportController extends BaseController {

  val drRepository: DiagnosticReportingRepository
  val trRepository: TestReportRepository
  val assessmentScoresRepo: ApplicationAssessmentScoresRepository

  def getApplicationByApplicationId(applicationId: String): Action[AnyContent] = Action.async { implicit request =>

    def mergeJsonObjects(application: JsObject, other: Option[JsObject]): JsObject = other match {
      case Some(o) => application ++ o
      case None => application
    }

    (for {
      application <- drRepository.findByApplicationId(applicationId)
      testResults <- trRepository.getReportByApplicationId(applicationId)
      assessmentScores <- assessmentScoresRepo.tryFind(applicationId)
    } yield {

      val testResultsJson = testResults.map { tr =>
        val results = Json.toJson(tr)
        Json.obj("online-test-results" -> results)
      }
      val assessmentCentreResultsJson = assessmentScores.map { ar =>
        val results = Json.toJson(ar).as[JsObject]
        Json.obj("assessment-centre-results" -> results)
      }

      val fullJson = mergeJsonObjects(mergeJsonObjects(application, testResultsJson), assessmentCentreResultsJson)

      Ok(Json.toJson(fullJson))
    }) recover {
      case _ => NotFound
    }
  }

  def getAllApplications = Action { implicit request =>
    val response = Source.fromPublisher(Streams.enumeratorToPublisher(drRepository.findAll()))
    Ok.chunked(response)
  }
}
