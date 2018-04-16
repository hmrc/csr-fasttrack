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

package repositories.application

import factories.{ DateTimeFactory, UUIDFactory }
import model.ApplicationStatuses._
import model.Exceptions.{ AdjustmentsCommentNotFound, LocationPreferencesNotFound, NotFoundException, SchemePreferencesNotFound }
import model._
import model.commands.ApplicationStatusDetails
import model.persisted.{ OnlineTestPassmarkEvaluation, SchemeEvaluationResult }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.{ BSONDateTimeHandler, BSONLocalDateHandler, CollectionNames }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class GeneralApplicationMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION

  def repository = new GeneralApplicationMongoRepository(GBTimeZoneService)

  "General Application repository" must {
    "Update schemes locations and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val schemeLocations = List("3", "1", "2")

      repository.updateSchemeLocations(appId, schemeLocations).futureValue
      val result = repository.getSchemeLocations(appId).futureValue

      result must contain theSameElementsInOrderAs schemeLocations

      val progress = repository.findProgress(appId).futureValue
      progress.hasSchemeLocations mustBe true
    }

    "Remove scheme locations and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val schemeLocations = List("3", "1", "2")

      repository.updateSchemeLocations(appId, schemeLocations).futureValue
      val result = repository.getSchemeLocations(appId).futureValue
      result must contain theSameElementsInOrderAs schemeLocations

      repository.removeSchemeLocations(appId).futureValue
      val progress = repository.findProgress(appId).futureValue
      progress.hasSchemeLocations mustBe false

      an[LocationPreferencesNotFound] must be thrownBy {
        Await.result(repository.getSchemeLocations(appId), 5 seconds)
      }
    }

    "Update schemes and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val schemes = List(Scheme.DigitalAndTechnology, Scheme.ProjectDelivery, Scheme.Business)

      repository.updateSchemes(appId, schemes).futureValue
      val result = repository.getSchemes(appId).futureValue

      result must contain theSameElementsInOrderAs schemes

      val progress = repository.findProgress(appId).futureValue
      progress.hasSchemes mustBe true
    }

    "Remove schemes and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val schemes = List(Scheme.DigitalAndTechnology, Scheme.ProjectDelivery, Scheme.Business)

      repository.updateSchemes(appId, schemes).futureValue
      val resultedSchemes = repository.getSchemes(appId).futureValue
      resultedSchemes must contain theSameElementsInOrderAs schemes

      repository.removeSchemes(appId).futureValue
      val progress = repository.findProgress(appId).futureValue
      progress.hasSchemes mustBe false

      an[SchemePreferencesNotFound] must be thrownBy {
        Await.result(repository.getSchemes(appId), 5 seconds)
      }
    }

    "return scheme preferences not found exception" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")

      an[SchemePreferencesNotFound] mustBe thrownBy {
        Await.result(repository.getSchemes(appId), 5 seconds)
      }
    }

    "return location preferences not found exception" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")

      an[LocationPreferencesNotFound] mustBe thrownBy {
        Await.result(repository.getSchemeLocations(appId), 5 seconds)
      }
    }

    "return None for adjustments" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val result = repository.findAdjustments(appId).futureValue
      result mustBe None
    }

    "update adjustments and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      val adjustments = Adjustments(
        typeOfAdjustments = Some(List("timeExtension")),
        onlineTests = Some(AdjustmentDetail(Some(9)))
      )
      createMinimumApplication(userId, appId, "FastTrack")
      repository.confirmAdjustments(appId, adjustments).futureValue

      val result = repository.findAdjustments(appId).futureValue
      result mustBe Some(adjustments.copy(adjustmentsConfirmed = Some(true)))
    }

    "return None for adjustments comments" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      an[AdjustmentsCommentNotFound] mustBe thrownBy {
        Await.result(repository.findAdjustmentsComment(appId), 5 seconds)
      }
    }

    "update comments and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      val adjustmentsComment = AdjustmentsComment("adjustment comment")
      createMinimumApplication(userId, appId, "FastTrack")
      repository.updateAdjustmentsComment(appId, adjustmentsComment).futureValue

      val result = repository.findAdjustmentsComment(appId).futureValue
      result mustBe adjustmentsComment
    }

  }

  "get progress status dates" must {
    "correctly return the latest application status and progress status date" in {
      val progressStatuses = Map (
        ProgressStatuses.RegisteredProgress -> DateTime.now.minusDays(12),
        ProgressStatuses.PersonalDetailsCompletedProgress -> DateTime.now.minusDays(11),
        ProgressStatuses.SchemeLocationsCompletedProgress -> DateTime.now.minusDays(10),
        ProgressStatuses.SchemesPreferencesCompletedProgress -> DateTime.now.minusDays(9),
        ProgressStatuses.AssistanceDetailsCompletedProgress -> DateTime.now.minusDays(9),
        ProgressStatuses.ReviewCompletedProgress -> DateTime.now.minusDays(8),
        ProgressStatuses.StartDiversityQuestionnaireProgress -> DateTime.now.minusDays(7),
        ProgressStatuses.DiversityQuestionsCompletedProgress -> DateTime.now.minusDays(6),
        ProgressStatuses.EducationQuestionsCompletedProgress -> DateTime.now.minusDays(5),
        ProgressStatuses.OccupationQuestionsCompletedProgress -> DateTime.now.minusDays(4),
        ProgressStatuses.SubmittedProgress -> DateTime.now.minusDays(3)
      )
      createApplicationWithAllFields("userId", "appId", "frameworkId", ApplicationStatuses.Submitted, buildProgressStatusBSON(progressStatuses))

      val result = repository.findApplicationStatusDetails("appId").futureValue

      inside (result) {
        case ApplicationStatusDetails(status, statusDate, overrideSubmissionDeadline) =>
          status mustBe ApplicationStatuses.Submitted
          statusDate mustEqual Some(progressStatuses(ProgressStatuses.SubmittedProgress).withZone(DateTimeZone.UTC))
          overrideSubmissionDeadline mustBe None
      }
    }

    "return the date the personal details were completed if the application is not submitted yet" in {
      val progressStatuses = Map (
        ProgressStatuses.RegisteredProgress -> DateTime.now.minusDays(12),
        ProgressStatuses.PersonalDetailsCompletedProgress -> DateTime.now.minusDays(11),
        ProgressStatuses.SchemeLocationsCompletedProgress -> DateTime.now.minusDays(10),
        ProgressStatuses.SchemesPreferencesCompletedProgress -> DateTime.now.minusDays(9),
        ProgressStatuses.AssistanceDetailsCompletedProgress -> DateTime.now.minusDays(9),
        ProgressStatuses.ReviewCompletedProgress -> DateTime.now.minusDays(8),
        ProgressStatuses.StartDiversityQuestionnaireProgress -> DateTime.now.minusDays(7),
        ProgressStatuses.DiversityQuestionsCompletedProgress -> DateTime.now.minusDays(6),
        ProgressStatuses.EducationQuestionsCompletedProgress -> DateTime.now.minusDays(5),
        ProgressStatuses.OccupationQuestionsCompletedProgress -> DateTime.now.minusDays(4)
     )

     createApplicationWithAllFields("userId", "appId", "frameworkId", ApplicationStatuses.Created,
        buildProgressStatusBSON(progressStatuses))

      val result = repository.findApplicationStatusDetails("appId").futureValue

      inside (result) {
        case ApplicationStatusDetails(status, statusDate, overrideSubmissionDeadline) =>
          status mustBe ApplicationStatuses.Created
          statusDate mustBe Some(progressStatuses(ProgressStatuses.PersonalDetailsCompletedProgress).withZone(DateTimeZone.UTC))
          overrideSubmissionDeadline mustBe None
      }
    }
  }

  "Progress to assessment centre allocation" must {
    "update the passmarks and application status" in {
      val helperRepo = new OnlineTestMongoRepository(DateTimeFactory)
      createMinimumApplication("userId", "appId", "FastTrack")
      val evaluation = SchemeEvaluationResult(Scheme.Business, EvaluationResults.Green) :: Nil
      val version = "version1"

      repository.progressToAssessmentCentre("appId", evaluation, version).futureValue

      val appStatus = repository.findApplicationStatusDetails("appId").futureValue
      appStatus.status mustBe ApplicationStatuses.AwaitingAllocationNotified
      val progress = repository.findProgress("appId").futureValue
      progress.onlineTest.awaitingAllocationNotified mustBe true
      val evaluationResults = helperRepo.findPassmarkEvaluation("appId").futureValue

      evaluationResults mustBe OnlineTestPassmarkEvaluation(version, evaluation)

    }

    "do nothing if the application has already progressed to awaiting allocation" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", ApplicationStatuses.AwaitingAllocationNotified,
        buildProgressStatusBSON(Map(ProgressStatuses.AwaitingAllocationNotifiedProgress -> DateTime.now)))

      val evaluation = SchemeEvaluationResult(Scheme.Business, EvaluationResults.Green) :: Nil
      val version = "version1"
      a[NotFoundException] mustBe thrownBy {
        Await.result(repository.progressToAssessmentCentre("appId", evaluation, version), 5 seconds)
      }
    }
  }

  "Allocation Expiry" must {
    "find candidates that are eligible for expiry" in {
      val appBsonDoc = createApplicationBSON("userId", "appId", "frameworkId",
        ApplicationStatuses.AllocationUnconfirmed,
        buildProgressStatusBSON(Map(ProgressStatuses.AllocationUnconfirmedProgress -> DateTime.now))
      ) ++ BSONDocument("allocation-expire-date" -> LocalDate.now().minusDays(1))
      createApplicationWithAppBSON(appBsonDoc)
      val appForExpiryOpt = repository.nextApplicationPendingAllocationExpiry.futureValue
      appForExpiryOpt.isDefined mustBe true
    }

    "not return candidate that hasn't expired" in {
      val appBsonDoc1 = createApplicationBSON("userId", "appId", "frameworkId",
        ApplicationStatuses.AllocationUnconfirmed,
        buildProgressStatusBSON(Map(ProgressStatuses.AllocationUnconfirmedProgress -> DateTime.now))
      ) ++ BSONDocument("allocation-expire-date" -> LocalDate.now())
      val appBsonDoc2 = createApplicationBSON("userId2", "appId2", "frameworkId",
        ApplicationStatuses.AllocationUnconfirmed,
        buildProgressStatusBSON(Map(ProgressStatuses.AllocationUnconfirmedProgress -> DateTime.now))
      ) ++ BSONDocument("allocation-expire-date" -> LocalDate.now().plusDays(1))

      createApplicationWithAppBSON(appBsonDoc1)
      createApplicationWithAppBSON(appBsonDoc2)

      val appForExpiryOpt = repository.nextApplicationPendingAllocationExpiry.futureValue
      appForExpiryOpt.isDefined mustBe false
    }
  }

  def createApplicationBSON(userId: String, appId: String, frameworkId: String,
                            appStatus: ApplicationStatuses.EnumVal = ApplicationStatuses.Submitted,
                            progressStatusBSON: BSONDocument = buildProgressStatusBSON()): BSONDocument = {
    BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> appStatus,
      "framework-preferences" -> BSONDocument(
        "firstLocation" -> BSONDocument(
          "region" -> "Region1",
          "location" -> "Location1",
          "firstFramework" -> "Commercial",
          "secondFramework" -> "Digital and technology"
        ),
        "secondLocation" -> BSONDocument(
          "location" -> "Location2",
          "firstFramework" -> "Business",
          "secondFramework" -> "Finance"
        ),
        "alternatives" -> BSONDocument(
          "location" -> true,
          "framework" -> true
        )
      ),
      "personal-details" -> BSONDocument(
        "aLevel" -> true,
        "stemLevel" -> true
      ),
      "assistance-details" -> BSONDocument(
        "hasDisability" -> "No",
        "needsSupportForOnlineAssessment" -> false,
        "needsSupportAtVenue" -> false,
        "guaranteedInterview" -> false
      ),
      "issue" -> "this candidate has changed the email"
    ) ++ progressStatusBSON
  }

  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: ApplicationStatuses.EnumVal = ApplicationStatuses.Submitted,
    progressStatusBSON: BSONDocument = buildProgressStatusBSON()
  ) = {
    val application = createApplicationBSON(userId, appId, frameworkId, appStatus, progressStatusBSON)

    repository.collection.insert(application).futureValue
  }

  def createApplicationWithAppBSON(applicationBson: BSONDocument) = {
    repository.collection.insert(applicationBson).futureValue
  }

  private def buildProgressStatusBSON(statusesAndDates: Map[ProgressStatuses.EnumVal, DateTime] =
    Map((ProgressStatuses.RegisteredProgress, DateTime.now))
  ): BSONDocument = {
    val dates = statusesAndDates.foldLeft(BSONDocument()) { (doc, map) =>
     doc ++ BSONDocument(s"${map._1}" -> map._2)
    }

    val statuses = statusesAndDates.keys.foldLeft(BSONDocument()) { (doc, status) =>
      doc ++ BSONDocument(s"$status" -> true)
    }

    BSONDocument(
      "progress-status" -> statuses,
      "progress-status-timestamp" -> dates
    )
  }

  def createMinimumApplication(userId: String, appId: String, frameworkId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> userId,
      "frameworkId" -> frameworkId
    )).futureValue
  }
}
