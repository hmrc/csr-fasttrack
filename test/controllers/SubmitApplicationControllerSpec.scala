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

import akka.stream.Materializer
import config.TestFixtureBase
import connectors.EmailClient
import common.Constants.No
import model.Commands.{ Address, PostCode }
import model.PersistedObjects.ContactDetails
import model.{ AssessmentCentreIndicator, Scheme }
import model.exchange.AssistanceDetails
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{ OneAppPerSuite, PlaySpec }
import play.api.mvc._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.FrameworkRepository.{ CandidateHighestQualification, Framework, Location, Region }
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import services.assistancedetails.AssistanceDetailsService
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.language.postfixOps

class SubmitApplicationControllerSpec extends PlaySpec with Results with OneAppPerSuite {

  implicit val mat: Materializer = app.materializer

  "Submit Application" should {

    "return 400 when the application is not valid" in new TestFixture {
      when(mockPdRepo.find(any[String])).thenReturn(Future.successful(PersonalDetails("", "", "", LocalDate.now)))

      val result = TestSubmitApplicationController.submitApplication("user1", "app1")(submitApplicationRequest("user1", "app1")).run

      status(result) mustBe BAD_REQUEST
      verify(mockAuditService, never).logEvent(any[String])(any[HeaderCarrier], any[RequestHeader])
    }

    "return 200 when the application is valid" in new TestFixture {

      when(mockPdRepo.find(any[String])).thenReturn(Future.successful(PersonalDetails("fname", "lname", "prefname", LocalDate.now)))

      when(mockAppRepo.submit(any[String])).thenReturn(Future.successful(unit))
      when(mockAssessmentCentreIndicatorRepo.calculateIndicator(any[Option[String]])).thenReturn(
        AssessmentCentreIndicator("London", "London")
      )
      when(mockAppRepo.updateAssessmentCentreIndicator(any[String], any[AssessmentCentreIndicator])).thenReturn(Future.successful(unit))
      when(mockEmailClient.sendApplicationSubmittedConfirmation(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(unit))

      val result = TestSubmitApplicationController.submitApplication("user1", "app1")(submitApplicationRequest("user1", "app1")).run

      status(result) mustBe OK
      verify(mockAuditService, times(2)).logEvent(any[String])(any[HeaderCarrier], any[RequestHeader])

    }

  }

  trait TestFixture extends TestFixtureBase {
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockPdRepo = mock[PersonalDetailsRepository]
    val mockAdService = mock[AssistanceDetailsService]
    val mockCdRepo = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]
    val mockAssessmentCentreIndicatorRepo = mock[AssessmentCentreIndicatorRepository]

    object TestSubmitApplicationController extends SubmitApplicationController {
      override val appRepository: GeneralApplicationRepository = mockAppRepo
      override val pdRepository: PersonalDetailsRepository = mockPdRepo
      override val adService: AssistanceDetailsService = mockAdService
      override val cdRepository: ContactDetailsRepository = mockCdRepo
      override val auditService: AuditService = mockAuditService
      override val emailClient: EmailClient = mockEmailClient
      override val assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository = mockAssessmentCentreIndicatorRepo
    }

    val address = Address("line1", Some("line2"), Some("line3"), Some("line4"))

    when(mockAdService.find(any[String], any[String])).thenReturn(Future.successful(AssistanceDetails(
      hasDisability = No,
      hasDisabilityDescription = None, guaranteedInterview = None, needsSupportForOnlineAssessment = false,
      needsSupportForOnlineAssessmentDescription = None, needsSupportAtVenue = false, needsSupportAtVenueDescription = None
    )))

    when(mockCdRepo.find(any[String])).thenReturn(Future.successful(ContactDetails(false, address, Some("QQ1 1QQ"): Option[PostCode],
      None, "test@test.com", phone = None)))

    when(mockAppRepo.getSchemes(any[String])).thenReturn(Future.successful(
      List(Scheme.Business)
    ))

    when(mockAppRepo.getSchemeLocations(any[String])).thenReturn(Future.successful(
      List("123456")
    ))

    def submitApplicationRequest(userId: String, applicationId: String) = {
      FakeRequest(Helpers.PUT, controllers.routes.SubmitApplicationController.submitApplication(userId, applicationId).url, FakeHeaders(), "")
    }
  }
}
