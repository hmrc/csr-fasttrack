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

import model.Exceptions.NotFoundException
import model.Scheme.Scheme
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.{ FileLocationSchemeRepository, LocationSchemeRepository, LocationSchemes, _ }
import services.locationschemes.exchangeobjects.GeoLocationSchemeResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
      sortLocations(selectedLocations, latitudeOpt.isDefined && longitudeOpt.isDefined)
    }
  }

  def getAvailableSchemesInSelectedLocations(applicationId: String): Future[List[SchemeInfo]] = {
    for {
      personalDetails <- pdRepository.find(applicationId)
      selectedLocationIds <- appRepository.getSchemeLocations(applicationId)
      eligibleSchemeLocations <- getSchemesAndLocationsByEligibility(personalDetails.aLevel, personalDetails.stemLevel)
    } yield {
      val selectedLocationSchemes = eligibleSchemeLocations.filter(schemeLocation => selectedLocationIds.contains(schemeLocation.locationId))
      selectedLocationSchemes.flatMap(_.schemes)
    }
  }

  def getSchemeLocations(applicationId: String): Future[List[LocationSchemes]] = {
    for {
      locationIds <- appRepository.getSchemeLocations(applicationId)
      locationSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {
      locationIds.map(locId =>
        locationSchemes.find(_.id == locId).getOrElse(throw NotFoundException(Some(s"Location $locId not found"))))
    }
  }

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit] = {
    appRepository.updateSchemeLocations(applicationId, locationIds)
  }

  def getSchemes(applicationId: String): Future[List[SchemeInfo]] = {
    for {
      schemeIds <- appRepository.getSchemes(applicationId)
      schemes <- locationSchemeRepository.getSchemeInfo
    } yield {
      schemeIds.map(schemeId =>
        schemes.find(_.id == schemeId).getOrElse(throw NotFoundException(Some(s"Scheme $schemeId not found"))))
    }
  }

  def updateSchemes(applicationId: String, schemeNames: List[Scheme]): Future[Unit] = {
    appRepository.updateSchemes(applicationId, schemeNames)
  }

  private def sortLocations(locations: List[GeoLocationSchemeResult], sortByDistance: Boolean) = {
    sortByDistance match {
      case true => locations.sortBy(r => r.distanceKm.getOrElse(0d))
      case false => locations.sortBy(_.locationName)
    }
  }
}
