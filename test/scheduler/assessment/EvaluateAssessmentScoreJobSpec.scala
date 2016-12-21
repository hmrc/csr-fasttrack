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

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentEvaluationCommands.{ AssessmentPassmarkPreferencesAndScores, OnlineTestEvaluationAndAssessmentCentreScores }
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults._
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, PreferencesWithQualification }
import model.{ LocationPreference, Preferences }
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.test.WithApplication
import services.applicationassessment.ApplicationAssessmentService
import testkit.{ ShortTimeout, UnitWithAppSpec }

import scala.concurrent.{ ExecutionContext, Future }

class EvaluateAssessmentScoreJobSpec extends UnitWithAppSpec with ShortTimeout {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val applicationAssessmentServiceMock = mock[ApplicationAssessmentService]
  val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = false, None, None)

  object TestableEvaluateAssessmentScoreJob extends EvaluateAssessmentScoreJob {
    val applicationAssessmentService = applicationAssessmentServiceMock
    override val minimumCompetencyLevelConfig = config
  }

  "application assessment service" should {
    "find a candidate and evaluate the score successfully" in new WithApplication {
      val onlineTestEvaluation = OnlineTestPassmarkEvaluation(Green, None, None, None, None)
      val assessmentEvaluation = AssessmentPassmarkPreferencesAndScores(
        AssessmentCentrePassMarkSettingsResponse(List(), None),
        PreferencesWithQualification(
          Preferences(LocationPreference("region", "location", "firstFramework", None)),
          aLevel = true, stemLevel = true
        ),
        CandidateScoresAndFeedback("appId", None, assessmentIncomplete = false)
      )
      val evaluation = OnlineTestEvaluationAndAssessmentCentreScores(onlineTestEvaluation, assessmentEvaluation)
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
