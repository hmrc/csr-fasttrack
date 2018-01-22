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

package services.locationschemes

import model.persisted.PersonalDetails
import model.Scheme._
import model.exchange.{ LocationSchemesExamples, SchemeInfoExamples }
import org.joda.time.LocalDate
import org.mockito.Mockito._
import repositories.LocationSchemeRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import testkit.UnitSpec

import scala.concurrent.Future

class LocationSchemeServiceSpec extends UnitSpec {

  "Location Scheme service" should {

    "return a list of eligible schemes for stem level (stem level is inclusive of A-level)" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = false, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme,
        SchemeInfoExamples.ALevelsStemScheme)
    }

    "return a list of schemes with A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = false)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme)
    }

    "return a list of eligible schemes for stem and A-level requirements" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = true, stemLevel = true)))
      val result = service.getEligibleSchemes(appId).futureValue
      result must contain theSameElementsAs List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme,
        SchemeInfoExamples.ALevelsStemScheme)
    }

    "return a list of location/scheme combinations sorted closest first" in new TestFixture {
      when(pdRepoMock.find(appId)).thenReturn(Future.successful(personalDetailsMock.copy(aLevel = false, stemLevel = false)))
      val result = service.getEligibleSchemeLocations(appId, latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.map(_.locationName) mustBe List(LocationSchemesExamples.Edinburgh.locationName,
        LocationSchemesExamples.Bristol.locationName,
        LocationSchemesExamples.London.locationName)
    }
  }

  trait TestFixture {
    val locationSchemeRepoMock = mock[LocationSchemeRepository]
    val pdRepoMock = mock[PersonalDetailsRepository]
    val appRepoMock = mock[GeneralApplicationRepository]
    val auditMock = mock[AuditService]
    val appId = "application-1"
    val personalDetailsMock = PersonalDetails("", "", "", LocalDate.now(), aLevel = false, stemLevel = false,
      civilServant = false, department = None)

    when(locationSchemeRepoMock.getSchemesAndLocations)
      .thenReturn(Future.successful(
        List(LocationSchemesExamples.London,LocationSchemesExamples.Bristol, LocationSchemesExamples.Edinburgh)
      ))

    when(locationSchemeRepoMock.schemeInfoList).thenReturn(
      List(SchemeInfoExamples.NoALevelsScheme, SchemeInfoExamples.ALevelsScheme, SchemeInfoExamples.ALevelsStemScheme)
    )

    when(appRepoMock.getSchemes(appId)).thenReturn(Future.successful(
      List(Business, Commercial, ProjectDelivery)
    ))

    val service = new LocationSchemeService {
      val locationSchemeRepository = locationSchemeRepoMock
      val pdRepository = pdRepoMock
      val appRepository = appRepoMock
      val auditService = auditMock
    }
  }
}
