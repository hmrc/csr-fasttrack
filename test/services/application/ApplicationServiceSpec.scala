/*
 * Copyright 2018 HM Revenue & Customs
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

import connectors.{ AuthProviderClient, CSREmailClient }
import connectors.ExchangeObjects.{ AuthProviderUserDetails, UpdateDetailsRequest }
import model.ApplicationStatuses
import model.Commands._
import model.Exceptions.NotFoundException
import model.PersistedObjects.{ ContactDetails, ExpiringAllocation }
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
import services.applicationassessment.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }
import testkit.MockitoImplicits._
import uk.gov.hmrc.http.HeaderCarrier

class ApplicationServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "withdraw an application" should {
    "work and log audit event" in new ApplicationServiceFixture {
      val result = applicationService.withdraw(applicationId, withdrawApplicationRequest)
      result.futureValue mustBe unit

      verify(appAssessServiceMock).deleteAssessmentCentreAllocation(eqTo(applicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", withdrawAuditDetails)
    }
  }

  "withdraw an application" should {
    "work when there is a not found exception deleting application assessment and log audit event" in new ApplicationServiceFixture {
      when(appAssessServiceMock.removeFromAssessmentCentreSlot(eqTo(applicationId))).thenReturn(
        Future.failed(new NotFoundException(s"No application assessments were found with applicationId $applicationId"))
      )

      val result = applicationService.withdraw(applicationId, withdrawApplicationRequest)
      result.futureValue mustBe unit

      verify(appAssessServiceMock).deleteAssessmentCentreAllocation(eqTo(applicationId))
      verify(auditServiceMock).logEventNoRequest("ApplicationWithdrawn", withdrawAuditDetails)
    }
  }

  "editDetails" should {
    "edit candidate details in an happy path scenario" in new ApplicationServiceFixture {
      when(contactDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentContactDetails))
      when(personalDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentPersonalDetails))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(Some(currentUser)))
      when(authProviderClientMock.update(any[String], any[UpdateDetailsRequest])(any[HeaderCarrier])).thenReturnAsync()
      when(contactDetailsRepositoryMock.update(any[String], any[ContactDetails])).thenReturnAsync()
      when(personalDetailsRepositoryMock.update(any[String], any[String], any[PersonalDetails]))
        .thenReturn(Future.successful(()))

      val result = applicationService.editDetails(userId, applicationId, editedDetails)
      result.futureValue mustBe unit

      verify(contactDetailsRepositoryMock).find(eqTo(userId))
      verify(personalDetailsRepositoryMock).find(applicationId)
      verify(authProviderClientMock).findByUserId(eqTo(userId))(any[HeaderCarrier])

      verify(authProviderClientMock).update(eqTo(userId), eqTo(expectedUpdateDetailsRequest))(any[HeaderCarrier])
      verify(personalDetailsRepositoryMock).update(eqTo(applicationId), eqTo(userId), eqTo(expectedPersonalDetails))
      verify(contactDetailsRepositoryMock).update(eqTo(userId), eqTo(expectedContactDetails))
      verify(auditServiceMock).logEventNoRequest("ApplicationEdited", editAuditDetails)
      verifyNoMoreInteractions(personalDetailsRepositoryMock, contactDetailsRepositoryMock, authProviderClientMock, auditServiceMock)
    }

    "stop executing if an operation fails" in new ApplicationServiceFixture {
      when(contactDetailsRepositoryMock.find(any[String])).thenReturn(failedFuture)
      when(personalDetailsRepositoryMock.find(any[String])).thenReturn(Future.successful(currentPersonalDetails))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(Some(currentUser)))
      when(contactDetailsRepositoryMock.update(any[String], any[ContactDetails])).thenReturnAsync()
      when(authProviderClientMock.update(any[String], any[UpdateDetailsRequest])(any[HeaderCarrier])).thenReturnAsync()
      when(personalDetailsRepositoryMock.update(any[String], any[String], any[PersonalDetails]))
        .thenReturn(Future.successful(()))

      val result = applicationService.editDetails(userId, applicationId, editedDetails).failed.futureValue
      result mustBe error

      verify(contactDetailsRepositoryMock).find(eqTo(userId))
      verify(personalDetailsRepositoryMock).find(applicationId)
      verify(authProviderClientMock).findByUserId(eqTo(userId))(any[HeaderCarrier])

      verifyNoMoreInteractions(personalDetailsRepositoryMock, contactDetailsRepositoryMock, authProviderClientMock, auditServiceMock)
    }
  }

  "process expired applications" should {
    "set candidates who have not confirmed their booking to allocation expired and " +
      "inform the candidate with an email" in new ApplicationServiceFixture {
      when(appRepositoryMock.nextApplicationPendingAllocationExpiry).thenReturnAsync(Some(expiringAllocation))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturnAsync(Some(currentUser))
      when(appRepositoryMock.updateStatus(any[String], any[model.ApplicationStatuses.EnumVal])).thenReturnAsync()
      when(emailClientMock.sendAssessmentCentreExpired(any[String], any[String])(any[HeaderCarrier])).thenReturnAsync()

      val result = applicationService.processExpiredApplications()
      result.futureValue mustBe unit

      verify(appRepositoryMock).updateStatus(eqTo(applicationId), eqTo(ApplicationStatuses.AllocationExpired))
      verify(emailClientMock).sendAssessmentCentreExpired(any[String], any[String])(any[HeaderCarrier])
    }

    "throw an exception if the candidate does not have an email address" in new ApplicationServiceFixture {
      when(appRepositoryMock.nextApplicationPendingAllocationExpiry).thenReturn(Future.successful(Some(expiringAllocation)))
      val user = Some(currentUser.copy(email = None))
      when(authProviderClientMock.findByUserId(any[String])(any[HeaderCarrier])).thenReturn(Future.successful(user))
      when(appRepositoryMock.updateStatus(any[String], any[model.ApplicationStatuses.EnumVal])).thenReturnAsync()
      when(emailClientMock.sendAssessmentCentreExpired(any[String], any[String])(any[HeaderCarrier])).thenReturnAsync()

      val result = applicationService.processExpiredApplications().failed.futureValue
      result mustBe a[IllegalStateException]

      verify(appRepositoryMock).updateStatus(eqTo(applicationId), eqTo(ApplicationStatuses.AllocationExpired))
      // Exception is thrown before the method is called
      verify(emailClientMock, never()).sendAssessmentCentreExpired(any[String], any[String])(any[HeaderCarrier])
    }
  }

  trait ApplicationServiceFixture {
    import model.PersistedObjects.ContactDetails
    implicit val hc = HeaderCarrier()

    val unit = ()
    val applicationId = "1111-1111"
    val userId = "a22b033f-846c-4712-9d19-357819f7491c"
    val withdrawApplicationRequest = WithdrawApplicationRequest("reason", Some("other reason"), "Candidate")
    val expiringAllocation = ExpiringAllocation(applicationId , userId)

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val appAssessServiceMock = mock[AssessmentCentreService]
    val auditServiceMock = mock[AuditService]
    val contactDetailsRepositoryMock = mock[ContactDetailsRepository]
    val personalDetailsRepositoryMock = mock[PersonalDetailsRepository]
    val authProviderClientMock = mock[AuthProviderClient]
    val emailClientMock = mock[CSREmailClient]

    when(appRepositoryMock.withdraw(eqTo(applicationId), eqTo(withdrawApplicationRequest))).thenReturnAsync()
    when(appAssessServiceMock.removeFromAssessmentCentreSlot(eqTo(applicationId))).thenReturnAsync()
    when(appAssessServiceMock.deleteAssessmentCentreAllocation(eqTo(applicationId))).thenReturnAsync()

    val applicationService = new ApplicationService {
      val appRepository = appRepositoryMock
      val appAssessService = appAssessServiceMock
      val auditService = auditServiceMock
      val contactDetailsRepository = contactDetailsRepositoryMock
      val personalDetailsRepository = personalDetailsRepositoryMock
      val authProviderClient = authProviderClientMock
      val emailClient = emailClientMock
    }

    val currentAddress = Address("First Line", Some("line2"), None, None)
    val newAddress = Address("new 1 Line", Some("new 2 Line"), Some("new 3 Line"), None)
    val currentContactDetails = ContactDetails(outsideUk = false, currentAddress, Some("N1 3GF"), None, "mazurka@jjj.yyy", Some("0988726353"))
    val currentPersonalDetails = PersonalDetails(
      firstName = "Marcel",
      lastName = "Cerdan",
      preferredName = "Casablanca Clouter",
      dateOfBirth = LocalDate.parse("1916-07-22")
    )
    val currentUser = AuthProviderUserDetails(
      firstName = "Marcel",
      lastName = "Cerdan",
      email = Some("marcel.cerdan@email.com"),
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

    val expectedContactDetails = ContactDetails(outsideUk = true, newAddress, None, Some("Mexico"), "mazurka@jjj.yyy", Some("2222909090"))

    val error = new RuntimeException("Something bad happened")
    val failedFuture = Future.failed(error)

    val withdrawAuditDetails = Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawApplicationRequest.toString)
    val editAuditDetails = Map("applicationId" -> applicationId, "editRequest" -> editedDetails.toString)
  }
}
