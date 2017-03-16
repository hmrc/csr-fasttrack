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
import model.persisted.{ OnlineTestPassmarkEvaluation, SchemeEvaluationResult }

trait AssessmentCentrePassmarkRulesEngine {

  def evaluate(onlineTestEvaluation: OnlineTestPassmarkEvaluation,
               candidateScore: AssessmentPassmarkPreferencesAndScores,
               config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentRuleCategoryResult
}

object AssessmentCentrePassmarkRulesEngine extends AssessmentCentrePassmarkRulesEngine with AssessmentScoreCalculator
    with AssessmentCentreAllSchemesEvaluator with FinalResultEvaluator {

  def evaluate(onlineTestEvaluation: OnlineTestPassmarkEvaluation,
               candidateScores: AssessmentPassmarkPreferencesAndScores,
               config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentRuleCategoryResult = {
    val competencyAverage = countAverage(candidateScores.scores)
    val passedMinimumCompetencyLevelCheckOpt = passMinimumCompetencyLevel(competencyAverage, config)

    passedMinimumCompetencyLevelCheckOpt match {
      case Some(false) =>
        val allSchemesRed = candidateScores.schemes.map(s => SchemeEvaluationResult(s, Red))
        AssessmentRuleCategoryResult(passedMinimumCompetencyLevelCheckOpt, competencyAverage, allSchemesRed, allSchemesRed)
      case _ =>
        val appId = candidateScores.scores.applicationId
        val onlyAssessmentCentreEvaluation = evaluateSchemes(appId, candidateScores.passmark,
          competencyAverage.overallScore, candidateScores.schemes)

        val overallEvaluation = combine(onlineTestEvaluation.result, onlyAssessmentCentreEvaluation)

        AssessmentRuleCategoryResult(
          passedMinimumCompetencyLevelCheckOpt,
          competencyAverage,
          onlyAssessmentCentreEvaluation,
          overallEvaluation
        )
    }
  }

  private def passMinimumCompetencyLevel(competencyAverage: CompetencyAverageResult,
                                         config: AssessmentEvaluationMinimumCompetencyLevel): Option[Boolean] = {
    require(competencyAverage.scoresWithWeightOne.nonEmpty, "Scores with weight one in minimum competency level cannot be empty")
    require(competencyAverage.scoresWithWeightTwo.nonEmpty, "Scores with weight two in minimum competency level cannot be empty")

    val result = for {
      mclWeightOne <- config.minimumCompetencyLevelScore if config.enabled
      mclWeightTwo <- config.motivationalFitMinimumCompetencyLevelScore
    } yield {
      val minCompetencyLevelWithWeightOnePassed = competencyAverage.scoresWithWeightOne.forall(_ >= mclWeightOne)
      val minCompetencyLevelWithWeightTwoPassed = competencyAverage.scoresWithWeightTwo.forall(_ >= mclWeightTwo)
      minCompetencyLevelWithWeightOnePassed && minCompetencyLevelWithWeightTwoPassed
    }

    require(!config.enabled || result.nonEmpty, "Cannot check min competency level for assessment")
    result
  }
}
