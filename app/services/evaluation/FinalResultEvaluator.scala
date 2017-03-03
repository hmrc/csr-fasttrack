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
import model.PersistedObjects.OnlineTestPassmarkEvaluation
import model.persisted.SchemeEvaluationResult
import play.api.Logger

trait FinalResultEvaluator {
  case class OnlineTestAndAssessmentResultPairNotFound(msg: String) extends Exception(msg)

  // calculates the results based on online test evaluation and assessment centre evaluation
  def mergeResults2(
                    onlineTestResult: OnlineTestPassmarkEvaluation,
                    assessmentCentreResult: AssessmentRuleCategoryResult,
                    schemePreferences: SchemePreferences
                  ): FinalEvaluationResult = {
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
    ).collect {
      case (Some(scheme), Some(result)) =>
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

  //TODO IS: this will change
  def mergeResults(
    onlineTestResult: OnlineTestPassmarkEvaluation,
    assessmentCentreResult: AssessmentRuleCategoryResult,
    schemePreferences: SchemePreferences
  ): FinalEvaluationResult = {
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
    ).collect {
        case (Some(scheme), Some(result)) =>
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

  // Determines the overall result for each scheme based on the online test result and the assessment centre result
  def determineOverallResultForEachScheme(onlineTestEvaluation: List[SchemeEvaluationResult],
                                          assessmentCentreEvaluation: List[PerSchemeEvaluation]): List[PerSchemeEvaluation] = {
    //scalastyle:off
    println("****")
    println(s"**** mergeResults2 onlineTestEvaluation = $onlineTestEvaluation")
    println(s"**** mergeResults2 assessmentCentreEvaluation = $assessmentCentreEvaluation")
    println("****")
    // List[(Scheme, Result)]
    val xx = onlineTestEvaluation.map { otEval: SchemeEvaluationResult =>
      val acEval: PerSchemeEvaluation = assessmentCentreEvaluation.filter(s => s.schemeName == otEval.scheme.toString).head // TODO IS: use enum!!
      println(s"**** mergeResults2 otEval = $otEval, acEval = $acEval")
      (otEval.result, acEval.result) match {
        case (Red, _) =>
          println(s"**** step1 Red -")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Red)//Red
        case (_, Red) =>
          println(s"**** step2 - Red")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Red)//Red
        case (Green, Green) =>
          println(s"**** step3 - Green")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Green)//Green
        case (Amber, _) =>
          println(s"**** step4 - Amber")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Amber)//Amber
        case (Green, Amber) =>
          println(s"**** step5 - Amber")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Amber)//Amber
        case _ => // TODO IS: This should never be called so should we throw exception here?
          println(s"**** step default - Amber")
          otEval.scheme -> PerSchemeEvaluation(otEval.scheme.toString, Amber)//Amber
      }
    }.map {
      case (name, result) => result
    }
    xx
  }

  private def determineResult(onlineTestResult: Result, assessmentCentreResult: Option[Result]): Option[Result] =
    determineResult(Some(onlineTestResult), assessmentCentreResult)

  private def determineResult(onlineTestResult: Option[Result], assessmentCentreResult: Option[Result]): Option[Result] = {
    (onlineTestResult, assessmentCentreResult) match {
      case (None, None) => None
      case (r1 @ Some(Red), _) => r1
      case (r1 @ Some(Amber), _) => r1
      case (Some(Green), r2) => r2
      case (r1, r2) =>
        throw OnlineTestAndAssessmentResultPairNotFound(s"The pair: Online Test [$r1] and Assessment Centre result [$r2] " +
          s"are not in acceptable state: Red/Amber/Green or both None")
    }
  }
}
