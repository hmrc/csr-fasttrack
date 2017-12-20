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
import model.{ ApplicationStatuses, ReminderNotice }
import model.PersistedObjects.ExpiringOnlineTest
import model.persisted.NotificationExpiringOnlineTest
import play.api.Logger
import repositories._
import repositories.application.OnlineTestRepository
import services.AuditService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

trait OnlineTestExpiryService {
  def processNextExpiredTest(): Future[Unit]
  def processExpiredTest(expiringTest: ExpiringOnlineTest): Future[Unit]
  def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String): Future[Unit]
  def commitExpiredStatus(expiringTest: ExpiringOnlineTest): Future[Unit]
  def processNextTestForReminder(reminder: model.ReminderNotice): Future[Unit]
}

class OnlineTestExpiryServiceImpl(
    otRepository: OnlineTestRepository,
    cdRepository: ContactDetailsRepository,
    emailClient: EmailClient,
    auditService: AuditService,
    newHeaderCarrier: => HeaderCarrier
)(implicit executor: ExecutionContext) extends OnlineTestExpiryService {

  private final val ExpiredStatus = ApplicationStatuses.OnlineTestExpired
  private implicit def headerCarrier = newHeaderCarrier

  override def processNextExpiredTest(): Future[Unit] =
    otRepository.nextApplicationPendingExpiry.flatMap {
      case Some(expiringTest) => processExpiredTest(expiringTest)
      case None => Future.successful(())
    }

  override def processExpiredTest(expiringTest: ExpiringOnlineTest): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- emailCandidate(expiringTest, emailAddress)
      _ <- commitExpiredStatus(expiringTest)
    } yield ()

  override def processNextTestForReminder(reminder: model.ReminderNotice): Future[Unit] =
    otRepository.nextTestForReminder(reminder).flatMap {
      case Some(expiringTest) => processReminder(expiringTest, reminder)
      case None => Future.successful(())
    }

  protected def processReminder(expiringTest: NotificationExpiringOnlineTest, reminder: ReminderNotice): Future[Unit] =
    for {
      emailAddress <- candidateEmailAddress(expiringTest.userId)
      _ <- otRepository.addReminderNotificationStatus(expiringTest.userId, reminder.notificationStatus)
      _ <- emailClient.sendExpiringReminder(reminder.template, emailAddress, expiringTest.preferredName, expiringTest.expiryDate)
      _ <- auditF(reminder.event, expiringTest.userId, Some(emailAddress))
    } yield ()

  override def emailCandidate(expiringTest: ExpiringOnlineTest, emailAddress: String): Future[Unit] =
    emailClient.sendOnlineTestExpired(emailAddress, expiringTest.preferredName).map { _ =>
      audit("ExpiredOnlineTestNotificationEmailed", expiringTest, Some(emailAddress))
    }

  override def commitExpiredStatus(expiringTest: ExpiringOnlineTest): Future[Unit] =
    otRepository.updateStatus(expiringTest.userId, ExpiredStatus).map { _ =>
      audit("ExpiredOnlineTest", expiringTest)
    }

  private def candidateEmailAddress(userId: String): Future[String] =
    cdRepository.find(userId).map(_.email)

  private def audit(event: String, expiringTest: ExpiringOnlineTest, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user ${expiringTest.userId}")
    auditService.logEventNoRequest(
      event,
      Map("userId" -> expiringTest.userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  private def auditF(event: String, userId: String, emailAddress: Option[String] = None): Future[Unit] = {
    Logger.info(s"$event for user $userId")
    Future.successful(auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    ))
  }
}
