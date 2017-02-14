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

package services.onlinetesting

import connectors.EmailClient
import model.ApplicationStatuses
import model.PersistedObjects.ApplicationForNotification
import play.api.Logger
import repositories._
import repositories.application.OnlineTestRepository
import services.AuditService
import services.onlinetesting.OnlineTestResultServiceImpl._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

trait OnlineTestResultService {

  def nextCandidateReadyForSendingOnlineTestResult: Future[Option[ApplicationForNotification]]

  def notifyCandidateAboutOnlineTestResult(failedTest: ApplicationForNotification): Future[Unit]
}

class OnlineTestResultServiceImpl(otRepository: OnlineTestRepository,
                                  cdRepository: ContactDetailsRepository,
                                  emailClient: EmailClient,
                                  auditService: AuditService,
                                  newHeaderCarrier: => HeaderCarrier
                                 )(implicit executor: ExecutionContext) extends OnlineTestResultService {

  private implicit def headerCarrier = newHeaderCarrier

  override def nextCandidateReadyForSendingOnlineTestResult: Future[Option[ApplicationForNotification]] =
    otRepository.nextApplicationReadyForSendingOnlineTestResult

  override def notifyCandidateAboutOnlineTestResult(app: ApplicationForNotification): Future[Unit] = {
    val newStatus = app.applicationStatus match {
      case ApplicationStatuses.OnlineTestFailed => ApplicationStatuses.OnlineTestFailedNotified
      case ApplicationStatuses.AwaitingAllocation => ApplicationStatuses.AwaitingAllocationNotified
      case _ => throw new IllegalStateException(s"${app.applicationStatus} is not supported for notify candidate about online test result")
    }

    for {
      email <- cdRepository.find(app.userId).map(_.email)
      _ <- sendResultReadyNotificationEmail(app, email)
      _ <- commitNotifiedStatus(app, newStatus)
    } yield ()
  }

  private def sendResultReadyNotificationEmail(app: ApplicationForNotification, email: String): Future[Unit] = {
    emailClient.sendOnlineTestResultReady(email, app.preferredName).map { _ =>
      logAuditEvent(OnlineTestResultReadyEmailSent, app.userId, Some(email))
    }
  }

  private def commitNotifiedStatus(app: ApplicationForNotification, newStatus: ApplicationStatuses.EnumVal): Future[Unit] = {
    val updated = otRepository.updateStatus(
      app.userId,
      currentStatuses = List(
        ApplicationStatuses.OnlineTestFailed,
        ApplicationStatuses.AwaitingAllocation),
      newStatus
    )

    updated.map { _ =>
      logAuditEvent(OnlineTestResultReadyUpdated, app.userId)
    }
  }

  private def logAuditEvent(event: AuditEvent, userId: String, email: Option[String] = None) = {
    Logger.info(s"$event for user $userId")
    auditService.logEventNoRequest(event.toString,
      Map("userId" -> userId) ++ email.map(e => Map("email" -> e)).getOrElse(Map.empty)
    )
  }
}

object OnlineTestResultServiceImpl {

  sealed trait AuditEvent

  case object OnlineTestResultReadyEmailSent extends AuditEvent
  case object OnlineTestResultReadyUpdated extends AuditEvent

}