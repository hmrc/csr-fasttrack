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

package services.onlinetesting

import connectors.EmailClient
import model.{ ApplicationStatuses, FirstReminder, ReminderNotice }
import model.Commands.Address
import model.PersistedObjects.{ ContactDetails, ExpiringOnlineTest }
import model.persisted.NotificationExpiringOnlineTest
import org.joda.time.DateTime
import org.mockito.Matchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Span }
import org.scalatestplus.play.PlaySpec
import repositories.ContactDetailsRepository
import repositories.application.OnlineTestRepository
import services.AuditService
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

class OnlineTestExpiryServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "when processing the next expiring test" should {
    "do nothing when there are no expiring tests" in new ProcessNextExpiredFixture {
      when(otRepository.nextApplicationPendingExpiry).thenReturnAsync(None)

      service.processNextExpiredTest().futureValue mustBe unit
    }

    "process the next expiring test" in new ProcessNextExpiredFixture {
      when(otRepository.nextApplicationPendingExpiry).thenReturnAsync(Some(expiringTest))

      service.processNextExpiredTest().futureValue mustBe unit

      verify(service).processExpiredTest(expiringTest)
    }
  }

  "when processing an expiring test" should {
    "email the candidate about their expired online test" in new ProcessExpiredFixture {
      service.processExpiredTest(expiringTest).futureValue mustBe unit

      verify(service).emailCandidate(expiringTest, emailAddress)
    }

    "update the application status on success" in new ProcessExpiredFixture {
      service.processExpiredTest(expiringTest).futureValue mustBe unit

      verify(service).commitExpiredStatus(expiringTest)
    }

    "not update the application status on failure" in new ProcessExpiredFixture {
      doThrowAsync().when(service).emailCandidate(any(), any())

      val result = service.processExpiredTest(expiringTest).failed.futureValue

      result mustBe an[Exception]
      verify(service).emailCandidate(expiringTest, emailAddress)
      verify(service, never).commitExpiredStatus(expiringTest)
    }
  }

  "when notify a candidate with a reminder for an expiring test" should {
    "do nothing when there are no notification to be sent" in new ProcessNextExpiredFixture {
      when(otRepository.nextTestForReminder(any[ReminderNotice])).thenReturnAsync(None)

      service.processNextTestForReminder(FirstReminder).futureValue mustBe unit

      verify(otRepository).nextTestForReminder(FirstReminder)
      verifyNoMoreInteractions(otRepository)
      verifyZeroInteractions(emailClient, cdRepository, audit)
    }

    "process the next test for reminder" in new ProcessNextExpiredFixture {
      when(otRepository.nextTestForReminder(any[ReminderNotice])).thenReturnAsync(Some(testForNotification))
      when(cdRepository.find(any())).thenReturnAsync(contactDetails)
      when(emailClient.sendExpiringReminder(any(), any(), any(), any())(any())).thenReturnAsync()
      when(otRepository.addReminderNotificationStatus(any(), any())).thenReturnAsync()

      service.processNextTestForReminder(FirstReminder).futureValue mustBe unit

      verify(otRepository).nextTestForReminder(FirstReminder)
      verify(cdRepository).find(userId)
      verify(otRepository).addReminderNotificationStatus(userId, FirstReminder.notificationStatus)
      verify(emailClient).sendExpiringReminder(
        eqTo(FirstReminder.template),
        eqTo(emailAddress),
        eqTo(testForNotification.preferredName),
        eqTo(testForNotification.expiryDate)
      )(any())
      verify(audit).logEventNoRequest(
        FirstReminder.event,
        Map(
          "userId" -> userId,
          "email" -> emailAddress
        )
      )

      verifyNoMoreInteractions(otRepository, cdRepository, emailClient, audit)
    }
  }

  "when emailing a candidate" should {
    "send the email to their email address" in new EmailCandidateFixture {
      service.emailCandidate(expiringTest, emailAddress).futureValue mustBe unit

      verify(emailClient).sendOnlineTestExpired(eqTo(emailAddress), any())(any())
    }

    "greet candidate by their preferred name" in new EmailCandidateFixture {
      service.emailCandidate(expiringTest, emailAddress).futureValue mustBe unit

      verify(emailClient).sendOnlineTestExpired(any(), eqTo(preferredName))(any())
    }

    "attach a 'header carrier' with the current timestamp" in new EmailCandidateFixture {
      // Test ensures we're not making the mistake of caching the HeaderCarrier, which
      // would result in an incorrect timestamp being passed on to other components.
      val hc1 = HeaderCarrier(nsStamp = 1)
      val hc2 = HeaderCarrier(nsStamp = 2)
      val hcs = List(hc1, hc2).iterator
      override def hc = hcs.next()

      service.emailCandidate(expiringTest, emailAddress).futureValue mustBe unit
      verify(emailClient).sendOnlineTestExpired(any(), any())(eqTo(hc1))

      service.emailCandidate(expiringTest, emailAddress).futureValue mustBe unit
      verify(emailClient).sendOnlineTestExpired(any(), any())(eqTo(hc2))
    }

    "audit an event after sending" in new EmailCandidateFixture {
      service.emailCandidate(expiringTest, emailAddress).futureValue mustBe unit

      verify(audit).logEventNoRequest(
        "ExpiredOnlineTestNotificationEmailed",
        Map("userId" -> userId, "email" -> emailAddress)
      )
    }
  }

  "when updating the application status" should {
    "mark the relevant application as expired" in new CommitExpiredStatusFixture {
      service.commitExpiredStatus(expiringTest).futureValue mustBe unit

      verify(otRepository).updateStatus(userId, ApplicationStatuses.OnlineTestExpired)
    }

    "audit an event after updating the application status" in new CommitExpiredStatusFixture {
      service.commitExpiredStatus(expiringTest).futureValue mustBe unit

      verify(audit).logEventNoRequest(
        "ExpiredOnlineTest",
        Map("userId" -> userId)
      )
    }
  }

  // The call to `Logger.info` within our implementation appears to add sufficient latency
  // to cause timeouts using the default configuration for the `futureValue` helper method.
  override implicit def patienceConfig = PatienceConfig(timeout = scaled(Span(2000, Millis)))

  trait Fixture {
    val applicationId = "abc"
    val userId = "xyz"
    val preferredName = "Jon"
    val emailAddress = "jon@test.com"
    val expirationDate = DateTime.now().plusHours((24 * 3) - 1)
    val contactDetails = ContactDetails(outsideUk = false, Address("line 1"), Some("HP27 9JU"), None, emailAddress, None)
    val expiringTest = ExpiringOnlineTest(applicationId, userId, preferredName)
    val testForNotification = NotificationExpiringOnlineTest(applicationId, userId, preferredName, expirationDate)
    val unit = ()
    def hc = HeaderCarrier()

    val ec = scala.concurrent.ExecutionContext.Implicits.global
    val otRepository = mock[OnlineTestRepository]
    val cdRepository = mock[ContactDetailsRepository]
    val emailClient = mock[EmailClient]
    val audit = mock[AuditService]
    val service = spy(new OnlineTestExpiryServiceImpl(otRepository, cdRepository, emailClient, audit, hc)(ec))
  }

  trait ProcessNextExpiredFixture extends Fixture {
    doReturnAsync()
      .when(service).processExpiredTest(any())
  }

  trait ProcessExpiredFixture extends Fixture {
    when(cdRepository.find(any())).thenReturnAsync(contactDetails)
    doReturnAsync()
      .when(service).emailCandidate(any(), any())
    doReturnAsync()
      .when(service).commitExpiredStatus(any())
  }

  trait EmailCandidateFixture extends Fixture {
    when(emailClient.sendOnlineTestExpired(any(), any())(any())).thenReturnAsync()
  }

  trait CommitExpiredStatusFixture extends Fixture {
    when(otRepository.updateStatus(any(), any())).thenReturnAsync()
  }
}
