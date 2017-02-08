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

package model

import connectors.PassMarkExchangeObjects.Settings
import controllers._
import model.CandidateScoresCommands.Implicits._
import model.Exceptions.{ NoResultsReturned, TooManyEntries }
import model.OnlineTestCommands.Implicits._
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme }
import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.commands.OnlineTestProgressResponse
import org.joda.time.{ DateTime, LocalDate, LocalTime }
import play.api.libs.json._

import scala.language.implicitConversions

//scalastyle:off
object Commands {

  case class AddMedia(userId: String, media: String)

  case class CreateApplicationRequest(userId: String, frameworkId: String)

  case class WithdrawApplicationRequest(reason: String, otherReason: Option[String], withdrawer: String)

  case class PassMarkSettingsRequest(settings: Settings)

  case class ApplicationCreated(applicationId: String, applicationStatus: ApplicationStatuses.EnumVal, userId: String)

  case class Address(line1: String, line2: Option[String] = None, line3: Option[String] = None, line4: Option[String] = None)

  type PostCode = String
  type PhoneNumber = String

  case class UpdateGeneralDetails(
    firstName: String,
    lastName: String,
    preferredName: String,
    email: String,
    dateOfBirth: LocalDate,
    outsideUk: Boolean,
    address: Address,
    postCode: Option[PostCode],
    country: Option[String],
    phone: Option[PhoneNumber],
    aLevel: Boolean,
    stemLevel: Boolean,
    civilServant: Boolean,
    department: Option[String]
  ) {
    require(outsideUk && country.isDefined || !outsideUk && postCode.isDefined, "Either post code or country is mandatory")
  }

  case class AssessmentScores(
    entered: Boolean = false,
    accepted: Boolean = false
  )

  case class AssessmentCentre(
    awaitingReevaluation: Boolean = false,
    passed: Boolean = false,
    failed: Boolean = false,
    passedNotified: Boolean = false,
    failedNotified: Boolean = false
  )



  case class ProgressResponse(
    applicationId: String,
    personalDetails: Boolean = false,
    hasSchemeLocations: Boolean = false,
    hasSchemes: Boolean = false,
    assistanceDetails: Boolean = false,
    review: Boolean = false,
    questionnaire: QuestionnaireProgressResponse = QuestionnaireProgressResponse(),
    submitted: Boolean = false,
    withdrawn: Boolean = false,
    onlineTest: OnlineTestProgressResponse = OnlineTestProgressResponse(),
    failedToAttend: Boolean = false,
    assessmentScores: AssessmentScores = AssessmentScores(),
    assessmentCentre: AssessmentCentre = AssessmentCentre()
  )

  case class QuestionnaireProgressResponse(
    diversityStarted: Boolean = false,
    diversityCompleted: Boolean = false,
    educationCompleted: Boolean = false,
    occupationCompleted: Boolean = false
  )

  case class CandidateDetailsReportItem(appId: String, userId: String, csvRecord: String)

  case class CsvExtract[A](header: String, records: Map[String, A]) {

    def emptyRecord() = List.fill(header.split(",").length)("\"\"").mkString(",")

  }

  case class ContactDetails(phone: Option[String], email: String, address: Address, postCode: Option[PostCode])

  type IsNonSubmitted = Boolean

  case class PreferencesWithContactDetails(firstName: Option[String], lastName: Option[String], preferredName: Option[String],
                                           email: Option[String], telephone: Option[String], location1: Option[String], location1Scheme1: Option[String],
                                           location1Scheme2: Option[String], location2: Option[String], location2Scheme1: Option[String],
                                           location2Scheme2: Option[String], progress: Option[String], timeApplicationCreated: Option[String])

  case class ApplicationResponse(
    applicationId: String,
    applicationStatus: ApplicationStatuses.EnumVal,
    userId: String,
    progressResponse: ProgressResponse
  )

  case class PassMarkSettingsCreateResponse(passMarkSettingsVersion: String, passMarkSettingsCreateDate: DateTime)

  //  questionnaire
  case class Answer(answer: Option[String], otherDetails: Option[String], unknown: Option[Boolean])

  case class Question(question: String, answer: Answer)

  case class Questionnaire(questions: List[Question])

  case class ReviewRequest(flag: Boolean)

  case class SearchCandidate(firstOrPreferredName: Option[String], lastName: Option[String],
                             dateOfBirth: Option[LocalDate], postCode: Option[PostCode])

