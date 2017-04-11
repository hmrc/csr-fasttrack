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

import connectors.ExchangeObjects.Candidate
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ScoresAndFeedback }
import model.Commands.AssessmentCentreAllocation
import model.ReportExchangeObjects.ApplicationForAssessmentScoresReport
import model.{ AssessmentCentreIndicator, UniqueIdentifier }
import org.joda.time.{ DateTime, LocalDate }

object AssessmentScoresReportExamples {

  val userId = UniqueIdentifier.randomUniqueIdentifier

  val applicationForAssessmentScoresReport = List(ApplicationForAssessmentScoresReport(
    applicationId = Some(ApplicationIdExamples.appId1.toString()),
    userId = userId.toString(),
    assessmentCentreIndicator = Some(AssessmentCentreIndicator(area = "London", assessmentCentre = "London")),
    firstName = Some("Joe"),
    lastName = Some("Bloggs")
  ))

  val applicationForAssessmentScoresReportMissingApplicationId = List(ApplicationForAssessmentScoresReport(
    applicationId = None,
    userId = userId.toString(),
    assessmentCentreIndicator = Some(AssessmentCentreIndicator(area = "London", assessmentCentre = "London")),
    firstName = Some("Joe"),
    lastName = Some("Bloggs")
  ))

  val assessmentCentreAllocations = List(AssessmentCentreAllocation(
    applicationId = ApplicationIdExamples.appId1.toString(),
    venue = "London FSAC", date = new LocalDate(2017, 4, 3), session = "PM", slot = 1, confirmed = true
  ))

  val assessorSavedAndSubmittedScoresAndFeedbackForCandidate = List(CandidateScoresAndFeedback(
    applicationId = ApplicationIdExamples.appId1.toString(),
    interview = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor1UserId", savedDate = Some(DateTime.now()),
      submittedDate = Some(DateTime.now())
    )),
    groupExercise = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor2UserId", savedDate = Some(DateTime.now()),
      submittedDate = Some(DateTime.now())
    )),
    writtenExercise = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor3UserId", savedDate = Some(DateTime.now()),
      submittedDate = Some(DateTime.now())
    ))
  ))

  val assessorSavedScoresAndFeedbackForCandidate = List(CandidateScoresAndFeedback(
    applicationId = ApplicationIdExamples.appId1.toString(),
    interview = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor1UserId", savedDate = Some(DateTime.now())
    )),
    groupExercise = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor2UserId", savedDate = Some(DateTime.now())
    )),
    writtenExercise = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor3UserId", savedDate = Some(DateTime.now())
    ))
  ))

  val assessorInterviewScoresAndFeedbackForCandidateButNoDatesPersisted = List(CandidateScoresAndFeedback(
    applicationId = ApplicationIdExamples.appId1.toString(),
    interview = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "assessor1UserId"
    ))
  ))

  val reviewerInterviewScoresAndFeedbackForCandidate = List(CandidateScoresAndFeedback(
    applicationId = ApplicationIdExamples.appId1.toString(),
    interview = Some(ScoresAndFeedback(
      attended = true, assessmentIncomplete = false, updatedBy = "reviewerUserId", savedDate = Some(DateTime.now()),
      submittedDate = Some(DateTime.now())
    ))
  ))

  val authProviderAssessors = List(
    Candidate(firstName = "John", lastName = "Doe", preferredName = None, email = "john@doe.com", userId = "assessor1UserId"),
    Candidate(firstName = "Jane", lastName = "Doe", preferredName = None, email = "john@doe.com", userId = "assessor2UserId"),
    Candidate(firstName = "Jenny", lastName = "Jackson", preferredName = None, email = "john@doe.com", userId = "assessor3UserId")
  )
}
