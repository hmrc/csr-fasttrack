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

package controllers

import config.TestFixtureBase
import connectors.AuthProviderClient
import model.Commands.{ Candidate, SearchCandidate }
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.ContactDetailsRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.search.SearchForApplicantService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class SearchForApplicantsControllerSpec extends UnitWithAppSpec {

  "Search for applicants controller" should {
    "return 404 NOT_FOUND if no applicants are found" in new TestFixture {
      when(mockSearchForApplicantService.findByCriteria(any[SearchCandidate])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty))

      val requestJson =
        s"""
           |{
           |  "firstOrPreferredName":"Clark",
           |  "lastName":"Kent",
           |  "dateOfBirth":"2015-07-10",
           |  "postCode":"H0H 0H0"
           |}
        """.stripMargin
      val result = TestSearchForApplicantsController.findByCriteria()(createRequest(requestJson))
      status(result) mustBe NOT_FOUND
    }

    "return 413 REQUEST_ENTITY_TOO_LARGE if the max result set is exceeded" in new TestFixture {
      val candidates = List.fill(TestSearchForApplicantsController.MAX_RESULTS + 1)(
        Candidate(userId = "userId", applicationId = None, email = None, firstName = None, lastName = None,
          dateOfBirth = None, address = None, postCode = None)
      )

      when(mockSearchForApplicantService.findByCriteria(any[SearchCandidate])(any[HeaderCarrier]))
        .thenReturn(Future.successful(candidates))

      val requestJson = "{\"lastName\":\"Kent\"}"
      val result = TestSearchForApplicantsController.findByCriteria()(createRequest(requestJson))
      status(result) mustBe REQUEST_ENTITY_TOO_LARGE
    }

    "return 200 OK if the search is successful" in new TestFixture {
      val candidates = List(Candidate(userId = "userId", applicationId = Some("appId"), email = Some("super@man.com"),
        firstName = Some("Clark"), lastName = Some("Kent"), dateOfBirth = None, address = None, postCode = Some("H0H 0H0")))

      when(mockSearchForApplicantService.findByCriteria(any[SearchCandidate])(any[HeaderCarrier]))
        .thenReturn(Future.successful(candidates))

      val requestJson = "{\"lastName\":\"Kent\"}"
      val result = TestSearchForApplicantsController.findByCriteria()(createRequest(requestJson))
      status(result) mustBe OK
      import model.Commands.Implicits.candidateFormat
      contentAsJson(result).as[List[Candidate]] mustBe candidates
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockGeneralApplicationRepository = mock[GeneralApplicationRepository]
    val mockPersonalDetailsRepository = mock[PersonalDetailsRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockAuthProviderClient = mock[AuthProviderClient]
    val mockSearchForApplicantService = mock[SearchForApplicantService]

    object TestSearchForApplicantsController extends SearchForApplicantsController {
      val appRepository = mockGeneralApplicationRepository
      val psRepository = mockPersonalDetailsRepository
      val cdRepository = mockContactDetailsRepository
      val authProviderClient = mockAuthProviderClient
      val searchForApplicantService = mockSearchForApplicantService
    }

    def createRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.POST, controllers.routes.SearchForApplicantsController.findByCriteria().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
