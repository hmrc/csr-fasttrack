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

package services.applicationassessment

import model.ApplicationStatuses
import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands.{ ApplicationScores, CandidateScoresAndFeedback, ExerciseScoresAndFeedback, RecordCandidateScores }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application._
import services.AuditService
import services.applicationassessment.ReviewerAssessmentScoresService.{ assessmentScoresRepo, auditService }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AssessmentCentreScoresRemovalService extends AssessmentCentreScoresRemovalService {
  val reviewerAssessmentScoresRepo: ApplicationAssessmentScoresRepository = reviewerAssessmentScoresRepository
  val assessorAssessmentScoresRepo: ApplicationAssessmentScoresRepository = assessorAssessmentScoresRepository
  val auditService: AuditService = AuditService
}

trait AssessmentCentreScoresRemovalService {
  val reviewerAssessmentScoresRepo: ApplicationAssessmentScoresRepository
  val assessorAssessmentScoresRepo: ApplicationAssessmentScoresRepository
  val auditService: AuditService

  def removeScoresAndFeedback(applicationId: String, exercise: AssessmentExercise)(implicit hc: HeaderCarrier,
                                                                                   rh: RequestHeader): Future[Unit] = {
    for {
      _ <- reviewerAssessmentScoresRepo.removeExercise(applicationId, exercise)
      _ <- assessorAssessmentScoresRepo.removeExercise(applicationId, exercise)
    } yield {
      auditService.logEvent("ApplicationExerciseScoresAndFeedbackRemoved", Map(
        "applicationId" -> applicationId, "exercise" -> exercise.toString)
      )
    }
  }
}
