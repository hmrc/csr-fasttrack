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
import model.ApplicationStatuses
import model.Commands.{ ApplicationResponse, ProgressResponse, WithdrawApplicationRequest }
import model.Exceptions.{ ApplicationNotFound, CannotUpdateReview }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.GeneralApplicationRepository
import services.AuditService
import services.application.ApplicationService
import testkit.UnitWithAppSpec
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class ApplicationControllerSpec extends UnitWithAppSpec {

  "Create Application" should {
    "create an application" in new TestFixture {
      val applicationCreated = ApplicationResponse(applicationId, ApplicationStatuses.Created, userId,
        ProgressResponse(applicationId))

      when(mockGeneralApplicationRepository.create(any[String], any[String])).thenReturnAsync(applicationCreated)
      val result = TestApplicationController.createApplication(createApplicationRequest(
        s"""
           |{
           |  "userId":"$userId",
           |  "frameworkId":"FASTTRACK-2015"
           |}
        """.stripMargin
      ))
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationStatus").as[String] mustBe "CREATED"
      (jsonResponse \ "userId").as[String] mustBe userId

      verify(mockAuditService).logEvent(eqTo("ApplicationCreated"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return a system error on invalid json" in new TestFixture {
      val result =
        TestApplicationController.createApplication(createApplicationRequest(
          s"""
             |{
             |  "some":"$userId",
             |  "other":"FASTTRACK-2015"
             |}
          """.stripMargin
        ))

      status(result) mustBe BAD_REQUEST
    }
  }

  "Application Progress" should {
    "return the progress of an application" in new TestFixture {
      when(mockGeneralApplicationRepository.findProgress(any[String])).thenReturnAsync(
        ProgressResponse(applicationId, personalDetails = true)
      )

      val result = TestApplicationController.applicationProgress(applicationId)(applicationProgressRequest(applicationId)).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationId").as[String] mustBe applicationId
      (jsonResponse \ "personalDetails").as[Boolean] mustBe true

      status(result) mustBe OK
    }

    "return a system error when applicationId doesn't exists" in new TestFixture {
      when(mockGeneralApplicationRepository.findProgress(any[String])).thenReturn(
        Future.failed(ApplicationNotFound(applicationId))
      )

      val result = TestApplicationController.applicationProgress("1111-1234")(applicationProgressRequest("1111-1234")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Find application" should {
    "return the application" in new TestFixture {
      val applicationCreated = ApplicationResponse(applicationId, ApplicationStatuses.Created, userId,
        ProgressResponse(applicationId))

      when(mockGeneralApplicationRepository.findByUserId(any[String], any[String])).thenReturnAsync(applicationCreated)

      val result = TestApplicationController.findApplication(
        "validUser",
        "validFramework"
      )(findApplicationRequest("validUser", "validFramework")).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "applicationId").as[String] mustBe applicationId
      (jsonResponse \ "applicationStatus").as[String] mustBe "CREATED"
      (jsonResponse \ "userId").as[String] mustBe userId

      status(result) mustBe OK
    }

    "return a system error when application doesn't exists" in new TestFixture {
      when(mockGeneralApplicationRepository.findByUserId(any[String], any[String])).thenReturn(
        Future.failed(ApplicationNotFound("invalidUser"))
      )

      val result = TestApplicationController.findApplication(
        "invalidUser",
        "invalidFramework"
      )(findApplicationRequest("invalidUser", "invalidFramework")).run

      status(result) mustBe NOT_FOUND
    }
  }

  "Withdraw application" should {
    "withdraw the application" in new TestFixture {
      val result = TestApplicationController.applicationWithdraw("1111-1111")(withdrawApplicationRequest(applicationId)(
        s"""
           |{
           |  "reason":"Something",
           |  "otherReason":"Else",
           |  "withdrawer":"Candidate"
           |}
        """.stripMargin
      ))
      status(result) mustBe OK
    }
  }

  "Review application" should {
    "return 200 and mark the application as reviewed" in new TestFixture {
      val result = TestApplicationFullyMockedController.review(applicationId)(reviewApplicationRequest(applicationId)(
        s"""
           |{
           |  "flag": true
           |}
        """.stripMargin
      ))
      status(result) mustBe OK
      verify(mockAuditService).logEvent(eqTo("ApplicationReviewed"))(any[HeaderCarrier], any[RequestHeader])
    }

    "return 404 when there is an error when storing review status" in new TestFixture {
      when(mockApplicationRepository.review(eqTo(applicationId))).thenReturn(Future.failed(CannotUpdateReview(s"review $applicationId")))

      val result = TestApplicationFullyMockedController.review(applicationId)(reviewApplicationRequest(applicationId)(
        s"""
           |{
           |  "flag": true
           |}
        """.stripMargin
      ))
      status(result) mustBe NOT_FOUND
      verify(mockAuditService, times(0)).logEvent(eqTo("ApplicationReviewed"))(any[HeaderCarrier], any[RequestHeader])
    }
  }

  trait TestFixture extends TestFixtureBase {
    val applicationId = "1111-1111"
    val userId = "1234"
    val withdrawApplicationRequest = WithdrawApplicationRequest("Something", Some("Else"), "Candidate")
    val auditDetails = Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawApplicationRequest.toString)
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockApplicationService = mock[ApplicationService]
    val mockGeneralApplicationRepository = mock[GeneralApplicationRepository]
    when(mockApplicationService.withdraw(eqTo(applicationId), eqTo(withdrawApplicationRequest))).thenReturnAsync()
    when(mockApplicationRepository.review(eqTo(applicationId))).thenReturnAsync()

    object TestApplicationController extends ApplicationController {
      override val appRepository: GeneralApplicationRepository = mockGeneralApplicationRepository
      override val auditService: AuditService = mockAuditService
      override val applicationService: ApplicationService = mockApplicationService
    }

    object TestApplicationFullyMockedController extends ApplicationController {
      override val appRepository: GeneralApplicationRepository = mockApplicationRepository
      override val auditService: AuditService = mockAuditService
      override val applicationService: ApplicationService = mockApplicationService
    }

    def applicationProgressRequest(applicationId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.applicationProgress(applicationId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def findApplicationRequest(userId: String, frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ApplicationController.findApplication(userId, frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createApplicationRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.ApplicationController.createApplication().url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def withdrawApplicationRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.ApplicationController.applicationWithdraw(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def reviewApplicationRequest(applicationId: String)(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.ApplicationController.review(applicationId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
