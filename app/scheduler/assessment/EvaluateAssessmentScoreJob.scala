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

package scheduler.assessment

import config.ScheduledJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import scheduler.onlinetesting.BasicJobConfig
import services.applicationassessment.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

object EvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
  val applicationAssessmentService: AssessmentCentreService = AssessmentCentreService
}

trait EvaluateAssessmentScoreJob extends SingleInstanceScheduledJob with EvaluateAssessmentScoreJobConfig {
  val applicationAssessmentService: AssessmentCentreService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    applicationAssessmentService.nextAssessmentCandidateReadyForEvaluation.flatMap { candidateResultsOpt =>
      candidateResultsOpt.map { candidateResults =>
        applicationAssessmentService.evaluateAssessmentCandidate(candidateResults, minimumCompetencyLevelConfig) // TODO IS: remove the 2
        Future.successful(())
      }.getOrElse(Future.successful(()))
    }
  }
}

trait EvaluateAssessmentScoreJobConfig extends BasicJobConfig[ScheduledJobConfig] {
  this: SingleInstanceScheduledJob =>
  val conf = config.MicroserviceAppConfig.evaluateCandidateScoreJobConfig
  val configPrefix = "scheduling.evaluate-assessment-score-job."
  val name = "EvaluateAssessmentScoreJob"
  val minimumCompetencyLevelConfig = config.MicroserviceAppConfig.assessmentEvaluationMinimumCompetencyLevelConfig
}
