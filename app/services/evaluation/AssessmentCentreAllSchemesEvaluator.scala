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

import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.EvaluationResults._
import model.PassmarkPersistedObjects.PassMarkSchemeThreshold

trait AssessmentCentreAllSchemesEvaluator {

  def evaluateSchemes(passmark: AssessmentCentrePassMarkSettingsResponse, overallScore: Double,
                      eligibleSchemes: List[String]): List[PerSchemeEvaluation] = {
    passmark.schemes.map(_.schemeName).map { schemeName =>
      val result = if (eligibleSchemes.contains(schemeName)) evaluateScore(schemeName, passmark, overallScore) else Red
      PerSchemeEvaluation(schemeName, result)
    }
  }

  private def evaluateScore(schemeName: String, passmark: AssessmentCentrePassMarkSettingsResponse, overallScore: Double): Result = {
    val passmarkSetting = passmark.schemes.find(_.schemeName == schemeName)
      .getOrElse(throw new IllegalStateException(s"schemeName=$schemeName is not set in Passmark settings"))
      .overallPassMarks
      .getOrElse(throw new IllegalStateException(s"Scheme threshold for $schemeName is not set in Passmark settings"))

    determineSchemeResult(overallScore, passmarkSetting)
  }

  def evaluateAlternativeSchemes(allSchemesEvaluation: Map[String, Result], alternativeSchemes: List[String]): Result = {
    val evaluatedAlternativeSchemes = allSchemesEvaluation.filter {
      case (schemeName, _) =>
        alternativeSchemes.contains(schemeName)
    }

    val evaluation = evaluatedAlternativeSchemes.values.toList
    if (evaluation.contains(Green)) {
      Green
    } else if (evaluation.contains(Amber)) {
      Amber
    } else {
      Red
    }
  }

  private def determineSchemeResult(overallScore: Double, passmarkThreshold: PassMarkSchemeThreshold): Result = {
    if (overallScore <= passmarkThreshold.failThreshold) {
      Red
    } else if (overallScore >= passmarkThreshold.passThreshold) {
      Green
    } else {
      Amber
    }
  }
}
