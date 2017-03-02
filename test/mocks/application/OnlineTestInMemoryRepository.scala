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

package mocks.application

import factories.UUIDFactory
import model.ApplicationStatuses.EnumVal
import model.{ ApplicationStatuses, Scheme }
import model.PersistedObjects._
import model.persisted.{ CubiksTestProfile, SchemeEvaluationResult }
import model.EvaluationResults._
import model.OnlineTestCommands.{ OnlineTestApplication, OnlineTestApplicationWithCubiksUser }
import model.PersistedObjects._
import model.Scheme.Scheme
import model.persisted.SchemeEvaluationResult
import org.joda.time.{ DateTime, LocalDate }
import repositories.application.OnlineTestRepository

import scala.collection.mutable
import scala.concurrent.Future

/**
 * @deprecated Please use Mockito
 */
object OnlineTestInMemoryRepository extends OnlineTestInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
class OnlineTestInMemoryRepository extends OnlineTestRepository {
  case class RuleCategoryResult(location1Scheme1: Result, location1Scheme2: Option[Result],
    location2Scheme1: Option[Result], location2Scheme2: Option[Result], alternativeScheme: Option[Result])

  val inMemoryRepo = new mutable.HashMap[String, RuleCategoryResult]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] =
    Future.successful(Some(OnlineTestApplication("appId", ApplicationStatuses.Submitted, "userId", guaranteedInterview = false,
      needsAdjustments = false, "Test Preferred Name", None)))

  def getCubiksTestProfile(userId: String): Future[CubiksTestProfile] = Future.successful {
    val date = DateTime.now
    CubiksTestProfile(
      cubiksUserId = 123,
      participantScheduleId = 111,
      invitationDate = date,
      expirationDate = date.plusDays(7),
      onlineTestUrl = "http://www.google.co.uk",
      token = UUIDFactory.generateUUID(),
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

  def nextApplicationReadyForSendingOnlineTestResult: Future[Option[ApplicationForNotification]] = Future.successful(None)

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = Future.successful(None)

  def saveOnlineTestReport(applicationId: String, report: String): Future[Unit] = Future.successful(None)

  override def updateXMLReportSaved(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def updatePDFReportSaved(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = ???

  override def savePassMarkScore(applicationId: String, version: String, evaluationResult: List[SchemeEvaluationResult],
                        applicationStatus: Option[ApplicationStatuses.EnumVal]) = {
    Future.successful(())
  }

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]): Future[Unit] = Future.successful(())

  override def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = Future.successful(())

  def removeOnlineTestEvaluationAndReports(applicationId: String): Future[Unit] = ???

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String,
    evaluationResult: List[SchemeEvaluationResult]): Future[Unit] = ???

  override def findPassmarkEvaluation(appId: String): Future[List[SchemeEvaluationResult]] = ???

  def addReminderNotificationStatus(userId: String, notificationStatus: String): scala.concurrent.Future[Unit] = ???

  def nextTestForReminder(reminder: model.ReminderNotice): scala.concurrent.Future[Option[model.persisted.NotificationExpiringOnlineTest]] = ???

  override def startOnlineTest(cubiksId: Int): Future[Unit] = Future.successful(())

  override def getCubiksTestProfile(cubiksUserId: Int): Future[CubiksTestProfile] = ???

  override def getCubiksTestProfileByToken(token: String): Future[CubiksTestProfile] = ???

  override def completeOnlineTest(cubiksUserId: Int, assessmentId: Int, isGis: Boolean): Future[Unit] = ???

  override def updateStatus(userId: String, currentStatuses: List[EnumVal], newStatus: EnumVal): Future[Unit] = ???

  override def findAllPassMarkEvaluations: Future[Map[String, List[SchemeEvaluationResult]]] = ???
}
