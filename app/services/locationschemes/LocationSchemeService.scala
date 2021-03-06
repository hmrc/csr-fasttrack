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

package services.locationschemes

import model.Exceptions.{ NotEligibleForLocation, NotEligibleForScheme, NotFoundException }
import model.Scheme.Scheme
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import repositories.{ FileLocationSchemeRepository, LocationSchemeRepository, LocationSchemes, _ }
import services.AuditService
import services.locationschemes.exchangeobjects.GeoLocationSchemeResult
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object LocationSchemeService extends LocationSchemeService {
  val locationSchemeRepository = FileLocationSchemeRepository
  val appRepository = applicationRepository
  val pdRepository = personalDetailsRepository
  val auditService = AuditService
}

trait LocationSchemeService {
  val locationSchemeRepository: LocationSchemeRepository
  val appRepository: GeneralApplicationRepository
  val pdRepository: PersonalDetailsRepository
  val auditService: AuditService

  def getEligibleSchemeLocations(applicationId: String, latitudeOpt: Option[Double] = None,
    longitudeOpt: Option[Double] = None): Future[List[GeoLocationSchemeResult]] = {

    for {
      schemeChoices <- getSchemes(applicationId)
      locationsWithSchemes <- locationSchemeRepository.getSchemesAndLocations
    } yield {

      val selectedLocations = locationsWithSchemes.collect {
        case LocationSchemes(locationId, locationName,
          geocodes, availableSchemes) if schemeChoices.map(_.id).intersect(availableSchemes).nonEmpty =>
          val distance = for {
            latitude <- latitudeOpt
            longitude <- longitudeOpt
          } yield {
            geocodes.map { gc => DistanceCalculator.calcMilesBetween(latitude, longitude, gc.lat, gc.lng) }.min
          }

          GeoLocationSchemeResult(locationId, locationName, distance, schemeChoices.filter(scheme => availableSchemes.contains(scheme.id)))
      }
      sortLocations(selectedLocations, latitudeOpt.isDefined && longitudeOpt.isDefined)
    }
  }

  def getEligibleSchemes(applicationId: String): Future[List[SchemeInfo]] = {
    for {
      personalDetails <- pdRepository.find(applicationId)
      schemes = locationSchemeRepository.schemeInfoList
    } yield {
      schemes.filterNot(s =>
        (s.requiresALevel && !(personalDetails.aLevel || personalDetails.stemLevel)) ||
          (s.requiresALevelInStem && !personalDetails.stemLevel))
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

  def getAllSchemeLocations: Future[List[LocationSchemes]] = locationSchemeRepository.getSchemesAndLocations

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit] = {
    require(locationIds.nonEmpty, "Location preferences must not be empty")
    for {
      eligibleSchemeLocations <- getEligibleSchemeLocations(applicationId)
      _ <- if (locationIds.diff(eligibleSchemeLocations.map(_.locationId)).nonEmpty) {
        Future.failed(throw NotEligibleForLocation())
      } else {
        appRepository.updateSchemeLocations(applicationId, locationIds)
      }
    } yield {}
  }

  def getSchemes(applicationId: String): Future[List[SchemeInfo]] = {
    for {
      schemeIds <- appRepository.getSchemes(applicationId)
      schemes = locationSchemeRepository.schemeInfoList
    } yield {
      schemeIds.map(schemeId =>
        schemes.find(_.id == schemeId).getOrElse(throw NotFoundException(Some(s"Scheme $schemeId not found"))))
    }
  }

  def getAvailableSchemes: List[SchemeInfo] = locationSchemeRepository.schemeInfoList

  def updateSchemes(applicationId: String, schemeNames: List[Scheme]): Future[Unit] = {
    require(schemeNames.nonEmpty, "Scheme preferences must not be empty")
    for {
      eligibleSchemes <- getEligibleSchemes(applicationId)
      _ <- if (schemeNames.diff(eligibleSchemes.map(_.id)).nonEmpty) {
        Future.failed(throw NotEligibleForScheme())
      } else {
        appRepository.updateSchemes(applicationId, schemeNames)
      }
    } yield {}
  }

  def removeSchemes(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    auditService.logEvent(s"SchemePreferencesRemoved for candidate with application $applicationId")
    appRepository.removeSchemes(applicationId)
  }

  def removeSchemeLocations(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    auditService.logEvent(s"SchemeLocationPreferencesRemoved for candidate with application $applicationId")
    appRepository.removeSchemeLocations(applicationId)
  }

  private def sortLocations(locations: List[GeoLocationSchemeResult], sortByDistance: Boolean) = {
    if (sortByDistance) {
      locations.sortBy(r => r.distanceMiles.getOrElse(0d))
    } else {
      locations.sortBy(_.locationName)
    }
  }
}
