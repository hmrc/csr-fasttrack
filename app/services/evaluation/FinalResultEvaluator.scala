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

trait FinalResultEvaluator {

  def mergeResults(onlineTestResult: OnlineTestPassmarkEvaluation,
                   assessmentCentreResult: AssessmentRuleCategoryResult,
                   schemePreferences: SchemePreferences): FinalEvaluationResult = {
    val location1Scheme1Result = determineResult(onlineTestResult.location1Scheme1, assessmentCentreResult.location1Scheme1)
    val location1Scheme2Result = determineResult(onlineTestResult.location1Scheme2, assessmentCentreResult.location1Scheme2)
    val location2Scheme1Result = determineResult(onlineTestResult.location2Scheme1, assessmentCentreResult.location2Scheme1)
    val location2Scheme2Result = determineResult(onlineTestResult.location2Scheme2, assessmentCentreResult.location2Scheme2)
    val alternativeSchemeResult = determineResult(onlineTestResult.alternativeScheme, assessmentCentreResult.alternativeScheme)

    val preferredSchemeToResultMap = List(
      (Some(schemePreferences.location1Scheme1), location1Scheme1Result),
      (schemePreferences.location1Scheme2, location1Scheme2Result),
      (schemePreferences.location2Scheme1, location2Scheme1Result),
      (schemePreferences.location2Scheme2, location2Scheme2Result)
    ).collect { case (Some(scheme), Some(result)) =>
      (scheme, result)
    }.toMap

    val finalSchemesEvaluation = assessmentCentreResult.schemesEvaluation.map { assessmentSchemeEvaluations =>
      assessmentSchemeEvaluations.map { evaluation =>
        evaluation.copy(result = preferredSchemeToResultMap.getOrElse(evaluation.schemeName, evaluation.result))
      }
    }

    FinalEvaluationResult(location1Scheme1Result, location1Scheme2Result, location2Scheme1Result, location2Scheme2Result,
      alternativeSchemeResult, finalSchemesEvaluation)
  }

  private def determineResult(onlineTestResult: Result, assessmentCentreResult: Option[Result]): Option[Result] =
    determineResult(Some(onlineTestResult), assessmentCentreResult)

  private def determineResult(onlineTestResult: Option[Result], assessmentCentreResult: Option[Result]): Option[Result] = {
    (onlineTestResult, assessmentCentreResult) match {
      case (None, None) => None
      case (r1 @ Some(Red), _) => r1
      case (r1 @ Some(Amber), _) => r1 // TODO LT: The gap has not been closed, log warning?
      case (Some(Green), r2) => r2
      case _ => None // TODO LT: exception as there is no online test result
    }
  }

}
