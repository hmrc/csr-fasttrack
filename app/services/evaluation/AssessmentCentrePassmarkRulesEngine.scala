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
import model.AssessmentEvaluationCommands.AssessmentPassmarkPreferencesAndScores
import model.EvaluationResults._
import model.PersistedObjects.OnlineTestPassmarkEvaluation

trait AssessmentCentrePassmarkRulesEngine {

  def evaluate(
    onlineTestEvaluation: OnlineTestPassmarkEvaluation,
    candidateScore: AssessmentPassmarkPreferencesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): AssessmentRuleCategoryResult

}

object AssessmentCentrePassmarkRulesEngine extends AssessmentCentrePassmarkRulesEngine with AssessmentScoreCalculator
    with AssessmentCentreAllSchemesEvaluator with FinalResultEvaluator with EligibleSchemeSelector {

  def evaluate(
    onlineTestEvaluation: OnlineTestPassmarkEvaluation,
    candidateScores: AssessmentPassmarkPreferencesAndScores,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): AssessmentRuleCategoryResult = {
    val competencyAverage = countAverage(candidateScores.scores)
    val passedMinimumCompetencyLevelOpt = passMinimumCompetencyLevel(competencyAverage, config)

    passedMinimumCompetencyLevelOpt match {
      case Some(false) =>
        AssessmentRuleCategoryResult(passedMinimumCompetencyLevelOpt, None, None, None, None, None, None, None)
      case _ =>
        val (assessmentCentreResultNoMinCompetency, schemePreferences) = evaluateAssessmentPassmark(competencyAverage, candidateScores)
        val assessmentCentreResult = assessmentCentreResultNoMinCompetency.copy(passedMinimumCompetencyLevel = passedMinimumCompetencyLevelOpt)

        val aggregateEvaluation = mergeResults(onlineTestEvaluation, assessmentCentreResult, schemePreferences)

        AssessmentRuleCategoryResult(
          passedMinimumCompetencyLevelOpt,
          aggregateEvaluation.location1Scheme1,
          aggregateEvaluation.location1Scheme2,
          aggregateEvaluation.location2Scheme1,
          aggregateEvaluation.location2Scheme2,
          aggregateEvaluation.alternativeScheme,
          assessmentCentreResult.competencyAverageResult,
          aggregateEvaluation.schemesEvaluation
        )
    }
  }

  private def evaluateAssessmentPassmark(
    competencyAverage: CompetencyAverageResult,
    candidateScores: AssessmentPassmarkPreferencesAndScores
  ): (AssessmentRuleCategoryResult, SchemePreferences) = {
    val qualification = candidateScores.preferencesWithQualification
    val eligibleSchemesForQualification = eligibleSchemes(aLevel = qualification.aLevel, stemLevel = qualification.stemLevel)

    val overallScore = competencyAverage.overallScore
    val passmark = candidateScores.passmark
    val schemesEvaluation = evaluateSchemes(passmark, overallScore, eligibleSchemesForQualification)
    val schemesEvaluationMap = schemesEvaluation.map { x => x.scheme -> x.result }.toMap

    val preferences = candidateScores.preferencesWithQualification.preferences
    val location1Scheme1 = preferences.firstLocation.firstFramework
    val location1Scheme2 = preferences.firstLocation.secondFramework
    val location2Scheme1 = preferences.secondLocation.map(s => s.firstFramework)
    val location2Scheme2 = preferences.secondLocation.flatMap(s => s.secondFramework)
    val location1Scheme1Result = Some(schemesEvaluationMap(model.Scheme.withName(location1Scheme1)))
    val location1Scheme2Result = location1Scheme2.map(loc => model.Scheme.withName(loc)) map schemesEvaluationMap
    val location2Scheme1Result = location2Scheme1.map(loc => model.Scheme.withName(loc)) map schemesEvaluationMap
    val location2Scheme2Result = location2Scheme2.map(loc => model.Scheme.withName(loc)) map schemesEvaluationMap

    val alternativeSchemeResult: Option[Result] = preferences.alternatives.map(_.framework).collect {
      case true =>
        val preferredSchemes = List(Some(location1Scheme1), location1Scheme2, location2Scheme1, location2Scheme2).flatten
        val alternativeSchemes = eligibleSchemesForQualification.filterNot(preferredSchemes.contains(_))
        evaluateAlternativeSchemes(schemesEvaluationMap, alternativeSchemes)
    }

    val schemePreferences = SchemePreferences(location1Scheme1, location1Scheme2, location2Scheme1, location2Scheme2)

    (AssessmentRuleCategoryResult(None, location1Scheme1Result, location1Scheme2Result, location2Scheme1Result,
      location2Scheme2Result, alternativeSchemeResult, Some(competencyAverage), Some(schemesEvaluation)), schemePreferences)
  }

  private def passMinimumCompetencyLevel(
    competencyAverage: CompetencyAverageResult,
    config: AssessmentEvaluationMinimumCompetencyLevel
  ): Option[Boolean] = {
    if (config.enabled) {
      val minCompetencyLevelScore = config.minimumCompetencyLevelScore
        .getOrElse(throw new IllegalStateException("Competency level not set"))
      val minMotivationalFitScore = config.motivationalFitMinimumCompetencyLevelScore
        .getOrElse(throw new IllegalStateException("Motivational Fit competency level not set"))

      Some(competencyAverage.scoresWithWeightOne.forall(_ >= minCompetencyLevelScore) &&
        competencyAverage.scoresWithWeightTwo.forall(_ >= minMotivationalFitScore))
    } else {
      None
    }
  }
}
