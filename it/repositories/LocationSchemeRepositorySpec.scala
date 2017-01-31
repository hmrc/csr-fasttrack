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

package repositories

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import testkit.IntegrationSpec

class LocationSchemeRepositorySpec extends IntegrationSpec with MockitoSugar with OneAppPerSuite {

  "Locations Scheme Repository" should {
    "return all locations with defined schemes" in {
      val repo = new LocationSchemeRepository {}

      val locationSchemes = repo.getSchemesAndLocations.futureValue
      val schemes = repo.getSchemeInfo.futureValue

      val schemeNames = schemes.map(_.name)
      locationSchemes.length mustBe 46
      locationSchemes.map(_.schemes.forall(schemeNames.contains(_)))
    }
  }
}
