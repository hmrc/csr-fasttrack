/*
 * Copyright 2018 HM Revenue & Customs
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

package model

import model.Commands.{ Address, PhoneNumber, PostCode }
import model.OnlineTestCommands.TestResult
import org.joda.time.LocalDate
import play.api.libs.json._
import reactivemongo.bson.Macros

object PersistedObjects {

  case class ApplicationStatus(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal)

  case class PersonalDetailsWithUserId(preferredName: String, userId: String)

  case class ContactDetails(
      outsideUk: Boolean,
      address: Address,
      postCode: Option[PostCode],
      country: Option[String],
      email: String,
      phone: Option[PhoneNumber]
  ) {
    require(outsideUk && country.isDefined || !outsideUk && postCode.isDefined, "Either post code or country is mandatory")
  }

  case class ContactDetailsWithId(
      userId: String,
      outsideUk: Boolean,
      address: Address,
      postCode: Option[PostCode],
      country: Option[String],
      email: String,
      phone: Option[PhoneNumber]
  ) {
    require(outsideUk && country.isDefined || !outsideUk && postCode.isDefined, "Either post code or country is mandatory")
  }

  case class ExpiringOnlineTest(
    applicationId: String,
    userId: String,
    preferredName: String
  )

  case class ExpiringAllocation(applicationId: String, userId: String)

  case class ApplicationForNotification(
    applicationId: String,
    userId: String,
    preferredName: String,
    applicationStatus: ApplicationStatuses.EnumVal
  )

  case class ApplicationIdWithUserIdAndStatus(applicationId: String, userId: String, applicationStatus: ApplicationStatuses.EnumVal)

  case class PersistedAnswer(answer: Option[String], otherDetails: Option[String], unknown: Option[Boolean])
  case class PersistedQuestion(question: String, answer: PersistedAnswer)

  case class UserIdAndPhoneNumber(userId: String, phoneNumber: Option[PhoneNumber])

  case class CandidateTestReport(applicationId: String, reportType: String,
      competency: Option[TestResult] = None, numerical: Option[TestResult] = None,
      verbal: Option[TestResult] = None, situational: Option[TestResult] = None) {

    def isValid(gis: Boolean) = {
      val competencyValid = competency.exists(testIsValid(tScore = true))
      val situationalValid = situational.exists(testIsValid(tScore = true, raw = true, percentile = true, sten = true))
      val numericalValid = numerical.exists(testIsValid(tScore = true, raw = true, percentile = true))
      val verbalValid = verbal.exists(testIsValid(tScore = true, raw = true, percentile = true))

      competencyValid && situationalValid && (gis ^ (numericalValid && verbalValid))
    }

    private def testIsValid(tScore: Boolean, raw: Boolean = false, percentile: Boolean = false, sten: Boolean = false)(result: TestResult) = {
      !((tScore && result.tScore.isEmpty) ||
        (raw && result.raw.isEmpty) ||
        (percentile && result.percentile.isEmpty) ||
        (sten && result.sten.isEmpty))
    }
  }

  case class OnlineTestPDFReport(applicationId: String)

  case class AllocatedCandidate(candidateDetails: PersonalDetailsWithUserId, applicationId: String, expireDate: LocalDate)

  case class ApplicationProgressStatus(name: String, value: Boolean)
  case class ApplicationProgressStatuses(
    statuses: Option[List[ApplicationProgressStatus]],
    questionnaireStatuses: Option[List[ApplicationProgressStatus]]
  )

  object Implicits {
    implicit val applicationStatusFormats = Json.format[ApplicationStatus]
    implicit val addressFormats = Json.format[Address]
    implicit val contactDetailsFormats = Json.format[ContactDetails]
    implicit val contactDetailsIdFormats = Json.format[ContactDetailsWithId]
    implicit val expiringOnlineTestFormats = Json.format[ExpiringOnlineTest]
    implicit val applicationForNotificationFormats = Json.format[ApplicationForNotification]
    implicit val answerFormats = Json.format[PersistedAnswer]
    implicit val questionFormats = Json.format[PersistedQuestion]
    implicit val personalDetailsWithUserIdFormats = Json.format[PersonalDetailsWithUserId]

    implicit val testFormats = Json.format[TestResult]
    implicit val candidateTestReportFormats = Json.format[CandidateTestReport]
    implicit val allocatedCandidateFormats = Json.format[AllocatedCandidate]
    implicit val applicationProgressStatusFormats = Json.format[ApplicationProgressStatus]
    implicit val applicationProgressStatusesFormats = Json.format[ApplicationProgressStatuses]

    implicit val onlineTestPdfReportFormats = Json.format[OnlineTestPDFReport]
  }
}
