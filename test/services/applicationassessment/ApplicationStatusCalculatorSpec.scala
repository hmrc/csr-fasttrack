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
import model.ApplicationStatuses._
import model.EvaluationResults.{ AssessmentRuleCategoryResult, _ }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec

class ApplicationStatusCalculatorSpec extends PlaySpec with TableDrivenPropertyChecks {
  private val red = Some(Red)
  private val green = Some(Green)
  private val amber = Some(Amber)
  private val notChosen = None

  val calculator = new ApplicationStatusCalculator {}

  // scalastyle: OFF
  // format: OFF
  val FailedMinCompetencyLevel = Table(
    ("min competency level | loc1sch1 | loc1sch2 | loc2sch1 | loc2sch2 | alternative]", "Expected Application status"),
    (r(Some(false)),                                                                    AssessmentCentreFailed),
    (r(Some(false),          green),                                                    AssessmentCentreFailed),
    (r(Some(false),          amber),                                                    AssessmentCentreFailed),
    (r(Some(false),          green,     notChosen, notChosen, amber,     notChosen),    AssessmentCentreFailed),
    (r(Some(false),          green,     green,     green,     amber,     notChosen),    AssessmentCentreFailed),
    (r(Some(false),          green,     green,     green,     amber,     green),        AssessmentCentreFailed)
  )

  val FailedAllPreferences = Table(
    ("Final evaluation [loc1sch1 | loc1sch2 | loc2sch1 | loc2sch2 | alternative]", "Expected Application status"),
    (result(            red),                                                      AssessmentCentreFailed),
    (result(            red,       red),                                           AssessmentCentreFailed),
    (result(            red,       red,       red,      red),                      AssessmentCentreFailed),
    (result(            red,       red,       red,      red,        red),          AssessmentCentreFailed),
    (result(            red,       notChosen, notChosen, notChosen, red),          AssessmentCentreFailed)
  )
  
  val AmberForResidualPreferences = Table(
    ("Final evaluation [loc1sch1 | loc1sch2 | loc2sch1 | loc2sch2 | alternative]", "Expected Application status"),
    (result(            amber),                                                    AwaitingAssessmentCentreReevaluation),
    (result(            red,       amber),                                         AwaitingAssessmentCentreReevaluation),
    (result(            amber,     green),                                         AwaitingAssessmentCentreReevaluation),
    (result(            red,       amber,     green),                              AwaitingAssessmentCentreReevaluation),
    (result(            red,       amber,     green,     green,     green),        AwaitingAssessmentCentreReevaluation),
    (result(            red,       red,       amber,     green,     green),        AwaitingAssessmentCentreReevaluation),
    (result(            red,       red,       red,       amber,     green),        AwaitingAssessmentCentreReevaluation),
    (result(            red,       red,       red,       red,       amber),        AwaitingAssessmentCentreReevaluation),
    (result(            red,       amber,     red,       red,       amber),        AwaitingAssessmentCentreReevaluation),
    (result(            red,       notChosen, notChosen, notChosen, amber),        AwaitingAssessmentCentreReevaluation)
  )

  val GreenForResidualPreferences = Table(
    ("Final evaluation [loc1sch1 | loc1sch2 | loc2sch1 | loc2sch2 | alternative]", "Expected Application status"),
    (result(            green),                                                    AssessmentCentrePassed),
    (result(            red,       green),                                         AssessmentCentrePassed),
    (result(            green,     red),                                           AssessmentCentrePassed),
    (result(            red,       red,       green),                              AssessmentCentrePassed),
    (result(            red,       red,       red,       green),                   AssessmentCentrePassed),
    (result(            red,       red,       red,       red,       green),        AssessmentCentrePassed),
    (result(            red,       notChosen, notChosen, notChosen, green),        AssessmentCentrePassed)
  )
  // format: ON
  // scalastyle: ON


  "Final (with online test result) assessment centre" should {
    "Set application status to Failed when candidate failed min competency level" in {
      forAll(FailedMinCompetencyLevel) { (evaluation: AssessmentRuleCategoryResult, expectedApplicaitonStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatus(evaluation) mustBe expectedApplicaitonStatus
      }
    }

    "Set application status to Failed when candidate failed all preferences" in {
      forAll(FailedAllPreferences) { (evaluation: AssessmentRuleCategoryResult, expectedApplicaitonStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatus(evaluation) mustBe expectedApplicaitonStatus
      }
    }

    "Set application status to Amber when candidate is amber in their residual Preferences" in {
      forAll(AmberForResidualPreferences) { (evaluation: AssessmentRuleCategoryResult, expectedApplicaitonStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatus(evaluation) mustBe expectedApplicaitonStatus
      }
    }

    "Set application status to Passed when candidate is green in their residual Preferences" in {
      forAll(GreenForResidualPreferences) { (evaluation: AssessmentRuleCategoryResult, expectedApplicaitonStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatus(evaluation) mustBe expectedApplicaitonStatus
      }
    }
  }

  private def r(passedMinimumCompetencyLevel: Option[Boolean] = None,
                location1Scheme1: Option[Result] = None,
                location1Scheme2: Option[Result] = None,
                location2Scheme1: Option[Result] = None,
                location2Scheme2: Option[Result] = None,
                alternativeScheme: Option[Result] = None) =
    AssessmentRuleCategoryResult(passedMinimumCompetencyLevel, location1Scheme1, location1Scheme2,
      location2Scheme1, location2Scheme2, alternativeScheme, None, None)

  private def result(location1Scheme1: Option[Result] = None,
                location1Scheme2: Option[Result] = None,
                location2Scheme1: Option[Result] = None,
                location2Scheme2: Option[Result] = None,
                alternativeScheme: Option[Result] = None) =
    r(None, location1Scheme1, location1Scheme2, location2Scheme1, location2Scheme2, alternativeScheme)
}
