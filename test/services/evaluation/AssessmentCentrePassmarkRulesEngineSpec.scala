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

package services.evaluation

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.CandidateScoresCommands.{ CandidateScores, CandidateScoresAndFeedback }
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults.{ Amber, Red, _ }
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold }
import model.PersistedObjects.{ OnlineTestPassmarkEvaluation, PreferencesWithQualification }
import model.{ Alternatives, LocationPreference, Preferences }
import org.joda.time.DateTime
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import model.Schemes._
import model.Scheme
import model.persisted.SchemeEvaluationResult

class AssessmentCentrePassmarkRulesEngineSpec extends PlaySpec with MustMatchers {
  val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
    AssessmentCentrePassMarkScheme(Scheme.Business, Some(PassMarkSchemeThreshold(1.0, 32.0))),
    AssessmentCentrePassMarkScheme(Scheme.Commercial, Some(PassMarkSchemeThreshold(5.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology, Some(PassMarkSchemeThreshold(27.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.Finance, Some(PassMarkSchemeThreshold(12.0, 19.0))),
    AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery, Some(PassMarkSchemeThreshold(23.0, 30.0)))
  ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))
  val CandidateScoresWithFeedback = CandidateScoresAndFeedback("app1", Some(true), assessmentIncomplete = false,
    CandidateScores(Some(2.1), Some(3.4), Some(3.3)),
    CandidateScores(None, Some(2.0), Some(3.0)),
    CandidateScores(Some(4.0), None, Some(3.0)),
    CandidateScores(None, Some(3.0), Some(4.0)),
    CandidateScores(Some(4.0), None, Some(4.0)),
    CandidateScores(Some(4.0), Some(4.0), None),
    CandidateScores(Some(2.0), Some(4.0), None))
  val CandidatePreferences = Preferences(
    LocationPreference("London", "London", Business, Some(Commercial)),
    Some(LocationPreference("London", "Reading", Finance, Some(ProjectDelivery))),
    alternatives = Some(Alternatives(location = true, framework = true))
  )
  val CandidatePreferencesWithQualification = PreferencesWithQualification(CandidatePreferences, aLevel = true, stemLevel = true)
  val CandidateOnlineTestEvaluation = OnlineTestPassmarkEvaluation(
    location1Scheme1 = Green,
    location1Scheme2 = Some(Red), location2Scheme1 = Some(Amber), location2Scheme2 = Some(Green),
    alternativeScheme = Some(Green)
  )

  val rulesEngine = AssessmentCentrePassmarkRulesEngine

  "Assessment Centre Passmark Rules engine evaluation" should {
    "evalute to passedMinimumCompetencyLevel=false when minimum competency level is enabled and not met" in {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, minimumCompetencyLevelScore = Some(2.0),
        motivationalFitMinimumCompetencyLevelScore = Some(4.0))
      val scores = CandidateScoresWithFeedback.copy(collaboratingAndPartnering = CandidateScores(None, Some(1.0), Some(2.0)))
      val candidateScore = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, CandidatePreferencesWithQualification, scores)

      val result = rulesEngine.evaluate(CandidateOnlineTestEvaluation, candidateScore, config)

      result must be(AssessmentRuleCategoryResult(
        passedMinimumCompetencyLevel = Some(false),
        location1Scheme1 = None, location1Scheme2 = None, location2Scheme1 = None, location2Scheme2 = None,
        alternativeScheme = None, competencyAverageResult = None, schemesEvaluation = None
      ))
    }

    // TODO IAN: Please fix the tests once you tackle assessment centre evaluation
    "evalute to passedMinimumCompetencyLevel=true and evaluate preferred locations" ignore {
      val config = AssessmentEvaluationMinimumCompetencyLevel(enabled = true, Some(2.0), Some(4.0))
      val scores = CandidateScoresWithFeedback
      val assessmentPassmarkAndScores = AssessmentPassmarkPreferencesAndScores(PassmarkSettings, CandidatePreferencesWithQualification, scores)

      val result = rulesEngine.evaluate(CandidateOnlineTestEvaluation, assessmentPassmarkAndScores, config)

      result.passedMinimumCompetencyLevel mustBe Some(true)
      result.location1Scheme1 mustBe Some(Amber) // because online test = Green and assessment centre = Amber
      result.location1Scheme2 mustBe Some(Red) // because online test = Red
      result.location2Scheme1 mustBe Some(Amber) // because online test = Amber
      result.location2Scheme2 mustBe Some(Amber) // because online test = Green and assessment centre = Amber
      result.alternativeScheme mustBe Some(Red) // because online test = Green and assessment centre = Red

      val expectedCompetencyAverage = CompetencyAverageResult(2.9333333333333336, 2.5, 3.5, 3.5, 4.0, 4.0, 6.0, 26.433333333333334)
      result.competencyAverageResult mustBe Some(expectedCompetencyAverage)

      val FinalSchemeEvaluation = List(
        SchemeEvaluationResult(Scheme.Business, Amber), // location1Scheme1
        SchemeEvaluationResult(Scheme.Commercial, Red), // location1Scheme2
        SchemeEvaluationResult(Scheme.DigitalAndTechnology, Red),
        SchemeEvaluationResult(Scheme.Finance, Amber), //location2Scheme1
        SchemeEvaluationResult(Scheme.ProjectDelivery, Amber) //location2Scheme2
      )
      result.schemesEvaluation mustBe Some(FinalSchemeEvaluation)
    }
  }
}
