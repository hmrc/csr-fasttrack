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

package services.evaluation

import model.EvaluationResults._
import model.PersistedObjects.OnlineTestPassmarkEvaluation
import org.scalatest.{MustMatchers, PropSpec}
import model.Schemes._

class FinalResultEvaluatorSpec extends PropSpec with MustMatchers {
  import FinalResultEvaluatorSpec._

  val evaluator = new FinalResultEvaluator {}

  property("Red or Amber in Online Test stop further evaluation for the scheme and set the result to Red or Amber") {
    for {
      onlineTestResult <- AllPossibleOnlineTestResults
      onlineTestResultToUpdate <- List(Red, Amber)
    } {
      val location1Scheme1 = Green
      val location1Scheme2 = onlineTestResult.location1Scheme2 map (_ => Green)
      val location2Scheme1 = onlineTestResult.location2Scheme1 map (_ => Green)
      val location2Scheme2 = onlineTestResult.location2Scheme2 map (_ => Green)
      val alternativeScheme = onlineTestResult.alternativeScheme map (_ => Green)

      val greenAssessmentCentreResult = AssessmentRuleCategoryResult(None, Some(location1Scheme1), location1Scheme2, location2Scheme1,
        location2Scheme2, alternativeScheme, None, None)

      val resultLoc1Sch1 = mergeResults(onlineTestResult.copy(location1Scheme1 = onlineTestResultToUpdate), greenAssessmentCentreResult)
      resultLoc1Sch1.location1Scheme1 mustBe Some(onlineTestResultToUpdate)

      val resultLoc1Sch2 = mergeResults(onlineTestResult.copy(location1Scheme2 = Some(onlineTestResultToUpdate)), greenAssessmentCentreResult)
      resultLoc1Sch2.location1Scheme2 mustBe Some(onlineTestResultToUpdate)

      val resultLoc2Sch1 = mergeResults(onlineTestResult.copy(location2Scheme1 = Some(onlineTestResultToUpdate)), greenAssessmentCentreResult)
      resultLoc2Sch1.location2Scheme1 mustBe Some(onlineTestResultToUpdate)

      val resultLoc2Sch2 = mergeResults(onlineTestResult.copy(location2Scheme2 = Some(onlineTestResultToUpdate)), greenAssessmentCentreResult)
      resultLoc2Sch2.location2Scheme2 mustBe Some(onlineTestResultToUpdate)

      val alternative = mergeResults(onlineTestResult.copy(alternativeScheme = Some(onlineTestResultToUpdate)), greenAssessmentCentreResult)
      alternative.alternativeScheme mustBe Some(onlineTestResultToUpdate)
    }
  }

  property("Green in Online Test takes final evaluation from assessment centre") {
    for (assessmentCentreResult <- AllPossibleAssessmentResults) {
      val result = mergeResults(GreenOnlineTestResult, assessmentCentreResult)
      result.location1Scheme1 mustBe assessmentCentreResult.location1Scheme1
      result.location1Scheme2 mustBe assessmentCentreResult.location1Scheme2
      result.location2Scheme1 mustBe assessmentCentreResult.location2Scheme1
      result.location2Scheme2 mustBe assessmentCentreResult.location2Scheme2
      result.alternativeScheme mustBe assessmentCentreResult.alternativeScheme
    }
  }

  property("final result is reflected in per scheme evaluation") {
    val schemePreferences = SchemePreferences(Business, Some(Commercial), Some(DigitalAndTechnology), Some(Finance))

    val onlineTestResult = OnlineTestPassmarkEvaluation(Green, Some(Amber), Some(Red), Some(Green), Some(Green))
    val assessmentCentreResult = AssessmentRuleCategoryResult(passedMinimumCompetencyLevel = Some(true),
      Some(Green), Some(Green), Some(Green), Some(Amber), Some(Red), None, schemesEvaluation = Some(List(
        PerSchemeEvaluation(Business, Green),
        PerSchemeEvaluation(Commercial, Green),
        PerSchemeEvaluation(DigitalAndTechnology, Green),
        PerSchemeEvaluation(Finance, Red),
        PerSchemeEvaluation(ProjectDelivery, Red)
      )))

    val result = evaluator.mergeResults(onlineTestResult, assessmentCentreResult, schemePreferences)

    val actualSchemesEvaluation = result.schemesEvaluation.get.sortBy(_.schemeName)
    assertSchemeEvaluation(List(
      PerSchemeEvaluation(Business, Green),
      PerSchemeEvaluation(Commercial, Amber),
      PerSchemeEvaluation(DigitalAndTechnology, Red),
      PerSchemeEvaluation(Finance, Amber),
      PerSchemeEvaluation(ProjectDelivery, Red)
    ), actualSchemesEvaluation)
  }

