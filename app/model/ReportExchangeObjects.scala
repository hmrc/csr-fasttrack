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

import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.ContactDetails
import model.Commands.Implicits._
import model.CandidateScoresCommands.Implicits._
import model.Scheme.Scheme
import org.joda.time.LocalDate
import play.api.libs.json.Json

object ReportExchangeObjects {
  case class ApplicationUserIdReport(applicationId: UniqueIdentifier, userId: UniqueIdentifier)

  case class AssessmentCentreAllocationReport(
    firstName: String,
    lastName: String,
    preferredName: String,
    emailAddress: String,
    phoneNumber: String,
    dateOfBirth: LocalDate,
    adjustments: Option[String],
    assessmentCentreLocation: String
  )

  case class CandidateProgressReportItem(
    applicationId: Option[UniqueIdentifier],
    progress: Option[String],
    schemes: List[Scheme],
    locations: List[String],
    disability: Option[String],
    gis: Option[Boolean],
    onlineAdjustments: Option[String],
    assessmentCentreAdjustments: Option[String],
    civilServant: Option[Boolean],
    fsacIndicator: Option[String]
  )

  case object CandidateProgressReportItem {
    def apply(application: ApplicationForCandidateProgressReport): CandidateProgressReportItem = {
      CandidateProgressReportItem(
        applicationId = application.applicationId,
        progress = application.progress,
        schemes = application.schemes,
        locations = application.locationIds,
        disability = application.hasDisability,
        gis = application.gis,
        onlineAdjustments = application.onlineAdjustments.map(fromBooleanToYesNo(_)),
        assessmentCentreAdjustments = application.assessmentCentreAdjustments.map(fromBooleanToYesNo(_)),
        civilServant = application.civilServant,
        None
      )
    }
  }

  case class DiversityReportDiversityAnswers(gender: String, sexualOrientation: String, ethnicity: String)

  case class ApplicationForCandidateProgressReport(
    applicationId: Option[UniqueIdentifier],
    userId: UniqueIdentifier,
    progress: Option[String],
    schemes: List[Scheme],
    locationIds: List[String],
    hasDisability: Option[String],
    gis: Option[Boolean],
    onlineAdjustments: Option[Boolean],
    assessmentCentreAdjustments: Option[Boolean],
    adjustments: Option[Adjustments],
    civilServant: Option[Boolean],
    assessmentCentreIndicator: Option[AssessmentCentreIndicator]
  )

  case class ReportWithPersonalDetails(
    applicationId: UniqueIdentifier,
    userId: UniqueIdentifier,
    progress: Option[String],
    firstLocation: Option[String],
    firstLocationFirstScheme: Option[String],
    firstLocationSecondScheme: Option[String],
    secondLocation: Option[String],
    secondLocationFirstScheme: Option[String],
    secondLocationSecondScheme: Option[String],
    alevels: Option[String],
    stemlevels: Option[String],
    alternativeLocation: Option[String],
    alternativeScheme: Option[String],
    hasDisability: Option[String],
    hasAdjustments: Option[String],
    guaranteedInterview: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    preferredName: Option[String],
    dateOfBirth: Option[String],
    cubiksUserId: Option[Int]
  )

  case class PassMarkReportWithPersonalData(
    application: ReportWithPersonalDetails,
    testResults: PassMarkReportTestResults,
    contactDetails: ContactDetails
  )

  // TODO: delete this class once we rebuild the assessment results report
  case class PassMarkReportQuestionnaireData(
    gender: Option[String],
    sexualOrientation: Option[String],
    ethnicity: Option[String],
    parentEmploymentStatus: Option[String],
    parentOccupation: Option[String],
    parentEmployedOrSelf: Option[String],
    parentCompanySize: Option[String],
    socioEconomicScore: String
  )

  case class PassMarkReportTestResults(
    competency: Option[TestResult],
    numerical: Option[TestResult],
    verbal: Option[TestResult],
    situational: Option[TestResult]
  )

  case class OnlineTestPassmarkEvaluationSchemes(
    location1Scheme1: Option[String] = None,
    location1Scheme2: Option[String] = None,
    location2Scheme1: Option[String] = None,
    location2Scheme2: Option[String] = None,
    alternativeScheme: Option[String] = None
  )

  case class ApplicationPreferences(
    userId: UniqueIdentifier,
    applicationId: UniqueIdentifier,
    location1: Option[String],
    location1Scheme1: Option[String],
    location1Scheme2: Option[String],
    location2: Option[String],
    location2Scheme1: Option[String],
    location2Scheme2: Option[String],
    alternativeLocation: Option[String],
    alternativeScheme: Option[String],
    needsAssistance: Option[String],
    guaranteedInterview: Option[String],
    needsAdjustment: Option[String],
    aLevel: Option[String],
    stemLevel: Option[String],
    onlineTestPassmarkEvaluations: OnlineTestPassmarkEvaluationSchemes
  )

