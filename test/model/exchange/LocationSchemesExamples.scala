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

package model.exchange

import model.Scheme._
import repositories.{ Geocode, LocationSchemes }

object LocationSchemesExamples {

  val schemes = List(Business, Commercial, ProjectDelivery)

  val LocationSchemes1 = LocationSchemes("id1", "testLocation1", List(Geocode(2.0, 5.0)), schemes)
  val LocationSchemes2 = LocationSchemes("id2", "testLocation2", List(Geocode(6.0, 2.0)), schemes)
  val LocationSchemes3 = LocationSchemes("id3", "testLocation3", List(Geocode(2.5, 2.6)), schemes)
  val LocationSchemes4 = LocationSchemes("id4", "testLocation4", List(Geocode(1.0, 1.0), Geocode(10.0, 2.0), Geocode(12.0, 5.0)),
    schemes)
  val LocationsSchemesList = List(LocationSchemes1, LocationSchemes2, LocationSchemes3, LocationSchemes4)
}
