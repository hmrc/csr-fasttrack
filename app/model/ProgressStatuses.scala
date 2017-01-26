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


object ProgressStatuses extends Enum {

  sealed case class EnumVal(name: String, weight: Int) extends Value with Ordered[EnumVal] {

    override def compare(that: EnumVal): Int = this.weight compare that.weight

    def isBefore(that: EnumVal): Boolean = this.weight < that.weight
    def isAfter(that: EnumVal): Boolean = this.weight > that.weight
  }


  val RegisteredProgress = EnumVal("registered", 0)
  val PersonalDetailsCompletedProgress = EnumVal("personal_details_completed", 10)
  val SchemeLocationsCompletedProgress = EnumVal("scheme_locations_completed", 20)
  val SchemesPreferencesCompletedProgress = EnumVal("schemes_preferences_completed", 30)
  val AssistanceDetailsCompletedProgress = EnumVal("assistance_details_completed", 40)
  val StartDiversityQuestionnaireProgress = EnumVal("start_questionnaire", 50)
  val DiversityQuestionsCompletedProgress = EnumVal("diversity_questions_completed", 60)
  val EducationQuestionsCompletedProgress = EnumVal("education_questions_completed", 70)
  val OccupationQuestionsCompletedProgress = EnumVal("occupation_questions_completed", 80)
  val ReviewCompletedProgress = EnumVal("review_completed", 90)
  val SubmittedProgress = EnumVal(ApplicationStatuses.Submitted.name.toLowerCase, 100)

  val OnlineTestInvitedProgress = EnumVal(ApplicationStatuses.OnlineTestInvited.name.toLowerCase, 110)
  val OnlineTestStartedProgress = EnumVal(ApplicationStatuses.OnlineTestStarted.name.toLowerCase, 120)
  val OnlineTestFirstExpiryNotification = EnumVal("test_expiry_first_reminder", 121)
  val OnlineTestSecondExpiryNotification = EnumVal("test_expiry_second_reminder", 122)
  val OnlineTestCompletedProgress = EnumVal(ApplicationStatuses.OnlineTestCompleted.name.toLowerCase, 130)
  val OnlineTestExpiredProgress = EnumVal(ApplicationStatuses.OnlineTestExpired.name.toLowerCase, 140)

  val AwaitingOnlineTestReevaluationProgress = EnumVal(ApplicationStatuses.AwaitingOnlineTestReevaluation.name.toLowerCase, 150)

  val OnlineTestFailedProgress = EnumVal(ApplicationStatuses.OnlineTestFailed.name.toLowerCase, 160)
  val OnlineTestFailedNotifiedProgress = EnumVal(ApplicationStatuses.OnlineTestFailedNotified.name.toLowerCase, 170)

  val AwaitingOnlineTestAllocationProgress = EnumVal(ApplicationStatuses.AwaitingAllocation.name.toLowerCase, 180)
  val AllocationUnconfirmedProgress = EnumVal(ApplicationStatuses.AllocationUnconfirmed.name.toLowerCase, 190)

  val AllocationConfirmedProgress = EnumVal(ApplicationStatuses.AllocationConfirmed.name.toLowerCase, 200)
  val AssessmentScoresEnteredProgress = EnumVal(ApplicationStatuses.AssessmentScoresEntered.name.toLowerCase, 210)
  val FailedToAttendProgress = EnumVal(ApplicationStatuses.FailedToAttend.name.toLowerCase, 220)
  val AssessmentScoresAcceptedProgress = EnumVal(ApplicationStatuses.AssessmentScoresAccepted.name.toLowerCase, 230)
  val AwaitingAssessmentCentreReevaluationProgress = EnumVal(ApplicationStatuses.AwaitingAssessmentCentreReevaluation.name.toLowerCase, 240)
  val AssessmentCentreFailedProgress = EnumVal(ApplicationStatuses.AssessmentCentreFailed.name.toLowerCase, 250)
  val AssessmentCentreFailedNotifiedProgress = EnumVal(ApplicationStatuses.AssessmentCentreFailedNotified.name.toLowerCase, 260)
  val AssessmentCentrePassedProgress = EnumVal(ApplicationStatuses.AssessmentCentrePassed.name.toLowerCase, 270)
  val AssessmentCentrePassedNotifiedProgress = EnumVal(ApplicationStatuses.AssessmentCentrePassedNotified.name.toLowerCase, 280)

  val WithdrawnProgress = EnumVal(ApplicationStatuses.Withdrawn.name.toLowerCase, 999)
}
