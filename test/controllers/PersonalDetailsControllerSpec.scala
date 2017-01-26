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
import model.Exceptions.CannotUpdateContactDetails
import model.PersistedObjects.{ ContactDetails, PersonalDetails }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.application.PersonalDetailsService
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class PersonalDetailsControllerSpec extends UnitWithAppSpec {

  "Personal Details Controller" should {

    "create contact details and return 201" in new TestFixture {
      val userId = "1234"
      val applicationId = "111-111"

      val request = s"""
                       |{
                       |  "firstName":"Clark",
                       |  "lastName":"Kent",
                       |  "preferredName":"Superman",
                       |  "email":"super@man.com",
                       |  "dateOfBirth":"2015-07-10",
                       |  "address": {
                       |      "line1":"North Pole 1",
                       |      "line2":"North Pole 2",
                       |      "line3":"North Pole 3",
                       |      "line4":"North Pole 4"
                       |   },
                       |  "postCode":"H0H 0H0",
                       |  "phone":"071234567",
                       |  "aLevel": true,
                       |  "stemLevel": false,
                       |  "civilServant": false
                       |}
        """.stripMargin

      when(mockPersonalDetailsService.update(any[String], any[String], any[PersonalDetails], any[ContactDetails])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result = TestPersonalDetailsController.personalDetails(userId, applicationId)(
        updatePersonalDetailsRequest(userId, applicationId)(request)
      )

      status(result) mustBe CREATED

    "find and return personal details" in new TestFixture {

      contentAsJson(savedResult) mustBe Json.parse(request)
    }

    "return a system error on invalid json" in new TestFixture {
      val result = TestPersonalDetailsController.personalDetails("1234", "111-111")(
        updatePersonalDetailsRequest("1234", "111-111")(
          s"""
           |{
           |  "wrongField1":"Clark",
           |  "wrongField2":"Kent",
           |  "preferredName":"Superman",
           |  "dateOfBirth":"2015-07-10",
           |  "address": {"line1":"North Pole"},
           |  "postCode": "H0H 0H0",
           |  "mobilePhone": "071234567"
           |}
        """.stripMargin
        )
      )

      when(mockPersonalDetailsService.update(any[String], any[String], any[PersonalDetails], any[ContactDetails])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(CannotUpdateContactDetails("appId")))

      status(result) mustBe BAD_REQUEST
    }

    "throw an exception if the department is missing when the candidate is a civil servant" in {
      intercept[IllegalArgumentException] {
        PersonalDetails("firstName", "lastName", "preferredName", new LocalDate("1990-11-25"),
          aLevel = false, stemLevel = false, civilServant = true, department = None)
      }
    }

    "throw an exception if the department is supplied when the candidate is not a civil servant" in {
      intercept[IllegalArgumentException] {
        PersonalDetails("firstName", "lastName", "preferredName", new LocalDate("1990-11-25"),
          aLevel = false, stemLevel = false, civilServant = false, department = Some("dept"))
      }
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockPersonalDetailsService = mock[PersonalDetailsService]

    object TestPersonalDetailsController extends PersonalDetailsController {
      val personalDetailsService = mockPersonalDetailsService
    }

    def updatePersonalDetailsRequest(userId: String, applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.PersonalDetailsController.personalDetails(userId, applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def findPersonalDetailsRequest(userId: String, applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.PersonalDetailsController.find(userId, applicationId).url)
    }
  }
}
