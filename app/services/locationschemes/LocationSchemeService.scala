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

  def getEligibleSchemeLocations(applicationId: String, latitudeOpt: Option[Double] = None, longitudeOpt: Option[Double] = None)
  : Future[List[GeoLocationSchemeResult]] = {

    for {
      schemeChoices <- getSchemes(applicationId)
      locationsWithSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {

      val selectedLocations = locationsWithSchemes.collect {
        case LocationSchemes(locationId, locationName, schemeLatitude, schemeLongitude, availableSchemes)
          if schemeChoices.map(_.name).intersect(availableSchemes).nonEmpty =>
          val distance = for {
            latitude <- latitudeOpt
            longitude <- longitudeOpt
          } yield {
              DistanceCalculator.calcKilometersBetween(latitude, longitude, schemeLatitude, schemeLongitude)
          }

          GeoLocationSchemeResult(locationId, locationName, distance, schemeChoices.filter(scheme => availableSchemes.contains(scheme.name)))
      }
      sortLocations(selectedLocations, latitudeOpt.isDefined && longitudeOpt.isDefined)
    }
  }

  def getEligibleSchemes(applicationId: String): Future[List[SchemeInfo]] = {
      for {
        personalDetails <- pdRepository.find(applicationId)
        schemes <- locationSchemeRepository.getSchemeInfo
      } yield {
        schemes.filterNot(s => s.requiresALevel && !personalDetails.aLevel || s.requiresALevelInStem && !personalDetails.stemLevel)
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

  def getSchemeLocations(locationIds: List[String]): Future[List[LocationSchemes]] = {
    for {
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
