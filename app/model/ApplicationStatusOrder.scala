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

package model

import model.Commands.ProgressResponse
import model.ProgressStatuses._

object ApplicationStatusOrder {

  def getStatus(progress: Option[ProgressResponse]): ProgressStatuses.EnumVal = progress match {
    case Some(p) => getStatus(p)
    case None => RegisteredProgress
  }

  def getStatus(progress: ProgressResponse): ProgressStatuses.EnumVal = {
    val default = RegisteredProgress.weight -> RegisteredProgress

    type StatusMap = (Boolean, Int, ProgressStatuses.EnumVal)
    type HighestStatus = (Int, ProgressStatuses.EnumVal)

    def combineStatuses(statusMap: Seq[StatusMap]): HighestStatus = {
      statusMap.foldLeft(default) { (highest, current) =>
        val (highestWeighting, _) = highest
        current match {
          case (true, weighting, name) if weighting > highestWeighting => weighting -> name
          case _ => highest
        }
      }
    }

    val (_, statusName) = combineStatuses(statusMaps(progress))
    statusName
  }

  def isNonSubmittedStatus(progress: ProgressResponse): Boolean = {
    val isNotSubmitted = !progress.submitted
    val isNotWithdrawn = !progress.withdrawn
    isNotWithdrawn && isNotSubmitted
  }

  def statusMaps(progress: ProgressResponse) = Seq(
    (progress.personalDetails, PersonalDetailsCompletedProgress.weight, PersonalDetailsCompletedProgress),
    (progress.hasSchemeLocations, SchemeLocationsCompletedProgress.weight, SchemeLocationsCompletedProgress),
    (progress.hasSchemes, SchemesPreferencesCompletedProgress.weight, SchemesPreferencesCompletedProgress),
    (progress.assistanceDetails, AssistanceDetailsCompletedProgress.weight, AssistanceDetailsCompletedProgress),

    (progress.questionnaire.diversityStarted, StartDiversityQuestionnaireProgress.weight, StartDiversityQuestionnaireProgress),
    (progress.questionnaire.diversityCompleted, DiversityQuestionsCompletedProgress.weight, DiversityQuestionsCompletedProgress),
    (progress.questionnaire.educationCompleted, EducationQuestionsCompletedProgress.weight, EducationQuestionsCompletedProgress),
    (progress.questionnaire.occupationCompleted, OccupationQuestionsCompletedProgress.weight, OccupationQuestionsCompletedProgress),

    (progress.review, ReviewCompletedProgress.weight, ReviewCompletedProgress),
    (progress.submitted, SubmittedProgress.weight, SubmittedProgress),
    (progress.onlineTest.invited, OnlineTestInvitedProgress.weight, OnlineTestInvitedProgress),
    (progress.onlineTest.started, OnlineTestStartedProgress.weight, OnlineTestStartedProgress),
    (progress.onlineTest.completed, OnlineTestCompletedProgress.weight, OnlineTestCompletedProgress),
    (progress.onlineTest.expired, OnlineTestExpiredProgress.weight, OnlineTestExpiredProgress),
    (progress.onlineTest.awaitingReevaluation, AwaitingOnlineTestReevaluationProgress.weight, AwaitingOnlineTestReevaluationProgress),
    (progress.onlineTest.failed, OnlineTestFailedProgress.weight, OnlineTestFailedProgress),
    (progress.onlineTest.failedNotified, OnlineTestFailedNotifiedProgress.weight, OnlineTestFailedNotifiedProgress),
    (progress.onlineTest.awaitingAllocation, AwaitingAllocationProgress.weight, AwaitingAllocationProgress),
    (progress.onlineTest.awaitingAllocationNotified, AwaitingAllocationNotifiedProgress.weight,
      AwaitingAllocationNotifiedProgress),
    (progress.onlineTest.allocationUnconfirmed, AllocationUnconfirmedProgress.weight, AllocationUnconfirmedProgress),
    (progress.onlineTest.allocationExpired, AllocationExpiredProgress.weight, AllocationExpiredProgress),
    (progress.onlineTest.allocationConfirmed, AllocationConfirmedProgress.weight, AllocationConfirmedProgress),
    (progress.assessmentScores.entered, AssessmentScoresEnteredProgress.weight, AssessmentScoresEnteredProgress),
    (progress.failedToAttend, FailedToAttendProgress.weight, FailedToAttendProgress),
    (progress.assessmentScores.accepted, AssessmentScoresAcceptedProgress.weight, AssessmentScoresAcceptedProgress),

    (progress.assessmentCentre.awaitingReevaluation, AwaitingAssessmentCentreReevaluationProgress.weight,
      AwaitingAssessmentCentreReevaluationProgress),

    (progress.assessmentCentre.failed, AssessmentCentreFailedProgress.weight, AssessmentCentreFailedProgress),
    (progress.assessmentCentre.failedNotified, AssessmentCentreFailedNotifiedProgress.weight, AssessmentCentreFailedNotifiedProgress),
    (progress.assessmentCentre.passed, AssessmentCentrePassedProgress.weight, AssessmentCentrePassedProgress),
    (progress.assessmentCentre.passedNotified, AssessmentCentrePassedNotifiedProgress.weight, AssessmentCentrePassedNotifiedProgress),

    (progress.withdrawn, WithdrawnProgress.weight, WithdrawnProgress)
  )
}
