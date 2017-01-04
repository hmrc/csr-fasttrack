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

package controllers

import org.mockito.Matchers.{ eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, JsNumber, JsString }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.locationschemes.LocationSchemeService
import services.locationschemes.exchangeobjects.GeoLocationSchemeResult
import testkit.UnitSpec

import scala.concurrent.Future

class LocationSchemeControllerSpec extends UnitSpec {

  "Location Scheme Controller" should {
    "return a list of location/scheme combinations filtered by eligibility" in new TestFixture {
      val lat = 0.0
      val lng = 1.0
      when(service.getSchemesAndLocationsByEligibility(eqTo(lat), eqTo(lng), eqTo(true), eqTo(true))).
        thenReturn(Future.successful(Vector(GeoLocationSchemeResult(1, "London", schemes = Vector("Business", "Digital")))))

      val request = FakeRequest("GET", s"?latitude=$lat&longitude=$lng&hasALevels=true&hasStemALevels=true")
      val response = controller.getSchemesAndLocationsByEligibility().apply(request)
      val json = contentAsJson(response)

      ((json \ 0) \ "distanceKm").get mustBe JsNumber(1)
      ((json \ 0) \ "locationName").get mustBe JsString("London")
      ((json \ 0) \ "schemes").get mustBe JsArray(List(JsString("Business"), JsString("Digital")))
    }
  }

  trait TestFixture {
    val service = mock[LocationSchemeService]
    val controller = new LocationSchemeController {
      val locationSchemeService = service
    }
  }

}
