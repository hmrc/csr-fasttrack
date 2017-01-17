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

package services.locationschemes

import model.PersistedObjects.PersonalDetails
import model.Scheme._
import org.joda.time.LocalDate
import org.mockito.Mockito._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.{ LocationSchemeRepository, LocationSchemes, SchemeInfo }
import testkit.UnitSpec

import scala.concurrent.Future

class LocationSchemeServiceSpec extends UnitSpec {

  "Location Scheme service" should {

    "return a list of location/scheme combinations without A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = false, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(NoALevelsScheme)
    }

    "return a list of location/scheme combinations with A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = false)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(NoALevelsScheme, ALevelsScheme)
    }

    "return a list of location/scheme combinations with STEM A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(NoALevelsScheme, ALevelsScheme, ALevelsStemScheme)
    }

    "return a list of location/scheme combinations sorted closest first" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = false, stemLevel = false)))
      val result = service.getEligibleSchemeLocations(appId, latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.map(_.locationName) mustBe List("testLocation4", "testLocation3", "testLocation1", "testLocation2")
    }
  }

  trait TestFixture {
    val locationSchemeRepoMock = mock[LocationSchemeRepository]
    val pdRepoMock = mock[PersonalDetailsRepository]
    val appRepoMock = mock[GeneralApplicationRepository]
    val NoALevelsScheme = SchemeInfo(Business, "SchemeNoALevels", requiresALevel = false, requiresALevelInStem = false)
    val ALevelsScheme = SchemeInfo(Commercial, "SchemeALevels", requiresALevel = true, requiresALevelInStem = false)
    val ALevelsStemScheme = SchemeInfo(ProjectDelivery, "SchemeALevelsStem", requiresALevel = true, requiresALevelInStem = true)
    val appId = "application-1"
    val personalDetailsMock = PersonalDetails("", "", "", LocalDate.now(), aLevel = false, stemLevel = false)


    when(locationSchemeRepoMock.getSchemesAndLocations).
      thenReturn(Future.successful(
        List(
          LocationSchemes("id1", "testLocation1", 2.0, 5.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id2", "testLocation2", 6.0, 2.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id3", "testLocation3", 2.5, 2.6, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id4", "testLocation4", 1.0, 1.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
        )))

    when(locationSchemeRepoMock.getSchemeInfo).thenReturn(Future.successful(
      List(NoALevelsScheme, ALevelsScheme, ALevelsStemScheme)
    ))

    when(appRepoMock.getSchemes(appId)).thenReturn(Future.successful(
      List(Business, Commercial, ProjectDelivery)
    ))

    val service = new LocationSchemeService {
      val locationSchemeRepository = locationSchemeRepoMock
      val pdRepository = pdRepoMock
      val appRepository = appRepoMock
    }
  }
}
