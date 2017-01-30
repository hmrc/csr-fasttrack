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

package repositories

import java.util.UUID

import factories.DateTimeFactory
import model.{ AdjustmentDetail, ApplicationStatuses, ProgressStatuses }
import model.Adjustments._
import model.ApplicationStatuses._
import model.Exceptions.{ NotFoundException, OnlineTestFirstLocationResultNotFound, OnlineTestPassmarkEvaluationNotFound }
import model.OnlineTestCommands.{ OnlineTestApplicationWithCubiksUser, OnlineTestProfile }
import model.PersistedObjects.{ ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest, OnlineTestPassmarkEvaluation }
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.{ BSONArray, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{ GeneralApplicationMongoRepository, OnlineTestMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec
import model.EvaluationResults._
import reactivemongo.api.commands.WriteResult

class OnlineTestRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = CollectionNames.APPLICATION

  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  def onlineTestRepo = new OnlineTestMongoRepository(DateTimeFactory)

  "Next application ready for online testing" should {

    "return no application if there is only one application without adjustment needed but not submitted" in {

      createApplication("appId", "userId", "frameworkId", ApplicationStatuses.InProgress, needsAdjustment = false, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result mustBe (None)
    }

    "return no application if there is only one application with adjustment needed and not confirmed" in {
      createApplication("appId", "userId", "frameworkId", "SUBMITTED", needsAdjustment = true, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result mustBe (None)
    }

    "return one application if there is one submitted application without adjustment needed" in {
      createApplication("appId", "userId1", "frameworkId1", "SUBMITTED", needsAdjustment = false, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.isDefined mustBe true
      result.get.userId mustBe "userId1"
      result.get.applicationStatus mustBe ApplicationStatuses.Submitted
      result.get.needsAdjustments mustBe false
      result.get.adjustmentDetail.isEmpty mustBe true
    }


    "return one application if there is one submitted application with no time adjustment needed and adjustments confirmed" in {
      createApplication("appId", "userId1", "frameworkId1", "SUBMITTED", needsAdjustment = true, adjustmentsConfirmed = true,
        timeExtensionAdjustments = false)

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.isDefined mustBe true
      result.get.userId mustBe "userId1"
      result.get.applicationStatus mustBe ApplicationStatuses.Submitted
      result.get.needsAdjustments mustBe true
      result.get.adjustmentDetail.isEmpty mustBe true
    }

    "return one application if there is one submitted application with time adjustment needed and confirmed" in {
      createApplication("appId", "userId1", "frameworkId1", "SUBMITTED", needsAdjustment = true, adjustmentsConfirmed = true,
        timeExtensionAdjustments = true)

      val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue

      result.isDefined mustBe true
      result.get.userId mustBe "userId1"
      result.get.applicationStatus mustBe ApplicationStatuses.Submitted
      result.get.needsAdjustments mustBe true
      result.get.adjustmentDetail.isDefined mustBe true
      result.get.adjustmentDetail.get.extraTimeNeeded.get mustBe 9
      result.get.adjustmentDetail.get.extraTimeNeededNumerical.get mustBe 11
    }

    "return a random application from a choice of multiple submitted applications without adjustment needed" in {
      createApplication("appId1", "userId1", "frameworkId1", "SUBMITTED", needsAdjustment = false, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)
      createApplication("appId2", "userId2", "frameworkId1", "SUBMITTED", needsAdjustment = false, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)
      createApplication("appId3", "userId3", "frameworkId1", "SUBMITTED", needsAdjustment = false, adjustmentsConfirmed = false,
        timeExtensionAdjustments = false)

      val userIds = (1 to 25).map { _ =>
        val result = onlineTestRepo.nextApplicationReadyForOnlineTesting.futureValue
        result.get.userId
      }

      userIds must contain("userId1")
      userIds must contain("userId2")
      userIds must contain("userId3")
    }
  }

  "Getting the next application for expiry" should {
    "return one application if there is one expired un-started test" in {
      val appIdWithUserId = createOnlineTest(ApplicationStatuses.OnlineTestInvited, expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe Some(ExpiringOnlineTest(appIdWithUserId.applicationId, appIdWithUserId.userId, "Test Preferred Name"))
    }

    "return one application if there is one expired started test" in {
      val appIdWithUserId = createOnlineTest(ApplicationStatuses.OnlineTestStarted, expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe Some(ExpiringOnlineTest(appIdWithUserId.applicationId, appIdWithUserId.userId, "Test Preferred Name"))
    }

    "return no applications if there are started and un-started tests, but none expired" in {
      createOnlineTest(ApplicationStatuses.OnlineTestInvited, expirationDate = DateTime.now().plusMinutes(1))
      createOnlineTest(ApplicationStatuses.OnlineTestStarted, expirationDate = DateTime.now().plusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe None
    }

    "return no applications if there are expired tests, but are not active" in {
      createOnlineTest(ApplicationStatuses.Created, expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest(ApplicationStatuses.Withdrawn, expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest(ApplicationStatuses.InProgress, expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest(ApplicationStatuses.OnlineTestCompleted, expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest(ApplicationStatuses.OnlineTestExpired, expirationDate = DateTime.now().minusMinutes(1))

      val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue

      result mustBe None
    }

    "return a random application from a choice of multiple applications in relevant states" in {
      createOnlineTest("userId1", ApplicationStatuses.OnlineTestInvited, expirationDate = DateTime.now().minusMinutes(1))
      createOnlineTest("userId2", ApplicationStatuses.OnlineTestInvited, expirationDate = DateTime.now().minusMinutes(1))

      val userIds = (1 to 20).map { _ =>
        val result = onlineTestRepo.nextApplicationPendingExpiry.futureValue
        result.get.userId
      }

      userIds must contain("userId1")
      userIds must contain("userId2")
    }
  }

  "Update expiry time" should {
    "set ONLINE_TEST_INVITED to ONLINE_TEST_INVITED" in {
      updateExpiryAndAssert(ApplicationStatuses.OnlineTestInvited, ApplicationStatuses.OnlineTestInvited)
    }
    "set ONLINE_TEST_STARTED tests to ONLINE_TEST_STARTED" in {
      updateExpiryAndAssert(ApplicationStatuses.OnlineTestStarted, ApplicationStatuses.OnlineTestStarted)
    }
    "set EXPIRED tests to INVITED" in {
      updateExpiryAndAssert(ApplicationStatuses.OnlineTestExpired, ApplicationStatuses.OnlineTestInvited)
    }

    def updateExpiryAndAssert(currentStatus: ApplicationStatuses.EnumVal, newStatus: ApplicationStatuses.EnumVal) = {
      val oldExpiration = DateTime.now()
      val newExpiration = oldExpiration.plusDays(3)
      val appIdWithUserId = createOnlineTest(currentStatus, expirationDate = oldExpiration)

      onlineTestRepo.updateExpiryTime(appIdWithUserId.userId, newExpiration).futureValue

      val expireDate = onlineTestRepo.getOnlineTestDetails(appIdWithUserId.userId).map(_.expireDate).futureValue
      expireDate.toDate mustBe newExpiration.toDate

      val appStatus = onlineTestRepo.getOnlineTestApplication(appIdWithUserId.applicationId).map(_.get.applicationStatus).futureValue
      appStatus mustBe newStatus
    }
  }

  "Getting the next application for failure notification" should {
    "return one application if there is one failed test and pdf report has been saved" in {
      val appIdWithUserId = createOnlineTest(ApplicationStatuses.OnlineTestFailed, xmlReportSaved=Some(true), pdfReportSaved = Some(true))

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe Some(ApplicationForNotification(appIdWithUserId.applicationId,
        appIdWithUserId.userId, "Test Preferred Name", ApplicationStatuses.OnlineTestFailed))
    }

    "return no application if there is one failed test but pdf report has not been saved" in {
      createOnlineTest(ApplicationStatuses.OnlineTestFailed, xmlReportSaved=Some(true), pdfReportSaved=Some(false))

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe None
    }

    "return no applications if there are applications which don't require notifying of failure" in {
      createOnlineTest(ApplicationStatuses.OnlineTestStarted)
      createOnlineTest(ApplicationStatuses.OnlineTestInvited)
      createOnlineTest(ApplicationStatuses.OnlineTestFailedNotified)

      val result = onlineTestRepo.nextApplicationPendingFailure.futureValue

      result mustBe None
    }

    "return a random application from a choice of multiple failed tests" in {
      createOnlineTest("userId1", ApplicationStatuses.OnlineTestFailed, xmlReportSaved=Some(true), pdfReportSaved = Some(true))
      createOnlineTest("userId2", ApplicationStatuses.OnlineTestFailed, xmlReportSaved=Some(true), pdfReportSaved = Some(true))
      createOnlineTest("userId3", ApplicationStatuses.OnlineTestFailed, xmlReportSaved=Some(true), pdfReportSaved = Some(true))

      val userIds = (1 to 15).map { _ =>
        val result = onlineTestRepo.nextApplicationPendingFailure.futureValue
        result.get.userId
      }

      userIds must contain("userId1")
      userIds must contain("userId2")
      userIds must contain("userId3")
    }
  }

  "Get online test" should {
    "throw an exception if there is no test for the specific user id" in {
      val result = onlineTestRepo.getOnlineTestDetails("userId").failed.futureValue

      result mustBe an[NotFoundException]
    }

    "return an online test for the specific user id" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      createOnlineTest("userId", ApplicationStatuses.OnlineTestInvited, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)))

      val result = onlineTestRepo.getOnlineTestDetails("userId").futureValue

      result.expireDate.toDate mustBe new DateTime("2016-03-15T13:04:29.643Z").toDate
      result.onlineTestLink mustBe "http://www.someurl.com"
      result.isOnlineTestEnabled mustBe true
    }
  }

  "Update status" should {
    "update status for the specific user id" in {
      createApplication("appId", "userId", "frameworkId", ApplicationStatuses.Submitted,
        needsAdjustment = true, adjustmentsConfirmed = false, timeExtensionAdjustments =  true)

      onlineTestRepo.updateStatus("userId", ApplicationStatuses.OnlineTestInvited).futureValue

      val result = helperRepo.findByUserId("userId", "frameworkId").futureValue

      result.applicationStatus mustBe ApplicationStatuses.OnlineTestInvited
    }

    "fail when updating status but application doesn't exist" in {
      val result = onlineTestRepo.updateStatus("userId", ApplicationStatuses.OnlineTestInvited).failed.futureValue

      result mustBe an[NotFoundException]
    }
  }

  "Consume token" should {
    "update status to ONLINE_TEST_COMPLETED" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      createOnlineTest("userId", ApplicationStatuses.Submitted, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)))

      onlineTestRepo.consumeToken("token").futureValue

      val result = helperRepo.findByUserId("userId", "frameworkId").futureValue
      result.applicationStatus mustBe(ApplicationStatuses.OnlineTestCompleted)
    }
  }

  "Store online test profile" should {
    "update online test profile and set the status to ONLINE_TEST_INVITED" in {
      val date = new DateTime("2016-03-08T13:04:29.643Z")
      val appIdWithUserId = createOnlineTest("userId", ApplicationStatuses.Submitted, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), xmlReportSaved = Some(true), pdfReportSaved = Some(true))

      val TestProfile = OnlineTestProfile(
        1234,
        "tokenId",
        "http://someurl.com",
        invitationDate = date,
        expirationDate = date.plusDays(7),
        123456
      )
      onlineTestRepo.storeOnlineTestProfileAndUpdateStatusToInvite(appIdWithUserId.applicationId, TestProfile).futureValue

      val result = onlineTestRepo.getOnlineTestDetails(appIdWithUserId.userId).futureValue
      // The expireDate has +7 days, as the method get from the repo adds 7 days
      result.expireDate.toDate mustBe(new DateTime("2016-03-15T13:04:29.643Z").toDate)
      result.inviteDate.toDate mustBe(date.toDate)
      result.isOnlineTestEnabled mustBe(true)
      result.onlineTestLink mustBe("http://someurl.com")

      val query = BSONDocument("applicationId" -> appIdWithUserId.applicationId)
      val (xml, pdf) = helperRepo.collection.find(query).one[BSONDocument].map { docOpt =>
        val root = docOpt.get.getAs[BSONDocument]("online-tests").get
        (root.getAs[Boolean]("xmlReportSaved"),
          root.getAs[Boolean]("pdfReportSaved"))
      }.futureValue

      xml mustBe empty
      pdf mustBe empty
    }

    "unset the online test flags for already completed online test when storeOnlineTestProfileAndUpdateStatus is called again" in {
      val InvitationDate = DateTime.now()
      val ExpirationDate = InvitationDate.plusDays(7)
      val TestProfile = OnlineTestProfile(1234, "tokenId", "http://someurl.com", InvitationDate, ExpirationDate, 123456)
      helperRepo.collection.insert(BSONDocument(
        "applicationId" -> "appId",
        "applicationStatus" -> ApplicationStatuses.OnlineTestFailedNotified,
        "progress-status" -> BSONDocument(
          s"${ProgressStatuses.OnlineTestStartedProgress}" -> true,
          s"${ProgressStatuses.OnlineTestCompletedProgress}" -> true,
          s"${ProgressStatuses.OnlineTestExpiredProgress}" -> true,
          s"${ProgressStatuses.AwaitingOnlineTestReevaluationProgress}" -> true,
          s"${ProgressStatuses.OnlineTestFailedProgress}" -> true,
          s"${ProgressStatuses.OnlineTestFailedNotifiedProgress}" -> true,
          s"${ProgressStatuses.AwaitingOnlineTestAllocationProgress}" -> true
        ),
        "online-tests" -> BSONDocument(
          "cubiksUserId" -> 1111,
          "token" -> "previousToken",
          "onlineTestUrl" -> "previousOnlineTestUrl",
          "invitationDate" -> DateTime.now().minusDays(10),
          "expiratinDate" -> DateTime.now().minusDays(3),
          "participantScheduleId" -> "previousScheduleId",
          "xmlReportSaved" -> true,
          "pdfReportSaved" -> true
        ),
        "passmarkEvaluation" -> "notEmpty"
      )).futureValue

      onlineTestRepo.storeOnlineTestProfileAndUpdateStatusToInvite("appId", TestProfile).futureValue

      val query = BSONDocument("applicationId" -> "appId")
      helperRepo.collection.find(query).one[BSONDocument].map {
        case Some(doc) =>
          doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus") mustBe Some(ApplicationStatuses.OnlineTestInvited)

          val progressStatus = doc.getAs[BSONDocument]("progress-status").get
          val allProgressStatuses = progressStatus.elements.map(_._1).toList
          allProgressStatuses mustBe List(ProgressStatuses.OnlineTestInvitedProgress.name)

          val onlineTests = doc.getAs[BSONDocument]("online-tests").get
          onlineTests.getAs[Int]("cubiksUserId") mustBe Some(1234)
          onlineTests.getAs[String]("token") mustBe Some("tokenId")
          onlineTests.getAs[String]("onlineTestUrl") mustBe Some("http://someurl.com")
          onlineTests.getAs[DateTime]("invitationDate").get mustBe InvitationDate.withZone(DateTimeZone.UTC)
          onlineTests.getAs[DateTime]("expirationDate").get mustBe ExpirationDate.withZone(DateTimeZone.UTC)
          onlineTests.getAs[Int]("participantScheduleId") mustBe Some(123456)
          onlineTests.getAs[Boolean]("xmlReportSaved") mustBe empty
          onlineTests.getAs[Boolean]("pdfReportSaved") mustBe empty

          doc.getAs[BSONDocument]("passmarkEvaluation") mustBe (empty)

        case None => fail("Application should have been already created and cannot be empty")
      }.futureValue
    }
  }

  "Next application ready for report retrieving" should {
    "return None when there is no application with the status ONLINE_TEST_COMPLETED" in {
      createOnlineTest("userId1", ApplicationStatuses.OnlineTestInvited, expirationDate = DateTime.now())
      val result = onlineTestRepo.nextApplicationReadyForReportRetriving.futureValue
      result mustBe None
    }

    "return an application with a cubiksUserId" in {
      val date = DateTime.now()
      val appIdWithUserId = createOnlineTest(UUID.randomUUID().toString,  ApplicationStatuses.OnlineTestCompleted,
        "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123)
      )

      val result = onlineTestRepo.nextApplicationReadyForReportRetriving.futureValue

      result.get mustBe OnlineTestApplicationWithCubiksUser(appIdWithUserId.applicationId, appIdWithUserId.userId, 123)
    }

    "return None when the application has a flag XmlReportSaved set to true" in {
      val date = DateTime.now()
      createOnlineTest("userId", ApplicationStatuses.OnlineTestCompleted, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123), Some(true))

      val result = onlineTestRepo.nextApplicationReadyForReportRetriving.futureValue

      result mustBe None
    }
  }

  "Next application ready for pdf report retrieving" should {
    "return None when the application has not an xml report saved" in {
      val date = DateTime.now()
      createOnlineTest("userId", ApplicationStatuses.OnlineTestCompleted, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123), xmlReportSaved = Some(false))

      val result = onlineTestRepo.nextApplicationReadyForPDFReportRetrieving().futureValue
      result mustBe None
    }

    "return None when the application has an xml report saved and pdf report has been saved already" in {
      val date = DateTime.now()
      createOnlineTest("userId", ApplicationStatuses.OnlineTestCompleted, "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123), xmlReportSaved = Some(true),
        pdfReportSaved = Some(true))

      val result = onlineTestRepo.nextApplicationReadyForPDFReportRetrieving().futureValue

      result mustBe None
    }

    "return an application with a cubiksUserId when the application has an xml report saved and pdf report saved flag is not present" in {
      val date = DateTime.now()
      val appIdWithUserId = createOnlineTest(UUID.randomUUID().toString, appStatus = ApplicationStatuses.OnlineTestCompleted,
        "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123), xmlReportSaved = Some(true),
        pdfReportSaved = None)

      val result = onlineTestRepo.nextApplicationReadyForPDFReportRetrieving().futureValue

      result.get mustBe OnlineTestApplicationWithCubiksUser(appIdWithUserId.applicationId, appIdWithUserId.userId, 123)
    }

    "return an application with a cubiksUserId when the application has an xml report saved and pdf report saved flag is false" in {
      val date = DateTime.now()
      val appIdWithUserId = createOnlineTest(UUID.randomUUID().toString, ApplicationStatuses.OnlineTestCompleted,
       "token", Some("http://www.someurl.com"),
        invitationDate = Some(date), expirationDate = Some(date.plusDays(7)), Some(123), xmlReportSaved = Some(true),
        pdfReportSaved = Some(false)
      )

      val result = onlineTestRepo.nextApplicationReadyForPDFReportRetrieving().futureValue

      result.get mustBe OnlineTestApplicationWithCubiksUser(appIdWithUserId.applicationId, appIdWithUserId.userId, 123)
    }
  }

  "removing a candidate's allocation status" should {
    "remove the status, and status flags" in {
      val appIdWithUserId = createOnlineTest(UUID.randomUUID().toString, ApplicationStatuses.AllocationConfirmed)

      val result = onlineTestRepo.removeCandidateAllocationStatus(appIdWithUserId.applicationId).futureValue

      result mustBe (())

      val checkResult = onlineTestRepo.collection
        .find(BSONDocument("applicationId" -> appIdWithUserId.applicationId)).one[BSONDocument].futureValue

      checkResult.isDefined mustBe true
      checkResult.get.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get mustBe ApplicationStatuses.AwaitingAllocation
      checkResult.get.get("progress-status-dates.allocation_unconfirmed").isDefined mustBe false
    }
  }

  "next application ready for online test evaluation" should {
    "return no candidate if there is only a candidate in ONLINE_TEST_STARTED status" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.OnlineTestStarted)

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result mustBe empty
    }

    "return a candidate who has the report xml saved and who has never been evaluated before" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.OnlineTestCompleted, xmlReportSavedOpt = Some(true))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId mustBe AppId
    }

    "return a candidate who is in AWAITING_ONLINE_TEST_RE_EVALUATION status and with an old passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AwaitingOnlineTestReevaluation,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("oldVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId mustBe AppId
    }

    "return no candidate if there is only one who has been already evaluated against the same Passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AwaitingOnlineTestReevaluation,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("currentVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result mustBe empty
    }

    "return a candidate who is in ASSESSMENT_SCORES_ACCEPTED status and with an old passmark version" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AssessmentScoresAccepted,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("oldVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result must not be empty
      result.get.applicationId mustBe(AppId)
    }

    "return no candidate if there is only one who has been already evaluated but the application status is ASSESSMENT_SCORES_ENTERED" in {
      val AppId = UUID.randomUUID().toString
      createOnlineTestApplication(AppId, ApplicationStatuses.AssessmentScoresEntered,
        xmlReportSavedOpt = Some(true), alreadyEvaluatedAgainstPassmarkVersionOpt = Some("currentVersion"))

      val result = onlineTestRepo.nextApplicationPassMarkProcessing("currentVersion").futureValue

      result mustBe empty
    }
  }

  "find passmark evaluation" should {
    val appId = "AppId"

    "return online test passmark evaluation" in {
      helperRepo.collection.insert(BSONDocument(
        "applicationId" -> appId,
        "passmarkEvaluation" -> BSONDocument(
          "passmarkVersion" -> "passmarkVersion",
          "location1Scheme1" -> "Red",
          "location1Scheme2" -> "Amber",
          "location2Scheme1" -> "Green",
          "location2Scheme2" -> "Green",
          "alternativeScheme" -> "Amber"
        )
      )).futureValue

      val result = onlineTestRepo.findPassmarkEvaluation(appId).futureValue

      result mustBe OnlineTestPassmarkEvaluation(Red, Some(Amber), Some(Green), Some(Green), Some(Amber))
    }

    "throw an exception when there is no location1Scheme1 result" in {
      helperRepo.collection.insert(BSONDocument(
        "applicationId" -> appId,
        "passmarkEvaluation" -> BSONDocument(
          "passmarkVersion" -> "passmarkVersion"
        )
      )).futureValue

      val exception = onlineTestRepo.findPassmarkEvaluation(appId).failed.futureValue

      exception mustBe OnlineTestFirstLocationResultNotFound(appId)
    }

    "throw an exception when there is no passmarkEvaluation section" in {
      helperRepo.collection.insert(BSONDocument(
        "applicationId" -> appId
      )).futureValue

      val exception = onlineTestRepo.findPassmarkEvaluation(appId).failed.futureValue

      exception mustBe OnlineTestPassmarkEvaluationNotFound(appId)
    }
  }

  def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String, needsAdjustment: Boolean,
                        adjustmentsConfirmed: Boolean, timeExtensionAdjustments: Boolean): WriteResult = {
    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationId" -> appId,
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "assistance-details" -> createAsistanceDetails(needsAdjustment, adjustmentsConfirmed, timeExtensionAdjustments)
    )).futureValue
  }

  private def createAsistanceDetails(needsAdjustment: Boolean, adjustmentsConfirmed: Boolean, timeExtensionAdjustments:Boolean) = {
    if (needsAdjustment) {
      if (adjustmentsConfirmed) {
        if (timeExtensionAdjustments) {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> true,
            "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
            "confirmedAdjustments" -> true,
            "onlineTests" -> AdjustmentDetail(Some(9), Some(11)),
            "guaranteedInterview" -> false,
            "hasDisability" -> "No"
          )
        } else {
          BSONDocument(
            "needsSupportForOnlineAssessment" -> true,
            "needsSupportAtVenue" -> true,
            "typeOfAdjustments" -> BSONArray("room alone"),
            "confirmedAdjustments" -> true,
            "guaranteedInterview" -> false,
            "hasDisability" -> "No"
          )
        }
      } else {
        BSONDocument(
          "needsSupportForOnlineAssessment" -> true,
          "needsSupportAtVenue" -> true,
          "typeOfAdjustments" -> BSONArray("time extension", "room alone"),
          "confirmedAdjustments" -> false,
          "guaranteedInterview" -> false,
          "hasDisability" -> "No"
        )
      }
    } else {
      BSONDocument(
        "needsSupportForOnlineAssessment" -> false,
        "needsSupportAtVenue" -> false,
        "guaranteedInterview" -> false,
        "hasDisability" -> "No"
      )
    }
  }

  def createOnlineTest(appStatus: ApplicationStatuses.EnumVal): Unit =
    createOnlineTest(UUID.randomUUID().toString, appStatus, DateTime.now().plusDays(5))

  def createOnlineTest(appStatus: ApplicationStatuses.EnumVal, xmlReportSaved: Option[Boolean], pdfReportSaved: Option[Boolean])
    : ApplicationIdWithUserIdAndStatus =
    createOnlineTest(UUID.randomUUID().toString, appStatus, DateTime.now().plusDays(5), xmlReportSaved, pdfReportSaved)

  def createOnlineTest(appStatus: ApplicationStatuses.EnumVal, expirationDate: DateTime): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(UUID.randomUUID().toString, appStatus, expirationDate)

  def createOnlineTest(userId: String, appStatus: ApplicationStatuses.EnumVal): ApplicationIdWithUserIdAndStatus =
    createOnlineTest(userId, appStatus, DateTime.now().plusDays(5))

  def createOnlineTest(userId: String, appStatus: ApplicationStatuses.EnumVal, xmlReportSaved: Option[Boolean],
     pdfReportSaved: Option[Boolean]
  ): Unit = createOnlineTest(userId, appStatus, DateTime.now().plusDays(5), xmlReportSaved, pdfReportSaved)

  def createOnlineTest(userId: String, appStatus: ApplicationStatuses.EnumVal, expirationDate: DateTime):
    ApplicationIdWithUserIdAndStatus =
    createOnlineTest(userId, appStatus, "token", Some("http://www.someurl.com"),
      invitationDate = Some(expirationDate.minusDays(7)), expirationDate = Some(expirationDate))

  def createOnlineTest(userId: String, appStatus: ApplicationStatuses.EnumVal, expirationDate: DateTime,
    xmlReportSaved: Option[Boolean], pdfReportSaved: Option[Boolean]): ApplicationIdWithUserIdAndStatus = {
    createOnlineTest(userId, appStatus, "token", Some("http://www.someurl.com"),
      invitationDate = Some(expirationDate.minusDays(7)), expirationDate = Some(expirationDate), xmlReportSaved = xmlReportSaved,
      pdfReportSaved = pdfReportSaved)
  }

  //scalastyle:off
  def createOnlineTest(userId: String, appStatus: ApplicationStatuses.EnumVal, token: String, onlineTestUrl: Option[String],
                       invitationDate: Option[DateTime], expirationDate: Option[DateTime], cubiksUserId: Option[Int] = None,
                       xmlReportSaved: Option[Boolean] = None, pdfReportSaved: Option[Boolean] = None): ApplicationIdWithUserIdAndStatus = {
    val onlineTests = if (pdfReportSaved.isDefined && xmlReportSaved.isDefined) {
      BSONDocument(
        "cubiksUserId" -> cubiksUserId.getOrElse(0),
        "onlineTestUrl" -> onlineTestUrl.get,
        "invitationDate" -> invitationDate.get,
        "expirationDate" -> expirationDate.get,
        "token" -> token,
        "xmlReportSaved" -> xmlReportSaved.get,
        "pdfReportSaved" -> pdfReportSaved.get
      )
    } else {
      if (xmlReportSaved.isDefined) {
        BSONDocument(
          "cubiksUserId" -> cubiksUserId.getOrElse(0),
          "onlineTestUrl" -> onlineTestUrl.get,
          "invitationDate" -> invitationDate.get,
          "expirationDate" -> expirationDate.get,
          "token" -> token,
          "xmlReportSaved" -> xmlReportSaved.get
        )
      } else {
        BSONDocument(
          "cubiksUserId" -> cubiksUserId.getOrElse(0),
          "onlineTestUrl" -> onlineTestUrl.get,
          "invitationDate" -> invitationDate.get,
          "expirationDate" -> expirationDate.get,
          "token" -> token
        )
      }
    }

    val appId = UUID.randomUUID().toString

    helperRepo.collection.insert(BSONDocument(
      "userId" -> userId,
      "applicationId" -> appId,
      "frameworkId" -> "frameworkId",
      "applicationStatus" -> appStatus,
      "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
      "online-tests" -> onlineTests,
      "progress-status-dates" -> BSONDocument("allocation_unconfirmed" -> "2016-04-05"),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "No",
        "guaranteedInterview" -> true,
        "needsSupportForOnlineAssessment" -> false,
        "needsSupportAtVenue" -> false,
        "expirationDate" -> expirationDate.get,
        "token" -> token
      )
    )).futureValue

    ApplicationIdWithUserIdAndStatus(appId, userId, appStatus)
  }
  //scalastyle:on

  def createOnlineTestApplication(appId: String, applicationStatus: String, xmlReportSavedOpt: Option[Boolean] = None,
                                  alreadyEvaluatedAgainstPassmarkVersionOpt: Option[String] = None): String = {
    val result = (xmlReportSavedOpt, alreadyEvaluatedAgainstPassmarkVersionOpt) match {
      case (None, None ) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "userId" -> appId,
          "applicationStatus" -> applicationStatus
        ))
      case (Some(xmlReportSaved), None) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "userId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved)
        ))
      case (None, Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "userId" -> appId,
          "applicationStatus" -> applicationStatus,
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
      case (Some(xmlReportSaved), Some(alreadyEvaluatedAgainstPassmarkVersion)) =>
        helperRepo.collection.insert(BSONDocument(
          "applicationId" -> appId,
          "userId" -> appId,
          "applicationStatus" -> applicationStatus,
          "online-tests" -> BSONDocument("xmlReportSaved" -> xmlReportSaved),
          "passmarkEvaluation" -> BSONDocument("passmarkVersion" -> alreadyEvaluatedAgainstPassmarkVersion)
        ))
    }

    result.futureValue

    appId
  }

}
