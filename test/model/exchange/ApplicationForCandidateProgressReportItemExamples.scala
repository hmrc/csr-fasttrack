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

import model.ReportExchangeObjects.ApplicationForCandidateProgressReport
import model.{ ProgressStatuses, Scheme }

object ApplicationForCandidateProgressReportItemExamples {
  val PersonalDetailsCompleted = ApplicationForCandidateProgressReport(ApplicationIdExamples.appId1, UserIdExamples.userId1,
    Some(ProgressStatuses.PersonalDetailsCompleted), List.empty, List.empty,
    None, None, None, None, None, Some(false)
  )
  val SchemePreferencesCompleted = ApplicationForCandidateProgressReport(ApplicationIdExamples.appId2, UserIdExamples.userId2,
    Some(ProgressStatuses.SchemesPreferencesCompleted), List(Scheme.Finance, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes2.id),
    None, None, None, None, None, None
  )
  val AssistanceDetailsCompleted = ApplicationForCandidateProgressReport(ApplicationIdExamples.appId3, UserIdExamples.userId3,
    Some(ProgressStatuses.AssistanceDetailsCompleted), List(Scheme.Commercial, Scheme.DigitalAndTechnology),
    List(LocationSchemesExamples.LocationSchemes2.id, LocationSchemesExamples.LocationSchemes3.id),
    Some("Yes"), Some(false), Some(true), Some(true), None, Some(false)
  )
  val OccupationQuestionsCompleted = ApplicationForCandidateProgressReport(ApplicationIdExamples.appId4, UserIdExamples.userId4,
    Some(ProgressStatuses.OccupationQuestionsCompleted), List(Scheme.Business, Scheme.Finance),
    List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes3.id),
    Some("Yes"), Some(false), Some(true), Some(true), None, Some(false)
  )
  val ReviewCompleted = ApplicationForCandidateProgressReport(ApplicationIdExamples.appId5, UserIdExamples.userId5,
    Some(ProgressStatuses.ReviewCompleted), List(Scheme.Business, Scheme.ProjectDelivery),
    List(LocationSchemesExamples.LocationSchemes1.id, LocationSchemesExamples.LocationSchemes4.id),
    Some("Yes"), Some(false), Some(true), Some(true), None, Some(false)
  )
  val Applications = List(PersonalDetailsCompleted, SchemePreferencesCompleted, AssistanceDetailsCompleted,
    OccupationQuestionsCompleted, ReviewCompleted)
}
