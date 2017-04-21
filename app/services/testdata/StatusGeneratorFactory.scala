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

package services.testdata

import model.ApplicationStatuses
import model.Exceptions.InvalidStatusException

object StatusGeneratorFactory {
  // scalastyle:off cyclomatic.complexity
  def getGenerator(status: String): BaseGenerator = {
    status match {
      case "REGISTERED" => RegisteredStatusGenerator
      case ApplicationStatuses.Created.name => CreatedStatusGenerator
      case "IN_PROGRESS_PERSONAL_DETAILS" => InProgressPersonalDetailsStatusGenerator
      case "IN_PROGRESS_SCHEME_PREFERENCES" => InProgressSchemePreferencesStatusGenerator
      case "IN_PROGRESS_ASSISTANCE_DETAILS" => InProgressAssistanceDetailsStatusGenerator
      case "IN_PROGRESS_QUESTIONNAIRE_START" => InProgressQuestionnaireStartStatusGenerator
      case "IN_PROGRESS_QUESTIONNAIRE_DIVERSITY" => InProgressQuestionnaireDiversityStatusGenerator
      case "IN_PROGRESS_QUESTIONNAIRE_EDUCATION" => InProgressQuestionnaireEducationStatusGenerator
      case "IN_PROGRESS_QUESTIONNAIRE_PARENTAL_OCCUPATION" | "IN_PROGRESS_QUESTIONNAIRE" =>
        InProgressQuestionnaireParentalOccupationStatusGenerator
      case "IN_PROGRESS_REVIEW" => InProgressReviewStatusGenerator
      case ApplicationStatuses.Submitted.name => SubmittedStatusGenerator
      case ApplicationStatuses.OnlineTestInvited.name => OnlineTestInvitedStatusGenerator
      case ApplicationStatuses.OnlineTestStarted.name => OnlineTestStartedStatusGenerator
      case ApplicationStatuses.OnlineTestCompleted.name => OnlineTestCompletedWithPDFReportStatusGenerator
      case ApplicationStatuses.OnlineTestExpired.name => OnlineTestExpiredStatusGenerator
      case ApplicationStatuses.AwaitingOnlineTestReevaluation.name => AwaitingOnlineTestReevaluationStatusGenerator
      case ApplicationStatuses.OnlineTestFailed.name => OnlineTestFailedStatusGenerator
      case ApplicationStatuses.OnlineTestFailedNotified.name => OnlineTestFailedNotifiedStatusGenerator
      case ApplicationStatuses.AwaitingAllocation.name => AwaitingAllocationStatusGenerator
      case ApplicationStatuses.AwaitingAllocationNotified.name => AwaitingAllocationNotifiedStatusGenerator
      case ApplicationStatuses.AllocationConfirmed.name => AllocationStatusGenerator
      case ApplicationStatuses.AllocationUnconfirmed.name => AllocationStatusGenerator
      case ApplicationStatuses.FailedToAttend.name => FailedToAttendStatusGenerator
      case ApplicationStatuses.AssessmentScoresEntered.name => AssessmentScoresEnteredStatusGenerator
      case "ASSESSMENT_SCORES_SUBMITTED" => AssessmentScoresSubmittedStatusGenerator
      case ApplicationStatuses.AssessmentScoresAccepted.name => AssessmentScoresAcceptedStatusGenerator
      case ApplicationStatuses.AwaitingAssessmentCentreReevaluation.name => AwaitingAssessmentCentreReevalationStatusGenerator
      case ApplicationStatuses.AssessmentCentrePassed.name => AssessmentCentrePassedStatusGenerator
      case ApplicationStatuses.AssessmentCentreFailed.name => AssessmentCentreFailedStatusGenerator
      case ApplicationStatuses.AssessmentCentrePassedNotified.name => AssessmentCentrePassedNotifiedStatusGenerator
      case ApplicationStatuses.AssessmentCentreFailedNotified.name => AssessmentCentreFailedNotifiedStatusGenerator
      case ApplicationStatuses.Withdrawn.name => WithdrawnStatusGenerator
      case _ => throw InvalidStatusException(s"$status is not valid or not supported")
    }
  }
  // scalastyle:on cyclomatic.complexity
}
