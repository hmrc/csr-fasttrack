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

import config._
import connectors.ExchangeObjects._
import connectors.{ CubiksGatewayClient, EmailClient }
import mocks.application.OnlineTestInMemoryRepository
import model.ApplicationStatuses
import model.Commands.Address
import model.OnlineTestCommands.OnlineTestApplication
import model.PersistedObjects.ContactDetails
import model.exchange.OnlineTest
import model.persisted.CubiksTestProfile
import org.joda.time.DateTime
import org.mockito.Matchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.OnlineTestRepository
import repositories.OnlineTestPDFReportRepository
import services.onlinetesting.{ OnlineTestExtensionService, OnlineTestService }
import testkit.MockitoImplicits.OngoingStubbingExtensionUnit
import testkit.UnitWithAppSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestControllerSpec extends UnitWithAppSpec {
  "Get Online Test" should {

    "get an online test" in new TestFixture {
      when(mockOnlineTestService.getOnlineTest(any[String])).thenReturn(Future.successful(
        OnlineTest(123, DateTime.now, DateTime.now, "http://www.google.co.uk", "token")
      ))

      val userId = ""
      val result = TestOnlineTestController.getOnlineTest(userId)(createOnlineTestRequest(userId)).run
      val jsonResponse = contentAsJson(result)

      (jsonResponse \ "onlineTestLink").as[String] must be("http://www.google.co.uk")
    }
  }

  "Update Online Test Status" should {

    "update an online test status" in new TestFixture {

      when(mockOnlineTestRepository.updateStatus("1234", ApplicationStatuses.OnlineTestStarted)).thenReturn(Future.successful(unit))

      val result = TestOnlineTestController.onlineTestStatusUpdate("1234")(createOnlineTestStatusRequest(
        "1234",
        s"""
           |{
           |  "status":"ONLINE_TEST_STARTED"
           |}
        """.stripMargin
      ))

      status(result) must be(200)
    }
  }

  "Asking for a userId with a token" should {

    "return the userId if the token is valid" in new TestFixture {
      val token = "1234"
      when(mockOnlineTestRepository.consumeToken(token)).thenReturn(Future.successful(unit))
      val result = TestOnlineTestController.completeOnlineTestByToken(token)(createOnlineTestCompleteRequest(token)).run

      status(result) must be(200)
    }
  }

  "Reset online tests" should {

    "fail if application not found" in new TestFixture {
      val appId = ""
      when(mockOnlineTestRepository.getOnlineTestApplication(appId)).thenReturn(Future.successful(None))
      val result = TestOnlineTestController.resetOnlineTests(appId)(createResetOnlineTestRequest(appId)).run

      status(result) must be(NOT_FOUND)
    }

    "successfully reset the online test status" in new TestFixture {
      val appId = "appId"
      val testApplication = OnlineTestApplication(appId, ApplicationStatuses.OnlineTestStarted, "userId", guaranteedInterview = false,
        needsAdjustments = false, preferredName = "prefName", adjustmentDetail = None
      )

      when(mockOnlineTestRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(testApplication)))
      when(mockOnlineTestService.registerAndInviteApplicant(testApplication)).thenReturn(Future.successful(unit))


      val result = TestOnlineTestController.resetOnlineTests(appId)(createResetOnlineTestRequest(appId)).run

      status(result) must be(OK)
    }

  }

  "Extend online tests" should {

    "fail if application not found" in new TestFixture {
      val appId = ""
      val extraDays = 5
      when(mockOnlineTestRepository.getOnlineTestApplication(appId)).thenReturn(Future.successful(None))
      val result = TestOnlineTestController.extendOnlineTests(appId)(createExtendOnlineTests(appId, extraDays))

      status(result) must be(NOT_FOUND)
    }

    "successfully reset the online test status" in new TestFixture {
      val appId = ""
      val extraDays = 5
      val result = TestOnlineTestController.extendOnlineTests(appId)(createExtendOnlineTests(appId, extraDays))

      status(result) must be(OK)
    }

  }

  "Get PDF Report" should {
    "return a valid report if one exists" in new TestFixture {
      val result = TestOnlineTestController.getPDFReport(hasPDFReportApplicationId)(FakeRequest())

      status(result) mustBe OK
      headers(result).get("Content-Type").get mustEqual "application/pdf"
      headers(result).get("Content-Disposition").get must startWith("attachment;")
      headers(result).get("Content-Disposition").get must include("""filename="report-""")
      contentAsBytes(result) mustEqual testPDFContents
    }

    "return not found if one does not exist" in new TestFixture {
      val result = TestOnlineTestController.getPDFReport(hasNoPDFReportApplicationId)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }
  }

  trait TestFixture extends TestFixtureBase {

    implicit val hc = HeaderCarrier()

    val hasPDFReportApplicationId = "has-pdf-report-application-id"
    val hasNoPDFReportApplicationId = "has-no-pdf-report-application-id"
    val testPDFContents = Array[Byte](0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20)

    val onlineTestExtensionServiceMock = mock[OnlineTestExtensionService]
    when(onlineTestExtensionServiceMock.extendExpiryTime(any(), any())).thenReturnAsync()

    val onlineTestPDFReportRepoMock = mock[OnlineTestPDFReportRepository]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    val emailClientMock = mock[EmailClient]

    val mockOnlineTestRepository = mock[OnlineTestRepository]
    val mockOnlineTestService = mock[OnlineTestService]

    def onlineTest(appId: String) = OnlineTestApplication(appId, ApplicationStatuses.Submitted, "",
      guaranteedInterview = false, needsAdjustments = false, "", None)

    when(onlineTestPDFReportRepoMock.hasReport(any())).thenReturn(Future.successful(true))
    when(onlineTestPDFReportRepoMock.get(hasPDFReportApplicationId)).thenReturn(Future.successful(
      Some(testPDFContents)
    ))
    when(onlineTestPDFReportRepoMock.get(hasNoPDFReportApplicationId)).thenReturn(Future.successful(
      None
    ))

    when(emailClientMock.sendOnlineTestInvitation(any(), any(), any())(any())).thenReturn(
      Future.successful(())
    )

    when(cubiksGatewayClientMock.registerApplicant(any())(any())).thenReturn(Future.successful(Registration(0)))
    when(cubiksGatewayClientMock.inviteApplicant(any())(any())).thenReturn(Future.successful(Invitation(0, "", "", "", "", 0)))

    when(mockOnlineTestRepository.storeOnlineTestProfileAndUpdateStatusToInvite(any[String], any[CubiksTestProfile]))
      .thenReturn(Future.successful(()))

      when(mockOnlineTestRepository.getOnlineTestApplication(any[String])).thenReturn(Future.successful(Some(onlineTest("123"))))


    object TestOnlineTestController extends OnlineTestController {
      override val onlineTestingRepo = mockOnlineTestRepository
      override val onlineTestingService = mockOnlineTestService
      override val onlineTestExtensionService = onlineTestExtensionServiceMock
      override val onlineTestPDFReportRepo = onlineTestPDFReportRepoMock
    }

    def createOnlineTestRequest(userId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.OnlineTestController.getOnlineTest(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }

    def createOnlineTestStatusRequest(userId: String, jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.onlineTestStatusUpdate(userId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    def createOnlineTestCompleteRequest(token: String) = {
      FakeRequest(Helpers.PUT, controllers.routes.OnlineTestController.completeOnlineTestByToken(token).url, FakeHeaders(), "")
    }

    def createResetOnlineTestRequest(appId: String) = {
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.resetOnlineTests(appId).url, FakeHeaders(), "")
    }

    def createExtendOnlineTests(appId: String, extraDays: Int) = {
      val json = Json.parse(s"""{"extraDays":$extraDays}""")
      FakeRequest(Helpers.POST, controllers.routes.OnlineTestController.extendOnlineTests(appId).url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
