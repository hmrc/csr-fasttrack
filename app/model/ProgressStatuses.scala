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


object ProgressStatuses extends Enumeration {

  val RegisteredProgress = "registered"
  val PersonalDetailsCompletedProgress = "personal_details_completed"
  val LocationsCompletedProgress = "scheme_locations_completed"
  val SchemesCompletedProgress = "schemes_preferences_completed"
  val AssistanceDetailsCompletedProgress = "assistance_details_completed"
  val ReviewCompletedProgress = "review_completed"
  val StartDiversityQuestionnaireProgress = "start_diversity_questionnaire"
  val DiversityQuestionsCompletedProgress = "diversity_questions_completed"
  val EducationQuestionsCompletedProgress = "education_questions_completed"
  val OccupationQuestionsCompletedProgress = "occupation_questions_completed"

  val SubmittedProgress = ApplicationStatuses.Submitted.name.toLowerCase
  val WithdrawnProgress = ApplicationStatuses.Withdrawn.name.toLowerCase
  val OnlineTestInvitedProgress = ApplicationStatuses.OnlineTestInvited.name.toLowerCase
  val OnlineTestStartedProgress = ApplicationStatuses.OnlineTestStarted.name.toLowerCase
  val OnlineTestCompletedProgress = ApplicationStatuses.OnlineTestCompleted.name.toLowerCase
  val OnlineTestExpiredProgress = ApplicationStatuses.OnlineTestExpired.name.toLowerCase
  val AwaitingOnlineTestReevaluationProgress = ApplicationStatuses.AwaitingOnlineTestReevaluation.name.toLowerCase
  val OnlineTestFailedProgress = ApplicationStatuses.OnlineTestFailed.name.toLowerCase
  val OnlineTestFailedNotifiedProgress = ApplicationStatuses.OnlineTestFailedNotified.name.toLowerCase
  val AwaitingOnlineTestAllocationProgress = ApplicationStatuses.AwaitingAllocation.name.toLowerCase
  val AllocationConfirmedProgress = ApplicationStatuses.AllocationConfirmed.name.toLowerCase
  val AllocationUnconfirmedProgress = ApplicationStatuses.AllocationUnconfirmed.name.toLowerCase
  val FailedToAttendProgress = ApplicationStatuses.FailedToAttend.name.toLowerCase
  val AssessmentScoresEnteredProgress = ApplicationStatuses.AssessmentScoresEntered.name.toLowerCase
  val AssessmentScoresAcceptedProgress = ApplicationStatuses.AssessmentScoresAccepted.name.toLowerCase
  val AwaitingAssessmentCentreReevaluationProgress = ApplicationStatuses.AwaitingAssessmentCentreReevaluation.name.toLowerCase
  val AssessmentCentrePassedProgress = ApplicationStatuses.AssessmentCentrePassed.name.toLowerCase
  val AssessmentCentreFailedProgress = ApplicationStatuses.AssessmentCentreFailed.name.toLowerCase
  val AssessmentCentrePassedNotifiedProgress = ApplicationStatuses.AssessmentCentrePassedNotified.name.toLowerCase
  val AssessmentCentreFailedNotifiedProgress = ApplicationStatuses.AssessmentCentreFailedNotified.name.toLowerCase
}
