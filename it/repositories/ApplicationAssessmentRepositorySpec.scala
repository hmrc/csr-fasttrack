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

import model.Exceptions.NotFoundException
import org.joda.time.LocalDate
import reactivemongo.bson.BSONDocument
//import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.play.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec

class ApplicationAssessmentRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION_ASSESSMENT
  def repository = new AssessmentCentreAllocationMongoRepository()

  "Application Assessment repository" should {
    "create indexes for the repository" in {
      val repo = repositories.assessmentCentreAllocationRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("venue", "date", "session", "slot"))
      indexes must contain (List("applicationId"))
      indexes.size mustBe 3
    }

    "return an applicant's assessment details if one has been inserted" in {
      val appId = "appId"
      val venue = "Test Venue 1"
      val session = "AM"
      val slot = 1
      val date = LocalDate.parse("2015-04-01")
      createApplicationAssessment(appId, venue, date, session, slot, confirmed = false)

      val result = repository.findAll.futureValue

      result.size mustBe 1
      val appAssessment = result.head
      appAssessment.applicationId mustBe appId
      appAssessment.venue mustBe venue
      appAssessment.date mustBe date
      appAssessment.session mustBe session
      appAssessment.confirmed mustBe false
    }

    "return an application's assessment details by venue and date, if one has been inserted" in {
      val appId = "appId"
      val venue = "Test Venue 1"
      val session = "AM"
      val slot = 1
      val date = LocalDate.parse("2015-04-01")

      createApplicationAssessment(appId, venue, date, session, slot, confirmed = false)

      val result = repository.findAllForDate(venue, date).futureValue

      result.size mustBe 1
      val appAssessment = result.head
      appAssessment.applicationId mustBe appId
      appAssessment.venue mustBe venue
      appAssessment.date mustBe date
      appAssessment.session mustBe session
      appAssessment.confirmed mustBe false
    }

    "return an application's assessment details by applicationId, if one has been inserted" in {
      val appId = "appId-5"
      val venue = "Test Venue X"
      val session = "PM"
      val slot = 1
      val date = LocalDate.parse("2015-04-05")

      createApplicationAssessment(appId, venue, date, session, slot, confirmed = false)

      val result = repository.find(appId).futureValue

      result.isDefined mustBe true
      val appAssessment = result.get
      appAssessment.applicationId mustBe appId
      appAssessment.venue mustBe venue
      appAssessment.date mustBe date
      appAssessment.session mustBe session
      appAssessment.confirmed mustBe false
    }

    "return a None when no application assessment has been inserted and looked up by applicationId" in {
      val appId = "appId-5"
      repository.find(appId).futureValue mustBe None
    }

    "delete an application assessment when one exists" in {
      val appId = "appId-6"
      val venue = "Test Venue X"
      val session = "PM"
      val slot = 1
      val date = LocalDate.parse("2015-04-05")

      createApplicationAssessment(appId, venue, date, session, slot, confirmed = false)

      repository.delete(appId).futureValue

      val checkResult = repository.collection.find(BSONDocument("applicationId" ->  appId)).one[BSONDocument].futureValue

      checkResult mustBe None
    }

    "throw an exception when trying to delete an application assessment that does not exist" in {
      val result = repository.delete("appid-7")
      result.failed.futureValue mustBe a[NotFoundException]
    }
  }

  def createApplicationAssessment(applicationId: String, venue: String, date: LocalDate, session: String, slot: Int,
                                  confirmed: Boolean) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> applicationId,
      "venue" -> venue,
      "date" -> date,
      "session" -> session,
      "slot" -> slot,
      "confirmed" -> confirmed
    )).futureValue
  }
}
