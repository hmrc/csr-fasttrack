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

  val Registered = "registered"
  val PersonalDetailsCompleted = "personal_details_completed"
  val SchemeLocationsCompleted = "scheme_locations_completed"
  val SchemesPreferencesCompleted = "schemes_preferences_completed"
  val AssistanceDetailsCompleted = "assistance_details_completed"
  val StartQuestionnaire = "start_questionnaire"
  val DiversityQuestionsCompleted = "diversity_questions_completed"
  val EducationQuestionsCompleted = "education_questions_completed"
  val OccupationQuestionsCompleted = "occupation_questions_completed"
  val ReviewCompleted = "review_completed"
  val TestExpiryFirstReminder = "test_expiry_first_reminder"
  val TestExpirySecondReminder = "test_expiry_second_reminder"

  val RegisteredProgress = EnumVal(Registered, 0)
  val PersonalDetailsCompletedProgress = EnumVal(PersonalDetailsCompleted, 10)
  val SchemeLocationsCompletedProgress = EnumVal(SchemeLocationsCompleted, 20)
  val SchemesPreferencesCompletedProgress = EnumVal(SchemesPreferencesCompleted, 30)
  val AssistanceDetailsCompletedProgress = EnumVal(AssistanceDetailsCompleted, 40)
  val StartDiversityQuestionnaireProgress = EnumVal(StartQuestionnaire, 50)
  val DiversityQuestionsCompletedProgress = EnumVal(DiversityQuestionsCompleted, 60)
  val EducationQuestionsCompletedProgress = EnumVal(EducationQuestionsCompleted, 70)
  val OccupationQuestionsCompletedProgress = EnumVal(OccupationQuestionsCompleted, 80)
  val ReviewCompletedProgress = EnumVal(ReviewCompleted, 90)
  val SubmittedProgress = EnumVal(ApplicationStatuses.Submitted.name.toLowerCase, 100)

  val OnlineTestInvitedProgress = EnumVal(ApplicationStatuses.OnlineTestInvited.name.toLowerCase, 110)
  val OnlineTestStartedProgress = EnumVal(ApplicationStatuses.OnlineTestStarted.name.toLowerCase, 120)
  val OnlineTestFirstExpiryNotification = EnumVal(TestExpiryFirstReminder, 121)
  val OnlineTestSecondExpiryNotification = EnumVal(TestExpirySecondReminder, 122)
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
