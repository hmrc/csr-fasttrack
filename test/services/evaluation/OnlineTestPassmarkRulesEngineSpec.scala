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

import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, Settings }
import fixture.TestReportFixture._
import model.EvaluationResults._
import model.OnlineTestCommands._
import model.Scheme._
import model.{ ApplicationStatuses, Schemes }
import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class OnlineTestPassmarkRulesEngineSpec extends PlaySpec {
  //scalastyle:off
  val PassmarkSettings = Settings(
    Scheme(Business.toString, SchemeThresholds(competency = t(1.0, 99.0), verbal = t(5.0, 94.0), numerical = t(10.0, 90.0), situational = t(30.0, 85.0), combination = None))
      :: Scheme(Commercial.toString, SchemeThresholds(competency = t(15.0, 94.0), verbal = t(20.0, 90.0), numerical = t(25.0, 50.0), situational = t(29.0, 80.0), combination = None))
      :: Scheme(DigitalAndTechnology.toString, SchemeThresholds(competency = t(30.0, 80.0), verbal = t(30.0, 80.0), numerical = t(30.0, 80.0), situational = t(29.0, 80.0), combination = None))
      :: Scheme(Finance.toString, SchemeThresholds(competency = t(50.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0), combination = None))
      :: Scheme(ProjectDelivery.toString, SchemeThresholds(competency = t(10.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0), combination = None))
      :: Nil,
    version = "testVersion",
    createDate = new DateTime(),
    createdByUser = "testUser",
    setting = "location1Scheme1"
  )
  //scalastyle:on

  "Pass mark rules engine for candidate with only one scheme" should {
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

    "evaluate the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Green)
    }

    "evaluate the scheme to AMBER when a GIS candidate does not fail any tests but has not passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(79.2),
        verbal = None,
        numerical = None,
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Amber)
    }

    "evaluate the scheme to RED when a GIS candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = None,
        numerical = None,
        situational = tScore(19.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Red)
    }
  }

  "Pass mark rules engine for candidates with all schemes" should {
    val schemes = List(Business, Commercial, DigitalAndTechnology, Finance, ProjectDelivery)
    val scoresWithPassmark = CandidateScoresWithPreferencesAndPassmarkSettings(PassmarkSettings, schemes, FullTestReport,
      ApplicationStatuses.OnlineTestCompleted)

    "evaluate all the scheme to GREEN when a candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(94.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Green, Commercial -> Green, DigitalAndTechnology -> Green, Finance -> Green, ProjectDelivery -> Green)
    }

    "evaluate all the scheme to AMBER when a candidate does not achieve all pass marks and at least one fail mark" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(54.2),
        verbal = tScore(69.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Amber, Commercial -> Amber, DigitalAndTechnology -> Amber, Finance -> Amber, ProjectDelivery -> Amber)
    }

    "evaluate all the scheme to RED when a candidate gets a fail mark for all schemes" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(1.0),
        verbal = tScore(5.0),
        numerical = tScore(10.0),
        situational = tScore(20.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Red, Commercial -> Red, DigitalAndTechnology -> Red, Finance -> Red, ProjectDelivery -> Red)
    }

    "evaluate all the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Green, Commercial -> Green, DigitalAndTechnology -> Green, Finance -> Green, ProjectDelivery -> Green)
    }

    "evaluate all the schemes according to scores for different passmark per scheme" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(90.0),
        numerical = tScore(50.3),
        situational = tScore(30.0)
      )

      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe Map(Business -> Red, Commercial -> Amber, DigitalAndTechnology -> Amber, Finance -> Green, ProjectDelivery -> Green)
    }
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
