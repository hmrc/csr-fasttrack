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

import org.scalatest.prop.TableDrivenPropertyChecks
import testkit.UnitSpec

class DistanceCalculatorSpec extends UnitSpec with TableDrivenPropertyChecks {

  val samples = Table(
    ("latitude1", "longitude1", "latitude2", "longitude2", "result"),
    (55.86602, -3.98025, 51.21135, -1.49393, 542.91)
  )

  "Distance Caclculator" should {
    "calculate the distance in kilometers" in {
      forAll(samples) { (lat1: Double, lng1: Double, lat2: Double, lng2: Double, result: Double) =>
        DistanceCalculator.calcKilometersBetween(lat1, lng1, lat2, lng2) mustBe result +- 1
      }
    }
  }

}
