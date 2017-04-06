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
import model.PersistedObjects.ContactDetailsWithId
import model.ReportExchangeObjects.AssessmentCentreIndicatorReport
import model.report.AssessmentCentreIndicatorReportItem
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.{ Action, AnyContent }
import model.PersistedObjects.Implicits.candidateTestReportFormats
import play.api.Logger
import play.api.libs.streams.Streams
import repositories._
import repositories.application.DiagnosticReportingRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DiagnosticReportController extends DiagnosticReportController {
  val drRepository: DiagnosticReportingRepository = diagnosticReportRepository
  val trRepository: TestReportRepository = testReportRepository
  val assessorAssessmentScoresRepo: ApplicationAssessmentScoresRepository = assessorAssessmentScoresRepository
  val reviewerAssessmentScoresRepo: ApplicationAssessmentScoresRepository = reviewerAssessmentScoresRepository
}

trait DiagnosticReportController extends BaseController {

  val drRepository: DiagnosticReportingRepository
  val trRepository: TestReportRepository
  val assessorAssessmentScoresRepo: ApplicationAssessmentScoresRepository
  val reviewerAssessmentScoresRepo: ApplicationAssessmentScoresRepository

  def getApplicationByApplicationId(applicationId: String): Action[AnyContent] = Action.async { implicit request =>

    def mergeJsonObjects(application: JsObject, other: Option[JsObject]): JsObject = other match {
      case Some(o) => application ++ o
      case None => application
    }

    (for {
      application <- drRepository.findByApplicationId(applicationId)
      testResults <- trRepository.getReportByApplicationId(applicationId)
      assessorAssessmentScores <- assessorAssessmentScoresRepo.tryFind(applicationId)
      reviewerAssessmentScores <- reviewerAssessmentScoresRepo.tryFind(applicationId)
    } yield {

      val testResultsJson = testResults.map { tr =>
        val results = Json.toJson(tr)
        Json.obj("online-test-results" -> results)
      }

      val assessorAssessmentCentreResultsJson = assessorAssessmentScores.map { ar =>
        val results = Json.toJson(ar).as[JsObject]
        Json.obj("assessor-assessment-centre-results" -> results)
      }

      val reviewerAssessmentCentreResultsJson = reviewerAssessmentScores.map { ar =>
        val results = Json.toJson(ar).as[JsObject]
        Json.obj("quality-controlled-assessment-centre-results" -> results)
      }

      val fullJson = mergeJsonObjects(
        mergeJsonObjects(mergeJsonObjects(application, testResultsJson), assessorAssessmentCentreResultsJson),
        reviewerAssessmentCentreResultsJson
      )

      Ok(Json.toJson(fullJson))
    }) recover {
      case _ => NotFound
    }
  }

  def getAllApplications = Action { implicit request =>
    val response = Source.fromPublisher(Streams.enumeratorToPublisher(drRepository.findAll()))
    Ok.chunked(response)
  }

  private val assessmentCentreIndicatorHeaders = "ApplicationId, UserId, ApplicationStatus, Area, AssessmentCentre, Postcode"

  def getAllUsersAssessmentCentreIndicatorReport = Action.async { implicit request =>
    val applicationsFut = reportingRepository.assessmentCentreIndicatorReport
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.map(cd => cd.userId -> cd).toMap)
    val reportFut: Future[String] = for {
      allApplications <- applicationsFut
      allContactDetails <- allContactDetailsFut
    } yield {
      val data = buildAssessmentCentreIndicatorReportRows(allApplications, allContactDetails)

      data.map { row =>
        makeRow(
          row.applicationId,
          Some(row.userId),
          row.applicationStatus,
          row.assessmentCentreIndicator.map(_.area),
          row.assessmentCentreIndicator.map(_.assessmentCentre),
          row.postcode
        )
      }.mkString("\n")
    }
    reportFut.map { csvData =>
      val csvReport = assessmentCentreIndicatorHeaders + "\n" + csvData
      Ok(csvReport)
    }
  }

  private def makeRow(values: Option[String]*) =
    values.map { s =>
      val ret = s.getOrElse(" ").replace("\r", " ").replace("\n", " ").replace("\"", "'")
      "\"" + ret + "\""
    }.mkString(",")

  private def buildAssessmentCentreIndicatorReportRows(allApplications: List[AssessmentCentreIndicatorReport],
                                                       allContactDetails: Map[String, ContactDetailsWithId])
  : List[AssessmentCentreIndicatorReportItem] = {
    allApplications.map { application =>

      val postcode = allContactDetails.get(application.userId).flatMap { contactDetails =>
        contactDetails.postCode
      }

      AssessmentCentreIndicatorReportItem(
        applicationId = application.applicationId,
        userId = application.userId,
        applicationStatus = application.applicationStatus,
        assessmentCentreIndicator = application.assessmentCentreIndicator,
        postcode = postcode
      )
    }
  }
}
