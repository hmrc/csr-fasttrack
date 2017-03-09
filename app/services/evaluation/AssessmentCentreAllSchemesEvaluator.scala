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
import model.Scheme.Scheme
import model.persisted.{ AssessmentCentrePassMarkScheme, AssessmentCentrePassMarkSettings, PassMarkSchemeThreshold, SchemeEvaluationResult }

trait AssessmentCentreAllSchemesEvaluator {

  def evaluateSchemes(applicationId: String,
                      passmark: AssessmentCentrePassMarkSettings,
                      overallScore: Double,
                      schemes: List[Scheme]): List[SchemeEvaluationResult] = {
    schemes.map { scheme =>
      val msg = s"Did not find assessment centre pass mark for scheme = $scheme, applicationId = $applicationId"
      // TODO LT: Replace scheme string to enum
      val assessmentCentrePassMarkScheme = passmark.schemes.find { _.schemeName == scheme }
        .getOrElse(throw new IllegalStateException(msg))

      //scalastyle:off
      println(s"scheme = $scheme")
      val result = evaluateScore(assessmentCentrePassMarkScheme, passmark, overallScore)
      SchemeEvaluationResult(scheme, result)
    }
  }

  private def evaluateScore(scheme: AssessmentCentrePassMarkScheme,
                            passmark: AssessmentCentrePassMarkSettings,
                            overallScore: Double): Result = {
    val passmarkSetting = scheme.overallPassMarks
      .getOrElse(throw new IllegalStateException(s"Scheme threshold for ${scheme.schemeName} is not set in Passmark settings"))

    determineSchemeResult(overallScore, passmarkSetting)
  }

  // Determines the result of each scheme based on the assessment centre pass marks
  private def determineSchemeResult(overallScore: Double, passmarkThreshold: PassMarkSchemeThreshold): Result = {
    val result = if (overallScore <= passmarkThreshold.failThreshold) {
      Red
    } else if (overallScore >= passmarkThreshold.passThreshold) {
      Green
    } else {
      Amber
    }
    //scalastyle:off
    println(s"AC result = $result, overallScore = $overallScore, fail = ${passmarkThreshold.failThreshold}, pass = ${passmarkThreshold.passThreshold}")
    result
  }
}
