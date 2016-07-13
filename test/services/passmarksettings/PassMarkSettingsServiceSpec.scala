/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, Settings }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.{ FrameworkRepository, PassMarkSettingsRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class PassMarkSettingsServiceSpec extends PlaySpec with BeforeAndAfterEach with MockitoSugar with ScalaFutures {
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
        assert(result.schemes.head.schemeName == "TestScheme")
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

    val fwRepositoryMock = mock[FrameworkRepository]
    val pmsRepositoryMockNoSettings = mock[PassMarkSettingsRepository]
    val pmsRepositoryMockWithSettings = mock[PassMarkSettingsRepository]

    when(fwRepositoryMock.getFrameworkNames).thenReturn(Future.successful(List("TestScheme")))

    when(pmsRepositoryMockNoSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(None))
    when(pmsRepositoryMockWithSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(
      Some(
        Settings(
          List(
            Scheme(
              "TestScheme",
              SchemeThresholds(
                competency = SchemeThreshold(20d, 80d),
                verbal = SchemeThreshold(20d, 80d),
                numerical = SchemeThreshold(20d, 80d),
                situational = SchemeThreshold(20d, 80d),
                None
              )
            )
          ),
          version = "aVersion",
          createDate = DateTime.parse("2016-04-13T10:00:00Z"),
          createdByUser = "TestUser",
          setting = "location1Scheme1"
        )
      )
    ))

    val passMarkSettingsServiceNoSettings = new PassMarkSettingsService {
      val pmsRepository: PassMarkSettingsRepository = pmsRepositoryMockNoSettings
      val fwRepository: FrameworkRepository = fwRepositoryMock
    }

    val passMarkSettingsServiceWithSettings = new PassMarkSettingsService {
      val pmsRepository: PassMarkSettingsRepository = pmsRepositoryMockWithSettings
      val fwRepository: FrameworkRepository = fwRepositoryMock
    }
  }

}
