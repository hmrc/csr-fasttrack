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

import model.EvaluationResults._
import model.Scheme
import model.persisted.SchemeEvaluationResult
import org.scalatest.{ MustMatchers, PropSpec }

class FinalResultEvaluatorSpec extends PropSpec with MustMatchers {

  val evaluator = new FinalResultEvaluator {}

  property("Red in Online Test or Assessment Centre sets the overall result to Red") {
    // R G -> R
    // G R -> R
    // R A -> R
    // A R -> R
    val onlineTestEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Red),
      SchemeEvaluationResult(Scheme.Commercial, Green),
      SchemeEvaluationResult(Scheme.Finance, Red),
      SchemeEvaluationResult(Scheme.ProjectDelivery, Amber)
    )
    val assessmentCentreEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Green),
      SchemeEvaluationResult(Scheme.Commercial, Red),
      SchemeEvaluationResult(Scheme.Finance, Amber),
      SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
    )
    val result = evaluator.determineOverallResultForEachScheme(onlineTestEvaluation, assessmentCentreEvaluation)
    result mustBe List(
      SchemeEvaluationResult(Scheme.Business, Red),
      SchemeEvaluationResult(Scheme.Commercial, Red),
      SchemeEvaluationResult(Scheme.Finance, Red),
      SchemeEvaluationResult(Scheme.ProjectDelivery, Red)
    )
  }

  property("Green in both Online Test and Assessment Centre sets the overall result to Green") {
    val onlineTestEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Green)
    )
    val assessmentCentreEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Green)
    )
    val result = evaluator.determineOverallResultForEachScheme(onlineTestEvaluation, assessmentCentreEvaluation)
    result mustBe List(
      SchemeEvaluationResult(Scheme.Business, Green)
    )
  }

  property("Amber in Online Test with anything in Assessment Centre except Red sets the overall result to Amber") {
    val onlineTestEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Amber),
      SchemeEvaluationResult(Scheme.Commercial, Amber),
      SchemeEvaluationResult(Scheme.Finance, Amber)
    )
    val assessmentCentreEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Green),
      SchemeEvaluationResult(Scheme.Commercial, Amber),
      SchemeEvaluationResult(Scheme.Finance, Red)
    )
    val result = evaluator.determineOverallResultForEachScheme(onlineTestEvaluation, assessmentCentreEvaluation)
    result mustBe List(
      SchemeEvaluationResult(Scheme.Business, Amber),
      SchemeEvaluationResult(Scheme.Commercial, Amber),
      SchemeEvaluationResult(Scheme.Finance, Red)
    )
  }

  property("Green in Online Test with Amber in Assessment Centre sets the overall result to Amber") {
    val onlineTestEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Green)
    )
    val assessmentCentreEvaluation = List(
      SchemeEvaluationResult(Scheme.Business, Amber)
    )
    val result = evaluator.determineOverallResultForEachScheme(onlineTestEvaluation, assessmentCentreEvaluation)
    result mustBe List(
      SchemeEvaluationResult(Scheme.Business, Amber)
    )
  }
}