  case class AssessmentResultsReport(
    appPreferences: ApplicationPreferences,
    questionnaire: PassMarkReportQuestionnaireData,
    candidateScores: CandidateScoresAndFeedback
  )

  case class PersonalInfo(firstName: Option[String], lastName: Option[String], preferredName: Option[String],
    aLevel: Option[String], stemLevel: Option[String], dateOfBirth: Option[LocalDate])

  case class CandidateScoresSummary(
    avgLeadingAndCommunicating: Option[Double],
    avgCollaboratingAndPartnering: Option[Double],
    avgDeliveringAtPace: Option[Double],
    avgMakingEffectiveDecisions: Option[Double],
    avgChangingAndImproving: Option[Double],
    avgBuildingCapabilityForAll: Option[Double],
    avgMotivationFit: Option[Double],
    totalScore: Option[Double]
  )

  case class SchemeEvaluation(
    commercial: Option[String],
    digitalAndTechnology: Option[String],
    business: Option[String],
    projectDelivery: Option[String],
    finance: Option[String]
  )

  case class ApplicationPreferencesWithTestResults(
    userId: UniqueIdentifier,
    applicationId: UniqueIdentifier,
    location1: Option[String],
    location1Scheme1: Option[String],
    location1Scheme2: Option[String],
    location2: Option[String],
    location2Scheme1: Option[String],
    location2Scheme2: Option[String],
    alternativeLocation: Option[String],
    alternativeScheme: Option[String],
    personalDetails: PersonalInfo,
    scores: CandidateScoresSummary,
    passmarks: SchemeEvaluation
  )

  case class ApplicationPreferencesWithTestResultsAndContactDetails(
    application: ApplicationPreferencesWithTestResults,
    contactDetails: ContactDetails
  )

  case class CandidateAwaitingAllocation(
    userId: String,
    firstName: String,
    lastName: String,
    preferredName: String,
    dateOfBirth: LocalDate,
    adjustments: Option[String],
    assessmentCentreLocation: String
  )

  case class TestResult(tScore: Option[Double], percentile: Option[Double], raw: Option[Double], sten: Option[Double])

  case object TestResult {
    def apply(testResult: model.OnlineTestCommands.TestResult): TestResult = {
      TestResult(tScore = testResult.tScore, percentile = testResult.percentile, raw = testResult.raw, sten = testResult.sten)
    }
  }

  object Implicits {
    implicit val candidateProgressReportItemFormats2 = Json.format[CandidateProgressReportItem]
    implicit val applicationForCandidateProgressReportFormats = Json.format[ApplicationForCandidateProgressReport]
    implicit val candidateProgressReportItemWithPersonalDetailsFormats = Json.format[ReportWithPersonalDetails]
    implicit val passMarkReportTestResultFormats = Json.format[TestResult]
    implicit val assessmentCentreAllocationReportFormats = Json.format[AssessmentCentreAllocationReport]
    implicit val passMarkReportTestResultsFormats = Json.format[PassMarkReportTestResults]
    implicit val passMarkReportQuestionnaireDataFormats = Json.format[PassMarkReportQuestionnaireData]
    implicit val passMarkReportWithPersonalDataFormats = Json.format[PassMarkReportWithPersonalData]
    implicit val ApplicationUserIdFormats = Json.format[ApplicationUserIdReport]
    implicit val passmarkEvaluationSchemesFormats = Json.format[OnlineTestPassmarkEvaluationSchemes]
    implicit val applicationPreferencesFormats = Json.format[ApplicationPreferences]
    implicit val assessmentResultsReportFormats = Json.format[AssessmentResultsReport]
    implicit val personalInfoFormats = Json.format[PersonalInfo]
    implicit val candidateScoresSummaryFormats = Json.format[CandidateScoresSummary]
    implicit val schemeEvaluationFormats = Json.format[SchemeEvaluation]
    implicit val applicationPreferencesWithTestResultsFormats = Json.format[ApplicationPreferencesWithTestResults]
    implicit val successfulCandidatesReportFormats = Json.format[ApplicationPreferencesWithTestResultsAndContactDetails]
    implicit val candidateAwaitingAllocationFormats = Json.format[CandidateAwaitingAllocation]
  }

  val fromBooleanToYesNo: Boolean => String = (b: Boolean) => if (b) "Yes" else "No"
}
