/*
 * Copyright 2019 HM Revenue & Customs
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

package services.allocation

import connectors.EmailClient
import model.Commands.{ Address, AssessmentCentreAllocation }
import model.PersistedObjects.{ AllocatedCandidate, ContactDetails, PersonalDetailsWithUserId }
import org.joda.time.{ DateTime, LocalDate }
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import repositories.application.CandidateAllocationRepository
import repositories.{ AssessmentCentreAllocationRepository, ContactDetailsRepository }
import services.AuditService

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class CandidateAllocationServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  val candidate = AllocatedCandidate(PersonalDetailsWithUserId("Alice", "userId"), "app1", LocalDate.now().plusDays(3))
  val applicationAssessment = AssessmentCentreAllocation("app1", "London 1", LocalDate.now().plusDays(3), "AM", 1, confirmed = false)
  val candidateContact = ContactDetails(outsideUk = false, Address("Aldwych road"), Some("AB CDE"), None, "alice@test.com", None)

  val caRepositoryMock = mock[CandidateAllocationRepository]
  val cdRepositoryMock = mock[ContactDetailsRepository]
  val aaRepositoryMock = mock[AssessmentCentreAllocationRepository]
  val emailClientMock = mock[EmailClient]
  val auditServiceMock = mock[AuditService]

  val HeaderCarrier = new HeaderCarrier()
  implicit val headerCarrier = HeaderCarrier

  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  val service = new CandidateAllocationService {
    val caRepository = caRepositoryMock
    val cdRepository = cdRepositoryMock
    val aaRepository = aaRepositoryMock
    val emailClient = emailClientMock
    val auditService = auditServiceMock

    override val headerCarrier = HeaderCarrier
  }

  "Next unconfirmed candidate for sending a reminder" should {
    "return nothing when there is no unconfirmed candidates" in {
      when(caRepositoryMock.nextUnconfirmedCandidateToSendReminder(3)).thenReturn(Future.successful(None))

      val allocatedCandidate = service.nextUnconfirmedCandidateForSendingReminder.futureValue

      allocatedCandidate must be(empty)
    }

    "return an allocated candidate when there is one" in {
      val candidate = AllocatedCandidate(PersonalDetailsWithUserId("Bob", "userId"), "app1", LocalDate.now())
      when(caRepositoryMock.nextUnconfirmedCandidateToSendReminder(3)).thenReturn(Future.successful(Some(candidate)))

      val allocatedCandidate = service.nextUnconfirmedCandidateForSendingReminder.futureValue

      allocatedCandidate must not be empty
      allocatedCandidate.get must be(candidate)
    }
  }

  "A reminder email" should {
    "be sent to the next candidate and the candidate should be marked as contacted" in {
      when(cdRepositoryMock.find(candidate.candidateDetails.userId)).thenReturn(Future.successful(candidateContact))
      when(aaRepositoryMock.findOne(candidate.applicationId)).thenReturn(Future.successful(applicationAssessment))
      when(emailClientMock.sendReminderToConfirmAttendance(
        candidateContact.email,
        candidate.candidateDetails.preferredName,
        applicationAssessment.assessmentDateTime,
        candidate.expireDate
      )).thenReturn(Future.successful(()))
      when(caRepositoryMock.saveAllocationReminderSentDate(eqTo("app1"), any[DateTime])).thenReturn(Future.successful(()))

      val result = service.sendEmailConfirmationReminder(candidate).futureValue

      result must be(())
      verify(emailClientMock).sendReminderToConfirmAttendance(
        candidateContact.email,
        candidate.candidateDetails.preferredName, applicationAssessment.assessmentDateTime, candidate.expireDate
      )
      verify(caRepositoryMock).saveAllocationReminderSentDate(eqTo("app1"), any[DateTime])
      verify(auditServiceMock).logEventNoRequest("AllocationReminderEmailSent", Map(
        "userId" -> "userId",
        "email" -> "alice@test.com"
      ))
    }
  }
}
