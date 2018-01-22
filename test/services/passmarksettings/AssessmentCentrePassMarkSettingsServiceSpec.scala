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

package services.passmarksettings

import config.TestFixtureBase
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import repositories.{ AssessmentCentrePassMarkSettingsMongoRepository, FrameworkRepository, LocationSchemeRepository, SchemeInfo }

import scala.concurrent.Future
import model.Scheme
import model.Scheme.{ Business, Commercial, DigitalAndTechnology, Finance, ProjectDelivery }
import model.persisted.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, AssessmentCentrePassMarkSettings, PassMarkSchemeThreshold }

class AssessmentCentrePassMarkSettingsServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "get latest version" should {
    "return empty scores with Schemes when there is no passmark settings" in new TestFixture {
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(None))
      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue
      result mustBe None
    }

    "return the latest version of passmark settings" in new TestFixture {
      when(mockAssessmentCentrePassMarkSettingsRepository.tryGetLatestVersion).thenReturn(Future.successful(Some(Settings)))
      val result = TestableAssessmentCentrePassMarkSettingsService.getLatestVersion.futureValue
      result mustBe Some(AssessmentCentrePassMarkSettings(Settings.schemes, Settings.info))
    }
  }
}

trait TestFixture extends TestFixtureBase {
  val mockAssessmentCentrePassMarkSettingsRepository = mock[AssessmentCentrePassMarkSettingsMongoRepository]
  val mockLocSchemeRepository = mock[LocationSchemeRepository]

  val AllAssessmentCentrePassMarkSchemes = List(
    AssessmentCentrePassMarkScheme(Scheme.Business),
    AssessmentCentrePassMarkScheme(Scheme.Commercial),
    AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology),
    AssessmentCentrePassMarkScheme(Scheme.Finance),
    AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery)
  )

  val AllSchemeInfo = List(
    SchemeInfo(Business, "Business", requiresALevel = false, requiresALevelInStem = false),
    SchemeInfo(Commercial, "Commercial", requiresALevel = false, requiresALevelInStem = false),
    SchemeInfo(DigitalAndTechnology, "Digital and technology", requiresALevel = true, requiresALevelInStem = true),
    SchemeInfo(Finance, "Finance", requiresALevel = false, requiresALevelInStem = false),
    SchemeInfo(ProjectDelivery, "Project delivery", requiresALevel = true, requiresALevelInStem = false)
  )

  object TestableAssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
    val locationSchemeRepository = mockLocSchemeRepository
    val assessmentCentrePassmarkSettingRepository = mockAssessmentCentrePassMarkSettingsRepository
  }

  when(mockLocSchemeRepository.schemeInfoList).thenReturn(AllSchemeInfo)

  val Settings = AssessmentCentrePassMarkSettings(
    AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
    AssessmentCentrePassMarkInfo("version1", DateTime.now(), "userName")
  )
}
