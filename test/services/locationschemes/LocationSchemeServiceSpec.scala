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
import model.exchange.{ LocationSchemesExamples, SchemeInfoExamples }
import org.joda.time.LocalDate
import org.mockito.Mockito._
import repositories.LocationSchemeRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import testkit.UnitSpec

import scala.concurrent.Future

class LocationSchemeServiceSpec extends UnitSpec {

  "Location Scheme service" should {

    "return a list of location/scheme combinations without A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = false, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme)
    }

    "return a list of location/scheme combinations with A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = false)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme)
    }

    "return a list of location/scheme combinations with STEM A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme,
        SchemeInfoExamples.ALevelsStemScheme)
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
    val appId = "application-1"
    val personalDetailsMock = PersonalDetails("", "", "", LocalDate.now(), aLevel = false, stemLevel = false,
      civilServant = false, department = None)

    when(locationSchemeRepoMock.getSchemesAndLocations)
      .thenReturn(Future.successful(
        List(
          LocationSchemesExamples.LocationSchemes1, LocationSchemesExamples.LocationSchemes2,
          LocationSchemesExamples.LocationSchemes3, LocationSchemesExamples.LocationSchemes4
        )))

    when(locationSchemeRepoMock.getSchemeInfo).thenReturn(Future.successful(
      List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme, SchemeInfoExamples.ALevelsStemScheme)
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
