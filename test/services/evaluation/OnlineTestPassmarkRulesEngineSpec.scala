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

import connectors.PassMarkExchangeObjects.{Scheme, SchemeThreshold, SchemeThresholds, Settings}
import fixture.PreferencesFixture
import fixture.TestReportFixture._
import model.EvaluationResults.{RuleCategoryResult, _}
import model.OnlineTestCommands._
import model.{ApplicationStatuses, Schemes}
import model.Scheme._
import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class OnlineTestPassmarkRulesEngineSpec extends PlaySpec {
  //scalastyle:off
  val PassmarkSettings = Settings(
    Scheme(Schemes.Business, SchemeThresholds(competency = t(1.0, 99.0), verbal = t(5.0, 94.0), numerical = t(10.0, 90.0), situational = t(30.0, 85.0), combination = None))
      :: Scheme(Schemes.Commercial, SchemeThresholds(competency = t(15.0, 94.0), verbal = t(20.0, 90.0), numerical = t(25.0, 50.0), situational = t(29.0, 80.0), combination = None))
      :: Scheme(Schemes.DigitalAndTechnology, SchemeThresholds(competency = t(30.0, 80.0), verbal = t(30.0, 80.0), numerical = t(30.0, 80.0), situational = t(29.0, 80.0), combination = None))
      :: Scheme(Schemes.Finance, SchemeThresholds(competency = t(50.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0), combination = None))
      :: Scheme(Schemes.ProjectDelivery, SchemeThresholds(competency = t(10.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0), combination = None))
      :: Nil,
    version = "testVersion",
    createDate = new DateTime(),
    createdByUser = "testUser",
    setting = "location1Scheme1"
  )
  //scalastyle:on

//  val CombinedPassmarkSettings = PassmarkSettings.copy(schemes =
//    PassmarkSettings.schemes.map { scheme =>
//      scheme.copy(schemeThresholds = scheme.schemeThresholds.copy(combination = Some(t(51.0, 80.0))))
//    })
//
  "Pass mark rules engine for candidate with only one scheme" should {
//    val prefs = PreferencesFixture.preferences(Schemes.Business)
    val schemes = List(Business)

    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, schemes, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)

    "evaluate the scheme to GREEN when candidate achieves the pass mark for all tests" in {
      // These scores pass all 4 scheme thresholds for the business scheme
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.0),
        verbal = tScore(94.0),
        numerical = tScore(99.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Green)//RuleCategoryResult(Green, None, None, None, None)
    }

    "evaluate the scheme to AMBER when candidate does not fail any tests but has not passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(79.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Amber)//RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the scheme to RED when a candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(5.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Red) //RuleCategoryResult(Red, None, None, None, None))
    }
/*
    "evaluate the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Green, None, None, None, None))
    }

    "evaluate the scheme to AMBER when a GIS candidate does not fail any tests but has not passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(79.2),
        verbal = None,
        numerical = None,
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Amber, None, None, None, None))
    }

    "evaluate the scheme to RED when a GIS candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = None,
        numerical = None,
        situational = tScore(19.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result must be(RuleCategoryResult(Red, None, None, None, None))
    }
*/
  }

  "Pass mark rules engine for candidates with all schemes" should {
//    val schemes = List(Business, Commercial, DigitalAndTechnology, Finance, ProjectDelivery)
    val schemes = List(Business, Commercial, Finance)
//    val prefs = PreferencesFixture.preferences(Schemes.Business, Some(Schemes.Commercial),
//      Some(Schemes.DigitalAndTechnology), Some(Schemes.Finance), Some(true))
//
    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, schemes, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)
//
    "evaluate all the scheme to GREEN when a candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(94.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Green, Commercial -> Green, Finance -> Green)
    }
//
//    "evaluate all the scheme to AMBER when a candidate does not achieve all pass marks and at least one fail mark" in {
//      val candidateScores = FullTestReport.copy(
//        competency = tScore(54.2),
//        verbal = tScore(69.0),
//        numerical = tScore(90.3),
//        situational = tScore(100.0)
//      )
//      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
//
//      result must be(RuleCategoryResult(Amber, Some(Amber), Some(Amber), Some(Amber), Some(Amber)))
//    }
//
//    "evaluate all the scheme to RED when a candidate gets a fail mark for all schemes" in {
//      val candidateScores = FullTestReport.copy(
//        competency = tScore(1.0),
//        verbal = tScore(5.0),
//        numerical = tScore(10.0),
//        situational = tScore(20.0)
//      )
//      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
//
//      result must be(RuleCategoryResult(Red, Some(Red), Some(Red), Some(Red), Some(Red)))
//    }
//
//    "evaluate all the scheme to GREEN and do not set Alternative Scheme when a candidate does not want alternative schemes" in {
//      val candidateScores = FullTestReport.copy(
//        competency = tScore(99.2),
//        verbal = tScore(94.0),
//        numerical = tScore(90.3),
//        situational = tScore(100.0)
//      )
//      val prefs = PreferencesFixture.preferences(Schemes.Business, Some(Schemes.Commercial),
//        Some(Schemes.DigitalAndTechnology), Some(Schemes.Finance), alternativeScheme = Some(false))
//
//      val result = OnlineTestPassmarkRulesEngine.evaluate(
//        scoresWithPassmark.copy(preferences = prefs, scores = candidateScores)
//      )
//
//      result must be(RuleCategoryResult(Green, Some(Green), Some(Green), Some(Green), None))
//    }
//
//    "evaluate all the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
//      val candidateScores = FullTestReport.copy(
//        competency = tScore(99.2),
//        verbal = None,
//        numerical = None,
//        situational = tScore(100.0)
//      )
//      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
//
//      result must be(RuleCategoryResult(Green, Some(Green), Some(Green), Some(Green), Some(Green)))
//    }
//
//    "evaluate all the schemes according to scores for different passmark per scheme" in {
//      val candidateScores = FullTestReport.copy(
//        competency = tScore(99.2),
//        verbal = tScore(90.0),
//        numerical = tScore(50.3),
//        situational = tScore(30.0)
//      )
//
//      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
//
//      result must be(RuleCategoryResult(Red, Some(Amber), Some(Amber), Some(Green), Some(Green)))
//    }
  }

  "Pass mark rules engine" should {
    val schemes = List(Business)
    "throw an exception when there is no passmark for the Scheme" in {
      val passmarkWithoutBusinessScheme = CandidateScoresWithPreferencesAndPassmarkSettings(
        PassmarkSettings.copy(
          schemes = PassmarkSettings.schemes.filterNot(_.schemeName == Schemes.Business)
        ), schemes, FullTestReport, ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalStateException] {
        OnlineTestPassmarkRulesEngine.evaluate(passmarkWithoutBusinessScheme)
      }
    }

    "throw an exception when the candidate's report does not have tScore" in {
      val candidateScores = CandidateScoresWithPreferencesAndPassmarkSettings(
        PassmarkSettings,
        schemes, FullTestReport.copy(competency = noTScore),
        ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalArgumentException] {
        OnlineTestPassmarkRulesEngine.evaluate(candidateScores)
      }
    }
  }

  private def tScore(score: Double): Option[TestResult] = Some(TestResult("", "", Some(score), None, None, None))
  private def noTScore: Option[TestResult] = Some(TestResult("", "", None, None, None, None))
  private def t(failThreshold: Double, passThreshold: Double) = SchemeThreshold(failThreshold, passThreshold)
}
