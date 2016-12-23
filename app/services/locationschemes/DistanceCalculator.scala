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

object DistanceCalculator {
  def calcKilometersBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double = {
    // Using haversine distance
    val deltaLat = math.toRadians(lat2 - lat1)
    val deltaLong = math.toRadians(lng2 - lng1)

    val a = math.pow(math.sin(deltaLat / 2), 2) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) * math.pow(math.sin(deltaLong / 2), 2)

    val greatCircleDistance = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    // WGS 84 specifies this diameter at the equator, but this calculation takes the earth to be a perfect sphere,
    // which is a simplification as it is an oblate spheroid
    val diameterOfTheEarthAtEquator = 6378.137
    diameterOfTheEarthAtEquator * greatCircleDistance
  }
}
