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

package model.exchange

import model.ReportExchangeObjects.CandidateProgressReportItem
import model.{ ProgressStatuses, Scheme }

object CandidateProgressReportItemExamples {
  val PersonalDetailsCompleted = CandidateProgressReportItem(
    Some(ApplicationIdExamples.appId1),
    Some(ProgressStatuses.PersonalDetailsCompleted), List.empty, List.empty,
    None, None, None, None, Some(false), None

  )
  val SchemePreferencesCompleted = CandidateProgressReportItem(
    Some(ApplicationIdExamples.appId2),
    Some(ProgressStatuses.SchemesPreferencesCompleted), List(Scheme.Finance, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.London.locationName),
    None, None, None, None, None, None
  )
  val AssistanceDetailsCompleted = CandidateProgressReportItem(
    Some(ApplicationIdExamples.appId3),
    Some(ProgressStatuses.AssistanceDetailsCompleted), List(Scheme.Commercial, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.Bristol.locationName),
    Some("Yes"), Some(false), Some("Yes"), Some("Yes"), Some(false), None
  )
  val OccupationQuestionsCompleted = CandidateProgressReportItem(
    Some(ApplicationIdExamples.appId4),
    Some(ProgressStatuses.OccupationQuestionsCompleted), List(Scheme.Business, Scheme.Finance),
    List(LocationSchemesExamples.London.locationName, LocationSchemesExamples.Bristol.locationName),
    Some("Yes"), Some(false), Some("Yes"), Some("Yes"), Some(false), None
  )
  val ReviewCompleted = CandidateProgressReportItem(
    Some(ApplicationIdExamples.appId5),
    Some(ProgressStatuses.ReviewCompleted), List(Scheme.Business, Scheme.ProjectDelivery),
    List(LocationSchemesExamples.London.locationName, LocationSchemesExamples.Edinburgh.locationName),
    Some("Yes"), Some(false), Some("Yes"), Some("Yes"), Some(false), None
  )
  val Candidates = List(PersonalDetailsCompleted, SchemePreferencesCompleted, AssistanceDetailsCompleted,
    OccupationQuestionsCompleted, ReviewCompleted)
  val CandidatesWithoutFsac = Candidates.map(_.copy(fsacIndicator = None))
  val CandidatesWithoutLocationNames = Candidates.map(_.copy(locations = List.empty))
}