  property("only preferred schemes are updated in per scheme evaluation, alternative scheme are taken from assessment centre evaluation") {
    val schemePreferences = SchemePreferences(Commercial, None, None, None)

    val onlineTestResult = OnlineTestPassmarkEvaluation(Green, None, None, None, alternativeScheme = Some(Green))
    val assessmentCentreResult = AssessmentRuleCategoryResult(passedMinimumCompetencyLevel = Some(true),
      Some(Amber), None, None, None, alternativeScheme = Some(Red), None, schemesEvaluation = Some(List(
        PerSchemeEvaluation(Business, Green),
        PerSchemeEvaluation(Commercial, Amber),
        PerSchemeEvaluation(DigitalAndTechnology, Green),
        PerSchemeEvaluation(Finance, Red),
        PerSchemeEvaluation(ProjectDelivery, Red)
      )))

    val result = evaluator.mergeResults(onlineTestResult, assessmentCentreResult, schemePreferences)

    val actualSchemesEvaluation = result.schemesEvaluation.get.sortBy(_.schemeName)
    assertSchemeEvaluation(List(
      PerSchemeEvaluation(Business, Green),
      PerSchemeEvaluation(Commercial, Amber),
      PerSchemeEvaluation(DigitalAndTechnology, Green),
      PerSchemeEvaluation(Finance, Red),
      PerSchemeEvaluation(ProjectDelivery, Red)
    ), actualSchemesEvaluation)
  }

  private def assertSchemeEvaluation(expected: List[PerSchemeEvaluation], actual: List[PerSchemeEvaluation]) = {
    actual.size mustBe expected.size
    for (i <- expected.indices) {
      actual(i) mustBe expected(i)
    }
  }

  private def mergeResults(onlineTestEvaluation: OnlineTestPassmarkEvaluation, assessmentCentreEvaluation: AssessmentRuleCategoryResult) =
    evaluator.mergeResults(onlineTestEvaluation, assessmentCentreEvaluation, AllSchemePreferences)
}

object FinalResultEvaluatorSpec {
  val AllPossibleResults: List[Option[Result]] = List(None, Some(Red), Some(Amber), Some(Green))

  val AllPossibleOnlineTestResults = for {
    loc1Sch1 <- AllPossibleResults.filterNot(_.isEmpty) // the first scheme is never empty
    loc1Sch2 <- AllPossibleResults
    loc2Sch1 <- AllPossibleResults
    loc2Sch2 <- AllPossibleResults
    alternative <- AllPossibleResults
  } yield OnlineTestPassmarkEvaluation(loc1Sch1.get, loc1Sch2, loc2Sch1, loc2Sch2, alternative)

  val AllPossibleAssessmentResults = for {
    loc1Sch1 <- AllPossibleResults.filterNot(_.isEmpty) // the first scheme is never empty
    loc1Sch2 <- AllPossibleResults
    loc2Sch1 <- AllPossibleResults
    loc2Sch2 <- AllPossibleResults
    alternative <- AllPossibleResults
  } yield AssessmentRuleCategoryResult(None, loc1Sch1, loc1Sch2, loc2Sch1, loc2Sch2, alternative, None, None)

  val GreenOnlineTestResult = OnlineTestPassmarkEvaluation(Green, Some(Green), Some(Green), Some(Green), Some(Green))

  val AllSchemePreferences = SchemePreferences(Business, Some(Commercial), Some(DigitalAndTechnology), Some(Finance))


}
