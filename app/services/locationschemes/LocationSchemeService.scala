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
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object LocationSchemeService extends LocationSchemeService {
  val locationSchemeRepository = FileLocationSchemeRepository
  val appRepository = applicationRepository
  val pdRepository = personalDetailsRepository
}

trait LocationSchemeService {
  val locationSchemeRepository: LocationSchemeRepository
  val appRepository: GeneralApplicationRepository
  val pdRepository: PersonalDetailsRepository

  def getSchemesAndLocationsByEligibility(hasALevels: Boolean, hasStemALevels: Boolean,
                                          latitudeOpt: Option[Double] = None, longitudeOpt: Option[Double] = None)
  : Future[List[GeoLocationSchemeResult]] = {

    // calculate distance and search
    for {
      schemeInfo <- locationSchemeRepository.getSchemeInfo
      locationsWithSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {

      val eligibleSchemes = schemeInfo.filterNot(s => s.requiresALevel && !hasALevels || s.requiresALevelInStem && !hasStemALevels)

      val selectedLocations = locationsWithSchemes.collect {
        case LocationSchemes(locationId, locationName, schemeLatitude, schemeLongitude, schemes)
          if eligibleSchemes.map(_.name).intersect(schemes).nonEmpty =>
          val distance = for {
            latitude <- latitudeOpt
            longitude <- longitudeOpt
          } yield {
              DistanceCalculator.calcKilometersBetween(latitude, longitude, schemeLatitude, schemeLongitude)
          }

          GeoLocationSchemeResult(locationId, locationName, distance, eligibleSchemes.filter(eScheme => schemes.contains(eScheme.name)))
      }

      selectedLocations.sortBy(r => r.distanceKm.getOrElse(0d))
    }
  }

  def getAvailableSchemesInSelectedLocations(applicationId: String): Future[List[SchemeInfo]] = {
    for {
      personalDetails <- pdRepository.find(applicationId)
      selectedLocationIds <- getSchemeLocations(applicationId)
      eligibleSchemeLocations <- getSchemesAndLocationsByEligibility(personalDetails.aLevel, personalDetails.stemLevel)
    } yield {
      val selectedLocationSchemes = eligibleSchemeLocations.filter(schemeLocation => selectedLocationIds.contains(schemeLocation.locationId))
      selectedLocationSchemes.flatMap(_.schemes)
    }
  }

  def getSchemeLocations(applicationId: String): Future[List[String]] = {
    appRepository.getSchemeLocations(applicationId)
  }

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit] = {
    appRepository.updateSchemeLocations(applicationId, locationIds)
  }

  def getSchemes(applicationId: String): Future[List[String]] = {
    appRepository.getSchemes(applicationId)
  }

  def updateSchemes(applicationId: String, schemeNames: List[String]): Future[Unit] = {
    appRepository.updateSchemes(applicationId, schemeNames)
  }
}
