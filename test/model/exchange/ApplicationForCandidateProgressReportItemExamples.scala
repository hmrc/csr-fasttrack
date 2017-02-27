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

package model.exchange

import model.ReportExchangeObjects.{ ApplicationForCandidateProgressReport }
import model.{ ProgressStatuses, Scheme, UniqueIdentifier }

object ApplicationForCandidateProgressReportItemExamples {
  val PersonalDetailsCompleted = ApplicationForCandidateProgressReport(Some(ApplicationIdExamples.appId1), UserIdExamples.userId1,
    Some(ProgressStatuses.PersonalDetailsCompleted), List.empty, List.empty,
    None, None, None, None, None, Some(false), None)
  val SchemePreferencesCompleted = ApplicationForCandidateProgressReport(Some(ApplicationIdExamples.appId2), UserIdExamples.userId2,
    Some(ProgressStatuses.SchemesPreferencesCompleted), List(Scheme.Finance, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes2.id),
    None, None, None, None, None, None, None)
  val AssistanceDetailsCompleted = ApplicationForCandidateProgressReport(Some(ApplicationIdExamples.appId3), UserIdExamples.userId3,
    Some(ProgressStatuses.AssistanceDetailsCompleted), List(Scheme.Commercial, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.LocationSchemes2.id, LocationSchemesExamples.LocationSchemes3.id),
    Some("Yes"), Some(false), Some(true), Some(true), None, Some(false), None)
  val OccupationQuestionsCompleted = ApplicationForCandidateProgressReport(Some(ApplicationIdExamples.appId4), UserIdExamples.userId4,
    Some(ProgressStatuses.OccupationQuestionsCompleted), List(Scheme.Business, Scheme.Finance),
    List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes3.id),
    Some("Yes"), Some(false), Some(true), Some(true), None, Some(false), None)
  val ReviewCompleted = ApplicationForCandidateProgressReport(
    applicationId = Some(ApplicationIdExamples.appId5),
    userId = UserIdExamples.userId5,
    progress = Some(ProgressStatuses.ReviewCompleted),
    schemes = List(Scheme.Business, Scheme.ProjectDelivery),
    locationIds = List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes4.id),
    hasDisability = Some("Yes"),
    gis = Some(false),
    onlineAdjustments = Some(true),
    assessmentCentreAdjustments = Some(true),
    adjustments = None,
    civilServant = Some(false),
    assessmentCentreIndicator = None
  )

  val Applications = List(PersonalDetailsCompleted, SchemePreferencesCompleted, AssistanceDetailsCompleted,
    OccupationQuestionsCompleted, ReviewCompleted)
  val AssessmentScoresCompleted = ApplicationForCandidateProgressReport(
    applicationId = Some(UniqueIdentifier("3dee295c-6154-42b7-8353-472ae94ec980")),
    userId = UniqueIdentifier("e0b28a6e-9092-4aa2-a19e-c6a8cb13d349"),
    progress = Some(ProgressStatuses.AssessmentScoresAcceptedProgress),
    schemes = List(Scheme.Business,Scheme.ProjectDelivery),
    locationIds = List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes2.id),
    hasDisability = Some("Yes"),
    gis = Some(false),
    onlineAdjustments = Some(true),
    assessmentCentreAdjustments = Some(true),
    adjustments = None,
    civilServant = Some(false),
    assessmentCentreIndicator = None
  )
}
