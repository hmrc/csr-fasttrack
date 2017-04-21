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

import factories.{ DateTimeFactory, UUIDFactory }
import model.{ ApplicationStatuses, ProgressStatuses }
import model.OnlineTestCommands.OnlineTestApplication
import model.persisted.CubiksTestProfile
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.UnitSpec

class OnlineTestExtensionServiceSpec extends UnitSpec {

  "when extending the expiration time of an unexpired test" should {

    "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
      when(otRepositoryMock.getCubiksTestProfile(any[String])).thenReturnAsync(onlineTest)
      when(otRepositoryMock.updateExpiryTime(any(), any(), any())).thenReturnAsync()
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, oneExtraDays).futureValue mustBe unit

      verify(otRepositoryMock).getCubiksTestProfile(userId)
      verify(otRepositoryMock).updateExpiryTime(userId, expirationDate.plusDays(oneExtraDays), List(ApplicationStatuses.OnlineTestInvited,
        ApplicationStatuses.OnlineTestStarted))
      verify(appRepositoryMock).removeProgressStatuses(applicationId, List(ProgressStatuses.OnlineTestSecondExpiryNotification))
      verifyNoMoreInteractions(otRepositoryMock, appRepositoryMock)
    }

    "add extra days onto expiry, from today, if already expired" in new TestFixture {
      val nowBeyondExpiry = expirationDate.plusDays(10)
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(nowBeyondExpiry)
      when(otRepositoryMock.getCubiksTestProfile(any[String])).thenReturnAsync(onlineTest)
      when(otRepositoryMock.updateExpiryTime(any(), any(), any())).thenReturnAsync()
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, fourExtraDays).futureValue mustBe unit

      verify(otRepositoryMock).updateExpiryTime(userId, nowBeyondExpiry.plusDays(fourExtraDays), List(ApplicationStatuses.OnlineTestInvited,
        ApplicationStatuses.OnlineTestStarted))
      verify(appRepositoryMock).removeProgressStatuses(
        applicationId,
        List(ProgressStatuses.OnlineTestFirstExpiryNotification, ProgressStatuses.OnlineTestSecondExpiryNotification)
      )
    }
  }

  "when extending the expiration time of an already expired test" should {
    "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(now)
      when(otRepositoryMock.getCubiksTestProfile(any[String])).thenReturnAsync(onlineTest)
      when(otRepositoryMock.updateExpiryTime(any(), any(), any())).thenReturnAsync()
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturnAsync()
      when(appRepositoryMock.updateStatus(any(), any())).thenReturnAsync()

      service.extendExpiryTimeForExpiredTests(onlineTestApp, oneExtraDays).futureValue mustBe unit

      verify(otRepositoryMock).getCubiksTestProfile(userId)
      verify(otRepositoryMock).updateExpiryTime(userId, expirationDate.plusDays(oneExtraDays), List(ApplicationStatuses.OnlineTestExpired))
      verify(appRepositoryMock).removeProgressStatuses(applicationId, List(
        ProgressStatuses.OnlineTestSecondExpiryNotification, ProgressStatuses.OnlineTestExpiredProgress
      ))
      verify(appRepositoryMock).updateStatus(applicationId, ApplicationStatuses.OnlineTestStarted)
      verifyNoMoreInteractions(otRepositoryMock, appRepositoryMock)
    }

    "add extra days onto expiry, from today, if already expired" in new TestFixture {
      val nowBeyondExpiry = expirationDate.plusDays(10)
      when(dateTimeFactoryMock.nowLocalTimeZone).thenReturn(nowBeyondExpiry)
      when(otRepositoryMock.getCubiksTestProfile(any[String])).thenReturnAsync(onlineTest)
      when(otRepositoryMock.updateExpiryTime(any(), any(), any())).thenReturnAsync()
      when(appRepositoryMock.removeProgressStatuses(any(), any())).thenReturnAsync()
      when(appRepositoryMock.updateStatus(any(), any())).thenReturnAsync()

      service.extendExpiryTimeForExpiredTests(onlineTestApp, fourExtraDays).futureValue mustBe unit

      verify(otRepositoryMock).updateExpiryTime(userId, nowBeyondExpiry.plusDays(fourExtraDays), List(ApplicationStatuses.OnlineTestExpired))
      verify(appRepositoryMock).removeProgressStatuses(
        applicationId,
        List(ProgressStatuses.OnlineTestFirstExpiryNotification, ProgressStatuses.OnlineTestSecondExpiryNotification,
          ProgressStatuses.OnlineTestExpiredProgress)
      )
      verify(appRepositoryMock).updateStatus(applicationId, ApplicationStatuses.OnlineTestStarted)
    }
  }

  trait TestFixture {
    val applicationStatus = ApplicationStatuses.OnlineTestExpired
    val applicationId = "abc"
    val userId = "xyz"
    val preferredName = "Jon"
    val emailAddress = "jon@test.com"
    val cubiksEmail = "123@test.com"
    val oneExtraDays = 1
    val fourExtraDays = 4
    val now = DateTime.now()
    val invitationDate = now.minusDays(4)
    val expirationDate = now.plusDays(1)
    val onlineTest = CubiksTestProfile(
      cubiksUserId = 123,
      participantScheduleId = 111,
      invitationDate = invitationDate,
      expirationDate = expirationDate,
      onlineTestUrl = "http://www.google.co.uk",
      token = UUIDFactory.generateUUID(),
      isOnlineTestEnabled = true
    )
    val numericalTimeAdjustmentPercentage = 0
    val verbalTimeAdjustmentPercentage = 0
    val onlineTestApp = OnlineTestApplication(
      applicationId, applicationStatus, userId,
      guaranteedInterview = true, needsAdjustments = true, preferredName, None
    )
    val otRepositoryMock = mock[OnlineTestRepository]
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val dateTimeFactoryMock = mock[DateTimeFactory]
    val service = new OnlineTestExtensionServiceImpl(otRepositoryMock, appRepositoryMock, dateTimeFactoryMock)
  }
}
