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

import common.Constants.{ No, Yes }
import factories.UUIDFactory
import model.ApplicationStatuses._
import model.ReportExchangeObjects.CandidateProgressReportItem
import model.Exceptions.{ AdjustmentsCommentNotFound, LocationPreferencesNotFound, SchemePreferencesNotFound }
import model._
import model.commands.ApplicationStatusDetails
import org.joda.time.{ DateTime, DateTimeZone }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.{ BSONDateTimeHandler, CollectionNames }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

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
    }

    "Update schemes and retrieve" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")
      val schemes = List(Scheme.DigitalAndTechnology, Scheme.ProjectDelivery, Scheme.Business)

      repository.updateSchemes(appId, schemes).futureValue
      val result = repository.getSchemes(appId).futureValue

      result must contain theSameElementsInOrderAs schemes
    }

    "return scheme preferences not found exception" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")

      an[SchemePreferencesNotFound] must be thrownBy {
        Await.result(repository.getSchemes(appId), 5 seconds)
      }
    }

    "return location preferences not found exception" in {
      val userId = generateUUID()
      val appId = generateUUID()
      createMinimumApplication(userId, appId, "FastTrack")

      an[LocationPreferencesNotFound] must be thrownBy {
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

  def createApplicationWithAllFields(userId: String, appId: String, frameworkId: String,
    appStatus: ApplicationStatuses.EnumVal = ApplicationStatuses.Submitted,
    progressStatusBSON: BSONDocument = buildProgressStatusBSON()
  ) = {
    val application = BSONDocument(
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

    repository.collection.insert(application).futureValue
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
