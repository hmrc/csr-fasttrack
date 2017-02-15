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

package repositories

import model.Commands.{ AssessmentCentre, AssessmentScores, ProgressResponse, QuestionnaireProgressResponse }
import model.ProgressStatuses
import model.commands.OnlineTestProgressResponse
import reactivemongo.bson.{ BSONDocument, BSONDocumentReader }

trait BaseBSONReader {
  protected def bsonReader[T](f: BSONDocument => T): BSONDocumentReader[T] = {
    new BSONDocumentReader[T] {
      def read(bson: BSONDocument) = f(bson)
    }
  }

  def toProgressResponse(applicationId: String): BSONDocumentReader[ProgressResponse] = bsonReader {
    (doc: BSONDocument) => findProgress(doc, applicationId)
  }

  // scalastyle:off method.length
  private def findProgress(document: BSONDocument, applicationId: String): ProgressResponse = {
    (document.getAs[BSONDocument]("progress-status") map { root =>

      def getStatus(root: BSONDocument)(key: String) = {
        root.getAs[Boolean](key).getOrElse(false)
      }

      def getProgress = getStatus(root) _

      def getQuestionnaire = getStatus(root.getAs[BSONDocument]("questionnaire").getOrElse(BSONDocument())) _

      ProgressResponse(
        applicationId,
        personalDetails = getProgress(ProgressStatuses.PersonalDetailsCompletedProgress),
        hasSchemeLocations = getProgress(ProgressStatuses.SchemeLocationsCompletedProgress),
        hasSchemes = getProgress(ProgressStatuses.SchemesPreferencesCompletedProgress),
        assistanceDetails = getProgress(ProgressStatuses.AssistanceDetailsCompletedProgress),
        review = getProgress(ProgressStatuses.ReviewCompletedProgress),
        questionnaire = QuestionnaireProgressResponse(
          diversityStarted = getQuestionnaire(ProgressStatuses.StartDiversityQuestionnaireProgress),
          diversityCompleted = getQuestionnaire(ProgressStatuses.DiversityQuestionsCompletedProgress),
          educationCompleted = getQuestionnaire(ProgressStatuses.EducationQuestionsCompletedProgress),
          occupationCompleted = getQuestionnaire(ProgressStatuses.OccupationQuestionsCompletedProgress)
        ),
        submitted = getProgress(ProgressStatuses.SubmittedProgress),
        withdrawn = getProgress(ProgressStatuses.WithdrawnProgress),
        OnlineTestProgressResponse(
          invited = getProgress(ProgressStatuses.OnlineTestInvitedProgress),
          started = getProgress(ProgressStatuses.OnlineTestStartedProgress),
          completed = getProgress(ProgressStatuses.OnlineTestCompletedProgress),
          expired = getProgress(ProgressStatuses.OnlineTestExpiredProgress),
          awaitingReevaluation = getProgress(ProgressStatuses.AwaitingOnlineTestReevaluationProgress),
          failed = getProgress(ProgressStatuses.OnlineTestFailedProgress),
          failedNotified = getProgress(ProgressStatuses.OnlineTestFailedNotifiedProgress),
          awaitingAllocation = getProgress(ProgressStatuses.AwaitingAllocationProgress),
          awaitingAllocationNotified = getProgress(ProgressStatuses.AwaitingAllocationNotifiedProgress),
          allocationConfirmed = getProgress(ProgressStatuses.AllocationConfirmedProgress),
          allocationUnconfirmed = getProgress(ProgressStatuses.AllocationUnconfirmedProgress)
        ),
        failedToAttend = getProgress(ProgressStatuses.FailedToAttendProgress),
        assessmentScores = AssessmentScores(getProgress(ProgressStatuses.AssessmentScoresEnteredProgress),
          getProgress(ProgressStatuses.AssessmentScoresAcceptedProgress)
        ),
        assessmentCentre = AssessmentCentre(
          getProgress(ProgressStatuses.AwaitingAssessmentCentreReevaluationProgress),
          getProgress(ProgressStatuses.AssessmentCentrePassedProgress),
          getProgress(ProgressStatuses.AssessmentCentreFailedProgress),
          getProgress(ProgressStatuses.AssessmentCentrePassedNotifiedProgress),
          getProgress(ProgressStatuses.AssessmentCentreFailedNotifiedProgress)
        )
      )
    }).getOrElse(ProgressResponse(applicationId))
  }
  // scalastyle:on method.length

  protected def booleanTranslator(bool: Boolean) = if (bool) { "Yes" } else { "No" }
}
