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

import repositories.{ FileLocationSchemeRepository, LocationSchemeRepository, LocationSchemes }
import services.locationschemes.exchangeobjects.GeoLocationSchemeResult

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object LocationSchemeService extends LocationSchemeService {
  val locationSchemeRepository = FileLocationSchemeRepository
}

trait LocationSchemeService {
  def locationSchemeRepository: LocationSchemeRepository

  def getSchemesAndLocationsByEligibility(hasALevels: Boolean, hasStemALevels: Boolean,
                                          latitudeOpt: Option[Double] = None, longitudeOpt: Option[Double] = None)
  : Future[IndexedSeq[GeoLocationSchemeResult]] = {

    // calculate distance and search
    for {
      schemeInfo <- locationSchemeRepository.getSchemeInfo
      locationsWithSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {

      val eligibleSchemes = schemeInfo.filterNot(s => s.requiresALevel && !hasALevels || s.requiresALevelInStem && !hasStemALevels)
      val eligibleSchemeNames = eligibleSchemes.map(_.schemeName)

      val selectedLocations = locationsWithSchemes.collect {
        case LocationSchemes(locationName, schemeLatitude, schemeLongitude, schemes) if eligibleSchemeNames.intersect(schemes).nonEmpty =>
          val distance = for {
            latitude <- latitudeOpt
            longitude <- longitudeOpt
          } yield {
              DistanceCalculator.calcKilometersBetween(latitude, longitude, schemeLatitude, schemeLongitude)
          }

          GeoLocationSchemeResult(distance, locationName, eligibleSchemeNames.intersect(schemes))
      }

      selectedLocations.sortBy(r => r.distanceKm.getOrElse(0d))
    }
  }
}
