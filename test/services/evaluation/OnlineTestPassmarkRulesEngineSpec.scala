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

import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, OnlineTestPassmarkSettings }
import fixture.TestReportFixture._
import model.EvaluationResults._
import model.OnlineTestCommands._
import model.Scheme._
import model.persisted.SchemeEvaluationResult
import model.{ ApplicationStatuses, Schemes }
import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class OnlineTestPassmarkRulesEngineSpec extends PlaySpec {
  //scalastyle:off
  val PassmarkSettings = OnlineTestPassmarkSettings(
    Scheme(Business, SchemeThresholds(competency = t(1.0, 99.0), verbal = t(5.0, 94.0), numerical = t(10.0, 90.0), situational = t(30.0, 85.0)))
      :: Scheme(Commercial, SchemeThresholds(competency = t(15.0, 94.0), verbal = t(20.0, 90.0), numerical = t(25.0, 50.0), situational = t(29.0, 80.0)))
      :: Scheme(DigitalAndTechnology, SchemeThresholds(competency = t(30.0, 80.0), verbal = t(30.0, 80.0), numerical = t(30.0, 80.0), situational = t(29.0, 80.0)))
      :: Scheme(Finance, SchemeThresholds(competency = t(50.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0)))
      :: Scheme(ProjectDelivery, SchemeThresholds(competency = t(10.0, 55.0), verbal = t(53.0, 70.0), numerical = t(30.0, 45.0), situational = t(20.0, 30.0)))
      :: Nil,
    version = "testVersion",
    createDate = new DateTime(),
    createdByUser = "testUser"
  )
  //scalastyle:on

  "Pass mark rules engine for candidate with only one scheme" should {
    val schemes = List(Business)

    val scoresWithPassmark = CandidateEvaluationData(PassmarkSettings, schemes, FullTestReport, ApplicationStatuses.OnlineTestCompleted)

    "evaluate the scheme to GREEN when candidate achieves the pass mark for all tests" in {
      // These scores pass all 4 scheme thresholds for the business scheme
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.0),
        verbal = tScore(94.0),
        numerical = tScore(99.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Green))
    }

    "evaluate the scheme to AMBER when there is at least one AMBER and no REDs" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(100.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Amber))
    }

    "evaluate the scheme to AMBER when a candidate has tScore equals to failmark in at least one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(5.0),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Amber))
    }

    "evaluate the scheme to RED when a candidate has tScore less than failmark in at least one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(4.99),
        numerical = tScore(100.0),
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Red))
    }

    "evaluate the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Green))
    }

    "evaluate the scheme to AMBER when a GIS candidate does not fail any tests and has passed one of them" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(79.2),
        verbal = None,
        numerical = None,
        situational = tScore(99.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Amber))
    }

    "evaluate the scheme to RED when a non GIS candidate has failed one test" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(89.2),
        verbal = tScore(5.0),
        numerical = tScore(100.0),
        situational = tScore(19.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Red))
    }
  }

  "Pass mark rules engine for candidates with all schemes" should {
    val schemes = List(Business, Commercial, DigitalAndTechnology, Finance, ProjectDelivery)
    val scoresWithPassmark = CandidateEvaluationData(PassmarkSettings, schemes, FullTestReport, ApplicationStatuses.OnlineTestCompleted)

    "evaluate all the scheme to GREEN when a candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(94.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Green), SchemeEvaluationResult(Commercial, Green),
        SchemeEvaluationResult(DigitalAndTechnology, Green), SchemeEvaluationResult(Finance, Green),
        SchemeEvaluationResult(ProjectDelivery, Green))
    }

    "evaluate all the scheme to AMBER when a candidate does not achieve all pass marks and no failed tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(54.2),
        verbal = tScore(69.0),
        numerical = tScore(90.3),
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Amber), SchemeEvaluationResult(Commercial, Amber),
        SchemeEvaluationResult(DigitalAndTechnology, Amber), SchemeEvaluationResult(Finance, Amber),
        SchemeEvaluationResult(ProjectDelivery, Amber))
    }

    "evaluate all the scheme to RED when a candidate gets a fail mark for all schemes" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(1.0),
        verbal = tScore(5.0),
        numerical = tScore(10.0),
        situational = tScore(20.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Red), SchemeEvaluationResult(Commercial, Red),
        SchemeEvaluationResult(DigitalAndTechnology, Red), SchemeEvaluationResult(Finance, Red),
        SchemeEvaluationResult(ProjectDelivery, Red))
    }

    "evaluate all the scheme to GREEN when a GIS candidate achieves the pass mark for all tests" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = None,
        numerical = None,
        situational = tScore(100.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Green), SchemeEvaluationResult(Commercial, Green),
        SchemeEvaluationResult(DigitalAndTechnology, Green), SchemeEvaluationResult(Finance, Green),
        SchemeEvaluationResult(ProjectDelivery, Green))
    }

    "evaluate all the schemes according to scores for different passmark per scheme" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.2),
        verbal = tScore(90.0),
        numerical = tScore(50.3),
        situational = tScore(30.0)
      )

      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))
      result mustBe List(SchemeEvaluationResult(Business, Amber), SchemeEvaluationResult(Commercial, Amber),
        SchemeEvaluationResult(DigitalAndTechnology, Amber), SchemeEvaluationResult(Finance, Green),
        SchemeEvaluationResult(ProjectDelivery, Green))
    }
  }

  "Pass mark rules engine" should {
    val schemes = List(Business)
    "throw an exception when there is no passmark for the Scheme" in {
      val passmarkWithoutBusinessScheme = CandidateEvaluationData(
        PassmarkSettings.copy(
          schemes = PassmarkSettings.schemes.filterNot(_.schemeName == Business)
        ), schemes, FullTestReport, ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalStateException] {
        OnlineTestPassmarkRulesEngine.evaluate(passmarkWithoutBusinessScheme)
      }
    }

    "throw an exception when the candidate's report does not have tScore" in {
      val candidateScores = CandidateEvaluationData(
        PassmarkSettings,
        schemes, FullTestReport.copy(competency = noTScore), ApplicationStatuses.OnlineTestCompleted
      )

      intercept[IllegalArgumentException] {
        OnlineTestPassmarkRulesEngine.evaluate(candidateScores)
      }
    }
  }

  "Pass mark rules engine for pass mark equal to fail mark" should {
    //scalastyle:off
    val PassmarkSettings = OnlineTestPassmarkSettings(
      Scheme(Business, SchemeThresholds(competency = t(99.0, 99.0), verbal = t(94.0, 94.0), numerical = t(90.0, 90.0), situational = t(85.0, 85.0)))
        :: Nil,
      version = "testVersion",
      createDate = new DateTime(),
      createdByUser = "testUser"
    )
    //scalastyle:on
    val schemes = List(Business)
    val scoresWithPassmark = CandidateEvaluationData(PassmarkSettings, schemes, FullTestReport, ApplicationStatuses.OnlineTestCompleted)

    "evaluate the scheme to GREEN when the candidate reached the threshold" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(99.0),
        verbal = tScore(94.0),
        numerical = tScore(90.0),
        situational = tScore(85.0)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Green))
    }

    "evaluate the scheme to RED when the candidate does not reached the threshold" in {
      val candidateScores = FullTestReport.copy(
        competency = tScore(100.00),
        verbal = tScore(100.00),
        numerical = tScore(100.00),
        situational = tScore(84.99)
      )
      val result = OnlineTestPassmarkRulesEngine.evaluate(scoresWithPassmark.copy(scores = candidateScores))

      result mustBe List(SchemeEvaluationResult(Business, Red))
    }
  }

  private def tScore(score: Double): Option[TestResult] = Some(TestResult("", "", Some(score), None, None, None))
  private def noTScore: Option[TestResult] = Some(TestResult("", "", None, None, None, None))
  private def t(failThreshold: Double, passThreshold: Double) = SchemeThreshold(failThreshold, passThreshold)
}