  case class Candidate(userId: String, applicationId: Option[String], email: Option[String], firstName: Option[String], lastName: Option[String],
                       preferredName: Option[String], dateOfBirth: Option[LocalDate], address: Option[Address], postCode: Option[PostCode])

  case class ApplicationAssessment(applicationId: String, venue: String, date: LocalDate, session: String, slot: Int, confirmed: Boolean) {
    val assessmentDateTime = {
      // TODO This should be configurable in the future, but hardcoding it in the fasttrack service is the lesser of the evils at the moment
      // FSET-471 was an emergency last minute fix
      if (venue == "Manchester" || venue == "London (Berkeley House)") {
        if (session == "AM") {
          date.toLocalDateTime(new LocalTime(9, 0)).toDateTime
        } else {
          date.toLocalDateTime(new LocalTime(13, 0)).toDateTime
        }
      } else {
        if (session == "AM") {
          date.toLocalDateTime(new LocalTime(8, 30)).toDateTime
        } else {
          date.toLocalDateTime(new LocalTime(12, 30)).toDateTime
        }
      }
    }

    // If a candidate is allocated at DD/MM/YYYY, the deadline for the candidate to confirm is 10 days.
    // Because we don't store the time it means we need to set DD-11/MM/YYY, and remember that there is
    // an implicit time 23:59:59 after which the allocation expires.
    // After DD-11/MM/YYYY the allocation is expired.
    // For Example:
    // - The candidate is scheduled on 25/05/2016.
    // - It means the deadline is 14/05/2016 23:59:59
    def expireDate: LocalDate = date.minusDays(11)
  }

  case class AssessmentCentrePassMarkSettingsResponse(
    schemes: List[AssessmentCentrePassMarkScheme],
    info: Option[AssessmentCentrePassMarkInfo]
  )

  object Implicits {
    implicit val mediaFormats = Json.format[AddMedia]
    implicit val addressFormat = Json.format[Address]
    implicit val assessmentScoresFormat = Json.format[AssessmentScores]
    implicit val assessmentCentresFormat = Json.format[AssessmentCentre]
    implicit val questionnaireResponseFormat = Json.format[QuestionnaireProgressResponse]
    implicit val progressFormat = Json.format[ProgressResponse]
    implicit val applicationAddedFormat = Json.format[ApplicationResponse]
    implicit val passMarkSettingsCreateResponseFormat = Json.format[PassMarkSettingsCreateResponse]
    implicit val createApplicationRequestFormats: Format[CreateApplicationRequest] = Json.format[CreateApplicationRequest]
    implicit val withdrawApplicationRequestFormats: Format[WithdrawApplicationRequest] = Json.format[WithdrawApplicationRequest]
    implicit val updatePersonalDetailsRequestFormats: Format[UpdateGeneralDetails] = Json.format[UpdateGeneralDetails]

    implicit val answerFormats = Json.format[Answer]
    implicit val questionFormats = Json.format[Question]
    implicit val questionnaireFormats = Json.format[Questionnaire]
    implicit val reviewFormats = Json.format[ReviewRequest]

    implicit val tooManyEntriesFormat = Json.format[TooManyEntries]
    implicit val noResultsReturnedFormat = Json.format[NoResultsReturned]

    implicit val searchCandidateFormat = Json.format[SearchCandidate]
    implicit val candidateFormat = Json.format[Candidate]
    implicit val preferencesWithContactDetailsFormat = Json.format[PreferencesWithContactDetails]

    implicit def fromCommandToPersistedQuestion(q: Question): PersistedQuestion =
      PersistedQuestion(q.question, PersistedAnswer(q.answer.answer, q.answer.otherDetails, q.answer.unknown))

    implicit val onlineTestStatusFormats = Json.format[OnlineTestStatus]
    implicit val onlineTestExtensionFormats = Json.format[OnlineTestExtension]
    implicit val userIdWrapperFormats = Json.format[UserIdWrapper]

    import PassmarkPersistedObjects.Implicits._

    implicit val applicationAssessmentFormat = Json.format[ApplicationAssessment]
    implicit val phoneAndEmailFormat = Json.format[ContactDetails]
    implicit val assessmentCentrePassMarkSettingsResponseFormat = Json.format[AssessmentCentrePassMarkSettingsResponse]
  }
}
