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
import model.EvaluationResults._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec

class ApplicationStatusCalculatorSpec extends PlaySpec with TableDrivenPropertyChecks {
  val calculator = new ApplicationStatusCalculator {}

  // scalastyle: OFF
  // format: OFF

  val AllFailed = Table(
    ("Final evaluation [Business | Commercial | DigitalAndTechnology | Finance | ProjectDelivery]", "Expected application status"),
    (result(           Red,       Red,         Red,                   Red,      Red),              AssessmentCentreFailed)
  )

  val AllPassedOrFailed = Table(
    ("Final evaluation [Business | Commercial | DigitalAndTechnology | Finance | ProjectDelivery]", "Expected application status"),
    (result(           Red,       Red,         Red,                   Red,      Green),            AssessmentCentrePassed),
    (result(           Red,       Red,         Red,                   Green,    Red),              AssessmentCentrePassed),
    (result(           Green,     Red,         Green,                 Green,    Red),              AssessmentCentrePassed),
    (result(           Red,       Red,         Red,                   Green,    Green),            AssessmentCentrePassed),
    (result(           Green,     Green,       Green,                 Green,    Green),            AssessmentCentrePassed)
  )

  val AtLeastOneAmber = Table(
    ("Final evaluation [Business | Commercial | DigitalAndTechnology | Finance | ProjectDelivery]", "Expected application status"),
    (result(           Red,       Red,         Red,                   Amber,    Green),            AwaitingAssessmentCentreReevaluation),
    (result(           Red,       Red,         Red,                   Green,    Amber),            AwaitingAssessmentCentreReevaluation),
    (result(           Red,       Red,         Amber,                 Green,    Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Red,       Red,         Green,                 Amber,    Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Red,       Amber,       Green,                 Red,      Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Red,       Green,       Amber,                 Red,      Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Amber,     Green,       Red,                   Red,      Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Green,     Amber,       Red,                   Red,      Red),              AwaitingAssessmentCentreReevaluation),
    (result(           Amber,     Green,       Green,                 Green,    Green),            AwaitingAssessmentCentreReevaluation),
    (result(           Amber,     Red,         Red,                   Red,      Red),              AwaitingAssessmentCentreReevaluation)
  )

  // format: ON
  // scalastyle: ON

  "Final (with online test result) assessment centre" should {
    "Set application status to Failed when candidate failed min competency level" in {
      calculator.determineStatusNEW(candidateFailedMinCompetencyCheck) mustBe AssessmentCentreFailed
    }

    "Set application status to Failed when candidate failed all schemes" in {
      forAll(AllFailed) { (evaluation: AssessmentRuleCategoryResultNEW, expectedApplicationStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatusNEW(evaluation) mustBe expectedApplicationStatus
      }
    }

    "Set application status to Passed when all schemes are either passed or failed" in {
      forAll(AllPassedOrFailed) { (evaluation: AssessmentRuleCategoryResultNEW, expectedApplicationStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatusNEW(evaluation) mustBe expectedApplicationStatus
      }
    }

    "Set application status to Re-evaluate when at least one scheme is Amber" in {
      forAll(AtLeastOneAmber) { (evaluation: AssessmentRuleCategoryResultNEW, expectedApplicationStatus: ApplicationStatuses.EnumVal) =>
        calculator.determineStatusNEW(evaluation) mustBe expectedApplicationStatus
      }
    }
  }

  private val competencyAverageResult = CompetencyAverageResult(
    leadingAndCommunicatingAverage = 2.0d,
    collaboratingAndPartneringAverage = 2.0d,
    deliveringAtPaceAverage = 2.0d,
    makingEffectiveDecisionsAverage = 2.0d,
    changingAndImprovingAverage = 2.0d,
    buildingCapabilityForAllAverage = 2.0d,
    motivationFitAverage = 4.0d,
    overallScore = 16.0d
  )

  private def candidateFailedMinCompetencyCheck = {
    AssessmentRuleCategoryResultNEW(passedMinimumCompetencyLevel = Some(false),
      competencyAverageResult = competencyAverageResult,
      schemesEvaluation = Nil,
      overallEvaluation = Nil
    )
  }

  private def result(business: Result,
                     commercial: Result,
                     digitalAndTechnology: Result,
                     finance: Result,
                     projectDelivery: Result) = {
    AssessmentRuleCategoryResultNEW(passedMinimumCompetencyLevel = Some(true),
      competencyAverageResult = competencyAverageResult,
      schemesEvaluation = List(
        PerSchemeEvaluation("Business", business), // TODO IS: change to enum
        PerSchemeEvaluation("Commercial", commercial),
        PerSchemeEvaluation("DigitalAndTechnology", digitalAndTechnology),
        PerSchemeEvaluation("Finance", finance),
        PerSchemeEvaluation("ProjectDelivery", projectDelivery)
      ),
      overallEvaluation = Nil
    )
  }
}
