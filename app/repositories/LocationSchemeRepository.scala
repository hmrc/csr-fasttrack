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

package repositories

import play.Play
import play.api.libs.json.{ Json, Reads }
import resource._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import model.Scheme._
import scala.concurrent.Future

case class LocationSchemes(id: String, locationName: String, geocodes: List[Geocode], schemes: List[Scheme])

case class Geocode(lat: Double, lng: Double)

object LocationSchemes {
  implicit val geocodeReader: Reads[Geocode] = Json.reads[Geocode]
  implicit val locationSchemesReader: Reads[LocationSchemes] = (
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "geocodes").read[List[Geocode]] and
    (__ \ "schemes").read[List[Scheme]]
  )(LocationSchemes.apply _)

  implicit val geocodesWriter = Json.writes[Geocode]
  implicit val locationSchemesWriter = Json.writes[LocationSchemes]

}

protected case class Locations(locations: List[LocationSchemes])

object Locations {
  implicit val locationsReader: Reads[Locations] = Json.reads[Locations]
}

case class SchemeInfo(id: Scheme, name: String, requiresALevel: Boolean, requiresALevelInStem: Boolean)

object SchemeInfo {
  implicit val schemeInfoFormat = Json.format[SchemeInfo]
}

object FileLocationSchemeRepository extends LocationSchemeRepository

trait LocationSchemeRepository {

  private lazy val cachedLocationSchemes = {
    // TODO: File needs updating with correct scheme and location data
    val input = managed(Play.application.resourceAsStream("locations-schemes.json"))
    val loaded = input.acquireAndGet(r => { Json.parse(r).as[Locations] })
    Future.successful(loaded.locations)
  }

  def getSchemesAndLocations: Future[List[LocationSchemes]] = cachedLocationSchemes

  def schemeInfoList: List[SchemeInfo] = {
    List(
      SchemeInfo(Business, "Business", requiresALevel = false, requiresALevelInStem = false),
      SchemeInfo(Commercial, "Commercial", requiresALevel = false, requiresALevelInStem = false),
      SchemeInfo(DigitalAndTechnology, "Digital and technology", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo(Finance, "Finance", requiresALevel = false, requiresALevelInStem = false),
      SchemeInfo(ProjectDelivery, "Project delivery", requiresALevel = true, requiresALevelInStem = false)
    )
  }
}
