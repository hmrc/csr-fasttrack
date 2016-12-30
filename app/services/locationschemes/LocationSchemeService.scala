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

import play.api.libs.json.Json
import repositories.{ FileLocationSchemeRepository, LocationSchemeRepository, LocationSchemes }

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class GeoLocationSchemeResult(distanceKm: Int, locationName: String, schemes: IndexedSeq[String])

object GeoLocationSchemeResult {
  implicit val jsonWriter = Json.writes[GeoLocationSchemeResult]
}

object LocationSchemeService extends LocationSchemeService {
  val locationSchemeRepository = FileLocationSchemeRepository
}

trait LocationSchemeService {
  def locationSchemeRepository: LocationSchemeRepository

  def getSchemesAndLocationsByEligibility(latitude: Double, longitude: Double, hasALevels: Boolean,
                                          hasStemALevels: Boolean): Future[IndexedSeq[GeoLocationSchemeResult]] = {
    // calculate distance and search
    for {
      schemeInfo <- locationSchemeRepository.getSchemeInfo
      locationsWithSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {

      val eligibleSchemes = schemeInfo.filterNot(s => s.requiresALevel && !hasALevels || s.requiresALevelInStem && !hasStemALevels)
      val eligibleSchemeNames = eligibleSchemes.map(_.schemeName)

      val selectedLocations = locationsWithSchemes.collect {
        case LocationSchemes(locationName, lat, lng, schemes) if eligibleSchemeNames.intersect(schemes).nonEmpty =>
          val distance = DistanceCalculator.calcKilometersBetween(latitude, longitude, lat, lng)
          GeoLocationSchemeResult(distance.toInt, locationName, eligibleSchemeNames.intersect(schemes))
      }

      selectedLocations.sortBy(r => r.distanceKm)
    }
  }
}
