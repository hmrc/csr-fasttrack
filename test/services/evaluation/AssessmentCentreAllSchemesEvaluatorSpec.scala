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
import model.EvaluationResults.{ Green, PerSchemeEvaluation }
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkInfo, AssessmentCentrePassMarkScheme, PassMarkSchemeThreshold }
import model.Schemes
import org.scalatestplus.play.PlaySpec
import model.Schemes._
import model.EvaluationResults._
import org.joda.time.DateTime

class AssessmentCentreAllSchemesEvaluatorSpec extends PlaySpec {
  val PassmarkSettings = AssessmentCentrePassMarkSettingsResponse(List(
    AssessmentCentrePassMarkScheme(Business, Some(PassMarkSchemeThreshold(10.0, 20.0))),
    AssessmentCentrePassMarkScheme(Commercial, Some(PassMarkSchemeThreshold(15.0, 25.0))),
    AssessmentCentrePassMarkScheme(DigitalAndTechnology, Some(PassMarkSchemeThreshold(20.0, 30.0))),
    AssessmentCentrePassMarkScheme(Finance, Some(PassMarkSchemeThreshold(25.0, 30.0))),
    AssessmentCentrePassMarkScheme(ProjectDelivery, Some(PassMarkSchemeThreshold(30.0, 40.0)))
  ), Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))

  val evaluator = new AssessmentCentreAllSchemesEvaluator {}

  "evaluate schemes" should {
    "evaluate all schemes" in {
      val overallScore = 25.0
      val evaluation = evaluator.evaluateSchemes("appId", PassmarkSettings, overallScore, AllSchemes)
      evaluation mustBe List(
        PerSchemeEvaluation(Business, Green),
        PerSchemeEvaluation(Commercial, Green),
        PerSchemeEvaluation(DigitalAndTechnology, Amber),
        PerSchemeEvaluation(Finance, Red),
        PerSchemeEvaluation(ProjectDelivery, Red)
      )
    }

    "throw an exception if the pass mark is not found for a scheme" in {
      val noPassmarkSettings = AssessmentCentrePassMarkSettingsResponse(Nil, Some(AssessmentCentrePassMarkInfo("1", DateTime.now, "user")))
      val overallScore = 25.0
      intercept[IllegalStateException] {
        evaluator.evaluateSchemes("appId", noPassmarkSettings, overallScore, List(Schemes.Business))
      }
    }
  }
}
