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

import connectors.AssessmentScheduleExchangeObjects.LocationName
import model.ApplicationStatusOrder
import model.Commands._
import model.Exceptions.{ ApplicationNotFound, CannotUpdateReview }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent }
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.application.ApplicationService
import uk.gov.hmrc.play.microservice.controller.BaseController
import connectors.AssessmentScheduleExchangeObjects.Implicits.locationNameFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ApplicationController extends ApplicationController {
  val appRepository = applicationRepository
  val auditService = AuditService
  val applicationService = ApplicationService
}

trait ApplicationController extends BaseController {
  import Implicits._

  val appRepository: GeneralApplicationRepository
  val auditService: AuditService
  val applicationService: ApplicationService

  def createApplication = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateApplicationRequest] { applicationRequest =>
      appRepository.create(applicationRequest.userId, applicationRequest.frameworkId).map { result =>
        auditService.logEvent("ApplicationCreated")
        Ok(Json.toJson(result))
      }
    }
  }

  def applicationProgress(applicationId: String) = Action.async { implicit request =>
    appRepository.findProgress(applicationId).map { result =>
      Ok(Json.toJson(result))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
    }
  }

  def applicationStatus(applicationId: String) = Action.async { implicit request =>
    appRepository.findProgress(applicationId).map { result =>
      Ok(Json.toJson(ApplicationStatusOrder.getStatus(result)))
    }.recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for id: ${e.id}")
    }
  }

  def applicationStatusDetails(appId: String) = Action.async { implicit requst =>
    appRepository.findApplicationStatusDetails(appId).map { statusDetails =>
      Ok(Json.toJson(statusDetails))
    } recover {
      case _: ApplicationNotFound => NotFound(s"No application found for ID $appId")
    }
  }

  def findApplication(userId: String, frameworkId: String) = Action.async { implicit request =>
    appRepository.findByUserId(userId, frameworkId).map(result =>
      Ok(Json.toJson(result))).recover {
      case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
    }
  }

  def applicationWithdraw(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[WithdrawApplicationRequest] { withdrawRequest =>
      applicationService.withdraw(applicationId, withdrawRequest).map { _ =>
        Ok
      }.recover {
        case e: ApplicationNotFound => NotFound(s"cannot find application for user with id: ${e.id}")
      }
    }
  }

  def review(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[ReviewRequest] { _ =>
      appRepository.review(applicationId).map { _ =>
        auditService.logEvent("ApplicationReviewed")
        Ok
      }.recover {
        case e: CannotUpdateReview => NotFound(s"cannot find application with id: ${e.applicationId}")
      }
    }
  }

  def getAssessmentCentreIndicator(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    appRepository.findAssessmentCentreIndicator(applicationId) map {
      case Some(indicator) => Ok(Json.toJson(LocationName(indicator.assessmentCentre)))
      case None => NotFound("")
    }
  }
}
