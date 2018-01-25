/*
 * Copyright 2018 HM Revenue & Customs
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
import model.Scheme
import model.persisted._
import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec

class AssessmentCentreAllSchemesEvaluatorSpec extends PlaySpec {
  val PassmarkSettings = AssessmentCentrePassMarkSettings(List(
    AssessmentCentrePassMarkScheme(Scheme.Business, Some(PassMarkSchemeThreshold(10.0, 20.0))),
    AssessmentCentrePassMarkScheme(Scheme.Commercial, Some(PassMarkSchemeThreshold(15.0, 24.99))),
    AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology, Some(PassMarkSchemeThreshold(20.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.Finance, Some(PassMarkSchemeThreshold(25.0, 30.0))),
    AssessmentCentrePassMarkScheme(Scheme.Policy, Some(PassMarkSchemeThreshold(30.0, 40.0))),
    AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery, Some(PassMarkSchemeThreshold(30.0, 40.0)))
  ), AssessmentCentrePassMarkInfo("1", DateTime.now, "user"))

  val evaluator = new AssessmentCentreAllSchemesEvaluator {}

  "evaluate schemes" should {
    "evaluate all schemes" in {
      val overallScore = 24.99
      val evaluation = evaluator.evaluateSchemes("appId", PassmarkSettings, overallScore, Scheme.AllSchemes)
      evaluation mustBe List(
        SchemeEvaluationResult(Scheme.Business, Green),
        SchemeEvaluationResult(Scheme.Commercial, Green),
        SchemeEvaluationResult(Scheme.DigitalAndTechnology, Amber),
        SchemeEvaluationResult(Scheme.Finance, Red),
        SchemeEvaluationResult(Scheme.Policy, Red),
        SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
      )
    }

    "throw an exception if the pass mark is not found for a scheme" in {
      val noPassmarkSettings = AssessmentCentrePassMarkSettings(Nil, AssessmentCentrePassMarkInfo("1", DateTime.now, "user"))
      val overallScore = 25.0
      intercept[IllegalStateException] {
        evaluator.evaluateSchemes("appId", noPassmarkSettings, overallScore, List(Scheme.Business))
      }
    }

    "evaluate a candidate to Green if the pass threshold is equal to fail threshold and the overall scores" in {
      val PassmarkSettings = AssessmentCentrePassMarkSettings(List(
        AssessmentCentrePassMarkScheme(Scheme.Business, Some(PassMarkSchemeThreshold(20.0, 20.0)))
      ), AssessmentCentrePassMarkInfo("1", DateTime.now, "user"))
      val overallScore = 20.0
      val evaluation = evaluator.evaluateSchemes("appId", PassmarkSettings, overallScore, List(Scheme.Business))

      evaluation mustBe List(
        SchemeEvaluationResult(Scheme.Business, Green)
      )
    }

    "evaluate a candidate to Amber if their score is equal to Failmark" in {
      val overallScore = 10.0
      val evaluation = evaluator.evaluateSchemes("appId", PassmarkSettings, overallScore, Scheme.AllSchemes)
      evaluation mustBe List(
        SchemeEvaluationResult(Scheme.Business, Amber),
        SchemeEvaluationResult(Scheme.Commercial, Red),
        SchemeEvaluationResult(Scheme.DigitalAndTechnology, Red),
        SchemeEvaluationResult(Scheme.Finance, Red),
        SchemeEvaluationResult(Scheme.Policy, Red),
        SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
      )
    }
  }
}
