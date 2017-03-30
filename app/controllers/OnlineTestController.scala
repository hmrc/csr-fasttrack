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

import model.Exceptions.{ ApplicationNotFound, AssistanceDetailsNotFound, CannotUpdateCubiksTest, NotFoundException }
import model.{ ApplicationStatuses, Commands }
import model.exchange.CubiksTestResultReady
import play.api.Logger
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._
import repositories._
import repositories.application.{ AssistanceDetailsRepository, GeneralApplicationRepository, OnlineTestRepository }
import services.onlinetesting.{ OnlineTestExtensionService, OnlineTestService }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class OnlineTestStatus(status: ApplicationStatuses.EnumVal)


case class OnlineTestExtension(extraDays: Int)

case class UserIdWrapper(userId: String)

object OnlineTestController extends OnlineTestController {
  override val onlineTestingRepo: OnlineTestRepository = onlineTestRepository
  override val onlineTestingService: OnlineTestService = OnlineTestService
  override val onlineTestExtensionService: OnlineTestExtensionService = OnlineTestExtensionService
  override val onlineTestPDFReportRepo: OnlineTestPDFReportRepository = onlineTestPDFReportRepository
  override val assistanceDetailsRepo: AssistanceDetailsRepository = assistanceDetailsRepository
  override val generalApplicationRepository: GeneralApplicationRepository = applicationRepository
}

trait OnlineTestController extends BaseController {
  val onlineTestingRepo: OnlineTestRepository
  val onlineTestingService: OnlineTestService
  val onlineTestExtensionService: OnlineTestExtensionService
  val onlineTestPDFReportRepo: OnlineTestPDFReportRepository
  val assistanceDetailsRepo: AssistanceDetailsRepository
  val generalApplicationRepository: GeneralApplicationRepository

  val resetTestPermittedStatuses = List(
    ApplicationStatuses.OnlineTestInvited,
    ApplicationStatuses.OnlineTestStarted,
    ApplicationStatuses.OnlineTestExpired,
    ApplicationStatuses.OnlineTestFailed,
    ApplicationStatuses.OnlineTestCompleted,
    ApplicationStatuses.OnlineTestFailedNotified,
    ApplicationStatuses.AwaitingOnlineTestReevaluation,
    ApplicationStatuses.AwaitingAllocation,
    ApplicationStatuses.AwaitingAllocationNotified
  )

  import Commands.Implicits._

  def getOnlineTest(userId: String): Action[AnyContent] = Action.async { implicit request =>
    onlineTestingService.getOnlineTest(userId).map { onlineTest =>
      Ok(Json.toJson(onlineTest))
    } recover {
      case e =>
        Logger.debug(s"Cannot find online test for userId=$userId", e)
        NotFound
    }
  }

  def onlineTestStatusUpdate(userId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestStatus] { onlineTestStatus =>
      onlineTestingRepo.updateStatus(userId, onlineTestStatus.status).map { _ => Ok }
    }
  }

  def startOnlineTest(cubiksUserId: Int): Action[AnyContent] = Action.async { implicit request =>
    onlineTestingService.startOnlineTest(cubiksUserId).map { _ =>
      Ok
    } recover {
      case _: CannotUpdateCubiksTest =>
        Logger.warn(s"Online Test for cubiksUserId=$cubiksUserId cannot be updated")
        NotFound
      case _: NotFoundException =>
        Logger.warn(s"cubiksUserId=$cubiksUserId not found for Start Online Test")
        NotFound
    }
  }

  def completeOnlineTest(cubiksUserId: Int, assessmentId: Int): Action[AnyContent] = Action.async { implicit request =>
    (for {
      assistanceDetails <- assistanceDetailsRepo.find(cubiksUserId)
      isGis = assistanceDetails.isGis
      _ <- onlineTestingRepo.completeOnlineTest(cubiksUserId, assessmentId, isGis)
    } yield {
      Ok
    }).recover {
      case _: AssistanceDetailsNotFound =>
        Logger.warn(s"Assistance details for cubiksUserId=$cubiksUserId cannot be found in complete Online Test")
        NotFound
      case _: CannotUpdateCubiksTest =>
        Logger.warn(s"Online Test for cubiksUserId=$cubiksUserId cannot be updated in complete Online Test")
        NotFound
      case e: NotFoundException =>
        Logger.warn(s"Online Test for cubiksUserId=$cubiksUserId cannot be updated. Not found: ${e.getMessage}")
        NotFound
    }
  }

  def tryToDownloadOnlineTestReport(cubiksUserId: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CubiksTestResultReady] { cubiksTestResultReady =>
      onlineTestingService.tryToDownloadOnlineTestResult(cubiksUserId, cubiksTestResultReady).map(_ => Ok)
    } recover {
      case _: ApplicationNotFound =>
        Logger.warn(s"Assistance details for cubiksUserId=$cubiksUserId cannot be found in complete Online Test")
        NotFound
    }
  }

  /**
   * Note that this function will result with an ok even if the token is invalid.
   * This is done on purpose. We want to update the status of the user if the token is correct, but if for
   * any reason the token is wrong we still want to display the success page. The admin report will handle
   * potential errors
   *
   * @return
   */
  def completeOnlineTestByToken(token: String): Action[AnyContent] = Action.async { implicit request =>
    onlineTestingRepo.consumeToken(token).map(_ => Ok)
  }

  def resetOnlineTests(appId: String): Action[AnyContent] = Action.async { implicit request =>

    onlineTestingRepo.getOnlineTestApplication(appId).flatMap {
      case Some(onlineTestApp) =>
        if (resetTestPermittedStatuses.contains(onlineTestApp.applicationStatus)) {
          onlineTestingService.registerAndInviteApplicant(onlineTestApp).map { _ => Ok }
        } else {
          Future.successful(BadRequest(s"Cannot reset tests for candidate in state ${onlineTestApp.applicationStatus}"))
        }

      case _ => Future.successful(NotFound)
    }
  }

  def extendOnlineTests(appId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[OnlineTestExtension] { extension =>
      onlineTestingRepo.getOnlineTestApplication(appId).flatMap {
        case Some(onlineTestApp) =>
          onlineTestExtensionService.extendExpiryTime(onlineTestApp, extension.extraDays).map { _ => Ok }
        case _ => Future.successful(NotFound)
      }
    }
  }

  def getPDFReport(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      app <- generalApplicationRepository.findProgress(applicationId)
      reportOpt <- onlineTestPDFReportRepo.get(applicationId)
    } yield reportOpt match {
      case Some(report) if app.onlineTest.failedNotified || app.onlineTest.awaitingAllocationNotified =>
        Ok(report).as("application/pdf")
          .withHeaders(
            "Content-type" -> "application/pdf",
            "Content-Disposition" -> s"""attachment;filename="report-$applicationId.pdf""""
          )
      case _ => NotFound
    }
  }

  def resetStatusToContinueOnlineTests(applicationId: String): Action[AnyContent] = Action.async { implicit request =>
    generalApplicationRepository.resetStatusToContinueOnlineTests(applicationId).map(_ => Ok)
  }

}
