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

package scheduler.assessment

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.EvaluationResults._
import model.persisted.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkSettings, OnlineTestPassmarkEvaluation, SchemeEvaluationResult }
import model.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import org.joda.time.DateTime
import org.mockito.Mockito._
import services.applicationassessment.AssessmentCentreService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EvaluateAssessmentScoreJobSpec extends UnitWithAppSpec with ShortTimeout {
  val applicationAssessmentServiceMock = mock[AssessmentCentreService]
  val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)
  val onlineTestEvaluation = OnlineTestPassmarkEvaluation("passmark", List(SchemeEvaluationResult(model.Scheme.Business, Green)))
  val assessmentEvaluation = AssessmentPassmarkPreferencesAndScores(
    AssessmentCentrePassMarkSettings(List(), AssessmentCentrePassMarkInfo("1", DateTime.now, "user")),
    List(model.Scheme.Business), CandidateScoresAndFeedback("appId"))
  val evaluation = OnlineTestEvaluationAndAssessmentCentreScores(onlineTestEvaluation, assessmentEvaluation)

  object TestableEvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
    val applicationAssessmentService = applicationAssessmentServiceMock
    override val minimumCompetencyLevelConfig = config
  }

  "application assessment service" should {
    "find a candidate and evaluate the score successfully" in {
      when(applicationAssessmentServiceMock.nextAssessmentCandidateReadyForEvaluation).thenReturn(
        Future.successful(Some(evaluation))
      )

      when(applicationAssessmentServiceMock.evaluateAssessmentCandidate(evaluation, config)).thenReturn(
        Future.successful(())
      )

      TestableEvaluateAssessmentScoreJob.tryExecute().futureValue

      verify(applicationAssessmentServiceMock).evaluateAssessmentCandidate(evaluation, config)
    }
  }
}
