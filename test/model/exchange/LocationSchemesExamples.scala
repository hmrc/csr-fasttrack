/*
 * Copyright 2019 HM Revenue & Customs
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

  val schemesLondon = List(Business, Commercial, ProjectDelivery)
  val schemesBristol = List(Commercial, Finance)
  val schemesEdinburgh = List(Finance, Commercial)

  val London = LocationSchemes("2643743", "London", List(Geocode(2.0, 5.0)), schemesLondon)
  val Bristol = LocationSchemes("2643745", "Bristol", List(Geocode(2.5, 2.6)), schemesBristol)
  val Edinburgh = LocationSchemes("2643746", "Edinburgh", List(Geocode(1.0, 1.0), Geocode(10.0, 2.0), Geocode(12.0, 5.0)),
    schemesEdinburgh)
  val LocationsSchemesList = List(London, Bristol, Edinburgh)
}
