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

package repositories.application

import common.Constants.{ Yes, No }
import config.MicroserviceAppConfig._
import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.EvaluationResults._
import model.Exceptions.{NotFoundException, OnlineTestFirstLocationResultNotFound, OnlineTestPassmarkEvaluationNotFound, UnexpectedException}
import model.OnlineTestCommands._
import model.PersistedObjects.{ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest, OnlineTestPassmarkEvaluation}
import model.persisted.AssistanceDetails
import model.{ApplicationStatuses, Commands}
import org.joda.time.{DateTime, LocalDate}
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONArray, BSONDocument, BSONObjectID, BSONString}
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestRepository {
  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]]

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]]

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]]

  def getOnlineTestDetails(userId: String): Future[OnlineTestDetails]

  def updateStatus(userId: String, status: String): Future[Unit]

  def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit]

  def consumeToken(token: String): Future[Unit]

  def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: OnlineTestProfile): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def updateXMLReportSaved(applicationId: String): Future[Unit]

  def updatePDFReportSaved(applicationId: String): Future[Unit]

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]]

  def savePassMarkScore(applicationId: String, version: String, p: RuleCategoryResult, applicationStatus: String): Future[Unit]

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String, p: RuleCategoryResult): Future[Unit]

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit]

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: String, expireDate: Option[LocalDate]): Future[Unit]

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation]
}

class OnlineTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
  extends ReactiveRepository[OnlineTestDetails, BSONObjectID](CollectionNames.APPLICATION, mongo,
    Commands.Implicits.onlineTestDetailsFormat, ReactiveMongoFormats.objectIdFormats) with OnlineTestRepository with RandomSelection {

  private def applicationStatus(status: String): BSONDocument = {

    val flag = status match {
      case "ONLINE_TEST_INVITED" => "online_test_invited"
      case "ONLINE_TEST_STARTED" => "online_test_started"
      case "ONLINE_TEST_COMPLETED" => "online_test_completed"
      case "ONLINE_TEST_EXPIRED" => "online_test_expired"
      case "ONLINE_TEST_FAILED" => "online_test_failed"
      case "ONLINE_TEST_FAILED_NOTIFIED" => "online_test_failed_notified"
    }

    if (flag == "online_test_completed") {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,
        "applicationStatus" -> status,
        "online-tests.completionDate" -> DateTime.now
      ))
    } else {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,
        "applicationStatus" -> status
      ))
    }
  }

  override def getOnlineTestDetails(userId: String): Future[OnlineTestDetails] = {

    val query = BSONDocument("userId" -> userId)
    val projection = BSONDocument("online-tests" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("online-tests").isDefined => {
        val root = document.getAs[BSONDocument]("online-tests").get
        val onlineTestUrl = root.getAs[String]("onlineTestUrl").get
        val token = root.getAs[String]("token").get
        val invitationDate = root.getAs[DateTime]("invitationDate").get
        val expirationDate = root.getAs[DateTime]("expirationDate").get

        OnlineTestDetails(invitationDate, expirationDate, onlineTestUrl, s"$token@${cubiksGatewayConfig.emailDomain}", true)
      }
      case _ => throw new NotFoundException()
    }
  }

  override def updateStatus(userId: String, status: String) = {
    val query = BSONDocument("userId" -> userId)
    val applicationStatusBSON = applicationStatus(status)

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case r if r.n == 0 => throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      case r if r.n > 1 => throw new UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
      case _ =>
    }
  }

  override def updateExpiryTime(userId: String, expirationDate: DateTime): Future[Unit] = {
    val queryUser = BSONDocument("userId" -> userId)
    val queryUserExpired = BSONDocument("userId" -> userId, "applicationStatus" -> "ONLINE_TEST_EXPIRED")
    val newExpiryTime = BSONDocument("$set" -> BSONDocument(
      "online-tests.expirationDate" -> expirationDate
    ))
    val newStatus = BSONDocument("$set" -> BSONDocument(
      "progress-status.online_test_expired" -> false,
      "progress-status.online_test_invited" -> true,
      "applicationStatus" -> "ONLINE_TEST_INVITED"
    ))

    for {
      status <- collection.update(queryUser, newExpiryTime, upsert = false)
      _ <- collection.update(queryUserExpired, newStatus, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      if (status.n > 1) throw new UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
    }
  }

  override def consumeToken(token: String) = {
    val query = BSONDocument("online-tests.token" -> token)

    val applicationStatusBSON = applicationStatus("ONLINE_TEST_COMPLETED")

    collection.update(query, applicationStatusBSON, upsert = false).map { _ => () }
  }

  override def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, onlineTestProfile: OnlineTestProfile) = {
    import model.ProgressStatuses._

    val query = BSONDocument("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument("$unset" -> BSONDocument(
      s"progress-status.$OnlineTestStartedProgress" -> "",
      s"progress-status.$OnlineTestCompletedProgress" -> "",
      s"progress-status.$OnlineTestExpiredProgress" -> "",
      s"progress-status.$AwaitingOnlineTestReevaluationProgress" -> "",
      s"progress-status.$OnlineTestFailedProgress" -> "",
      s"progress-status.$OnlineTestFailedNotifiedProgress" -> "",
      s"progress-status.$AwaitingOnlineTestAllocationProgress" -> "",
      s"online-tests.xmlReportSaved" -> "",
      s"online-tests.pdfReportSaved" -> "",
      s"passmarkEvaluation" -> ""
    )) ++ BSONDocument("$set" -> BSONDocument(
      "progress-status.online_test_invited" -> true,
      "applicationStatus" -> "ONLINE_TEST_INVITED",
      "online-tests.cubiksUserId" -> onlineTestProfile.cubiksUserId,
      "online-tests.token" -> onlineTestProfile.token,
      "online-tests.onlineTestUrl" -> onlineTestProfile.onlineTestUrl,
      "online-tests.invitationDate" -> onlineTestProfile.invitationDate,
      "online-tests.expirationDate" -> onlineTestProfile.expirationDate,
      "online-tests.participantScheduleId" -> onlineTestProfile.participantScheduleId
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case _ => ()
    }
  }

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument(
      "applicationId" -> appId
    )
    collection.find(query).one[BSONDocument] map {
      _.map(bsonDocToOnlineTestApplication)
    }
  }

  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument(
        "online-tests.expirationDate" -> BSONDocument("$lte" -> dateTime.nowLocalTimeZone) // Serialises to UTC.
      ),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationStatus" -> "ONLINE_TEST_INVITED"),
        BSONDocument("applicationStatus" -> "ONLINE_TEST_STARTED")
      ))
    ))

    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextApplicationPendingFailure: Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> "ONLINE_TEST_FAILED"),
      BSONDocument("online-tests.pdfReportSaved" -> true)
    ))
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> "SUBMITTED"),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> false),
        BSONDocument("$and" -> BSONArray(
          BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
          BSONDocument("assistance-details.confirmedAdjustments" -> true)
        ))
      ))
    ))
    selectRandom(query).map(_.map(bsonDocToOnlineTestApplication))
  }

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> "ONLINE_TEST_COMPLETED"),
      BSONDocument("online-tests.xmlReportSaved" ->
        BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map(bsonDocToOnlineTestApplicationForReportRetrieving))
  }

  override def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("online-tests.pdfReportSaved" -> BSONDocument("$ne" -> true)),
      BSONDocument("online-tests.xmlReportSaved" -> BSONDocument("$eq" -> true))
    ))
    selectRandom(query).map(_.map(bsonDocToOnlineTestApplicationForReportRetrieving))
  }

  private def bsonDocToExpiringOnlineTest(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ExpiringOnlineTest(applicationId, userId, preferredName)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }

  private def bsonDocToOnlineTestApplication(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val applicationStatus = doc.getAs[String]("applicationStatus").get
    val userId = doc.getAs[String]("userId").get

    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get

    val ad = doc.getAs[BSONDocument]("assistance-details")
    val hasDisability = ad.flatMap(_.getAs[String]("hasDisability")).get
    val needsSupportForOnlineAssessment = ad.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).get
    val guaranteedInterview = ad.flatMap(_.getAs[Boolean]("guaranteedInterview")).getOrElse(false)
    val typesOfAdjustments = ad.flatMap(_.getAs[List[String]]("typeOfAdjustments"))
    val hasTimeExtension = typesOfAdjustments.exists(_.contains("time extension"))

    val timeExtensionOpt: Option[TimeAdjustmentsOnlineTestApplication] = if (needsSupportForOnlineAssessment && hasTimeExtension) {
      val verbalTimeAdjustmentPercentage = ad.flatMap(_.getAs[Int]("verbalTimeAdjustmentPercentage"))
      val numericalTimeAdjustmentPercentage = ad.flatMap(_.getAs[Int]("numericalTimeAdjustmentPercentage"))
      Some(TimeAdjustmentsOnlineTestApplication(verbalTimeAdjustmentPercentage.get, numericalTimeAdjustmentPercentage.get))
    } else {
      None
    }

    OnlineTestApplication(applicationId, applicationStatus, userId, guaranteedInterview,
      needsSupportForOnlineAssessment, preferredName, timeExtensionOpt)
  }

  private def bsonDocToOnlineTestApplicationForReportRetrieving(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get

    def errorMsg(attr: String) = s"Error retrieving $attr for user with applicationId:$applicationId ${doc.toString()}"

    val onlineTests = doc.getAs[BSONDocument]("online-tests")
      .getOrElse(throw new Exception(errorMsg("online-tests")))
    val cubiksUserId = onlineTests.getAs[Int]("cubiksUserId")
      .getOrElse(throw new Exception(errorMsg("cubiksUserId")))

    OnlineTestApplicationWithCubiksUser(applicationId, userId, cubiksUserId)
  }

  def updateXMLReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.xmlReportSaved", true)
  }

  def updatePDFReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.pdfReportSaved", true)
  }

  private def updateFlag(applicationId: String, flag: String, value: Boolean): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val MRAReportGenerated = BSONDocument("$set" -> BSONDocument(
      flag -> value
    ))

    for {
      status <- collection.update(query, MRAReportGenerated, upsert = false)
    } yield {
      if (status.n == 0) throw new NotFoundException(s"updateStatus didn't update anything for applicationId:$applicationId")
      if (status.n > 1) throw new UnexpectedException(s"updateStatus updated more than one record for applicationId:$applicationId")
    }
  }

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = {
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("online-tests.xmlReportSaved" -> true),
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)),
              BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingOnlineTestReevaluation)
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)),
              BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted)
            )
          )
        ))

    selectRandom(query).map(_.map { doc =>
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationStatus = doc.getAs[String]("applicationStatus")
        .getOrElse(throw new IllegalStateException("applicationStatus must be defined"))

      ApplicationIdWithUserIdAndStatus(applicationId, userId, applicationStatus)
    })
  }

  def savePassMarkScore(applicationId: String, version: String, p: RuleCategoryResult, applicationStatus: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = applicationStatus match {
      case ApplicationStatuses.AwaitingOnlineTestReevaluation => "awaiting_online_test_re_evaluation"
      case ApplicationStatuses.OnlineTestFailed => "online_test_failed"
      case ApplicationStatuses.AwaitingAllocation => "awaiting_online_test_allocation"
    }

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "passmarkEvaluation" ->
          BSONDocument("passmarkVersion" -> version, "location1Scheme1" -> p.location1Scheme1.toString).
            add(schemeToBSON("location1Scheme2" -> p.location1Scheme2)).
            add(schemeToBSON("location2Scheme1" -> p.location2Scheme1)).
            add(schemeToBSON("location2Scheme2" -> p.location2Scheme2)).
            add(schemeToBSON("alternativeScheme" -> p.alternativeScheme)),
        "applicationStatus" -> applicationStatus,
        s"progress-status.$progressStatus" -> true
      ))

    collection.update(query, passMarkEvaluation, upsert = false).map(checkUpdateWriteResult)
  }

  private def schemeToBSON(scheme: (String, Option[Result])) = scheme._2 match {
    case Some(s) => BSONDocument(scheme._1 -> s.toString)
    case _ => BSONDocument.empty
  }

  def savePassMarkScoreWithoutApplicationStatusUpdate(applicationId: String, version: String, p: RuleCategoryResult): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "passmarkEvaluation" ->
          BSONDocument("passmarkVersion" -> version, "location1Scheme1" -> p.location1Scheme1.toString).
            add(schemeToBSON("location1Scheme2" -> p.location1Scheme2)).
            add(schemeToBSON("location2Scheme1" -> p.location2Scheme1)).
            add(schemeToBSON("location2Scheme2" -> p.location2Scheme2)).
            add(schemeToBSON("alternativeScheme" -> p.alternativeScheme))
      ))

    collection.update(query, passMarkEvaluation, upsert = false).map(checkUpdateWriteResult)
  }

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: String, expireDate: Option[LocalDate]): Future[Unit] = {
    import ApplicationStatuses._

    require(List(AllocationConfirmed, AllocationUnconfirmed).contains(applicationStatus))

    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = applicationStatus.toLowerCase()

    val allocation = BSONDocument("$set" -> {
      def withExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now(),
          "allocation-expire-date" -> expireDate.get
        )

      def withoutExpireDate =
        BSONDocument(
          "applicationStatus" -> applicationStatus,
          s"progress-status.$progressStatus" -> true,
          s"progress-status-dates.$progressStatus" -> LocalDate.now()
        )

      if (expireDate.isDefined) {
        withExpireDate
      } else {
        withoutExpireDate
      }
    })

    collection.update(query, allocation, upsert = false).map(checkUpdateWriteResult)
  }

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = "awaiting_online_test_allocation"

    val deAllocationSet = BSONDocument("$set" -> {
      BSONDocument(
        "applicationStatus" -> "AWAITING_ALLOCATION",
        s"progress-status.$progressStatus" -> true,
        s"progress-status-dates.$progressStatus" -> LocalDate.now()
      )
    })

    val deAllocationUnset = BSONDocument("$unset" -> {
      BSONDocument(
        "progress-status.allocation_confirmed" -> "",
        "progress-status.allocation_unconfirmed" -> "",
        "progress-status-dates.allocation_confirmed" -> "",
        "progress-status-dates.allocation_unconfirmed" -> "",
        "allocation-expire-date" -> ""
      )
    })

    collection.update(query, deAllocationSet, upsert = false).map(checkUpdateWriteResult).flatMap(_ =>
      collection.update(query, deAllocationUnset, upsert = false).map(checkUpdateWriteResult))
  }

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument("passmarkEvaluation" -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) if doc.getAs[BSONDocument]("passmarkEvaluation").isDefined =>
        val pe = doc.getAs[BSONDocument]("passmarkEvaluation").get
        val otLocation1Scheme1Result = pe.getAs[String]("location1Scheme1").map(Result(_))
        val otLocation1Scheme2Result = pe.getAs[String]("location1Scheme2").map(Result(_))
        val otLocation2Scheme1Result = pe.getAs[String]("location2Scheme1").map(Result(_))
        val otLocation2Scheme2Result = pe.getAs[String]("location2Scheme2").map(Result(_))
        val otAlternativeResult = pe.getAs[String]("alternativeScheme").map(Result(_))

        OnlineTestPassmarkEvaluation(
          otLocation1Scheme1Result.getOrElse(throw OnlineTestFirstLocationResultNotFound(appId)),
          otLocation1Scheme2Result,
          otLocation2Scheme1Result,
          otLocation2Scheme2Result,
          otAlternativeResult
        )
      case _ => throw OnlineTestPassmarkEvaluationNotFound(appId)
    }
  }

  private def checkUpdateWriteResult(writeResult: UpdateWriteResult): Unit = {
    writeResult.errmsg.map(msg => throw new UnexpectedException(s"Database update failed: $msg"))
  }
}
