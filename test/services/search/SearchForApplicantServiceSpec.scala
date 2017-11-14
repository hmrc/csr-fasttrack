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
import model.Commands.{ Address, Candidate, SearchCandidate }
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import org.mockito.Matchers._
import org.mockito.Mockito._
import repositories.ContactDetailsRepository
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.BaseServiceSpec
import testkit.ShortTimeout

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SearchForApplicantServiceSpec extends BaseServiceSpec with ShortTimeout {

  "find by criteria" should {
    "search by first name only" in new TestFixture {
      when(authProviderClientMock.findByFirstName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = Some("Leia"),
        lastName = None, dateOfBirth = None, postCode = None
      ), MaxResults).futureValue

      actual mustBe List(expected)
    }

    "search by last name only" in new TestFixture {
      when(authProviderClientMock.findByLastName(any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = None,
        lastName = Some("Amadala"), dateOfBirth = None, postCode = None
      ), MaxResults).futureValue

      actual mustBe List(expected)
    }

    "search by first name and last name" in new TestFixture {
      when(authProviderClientMock.findByFirstNameAndLastName(any[String], any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = None
      ), MaxResults).futureValue

      actual mustBe List(expected)
    }

    "search by date of birth only" in new TestFixture {
      when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]],
        any[Option[LocalDate]], any[List[String]])).thenReturn(Future.successful(List(Candidate("123", None, None, Some("Leia"), Some("Amadala"), Some("Amadala"),
        Some(new LocalDate("1990-11-25")), None, None))))

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = None,
        lastName = None, dateOfBirth = Some(new LocalDate("1990-11-25")), postCode = None
      ), MaxResults).futureValue

      val expectedWithDateOfBirth = expected.copy(dateOfBirth = Some(new LocalDate("1990-11-25")))
      actual mustBe List(expectedWithDateOfBirth)
    }

    "filter by post code" in new TestFixture {
      when(authProviderClientMock.findByFirstNameAndLastName(any[String], any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(
        Future.successful(Nil)
      )

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = Some("QQ1 1QQ")
      ), MaxResults).futureValue

      actual mustBe List(expected)
    }

    "return the number of candidates from auth provider even if more candidates are in the application repository" in new TestFixture {
      val candidate = Candidate("123", None, None, Some("Leia"), Some("Amadala"), Some("Amadala"), Some(new LocalDate("1990-11-25")), None, None)
      val candidates = List(candidate, candidate, candidate, candidate)
      when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]], any[Option[LocalDate]],
        any[List[String]])).thenReturn(Future.successful(candidates))

      val authProviderCandidate = connectors.ExchangeObjects.Candidate("Leia", "Amadala", None, "email@test.com", "userId")
      val authProviderCandidates = List(authProviderCandidate, authProviderCandidate, authProviderCandidate)
      when(authProviderClientMock.findByFirstNameAndLastName(any[String], any[String], any[List[String]])(any[HeaderCarrier])).thenReturn(Future.successful(authProviderCandidates))

      val actual = searchForApplicantService.findByCriteria(SearchCandidate(
        firstOrPreferredName = Some("Leia"),
        lastName = Some("Amadala"), dateOfBirth = None, postCode = None
      ), MaxResults).futureValue

      actual.size mustBe authProviderCandidates.size
    }
  }

  trait TestFixture {
    val appRepositoryMock = mock[GeneralApplicationRepository]
    val psRepositoryMock = mock[PersonalDetailsRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val authProviderClientMock = mock[AuthProviderClient]

    val MaxResults = 1

    val searchForApplicantService = new SearchForApplicantService {
      override val appRepository = appRepositoryMock
      override val psRepository = psRepositoryMock
      override val cdRepository = cdRepositoryMock
      override val authProviderClient = authProviderClientMock
    }

    val testAddress = Address(line1 = "1 Test Street", line2 = None, line3 = None, line4 = None)
    val testEmail = "test@test.com"
    val expected = Candidate(
      userId = "123",
      applicationId = None,
      email = Some(testEmail),
      firstName = Some("Leia"),
      lastName = Some("Amadala"),
      preferredName = Some("Amadala"),
      dateOfBirth = None,
      address = Some(testAddress),
      postCode = Some("QQ1 1QQ")
    )

    when(cdRepositoryMock.findByPostCode(any[String])).thenReturn(
      Future.successful(
        List(ContactDetailsWithId(userId = "123", false, address = testAddress, postCode = Some("QQ1 1QQ"), None, email = testEmail,
          phone = None))
      )
    )

    when(cdRepositoryMock.findByUserIds(any[List[String]])).thenReturn(
      Future.successful(
        List(ContactDetailsWithId(userId = "123", false, address = testAddress, postCode = Some("QQ1 1QQ"), None, email = testEmail,
          phone = None))
      )
    )

    when(appRepositoryMock.findByCriteria(any[Option[String]], any[Option[String]],
      any[Option[LocalDate]], any[List[String]])).thenReturn(Future.successful(List(Candidate("123", None, None, Some("Leia"), Some("Amadala"), Some("Amadala"), None, None, None))))
  }
}
