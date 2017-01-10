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

import repositories.{ LocationSchemeRepository, LocationSchemes, SchemeInfo }
import testkit.UnitSpec
import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.locationschemes.exchangeobjects.GeoLocationSchemeResult

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future

class LocationSchemeServiceSpec extends UnitSpec {
  "Location Scheme service" should {

    // TODO
    "return a list of location/scheme combinations without A-level requirements" ignore {
      /*val result = service.getSchemesAndLocationsByEligibility(hasALevels = false, hasStemALevels = false,
        latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.head.schemes must contain theSameElementsAs(List(SchemeInfo("SchemeNoALevels", false, false)))
      */
    }

    // TODO
    "return a list of location/scheme combinations with A-level requirements" ignore {
      /*val result = service.getSchemesAndLocationsByEligibility(hasALevels = false, hasStemALevels = false,
        latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.head.schemes must contain theSameElementsAs(List(
        SchemeInfo("SchemeNoALevels", false, false),
        SchemeInfo("SchemeNoALevels", true, false)
        ))*/
    }

    // TODO
    "return a list of location/scheme combinations with STEM A-level requirements" ignore {
      /* val result = service.getSchemesAndLocationsByEligibility(hasALevels = false, hasStemALevels = false,
        latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.head.schemes must contain theSameElementsAs(List(
        SchemeInfo("SchemeNoALevels", false, false),
        SchemeInfo("SchemeNoALevels", true, false),
        SchemeInfo("SchemeALevelsStem", true, true)
      ))
      */
    }

    "return a list of location/scheme combinations sorted closest first" ignore {
      /*val result = service.getSchemesAndLocationsByEligibility(hasALevels = false, hasStemALevels = false,
        latitudeOpt = Some(1.1), longitudeOpt = Some(2.2)).futureValue
      result.map(_.locationName) mustBe List("testLocation4", "testLocation3", "testLocation1", "testLocation2")
      */
    }
  }

  trait TestFixture {
    val repo = mock[LocationSchemeRepository]

    when(repo.getSchemesAndLocations).
      thenReturn(Future.successful(
        List(
          LocationSchemes("id1", "testLocation1", 2.0, 5.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id2", "testLocation2", 6.0, 2.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id3", "testLocation3", 2.5, 2.6, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("id4", "testLocation4", 1.0, 1.0, schemes = List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
        )))

    when(repo.getSchemeInfo).thenReturn(Future.successful(
      List(
        SchemeInfo("SchemeNoALevels", requiresALevel = false, requiresALevelInStem = false),
        SchemeInfo("SchemeALevels", requiresALevel = true, requiresALevelInStem = false),
        SchemeInfo("SchemeALevelsStem", requiresALevel = true, requiresALevelInStem = true)
      )
    ))

    val service = new LocationSchemeService {
      val locationSchemeRepository = repo
      val pdRepository = mock[PersonalDetailsRepository]
      val appRepository = mock[GeneralApplicationRepository]
    }
  }
}
