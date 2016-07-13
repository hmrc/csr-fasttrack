/*
 * Copyright 2016 HM Revenue & Customs
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
import services.applicationassessment.ApplicationAssessmentService

import scala.concurrent.{ ExecutionContext, Future }

object EvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
  val applicationAssessmentService: ApplicationAssessmentService = ApplicationAssessmentService
}

trait EvaluateAssessmentScoreJob extends SingleInstanceScheduledJob with EvaluateAssessmentScoreJobConfig {
  val applicationAssessmentService: ApplicationAssessmentService

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    applicationAssessmentService.nextAssessmentCandidateScoreReadyForEvaluation.flatMap { scoresOpt =>
      scoresOpt.map { scores =>
        applicationAssessmentService.evaluateAssessmentCandidateScore(scores, minimumCompetencyLevelConfig)
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
