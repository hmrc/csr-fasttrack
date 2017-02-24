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

package services.search

import connectors.AuthProviderClient
import model.Commands.{ Candidate, PostCode, SearchCandidate }
import model.Exceptions.ContactDetailsNotFound
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SearchForApplicantService extends SearchForApplicantService {
  val appRepository = applicationRepository
  val psRepository = personalDetailsRepository
  val cdRepository = contactDetailsRepository
  val authProviderClient = AuthProviderClient
}

trait SearchForApplicantService {
  val appRepository: GeneralApplicationRepository
  val psRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val authProviderClient: AuthProviderClient

  def findByCriteria(
    searchCandidate: SearchCandidate,
    maxResults: Int
  )(implicit hc: HeaderCarrier): Future[List[Candidate]] = searchCandidate match {
    case SearchCandidate(None, None, None, Some(postCode)) =>
      searchByPostCode(postCode)
    case SearchCandidate(firstOrPreferredName, lastName, dateOfBirth, postCode) =>
      search(firstOrPreferredName, lastName, dateOfBirth, postCode, maxResults)
  }

  private def searchByPostCode(postCode: String): Future[List[Candidate]] =
    cdRepository.findByPostCode(postCode).flatMap { cdList =>
      Future.sequence(cdList.map { cd =>
        appRepository.findCandidateByUserId(cd.userId).map(_.map { candidate =>
          candidate.copy(address = Some(cd.address), postCode = cd.postCode)
        }).recover {
          case _: ContactDetailsNotFound => None
        }
      })
    }.map(_.flatten)

  private def search(firstOrPreferredName: Option[String], lastName: Option[String],
    dateOfBirth: Option[LocalDate], postCode: Option[PostCode], maxResults: Int)(implicit hc: HeaderCarrier) = {
    def joinCandidatesWithApplicationsIfInTheLimit(candidates: List[Candidate]) = {
      if (candidates.size > maxResults) {
        Future.successful(candidates)
      } else {
        searchByAllNamesOrDobAndFilterPostCode(candidates, firstOrPreferredName, lastName, dateOfBirth, postCode)
      }
    }

    val authProviderResults = searchAuthProviderByFirstAndLastName(firstOrPreferredName, lastName)
    authProviderResults flatMap joinCandidatesWithApplicationsIfInTheLimit
  }

  private def searchByAllNamesOrDobAndFilterPostCode(
    authProviderResults: List[Candidate],
    firstOrPreferredNameOpt: Option[String],
    lastNameOpt: Option[String],
    dateOfBirthOpt: Option[LocalDate],
    postCodeOpt: Option[String]
  )(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    for {
      contactDetailsFromPostcode <- postCodeOpt.map(cdRepository.findByPostCode).getOrElse(Future.successful(List.empty))
      candidates <- appRepository.findByCriteria(firstOrPreferredNameOpt, lastNameOpt, dateOfBirthOpt, contactDetailsFromPostcode.map(_.userId))
      combinedCandidates = candidates ++ authProviderResults.filter(
        authProviderCandidate => !candidates.exists(_.userId == authProviderCandidate.userId)
      )
      contactDetailsOfCandidates <- cdRepository.findByUserIds(candidates.map(_.userId))
    } yield for {
      candidate <- combinedCandidates
      contactDetailMap = contactDetailsOfCandidates.map(x => x.userId -> x)(collection.breakOut): Map[String, ContactDetailsWithId]
    } yield {
      contactDetailMap.get(candidate.userId).map { candidateContactDetails =>
        candidate.copy(
          email = Some(candidateContactDetails.email),
          address = Some(candidateContactDetails.address),
          postCode = candidateContactDetails.postCode
        )
      }.getOrElse(candidate)
    }

  private def searchAuthProviderByFirstAndLastName(
    firstNameOpt: Option[String],
    lastNameOpt: Option[String]
  )(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    (firstNameOpt, lastNameOpt) match {
      case (Some(firstName), Some(lastName)) => searchByFirstNameAndLastName(firstName, lastName)
      case (Some(firstName), None) => searchByFirstName(firstName)
      case (None, Some(lastName)) => searchByLastName(lastName)
      case (None, None) => Future.successful(List.empty)
    }

  private def searchByFirstNameAndLastName(firstName: String, lastName: String)(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    for {
      results <- authProviderClient.findByFirstNameAndLastName(firstName, lastName, List("candidate"))
    } yield {
      results.map(exchangeCandidate =>
        convertCandidate(exchangeCandidate))
    }

  private def searchByFirstName(firstName: String)(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    for {
      results <- authProviderClient.findByFirstName(firstName, List("candidate"))
    } yield {
      results.map(exchangeCandidate =>
        convertCandidate(exchangeCandidate))
    }

  private def searchByLastName(lastName: String)(implicit hc: HeaderCarrier): Future[List[Candidate]] =
    for {
      results <- authProviderClient.findByLastName(lastName, List("candidate"))
    } yield {
      results.map(exchangeCandidate =>
        convertCandidate(exchangeCandidate))
    }

  private def convertCandidate(exchangeCandidate: connectors.ExchangeObjects.Candidate): Candidate =
    Candidate(
      userId = exchangeCandidate.userId,
      applicationId = None,
      email = Some(exchangeCandidate.email),
      firstName = Some(exchangeCandidate.firstName),
      lastName = Some(exchangeCandidate.lastName),
      preferredName = exchangeCandidate.preferredName,
      dateOfBirth = None,
      address = None,
      postCode = None
    )
}
