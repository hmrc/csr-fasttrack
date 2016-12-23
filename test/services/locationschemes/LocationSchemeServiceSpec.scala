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

package services.locationschemes

import repositories.{ LocationSchemeRepository, LocationSchemes, SchemeInfo }
import testkit.UnitSpec
import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future

class LocationSchemeServiceSpec extends UnitSpec {
  "Location Scheme service" should {

    "return a list of location/scheme combinations without A-level requirements" in new TestFixture {
      val result = service.getSchemesAndLocationsByEligibility(1.1, 2.2, hasALevels = false, hasStemALevels = false).futureValue
      result.head.schemes must contain theSameElementsAs(List("SchemeNoALevels"))
    }

    "return a list of location/scheme combinations with A-level requirements" in new TestFixture {
      val result = service.getSchemesAndLocationsByEligibility(1.1, 2.2, hasALevels = true, hasStemALevels = false).futureValue
      result.head.schemes must contain theSameElementsAs(List("SchemeNoALevels", "SchemeALevels"))
    }

    "return a list of location/scheme combinations with STEM A-level requirements" in new TestFixture {
      val result = service.getSchemesAndLocationsByEligibility(1.1, 2.2, hasALevels = true, hasStemALevels = true).futureValue
      result.head.schemes must contain theSameElementsAs(List("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
    }

    "return a list of location/scheme combinations sorted closest first" in new TestFixture {
      val result = service.getSchemesAndLocationsByEligibility(0.0, 0.0, hasALevels = true, hasStemALevels = true).futureValue
      result.map(_.locationName) mustBe IndexedSeq("testLocation4", "testLocation3", "testLocation1", "testLocation2")
    }
  }

  trait TestFixture {
    val repo = mock[LocationSchemeRepository]

    when(repo.getSchemesAndLocations).
      thenReturn(Future.successful(
        IndexedSeq(
          LocationSchemes("testLocation1", 2.0, 5.0, schemes = IndexedSeq("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("testLocation2", 6.0, 2.0, schemes = IndexedSeq("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("testLocation3", 2.5, 2.6, schemes = IndexedSeq("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem")),
          LocationSchemes("testLocation4", 1.0, 1.0, schemes = IndexedSeq("SchemeNoALevels", "SchemeALevels", "SchemeALevelsStem"))
        )))

    when(repo.getSchemeInfo).thenReturn(Future.successful(
      IndexedSeq(
        SchemeInfo("SchemeNoALevels", requiresALevel = false, requiresALevelInStem = false),
        SchemeInfo("SchemeALevels", requiresALevel = true, requiresALevelInStem = false),
        SchemeInfo("SchemeALevelsStem", requiresALevel = true, requiresALevelInStem = true)
      )
    ))

    val service = new LocationSchemeService {
      val locationSchemeRepository = repo
    }
  }
}
