/*
 * Copyright 2016 HM Revenue & Customs
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

package mocks

import model.EvaluationResults._
import model.{ ApplicationStatuses, ReminderNotice }
import model.OnlineTestCommands.{ OnlineTestApplication, OnlineTestApplicationWithCubiksUser }
import model.PersistedObjects.{ ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest, OnlineTestPassmarkEvaluation }
import model.persisted.{ CubiksTestProfile, NotificationExpiringOnlineTest }
import org.joda.time.{ DateTime, LocalDate }
import repositories.application.OnlineTestRepository

import scala.collection.mutable
import scala.concurrent.Future

/**
  * @deprecated Please use Mockito
  */
case class TestableResult(result: RuleCategoryResult, version: String, applicationStatus: ApplicationStatuses.EnumVal)

/**
  * @deprecated Please use Mockito
  */
object OnlineIntegrationTestInMemoryRepository extends OnlineIntegrationTestInMemoryRepository

class OnlineIntegrationTestInMemoryRepository extends OnlineTestRepository {
  val inMemoryRepo = new mutable.HashMap[String, TestableResult]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] =
    Future.successful(Some(OnlineTestApplication("appId", ApplicationStatuses.Submitted, "userId", guaranteedInterview = false,
      needsAdjustments = false, "Test Preferred Name", None
    )))

  def getCubiksTestProfile(userId: String): Future[CubiksTestProfile] = Future.successful {
    val date = DateTime.now
    CubiksTestProfile(
      cubiksUserId = 123,
      participantScheduleId = 111,
      invitationDate = date,
      expirationDate = date.plusDays(7),
      onlineTestUrl = "http://www.google.co.uk",
      token = "222222",
      isOnlineTestEnabled = true
    )
  }

  def updateStatus(userId: String, status: ApplicationStatuses.EnumVal): Future[Unit] = Future.successful(Unit)

  def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit] = Future.successful(Unit)

  def consumeToken(token: String): Future[Unit] = Future.successful(Unit)

  def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: CubiksTestProfile): Future[Unit] =
    Future.successful(Unit)

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = Future.successful(None)

  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]] = Future.successful(None)

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def saveOnlineTestReport(applicationId: String, report: String): Future[Unit] = Future.successful(None)

  override def updateXMLReportSaved(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def updatePDFReportSaved(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = ???

  override def savePassMarkScore(applicationId: String, version: String, p: RuleCategoryResult,
    applicationStatus: ApplicationStatuses.EnumVal
  ): Future[Unit] = {
    inMemoryRepo += applicationId -> TestableResult(p, version, applicationStatus)
    Future.successful(())
  }

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, newVersion: String, p: RuleCategoryResult): Future[Unit] = {
    val updatedResult = inMemoryRepo(applicationId).copy(result = p, version = newVersion)
    inMemoryRepo += applicationId -> updatedResult
    Future.successful(())
  }

  override def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]] = Future.successful(None)

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]): Future[Unit] = Future.successful(())

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = ???

  def removeOnlineTestEvaluationAndReports(applicationId: String): Future[Unit] = ???

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation] = ???

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = ???

  def addReminderNotificationStatus(userId: String, notificationStatus: String): Future[Unit] = ???

  override def startOnlineTest(appId: String): Future[Unit] = Future.successful(())
}
