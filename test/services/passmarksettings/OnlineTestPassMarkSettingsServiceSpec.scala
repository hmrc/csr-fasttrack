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

package services.passmarksettings

import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, OnlineTestPassmarkSettings }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.OnlineTestPassMarkSettingsRepository

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

class OnlineTestPassMarkSettingsServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "try and getting the latest pass mark settings" should {
    "be none if there are no settings" in {
      Fixtures.passMarkSettingsServiceNoSettings.tryGetLatestVersion().map(resultOpt =>
        assert(resultOpt.isEmpty))
    }

    "be a valid settings object if there are stored settings" in {
      Fixtures.passMarkSettingsServiceWithSettings.tryGetLatestVersion().map { resultOpt =>
        assert(resultOpt.nonEmpty)
        val result = resultOpt.get
        assert(result.schemes.size == 1)
        assert(result.schemes.head.schemeName == model.Scheme.Business)
        assert(result.schemes.head.schemeThresholds.competency.failThreshold == 20d)
        assert(result.schemes.head.schemeThresholds.competency.passThreshold == 80d)
        assert(result.version == "aVersion")
        assert(result.createDate == DateTime.parse("2016-04-13T10:00:00Z"))
        assert(result.createdByUser == "TestUser")
      }
    }
  }

  object Fixtures {
    implicit val hc = HeaderCarrier()

    val pmsRepositoryMockNoSettings = mock[OnlineTestPassMarkSettingsRepository]
    val pmsRepositoryMockWithSettings = mock[OnlineTestPassMarkSettingsRepository]

    when(pmsRepositoryMockNoSettings.tryGetLatestVersion()).thenReturn(Future.successful(None))
    when(pmsRepositoryMockWithSettings.tryGetLatestVersion()).thenReturn(Future.successful(
      Some(
        OnlineTestPassmarkSettings(
          List(
            Scheme(
              model.Scheme.Business,
              SchemeThresholds(
                competency = SchemeThreshold(20d, 80d),
                verbal = SchemeThreshold(20d, 80d),
                numerical = SchemeThreshold(20d, 80d),
                situational = SchemeThreshold(20d, 80d)
              )
            )
          ),
          version = "aVersion",
          createDate = DateTime.parse("2016-04-13T10:00:00Z"),
          createdByUser = "TestUser"
        )
      )
    ))

    val passMarkSettingsServiceNoSettings = new OnlineTestPassMarkSettingsService {
      val onlineTestPassMarkSettingsRepository: OnlineTestPassMarkSettingsRepository = pmsRepositoryMockNoSettings
    }

    val passMarkSettingsServiceWithSettings = new OnlineTestPassMarkSettingsService {
      val onlineTestPassMarkSettingsRepository: OnlineTestPassMarkSettingsRepository = pmsRepositoryMockWithSettings
    }
  }

}
