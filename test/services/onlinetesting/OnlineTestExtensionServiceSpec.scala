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

import controllers.OnlineTestDetails
import factories.DateTimeFactory
import model.{ ApplicationStatuses, ProgressStatuses }
import model.OnlineTestCommands.OnlineTestApplication
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }
import testkit.MockitoImplicits.{ OngoingStubbingExtension, OngoingStubbingExtensionUnit }
import testkit.MockitoSugar

class OnlineTestExtensionServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  "when extending the expiration time of a test" should {

    "add extra days onto expiry, from the expiry time, if not expired" in new TestFixture {
      when(dateTime.nowLocalTimeZone).thenReturn(now)
      when(otRepository.getOnlineTestDetails(any())).thenReturnAsync(onlineTest)
      when(otRepository.updateExpiryTime(any(), any())).thenReturnAsync()
      when(appRepository.removeProgressStatuses(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, oneExtraDays).futureValue mustBe (())

      verify(otRepository).getOnlineTestDetails(userId)
      verify(otRepository).updateExpiryTime(userId, expirationDate.plusDays(oneExtraDays))
      verify(appRepository).removeProgressStatuses(applicationId, List(ProgressStatuses.OnlineTestSecondExpiryNotification))
      verifyNoMoreInteractions(otRepository, appRepository)
    }

    "add extra days onto expiry, from today, if already expired" in new TestFixture {
      val nowBeyondExpiry = expirationDate.plusDays(10)
      when(dateTime.nowLocalTimeZone).thenReturn(nowBeyondExpiry)
      when(otRepository.getOnlineTestDetails(any())).thenReturnAsync(onlineTest)
      when(otRepository.updateExpiryTime(any(), any())).thenReturnAsync()
      when(appRepository.removeProgressStatuses(any(), any())).thenReturnAsync()

      service.extendExpiryTime(onlineTestApp, fourExtraDays).futureValue mustBe (())

      verify(otRepository).updateExpiryTime(userId, nowBeyondExpiry.plusDays(fourExtraDays))
      verify(appRepository).removeProgressStatuses(applicationId,
        List(ProgressStatuses.OnlineTestFirstExpiryNotification, ProgressStatuses.OnlineTestSecondExpiryNotification))

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
    val onlineTest = OnlineTestDetails(invitationDate, expirationDate, "http://example.com/", cubiksEmail, isOnlineTestEnabled = true)
    val numericalTimeAdjustmentPercentage = 0
    val verbalTimeAdjustmentPercentage = 0
    val onlineTestApp = OnlineTestApplication(
      applicationId, applicationStatus, userId,
      guaranteedInterview = true, needsAdjustments = true, preferredName, None
    )
    val otRepository = mock[OnlineTestRepository]
    val appRepository = mock[GeneralApplicationRepository]
    val dateTime = mock[DateTimeFactory]
    val service = new OnlineTestExtensionServiceImpl(otRepository, appRepository, dateTime)
  }
}
