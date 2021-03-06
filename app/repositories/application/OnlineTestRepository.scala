/*
 * Copyright 2019 HM Revenue & Customs
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

import config.MicroserviceAppConfig._
import factories.DateTimeFactory
import model.ApplicationStatuses.BSONEnumHandler
import model.EvaluationResults._
import model.Exceptions._
import model.OnlineTestCommands._
import model.PersistedObjects.{ ApplicationForNotification, ApplicationIdWithUserIdAndStatus, ExpiringOnlineTest }
import model.persisted.{ CubiksTestProfile, NotificationExpiringOnlineTest, OnlineTestPassmarkEvaluation, SchemeEvaluationResult }
import model._
import model.Adjustments._
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import repositories._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

trait OnlineTestRepository {
  def nextApplicationPendingExpiry: Future[Option[ExpiringOnlineTest]]

  def nextApplicationReadyForSendingOnlineTestResult: Future[Option[ApplicationForNotification]]

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]]

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]]

  def nextApplicationReadyForPDFReportRetrieving(): Future[Option[OnlineTestApplicationWithCubiksUser]]

  def getCubiksTestProfile(userId: String): Future[CubiksTestProfile]

  def getCubiksTestProfileByToken(token: String): Future[CubiksTestProfile]

  def getCubiksTestProfile(cubiksUserId: Int): Future[CubiksTestProfile]

  @deprecated("Use safer version with guarded statuses", "13/02/2017")
  def updateStatus(userId: String, status: ApplicationStatuses.EnumVal): Future[Unit]

  def updateStatus(userId: String, currentStatuses: List[ApplicationStatuses.EnumVal],
    newStatus: ApplicationStatuses.EnumVal): Future[Unit]

  def updateExpiryTime(userId: String, expirationDate: DateTime, limitToStatuses: List[ApplicationStatuses.EnumVal]): Future[Unit]

  def consumeToken(token: String): Future[Unit]

  def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, cubiksTestProfile: CubiksTestProfile): Future[Unit]

  def getOnlineTestApplication(appId: String): Future[Option[OnlineTestApplication]]

  def updateXMLReportSaved(applicationId: String): Future[Unit]

  def updatePDFReportSaved(applicationId: String): Future[Unit]

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]]

  def savePassMarkScore(applicationId: String, version: String, evaluationResult: List[SchemeEvaluationResult],
    applicationStatus: Option[ApplicationStatuses.EnumVal]): Future[Unit]

  def findAllPassMarkEvaluations: Future[Map[String, List[SchemeEvaluationResult]]]

  def findAllAssessmentCentreEvaluations: Future[Map[String, List[SchemeEvaluationResult]]]

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit]

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]): Future[Unit]

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation]

  def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]]

  def addReminderNotificationStatus(userId: String, notificationStatus: String): Future[Unit]

  def startOnlineTest(cubiksUserId: Int): Future[Unit]

  def completeOnlineTest(cubiksUserId: Int, assessmentId: Int, isGis: Boolean): Future[Unit]
}

// scalastyle:off number.of.methods
class OnlineTestMongoRepository(dateTime: DateTimeFactory)(implicit mongo: () => DB)
    extends ReactiveRepository[CubiksTestProfile, BSONObjectID](CollectionNames.APPLICATION, mongo,
      model.persisted.CubiksTestProfile.format, ReactiveMongoFormats.objectIdFormats)
    with OnlineTestRepository with RandomSelection with ReactiveRepositoryHelpers {

  val dateTimeFactory: DateTimeFactory = DateTimeFactory

  private def applicationStatus(status: ApplicationStatuses.EnumVal): BSONDocument = {
    import model.ApplicationStatuses._

    val flag = status match {
      case OnlineTestInvited => ProgressStatuses.OnlineTestInvitedProgress
      case OnlineTestStarted => ProgressStatuses.OnlineTestStartedProgress
      case OnlineTestCompleted => ProgressStatuses.OnlineTestCompletedProgress
      case OnlineTestExpired => ProgressStatuses.OnlineTestExpiredProgress
      case OnlineTestFailed => ProgressStatuses.OnlineTestFailedProgress
      case OnlineTestFailedNotified => ProgressStatuses.OnlineTestFailedNotifiedProgress
      case AwaitingAllocation => ProgressStatuses.AwaitingAllocationProgress
      case AwaitingAllocationNotified => ProgressStatuses.AwaitingAllocationNotifiedProgress
    }

    if (flag == ProgressStatuses.OnlineTestCompletedProgress) {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,
        "applicationStatus" -> status,
        "online-tests.completedDateTime" -> DateTime.now
      ))
    } else {
      BSONDocument("$set" -> BSONDocument(
        s"progress-status.$flag" -> true,

        "applicationStatus" -> status
      ))
    }
  }

  override def getCubiksTestProfile(userId: String): Future[CubiksTestProfile] = {
    val query = BSONDocument("userId" -> userId)
    val exception = NotFoundException(Some(s"No online test found for userId $userId"))
    getCubiksTestProfile(query, exception)
  }

  override def getCubiksTestProfileByToken(token: String): Future[CubiksTestProfile] = {
    val query = BSONDocument("online-tests.token" -> token)
    val exception = NotFoundException(Some(s"No online test found for token $token"))
    getCubiksTestProfile(query, exception)
  }

  override def getCubiksTestProfile(cubiksUserId: Int): Future[CubiksTestProfile] = {
    val query = BSONDocument("online-tests.cubiksUserId" -> cubiksUserId)
    val exception = NotFoundException(Some(s"No online test found for cubiksId $cubiksUserId"))
    getCubiksTestProfile(query, exception)
  }

  private def getCubiksTestProfile(query: BSONDocument, exception: NotFoundException): Future[CubiksTestProfile] = {
    val projection = BSONDocument("online-tests" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[BSONDocument]("online-tests").isDefined =>
        document.getAs[CubiksTestProfile]("online-tests").getOrElse(throw exception)

      case _ => throw NotFoundException()
    }
  }

  @deprecated("Use safer version with guarded statuses", "13/02/2017")
  override def updateStatus(userId: String, status: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val applicationStatusBSON = applicationStatus(status)

    collection.update(query, applicationStatusBSON, upsert = false) map {
      case r if r.n == 0 => throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      case r if r.n > 1 => throw UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
      case _ =>
    }
  }

  override def updateStatus(userId: String, currentStatuses: List[ApplicationStatuses.EnumVal],
    newStatus: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument(
      "userId" -> userId,
      "applicationStatus" -> BSONDocument("$in" -> currentStatuses)
    )

    val applicationStatusBSON = applicationStatus(newStatus)

    // TODO: Use singleUpdateValidator as it has the logic already
    collection.update(query, applicationStatusBSON, upsert = false) map {
      case r if r.n == 0 => throw new NotFoundException(s"updateStatus didn't update anything for userId:$userId")
      case r if r.n > 1 => throw UnexpectedException(s"updateStatus somehow updated more than one record for userId:$userId")
      case _ =>
    }
  }

  override def startOnlineTest(cubiksUserId: Int): Future[Unit] = {
    val query = BSONDocument("online-tests.cubiksUserId" -> cubiksUserId)
    val update = BSONDocument("$set" -> BSONDocument(
      s"applicationStatus" -> ApplicationStatuses.OnlineTestStarted,
      s"progress-status.${ProgressStatuses.OnlineTestStartedProgress}" -> true,
      "online-tests.startedDateTime" -> DateTime.now
    ))

    val validator = singleUpdateValidator(s"$cubiksUserId", actionDesc = "recording cubiks test start",
      CannotUpdateCubiksTest(s"Cant update test with cubiksId $cubiksUserId"))
    collection.update(query, update, upsert = false) map validator

  }

  override def addReminderNotificationStatus(userId: String, notificationStatus: String): Future[Unit] = {
    val query = BSONDocument("userId" -> userId)
    val updateStatus = BSONDocument("$set" -> BSONDocument(
      s"progress-status.$notificationStatus" -> true
    ))

    collection.update(query, updateStatus, upsert = false).map(_ => ())
  }

  override def updateExpiryTime(userId: String, expirationDate: DateTime,
                                limitToStatuses: List[ApplicationStatuses.EnumVal]): Future[Unit] = {
    import model.ApplicationStatuses.BSONEnumHandler

    val query = BSONDocument(
      "userId" -> userId,
      "applicationStatus" -> BSONDocument("$in" -> limitToStatuses)
    )

    val update = BSONDocument("$set" -> BSONDocument(
      "online-tests.expirationDate" -> expirationDate
    ))

    val validator = singleUpdateValidator(userId, actionDesc = "extending cubiks test",
      CannotExtendCubiksTest(s"Cannot extend test for userId $userId"))

    collection.update(query, update) map validator
  }

  override def consumeToken(token: String): Future[Unit] = {
    val query = BSONDocument(
      "online-tests.token" -> token,
      "applicationStatus" -> ApplicationStatuses.OnlineTestStarted
    )

    val applicationStatusBSON = applicationStatus(ApplicationStatuses.OnlineTestCompleted)

    collection.update(query, applicationStatusBSON, upsert = false).map { _ => () }
  }

  override def completeOnlineTest(cubiksUserId: Int, assessmentId: Int, isGis: Boolean): Future[Unit] = {
    val query = BSONDocument(
      "online-tests.cubiksUserId" -> cubiksUserId,
      "applicationStatus" -> ApplicationStatuses.OnlineTestStarted
    )

    val update = BSONDocument(
      "$addToSet" -> BSONDocument(
        "online-tests.completed" -> assessmentId
      )
    )

    val validator = singleUpdateValidator(s"$cubiksUserId", actionDesc = "recording cubiks test completion",
      CannotUpdateCubiksTest(s"Cant update test with cubiksId $cubiksUserId"))

    for {
      _ <- collection.update(query, update, upsert = false) map validator
      testProfile <- getCubiksTestProfile(cubiksUserId)
      allAssessments = if (isGis) cubiksGatewayConfig.gisAssessmentIds else cubiksGatewayConfig.nonGisAssessmentIds
      isCompleted = testProfile.hasAllAssessmentsCompleted(allAssessments)
      _ <- if (isCompleted) completeAllAssessments(cubiksUserId) else Future.successful(())
    } yield {}
  }

  private def completeAllAssessments(cubiksUserId: Int): Future[Unit] = {
    val query = BSONDocument(
      "online-tests.cubiksUserId" -> cubiksUserId,
      "applicationStatus" -> ApplicationStatuses.OnlineTestStarted
    )

    val update = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> ApplicationStatuses.OnlineTestCompleted,
      s"progress-status.${ProgressStatuses.OnlineTestCompletedProgress}" -> true,
      "online-tests.completedDateTime" -> DateTime.now
    ))

    val validator = singleUpdateValidator(s"$cubiksUserId", actionDesc = "recording cubiks test completion",
      CannotUpdateCubiksTest(s"Cant update test with cubiksId $cubiksUserId"))
    collection.update(query, update, upsert = false) map validator
  }

  override def storeOnlineTestProfileAndUpdateStatusToInvite(applicationId: String, cubiksTestProfile: CubiksTestProfile): Future[Unit] = {
    import model.ProgressStatuses._

    val query = BSONDocument("applicationId" -> applicationId)

    val applicationStatusBSON = BSONDocument("$unset" -> BSONDocument(
      s"progress-status.$OnlineTestStartedProgress" -> "",
      s"progress-status.$OnlineTestCompletedProgress" -> "",
      s"progress-status.$OnlineTestExpiredProgress" -> "",
      s"progress-status.$AwaitingOnlineTestReevaluationProgress" -> "",
      s"progress-status.$OnlineTestFailedProgress" -> "",
      s"progress-status.$OnlineTestFailedNotifiedProgress" -> "",
      s"progress-status.$AwaitingAllocationProgress" -> "",
      s"progress-status.$AwaitingAllocationNotifiedProgress" -> "",
      "passmarkEvaluation" -> ""
    )) ++ BSONDocument("$set" -> BSONDocument(
      s"progress-status.$OnlineTestInvitedProgress" -> true,
      "applicationStatus" -> ApplicationStatuses.OnlineTestInvited,
      "online-tests" -> cubiksTestProfile
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
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
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestInvited),
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestStarted)
      ))
    ))

    selectRandom(query).map(_.map(bsonDocToExpiringOnlineTest))
  }

  def nextApplicationReadyForSendingOnlineTestResult: Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestFailed),
        BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAllocation)
      )),
      BSONDocument("online-tests.pdfReportSaved" -> true)
    ))
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }

  def nextApplicationReadyForOnlineTesting: Future[Option[OnlineTestApplication]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.Submitted),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> false),
        BSONDocument("$and" -> BSONArray(
          BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
          BSONDocument("assistance-details.adjustmentsConfirmed" -> true)
        ))
      ))
    ))
    selectRandom(query).map(_.map(bsonDocToOnlineTestApplication))
  }

  def nextApplicationReadyForReportRetriving: Future[Option[OnlineTestApplicationWithCubiksUser]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestCompleted),
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

  override def nextTestForReminder(reminder: ReminderNotice): Future[Option[NotificationExpiringOnlineTest]] = {

    val progressStatusQuery = BSONDocument("$and" -> BSONArray(
      BSONDocument("$or" -> BSONArray(
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestInvited),
        BSONDocument("applicationStatus" -> ApplicationStatuses.OnlineTestStarted)
      )),
      BSONDocument("online-tests.expirationDate" ->
        BSONDocument("$lte" -> dateTimeFactory.nowLocalTimeZone.plusHours(reminder.hoursBeforeReminder)) // Serialises to UTC.
      ),
      BSONDocument(s"progress-status.${reminder.notificationStatus}" -> BSONDocument("$ne" -> true))
    ))
    selectRandom(progressStatusQuery).map(_.map(NotificationExpiringOnlineTest.fromBson))
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
    val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
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
    val needsSupportForOnlineAssessment = ad.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment")).get
    val guaranteedInterview = ad.flatMap(_.getAs[Boolean]("guaranteedInterview")).getOrElse(false)

    val onlineTestsAdjustments = ad.flatMap(_.getAs[AdjustmentDetail]("onlineTests"))

    OnlineTestApplication(applicationId, applicationStatus, userId, guaranteedInterview,
      needsSupportForOnlineAssessment, preferredName, onlineTestsAdjustments)
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
    updateFlag(applicationId, "online-tests.xmlReportSaved", value = true)
  }

  def updatePDFReportSaved(applicationId: String): Future[Unit] = {
    updateFlag(applicationId, "online-tests.pdfReportSaved", value = true)
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
      if (status.n > 1) throw UnexpectedException(s"updateStatus updated more than one record for applicationId:$applicationId")
    }
  }

  def nextApplicationPassMarkProcessing(currentVersion: String): Future[Option[ApplicationIdWithUserIdAndStatus]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("$or" -> BSONArray(
        BSONDocument(
          "applicationStatus" -> ApplicationStatuses.OnlineTestCompleted,
          "online-tests.xmlReportSaved" -> true,
          "passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> false)
        ),
        BSONDocument(
          "applicationStatus" -> BSONDocument("$nin" -> List(ApplicationStatuses.OnlineTestFailed,
            ApplicationStatuses.OnlineTestFailedNotified, ApplicationStatuses.AssessmentCentreFailed,
            ApplicationStatuses.AssessmentCentreFailedNotified, ApplicationStatuses.Withdrawn)),
          "online-tests.xmlReportSaved" -> true,
          "passmarkEvaluation.passmarkVersion" -> BSONDocument("$exists" -> true),
          "passmarkEvaluation.passmarkVersion" -> BSONDocument("$ne" -> currentVersion)
        )
      )),
      BSONDocument("noOnlineTestReEvaluation" -> BSONDocument("$ne" -> true))
    ))

    selectRandom(query).map(_.map { doc =>
      val applicationId = doc.getAs[String]("applicationId").get
      val userId = doc.getAs[String]("userId").get
      val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus")
        .getOrElse(throw new IllegalStateException("applicationStatus must be defined"))

      ApplicationIdWithUserIdAndStatus(applicationId, userId, applicationStatus)
    })
  }

  def savePassMarkScore(applicationId: String, version: String, evaluationResult: List[SchemeEvaluationResult],
                        newApplicationStatus: Option[ApplicationStatuses.EnumVal]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateQuery = newApplicationStatus match {
      case Some(status) =>
        val progressStatus = status match {
          case ApplicationStatuses.AwaitingOnlineTestReevaluation => ProgressStatuses.AwaitingOnlineTestReevaluationProgress
          case ApplicationStatuses.OnlineTestFailed => ProgressStatuses.OnlineTestFailedProgress
          case ApplicationStatuses.AwaitingAllocation => ProgressStatuses.AwaitingAllocationProgress
          case _ => throw new IllegalStateException(s"Saving assessment evaluation result to " +
            s"appId=$applicationId is not supported for $status")
        }

        BSONDocument("$set" ->
          BSONDocument(
            "passmarkEvaluation" -> BSONDocument(
              "passmarkVersion" -> version,
              "result" -> evaluationResult
            ),
            "applicationStatus" -> status,
            s"progress-status.$progressStatus" -> true
          ))
      case None =>
        BSONDocument("$set" ->
          BSONDocument(
            "passmarkEvaluation" -> BSONDocument(
              "passmarkVersion" -> version,
              "result" -> evaluationResult
            )
          ))
    }

    collection.update(query, updateQuery, upsert = false).map(checkUpdateWriteResult)
  }

  def saveCandidateAllocationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal,
    expireDate: Option[LocalDate]): Future[Unit] = {
    import ApplicationStatuses._

    val query = BSONDocument("applicationId" -> applicationId)

    val progressStatus = Try(applicationStatus match {
      case AllocationConfirmed => ProgressStatuses.AllocationConfirmedProgress
      case AllocationUnconfirmed => ProgressStatuses.AllocationUnconfirmedProgress
      case AllocationExpired => ProgressStatuses.AllocationExpiredProgress
    })

    val update = (progressStatus: String) => BSONDocument("$set" -> {
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

    Future.fromTry(progressStatus).flatMap { progress =>
      collection.update(query, update(progress), upsert = false).map(checkUpdateWriteResult)
    }
  }

  def removeCandidateAllocationStatus(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val update = BSONDocument(
      "$unset" -> BSONDocument(
        s"progress-status.${ProgressStatuses.AllocationConfirmedProgress}" -> "",
        s"progress-status.${ProgressStatuses.AllocationUnconfirmedProgress}" -> "",
        s"progress-status-dates.${ProgressStatuses.AllocationConfirmedProgress}" -> "",
        s"progress-status-dates.${ProgressStatuses.AllocationUnconfirmedProgress}" -> "",
        "allocation-expire-date" -> ""
      ),
      "$set" -> BSONDocument(
        "applicationStatus" -> ApplicationStatuses.AwaitingAllocationNotified,
        s"progress-status.${ProgressStatuses.AwaitingAllocationProgress}" -> true,
        s"progress-status-dates.${ProgressStatuses.AwaitingAllocationProgress}" -> LocalDate.now()
      )
    )

    val validator = singleUpdateValidator(applicationId, "resetting assessment centre allocation status",
      UnexpectedException("failed to reset assessment centre allocation status"))

    collection.update(query, update, upsert = false) map validator
  }

  def findPassmarkEvaluation(appId: String): Future[OnlineTestPassmarkEvaluation] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument("passmarkEvaluation" -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) if doc.getAs[BSONDocument]("passmarkEvaluation").isDefined =>
        doc.getAs[OnlineTestPassmarkEvaluation]("passmarkEvaluation").get
      case _ => throw OnlineTestPassmarkEvaluationNotFound(appId)
    }
  }

  def findAllPassMarkEvaluations: Future[Map[String, List[SchemeEvaluationResult]]] = {
    val query = BSONDocument()
    val projection = BSONDocument(
      "applicationId" -> 1,
      "passmarkEvaluation" -> 1
    )
    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc  =>
        val appId = doc.getAs[String]("applicationId").get
        val passMarkEvaluationResults = doc.getAs[BSONDocument]("passmarkEvaluation") flatMap { root =>
          root.getAs[List[SchemeEvaluationResult]]("result")
        }
        appId -> passMarkEvaluationResults.getOrElse(Nil)
      }.toMap
    }
  }

  def findAllAssessmentCentreEvaluations: Future[Map[String, List[SchemeEvaluationResult]]] = {
    val query = BSONDocument()
    val projection = BSONDocument(
      "applicationId" -> 1,
      "assessment-centre-passmark-evaluation" -> 1
    )
    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val appId = doc.getAs[String]("applicationId").get
        val assessmentCentreEvaluation: Option[BSONDocument] = doc.getAs[BSONDocument]("assessment-centre-passmark-evaluation")

        val overallEvaluation:Option[List[SchemeEvaluationResult]] = assessmentCentreEvaluation.flatMap { root =>
          root.getAs[List[SchemeEvaluationResult]]("overall-evaluation")
        }
        appId -> overallEvaluation.getOrElse(Nil)
      }.toMap
    }
  }

  private def checkUpdateWriteResult(writeResult: UpdateWriteResult): Unit = {
    writeResult.errmsg.map(msg => throw UnexpectedException(s"Database update failed: $msg"))
  }
}
