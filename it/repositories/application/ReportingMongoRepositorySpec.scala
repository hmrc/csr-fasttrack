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

    def testDataRepo = new TestDataMongoRepository()

  val testDataGeneratorService = TestDataGeneratorService

  "Candidate Progress Report" must {
    "for an application with all fields" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createApplicationWithAllFields(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head mustBe CandidateProgressReportItem(userId, appId, Some("submitted"),
        List(SchemeType.DiplomaticService, SchemeType.GovernmentOperationalResearchService), Some("Yes"),
        Some("No"), Some("No"), None, Some("No"), Some("Yes"), Some("No"), Some("Yes"), Some("No"), Some("Yes"),
        Some("1234567"), None, ApplicationRoute.Faststream)
    }

    "for the minimum application" in {
      val userId = generateUUID()
      val appId = generateUUID()
      testDataRepo.createMinimumApplication(userId, appId, "FastStream-2016").futureValue

      val result = repository.candidateProgressReport("FastStream-2016").futureValue

      result must not be empty
      result.head must be(CandidateProgressReportItem(userId, appId, Some("registered"),
        List.empty[SchemeType], None, None, None, None, None, None, None, None, None, None, None, None, ApplicationRoute.Faststream)
      )
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
