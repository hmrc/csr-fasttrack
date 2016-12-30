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

import model.Exceptions.ApplicationNotFound
import play.api.libs.json.JsString
import reactivemongo.bson.{ BSONBoolean, BSONDocument }
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.{ DiagnosticReportingMongoRepository, GeneralApplicationMongoRepository }
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class DiagnosticReportRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"
  
  def diagnosticReportRepo = new DiagnosticReportingMongoRepository()
  def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)

  "Find by user id" should {
    "return ApplicationNotFound if there is nobody with this userId" in {
      val result = diagnosticReportRepo.findByUserIdV2("123").failed.futureValue
      result mustBe an[ApplicationNotFound]
    }

    "return user with the given user id with personal details excluded" in {
      helperRepo.collection.insert(UserWithAllDetails).futureValue

      val result = diagnosticReportRepo.findByUserIdV2("user1").futureValue
      result.size mustBe 1
      val msg = "Expected to find the userId in the result"
      result.head.value.get("userId").fold(fail(msg)){u => u mustBe JsString("user1")}
      result.head.value.get("personal-details") mustBe None
    }
  }

  private val UserWithAllDetails = BSONDocument(
    "applicationId" -> "app1",
    "userId" -> "user1",
    "frameworkId" -> "FastTrack-2015",
    "applicationStatus" -> "AWAITING_ALLOCATION",
    "progress-status" -> BSONDocument(
      "registered" -> BSONBoolean(true),
      "personal_details_completed" -> BSONBoolean(true),
      "schemes_and_locations_completed" -> BSONBoolean(true),
      "assistance_completed" -> BSONBoolean(true),
      "review_completed" -> BSONBoolean(true),
      "questionnaire" -> BSONDocument(
        "start_diversity_questionnaire" -> BSONBoolean(true),
        "diversity_questions_completed" -> BSONBoolean(true),
        "education_questions_completed" -> BSONBoolean(true),
        "occupation_questions_completed" -> BSONBoolean(true)
      ),
      "submitted" -> BSONBoolean(true),
      "online_test_invited" -> BSONBoolean(true),
      "online_test_started" -> BSONBoolean(true),
      "online_test_completed" -> BSONBoolean(true),
      "awaiting_online_test_allocation" -> BSONBoolean(true)
    ),
    "personal-details" -> BSONDocument(
      "firstName" -> "testFirst",
      "lastName" -> "testLast"
    )
  )
}
