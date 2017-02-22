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

package services.application

import connectors.AuthProviderClient
import connectors.ExchangeObjects.{ UpdateDetailsRequest, AuthProviderUserDetails }
import model.Commands._
import model.Exceptions.NotFoundException
import model.PersistedObjects.ContactDetails
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.ContactDetailsRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import services.applicationassessment.ApplicationAssessmentService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class ApplicationServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "withdraw an application" should {
    "work and log audit event" in new ApplicationServiceFixture {
      val result = applicationService.withdraw(ApplicationId, withdrawApplicationRequest)
      result.futureValue mustBe (())

      verify(appAssessServiceMock).deleteApplicationAssessment(eqTo(ApplicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", withdrawAuditDetails)
    }
  }

  "withdraw an application" should {
    "work when there is a not found exception deleting application assessment and log audit event" in new ApplicationServiceFixture {
      when(appAssessServiceMock.removeFromApplicationAssessmentSlot(eqTo(ApplicationId))).thenReturn(
        Future.failed(new NotFoundException(s"No application assessments were found with applicationId $ApplicationId"))
      )

      val result = applicationService.withdraw(ApplicationId, withdrawApplicationRequest)
      result.futureValue mustBe (())

      verify(appAssessServiceMock).deleteApplicationAssessment(eqTo(ApplicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", withdrawAuditDetails)
    }
  }

  "editDetails" should {
    "edit candidate details in an happy path scenario" in new ApplicationServiceFixture {
      when(contactDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentContactDetails))
      when(personalDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentPersonalDetails))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(Some(currentUser)))
      when(authProviderClientMock.update(any[String], any[UpdateDetailsRequest])(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(contactDetailsRepositoryMock.update(any[String], any[ContactDetails])).thenReturn(Future.successful(()))
      when(personalDetailsRepositoryMock.update(any[String], any[String], any[PersonalDetails]))
        .thenReturn(Future.successful(()))

      val result = applicationService.editDetails(userId, ApplicationId, editedDetails)
      result.futureValue mustBe (())

      verify(contactDetailsRepositoryMock).find(eqTo(userId))
      verify(personalDetailsRepositoryMock).find(ApplicationId)
      verify(authProviderClientMock).findByUserId(eqTo(userId))(any[HeaderCarrier])

      verify(authProviderClientMock).update(eqTo(userId), eqTo(expectedUpdateDetailsRequest))(any[HeaderCarrier])
      verify(personalDetailsRepositoryMock).update(eqTo(ApplicationId), eqTo(userId), eqTo(expectedPersonalDetails))
      verify(contactDetailsRepositoryMock).update(eqTo(userId), eqTo(expectedContactDetails))
      verify(auditServiceMock).logEventNoRequest("ApplicationEdited", editAuditDetails)
      verifyNoMoreInteractions(personalDetailsRepositoryMock, contactDetailsRepositoryMock, authProviderClientMock, auditServiceMock)
    }

    "stop executing if an operation fails" in new ApplicationServiceFixture {
      when(contactDetailsRepositoryMock.find(any[String])).thenReturn(failedFuture)
      when(personalDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentPersonalDetails))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(Some(currentUser)))
      when(contactDetailsRepositoryMock.update(any[String], any[ContactDetails])).thenReturn(Future.successful(()))
      when(authProviderClientMock.update(any[String], any[UpdateDetailsRequest])(any[HeaderCarrier])).thenReturn(Future.successful(()))
      when(personalDetailsRepositoryMock.update(any[String], any[String], any[PersonalDetails]))
        .thenReturn(Future.successful(()))

      val result = applicationService.editDetails(userId, ApplicationId, editedDetails).failed.futureValue
      result mustBe (error)

      verify(contactDetailsRepositoryMock).find(eqTo(userId))
      verify(personalDetailsRepositoryMock).find(ApplicationId)
      verify(authProviderClientMock).findByUserId(eqTo(userId))(any[HeaderCarrier])

      verifyNoMoreInteractions(personalDetailsRepositoryMock, contactDetailsRepositoryMock, authProviderClientMock, auditServiceMock)
    }

  }

  trait ApplicationServiceFixture {
    import model.PersistedObjects.ContactDetails
    implicit val hc = HeaderCarrier()

    val ApplicationId = "1111-1111"
    val userId = "a22b033f-846c-4712-9d19-357819f7491c"
    val withdrawApplicationRequest = WithdrawApplicationRequest("reason", Some("other reason"), "Candidate")

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val appAssessServiceMock = mock[ApplicationAssessmentService]
    val auditServiceMock = mock[AuditService]
    val contactDetailsRepositoryMock = mock[ContactDetailsRepository]
    val personalDetailsRepositoryMock = mock[PersonalDetailsRepository]
    val authProviderClientMock = mock[AuthProviderClient]

    when(appRepositoryMock.withdraw(eqTo(ApplicationId), eqTo(withdrawApplicationRequest))).thenReturn(Future.successful(()))
    when(appAssessServiceMock.removeFromApplicationAssessmentSlot(eqTo(ApplicationId))).thenReturn(Future.successful(()))
    when(appAssessServiceMock.deleteApplicationAssessment(eqTo(ApplicationId))).thenReturn(Future.successful(()))

    val applicationService = new ApplicationService {
      val appRepository = appRepositoryMock
      val appAssessService = appAssessServiceMock
      val auditService = auditServiceMock
      val contactDetailsRepository = contactDetailsRepositoryMock
      val personalDetailsRepository = personalDetailsRepositoryMock
      val authProviderClient = authProviderClientMock
    }

    val currentAddress = Address("First Line", Some("line2"), None, None)
    val newAddress = Address("new 1 Line", Some("new 2 Line"), Some("new 3 Line"), None)
    val currentContactDetails = ContactDetails(false, currentAddress, Some("N1 3GF"), None, "mazurka@jjj.yyy", Some("0988726353"))
    val currentPersonalDetails = PersonalDetails(
      firstName = "Marcel",
      lastName = "Cerdan",
      preferredName = "Casablanca Clouter",
      dateOfBirth = LocalDate.parse("1916-07-22")
    )
    val currentUser = AuthProviderUserDetails(
      firstName = "Marcel",
      lastName = "Cerdan",
      email = Some("marcel.cerdan@email.con"),
      preferredName = Some("Casablanca Clouter"),
      role = Some("role"),
      disabled = Some(false)
    )

    val editedDetails = CandidateEditableDetails(
      firstName = "Salvador",
      lastName = "Sanchez",
      preferredName = "Chava",
      dateOfBirth = LocalDate.parse("1959-01-26"),
      address = newAddress,
      outsideUk = Some(true),
      country = Some("Mexico"),
      postCode = None,
      phone = Some("2222909090")
    )

    val expectedPersonalDetails = PersonalDetails(
      firstName = "Salvador",
      lastName = "Sanchez",
      preferredName = "Chava",
      dateOfBirth = LocalDate.parse("1959-01-26")
    )

    val expectedUpdateDetailsRequest = UpdateDetailsRequest(
      editedDetails.firstName,
      editedDetails.lastName,
      currentUser.email,
      Some(editedDetails.preferredName),
      currentUser.role,
      currentUser.disabled
    )

    val expectedContactDetails = ContactDetails(true, newAddress, None, Some("Mexico"), "mazurka@jjj.yyy", Some("2222909090"))

    val error = new RuntimeException("Something bad happened")
    val failedFuture = Future.failed(error)

    val withdrawAuditDetails = Map("applicationId" -> ApplicationId, "withdrawRequest" -> withdrawApplicationRequest.toString)
    val editAuditDetails = Map("applicationId" -> ApplicationId, "editRequest" -> editedDetails.toString)
  }
}
