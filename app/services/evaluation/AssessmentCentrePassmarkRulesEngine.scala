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

import config.AssessmentEvaluationMinimumCompetencyLevel
import model.AssessmentPassmarkPreferencesAndScores
import model.EvaluationResults._
import model.persisted.SchemeEvaluationResult

trait AssessmentCentrePassmarkRulesEngine {

  def evaluate(onlineTestEvaluation: List[SchemeEvaluationResult],
               candidateScore: AssessmentPassmarkPreferencesAndScores,
               config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentRuleCategoryResult
}

object AssessmentCentrePassmarkRulesEngine extends AssessmentCentrePassmarkRulesEngine with AssessmentScoreCalculator
    with AssessmentCentreAllSchemesEvaluator with FinalResultEvaluator with EligibleSchemeSelector {

  def evaluate(onlineTestEvaluation: List[SchemeEvaluationResult],
               candidateScores: AssessmentPassmarkPreferencesAndScores,
               config: AssessmentEvaluationMinimumCompetencyLevel
              ): AssessmentRuleCategoryResult = {
    val competencyAverage = countAverage(candidateScores.scores)
    val passedMinimumCompetencyLevelCheckOpt = passMinimumCompetencyLevel(competencyAverage, config)

    passedMinimumCompetencyLevelCheckOpt match {
      case Some(false) =>
        //scalastyle:off
        println("**** AssessmentCentrePassmarkRulesEngine - candidate failed minimum competency level check - all schemes will be red")

        val allSchemesHaveFailed = candidateScores.schemes.map { scheme =>
          SchemeEvaluationResult(scheme, Red)
        }
        AssessmentRuleCategoryResult(passedMinimumCompetencyLevelCheckOpt, competencyAverage, allSchemesHaveFailed, allSchemesHaveFailed)
      case _ =>
        //scalastyle:off
        println("**** AssessmentCentrePassmarkRulesEngine - candidate passed minimum competency level check")
        val assessmentCentreEvaluation = evaluateAssessmentPassmark(competencyAverage, candidateScores)
        println(s"**** AssessmentCentrePassmarkRulesEngine - schemesAssessmentEvaluation = $assessmentCentreEvaluation")

        val finalResults = determineOverallResultForEachScheme(onlineTestEvaluation, assessmentCentreEvaluation)
        println(s"**** AssessmentCentrePassmarkRulesEngine - finalResults = $finalResults")

        AssessmentRuleCategoryResult(
          passedMinimumCompetencyLevelCheckOpt,
          competencyAverage,
          assessmentCentreEvaluation,
          finalResults
        )
    }
  }

  private def evaluateAssessmentPassmark(competencyAverage: CompetencyAverageResult,
                                          candidateScores: AssessmentPassmarkPreferencesAndScores
                                         ): List[SchemeEvaluationResult] = {
    val eligibleSchemesForQualification = candidateScores.schemes

    val overallScore = competencyAverage.overallScore
    val passmark = candidateScores.passmark
    val schemesEvaluation = evaluateSchemes(candidateScores.scores.applicationId, passmark, overallScore, eligibleSchemesForQualification)
    //scalastyle:off
    println(s"**** evaluateAssessmentPassmark - schemesEvaluation = $schemesEvaluation")
    schemesEvaluation
  }

  private def passMinimumCompetencyLevel(
    competencyAverage: CompetencyAverageResult,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): Option[Boolean] = {
    if (config.enabled) {
      //scalastyle:off
      println("**** minimum competency level check is enabled now checking...")
      println(s"**** competencyAverage = $competencyAverage")

      val minCompetencyLevelScore = config.minimumCompetencyLevelScore
        .getOrElse(throw new IllegalStateException("Competency level not set"))
      val minMotivationalFitScore = config.motivationalFitMinimumCompetencyLevelScore
        .getOrElse(throw new IllegalStateException("Motivational Fit competency level not set"))


      val weightOneChecks: Boolean = competencyAverage.scoresWithWeightOne.forall { avg =>
        val result = avg >= minCompetencyLevelScore
        println(s"**** weight one min competency $avg >= $minCompetencyLevelScore = $result")
        result
      }

      val weightTwoChecks: Boolean = competencyAverage.scoresWithWeightTwo.forall { avg =>
        val result = avg >= minMotivationalFitScore
        println(s"**** weight two min competency $avg >= $minMotivationalFitScore = $result")
        result
      }

      val result = Some(weightOneChecks && weightTwoChecks)
      println(s"**** minimum competency level check result = $result")
      result
//      Some(competencyAverage.scoresWithWeightOne.forall(_ >= minCompetencyLevelScore) &&
//        competencyAverage.scoresWithWeightTwo.forall(_ >= minMotivationalFitScore))
    } else {
      //scalastyle:off
      println("**** minimum competency level check is off")
      None
    }
  }
}
