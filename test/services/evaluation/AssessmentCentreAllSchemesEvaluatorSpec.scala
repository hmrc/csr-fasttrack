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

import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults.Green
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold }
import org.scalatestplus.play.PlaySpec
import model.Schemes._
import model.EvaluationResults._
import org.joda.time.DateTime
import model.Scheme
import model.persisted.SchemeEvaluationResult

class AssessmentCentreAllSchemesEvaluatorSpec extends PlaySpec {
  val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
    AssessmentCentrePassMarkScheme(Scheme.Business, Some(PassMarkSchemeThreshold(10.0, 20.0))),
    AssessmentCentrePassMarkScheme(Scheme.Commercial, Some(PassMarkSchemeThreshold(15.0, 25.0))),
    AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology, Some(PassMarkSchemeThreshold(20.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.Finance, Some(PassMarkSchemeThreshold(25.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery, Some(PassMarkSchemeThreshold(30.0, 40.0)))
  ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))

  val evaluator = new AssessmentCentreAllSchemesEvaluator {}

  "evaluate schemes" should {
    // TODO IAN: Please fix the tests once you tackle assessment centre evaluation
    "evaluate all schemes if all are eligible" ignore {
      val overallScore = 25.0
      val evaluation = evaluator.evaluateSchemes(PassmarkSettings, overallScore, AllSchemes)
      evaluation mustBe List(
        SchemeEvaluationResult(Scheme.Business, Green),
        SchemeEvaluationResult(Scheme.Commercial, Green),
        SchemeEvaluationResult(Scheme.DigitalAndTechnology, Amber),
        SchemeEvaluationResult(Scheme.Finance, Red),
        SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
      )
    }

    // TODO IAN: Please fix the tests once you tackle assessment centre evaluation
    "evaluated ineligible schemes to Red and to evaluate the rest of them use passmark and overal scores" ignore {
      val overallScore = 25.0
      val evaluation = evaluator.evaluateSchemes(PassmarkSettings, overallScore, DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil)
      evaluation mustBe List(
        SchemeEvaluationResult(Scheme.Business, Red),
        SchemeEvaluationResult(Scheme.Commercial, Red),
        SchemeEvaluationResult(Scheme.DigitalAndTechnology, Amber),
        SchemeEvaluationResult(Scheme.Finance, Red),
        SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
      )
    }
  }

  "evaluate alternative schemes" should {
    // TODO IAN: Please fix the tests once you tackle assessment centre evaluations
    //    "return Green if at least one alternative schemes is Green (ignore Preferences)" in {
//      val allSchemesEvaluation = Map(
//        Scheme.Business -> Red,
//        Scheme.Commercial -> Red,
//        Scheme.DigitalAndTechnology -> Amber, // alternative scheme
//        Scheme.Finance -> Amber, // alternative scheme
//        Scheme.ProjectDelivery -> Green // alternative scheme
//      )
//      val alternativeSchemes: List[String] = DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil
//
//      val evaluation = evaluator.evaluateAlternativeSchemes(allSchemesEvaluation, alternativeSchemes)
//
//      evaluation mustBe Green
//    }
//
//    "return Amber if there is no Green, and at least one Amber in alternative schemes (ignore Preferences)" in {
//      val allSchemesEvaluation = Map(
//        Scheme.Business -> Red,
//        Scheme.Commercial -> Red,
//        Scheme.DigitalAndTechnology -> Amber, // alternative scheme
//        Scheme.Finance -> Red, // alternative scheme
//        Scheme.ProjectDelivery -> Red // alternative scheme
//      )
//      val alternativeSchemes: List[String] = DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil
//
//      val evaluation = evaluator.evaluateAlternativeSchemes(allSchemesEvaluation, alternativeSchemes)
//
//      evaluation mustBe Amber
//    }

    "return Red if there is no Green and no Amber for alternative schemes (ignore Preferences)" in {
      val allSchemesEvaluation = Map(
        Scheme.Business -> Green,
        Scheme.Commercial -> Amber,
        Scheme.DigitalAndTechnology -> Red, // alternative scheme
        Scheme.Finance -> Red, // alternative scheme
        Scheme.ProjectDelivery -> Red // alternative scheme
      )
      val alternativeSchemes: List[String] = DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil

      val evaluation = evaluator.evaluateAlternativeSchemes(allSchemesEvaluation, alternativeSchemes)

      evaluation mustBe Red
    }
  }
}
