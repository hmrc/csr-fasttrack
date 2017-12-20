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

package services.onlinetesting

import connectors.EmailClient
import model.ApplicationStatuses
import model.Commands.Address
import model.PersistedObjects.{ ApplicationForNotification, ContactDetails }
import org.mockito.Matchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import org.scalatestplus.play.PlaySpec
import repositories.ContactDetailsRepository
import repositories.application.OnlineTestRepository
import services.AuditService
import services.onlinetesting.SendOnlineTestResultServiceImpl.{ OnlineTestResultReadyEmailSent, OnlineTestResultReadyUpdated }
import testkit.MockitoSugar

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SendOnlinetestResultServiceImplSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "Notify candidate about online test result" should {

    "email the candidate about their failed online test and mark that the application has been contacted" in new Fixture {
      service.notifyCandidateAboutOnlineTestResult(failedTest).futureValue

      verify(emailClient).sendOnlineTestResultReady(eqTo(emailAddress), eqTo(preferredName))(any[HeaderCarrier])
      verify(otRepository).updateStatus(
        userId,
        List(ApplicationStatuses.OnlineTestFailed, ApplicationStatuses.AwaitingAllocation),
        ApplicationStatuses.OnlineTestFailedNotified
      )
    }

    "email the candidate about their passed online test and mark that the application has been contacted" in new Fixture {
      service.notifyCandidateAboutOnlineTestResult(passedTest).futureValue

      verify(emailClient).sendOnlineTestResultReady(eqTo(emailAddress), eqTo(preferredName))(any[HeaderCarrier])
      verify(otRepository).updateStatus(
        userId,
        List(ApplicationStatuses.OnlineTestFailed, ApplicationStatuses.AwaitingAllocation),
        ApplicationStatuses.AwaitingAllocationNotified
      )
    }

    "do not update that the application has been conctacted when the email service fails" in new Fixture {
      doThrowAsync().when(emailClient).sendOnlineTestResultReady(any(), any())(any[HeaderCarrier])

      val result = service.notifyCandidateAboutOnlineTestResult(failedTest).failed.futureValue

      result mustBe an[Exception]
      verifyZeroInteractions(otRepository)
    }

    "attach a 'header carrier' with the current timestamp" in new Fixture {
      // Test ensures we're not making the mistake of caching the HeaderCarrier, which
      // would result in an incorrect timestamp being passed on to other components.
      val hc1 = HeaderCarrier(nsStamp = 1)
      val hc2 = HeaderCarrier(nsStamp = 2)
      val hcs = List(hc1, hc2).iterator
      override def hc = hcs.next()

      service.notifyCandidateAboutOnlineTestResult(passedTest).futureValue
      verify(emailClient).sendOnlineTestResultReady(any(), any())(eqTo(hc1))

      service.notifyCandidateAboutOnlineTestResult(passedTest).futureValue
      verify(emailClient).sendOnlineTestResultReady(any(), any())(eqTo(hc2))
    }

    "Emit audit events" in new Fixture {
      service.notifyCandidateAboutOnlineTestResult(passedTest).futureValue

      verify(audit).logEventNoRequest(
        OnlineTestResultReadyEmailSent.toString,
        Map("userId" -> userId, "email" -> emailAddress)
      )
      verify(audit).logEventNoRequest(
        OnlineTestResultReadyUpdated.toString,
        Map("userId" -> userId)
      )
    }
  }

  // The call to `Logger.info` within our implementation appears to add sufficient latency
  // to cause timeouts using the default configuration for the `futureValue` helper method.
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(7, Seconds)))

  trait Fixture {
    val applicationId = "abc"
    val userId = "xyz"
    val preferredName = "Jon"
    val emailAddress = "jon@test.com"
    val contactDetails = ContactDetails(outsideUk = false, Address("line 1"), Some("HP27 9JU"), None, emailAddress, None)
    val failedTest = ApplicationForNotification(applicationId, userId, preferredName, ApplicationStatuses.OnlineTestFailed)
    val passedTest = ApplicationForNotification(applicationId, userId, preferredName, ApplicationStatuses.AwaitingAllocation)
    def hc = HeaderCarrier()

    val ec = scala.concurrent.ExecutionContext.Implicits.global
    val otRepository = mock[OnlineTestRepository]
    val cdRepository = mock[ContactDetailsRepository]
    val emailClient = mock[EmailClient]
    val audit = mock[AuditService]
    val service = new SendOnlineTestResultServiceImpl(otRepository, cdRepository, emailClient, audit, hc)(ec)

    when(cdRepository.find(any[String])).thenReturn(Future.successful(contactDetails))
    when(otRepository.updateStatus(any[String], any[List[ApplicationStatuses.EnumVal]],
      any[ApplicationStatuses.EnumVal])).thenReturn(Future.successful(()))
    when(emailClient.sendOnlineTestResultReady(any[String], any[String])(any[HeaderCarrier])).thenReturn(Future.successful(()))
  }
}
