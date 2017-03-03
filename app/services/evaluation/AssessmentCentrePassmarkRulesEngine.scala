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
import model.persisted.SchemeEvaluationResult

trait AssessmentCentrePassmarkRulesEngine {

  def evaluate(onlineTestEvaluation: OnlineTestPassmarkEvaluation, candidateScore: AssessmentPassmarkPreferencesAndScores,
              config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentRuleCategoryResult

  def evaluate2(onlineTestEvaluation: List[SchemeEvaluationResult],
                candidateScore: AssessmentPassmarkPreferencesAndScores,
                config: AssessmentEvaluationMinimumCompetencyLevel): AssessmentRuleCategoryResultNEW
}

object AssessmentCentrePassmarkRulesEngine extends AssessmentCentrePassmarkRulesEngine with AssessmentScoreCalculator
    with AssessmentCentreAllSchemesEvaluator with FinalResultEvaluator with EligibleSchemeSelector {

  def evaluate(onlineTestEvaluation: OnlineTestPassmarkEvaluation,
               candidateScores: AssessmentPassmarkPreferencesAndScores,
               config: AssessmentEvaluationMinimumCompetencyLevel
  ): AssessmentRuleCategoryResult = {
    val competencyAverage = countAverage(candidateScores.scores)
    val passedMinimumCompetencyLevelOpt = passMinimumCompetencyLevel(competencyAverage, config)

    passedMinimumCompetencyLevelOpt match {
      case Some(false) =>
        //scalastyle:off
        println("**** candidate failed minimum competency level check")
        AssessmentRuleCategoryResult(passedMinimumCompetencyLevelOpt, None, None, None, None, None, None, None)
      case _ =>
        //scalastyle:off
        println("**** candidate passed minimum competency level check")
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

  def evaluate2(onlineTestEvaluation: List[SchemeEvaluationResult],
                candidateScores: AssessmentPassmarkPreferencesAndScores,
                config: AssessmentEvaluationMinimumCompetencyLevel
              ): AssessmentRuleCategoryResultNEW = {
    val competencyAverage = countAverage(candidateScores.scores)
    val passedMinimumCompetencyLevelCheckOpt = passMinimumCompetencyLevel(competencyAverage, config)

    passedMinimumCompetencyLevelCheckOpt match {
      case Some(false) =>
        //scalastyle:off
        println("**** AssessmentCentrePassmarkRulesEngine - candidate failed minimum competency level check - all schemes will be red")

        val allSchemesHaveFailed = candidateScores.preferencesWithQualification.schemes.map { scheme =>
          PerSchemeEvaluation(schemeName = scheme.toString, result = Red)
        }
        AssessmentRuleCategoryResultNEW(passedMinimumCompetencyLevelCheckOpt, competencyAverage, allSchemesHaveFailed, allSchemesHaveFailed)
      case _ =>
        //scalastyle:off
        println("**** AssessmentCentrePassmarkRulesEngine - candidate passed minimum competency level check")
        val assessmentCentreEvaluation = evaluateAssessmentPassmark2(competencyAverage, candidateScores)

        println(s"**** AssessmentCentrePassmarkRulesEngine - schemesAssessmentEvaluation = $assessmentCentreEvaluation")
        // from here
//        val (assessmentCentreResultNoMinCompetency, schemePreferences) = evaluateAssessmentPassmark(competencyAverage, candidateScores)
//        val assessmentCentreResult = assessmentCentreResultNoMinCompetency.copy(passedMinimumCompetencyLevel = passedMinimumCompetencyLevelCheckOpt)

//        val aggregateEvaluation = mergeResults(onlineTestEvaluation, assessmentCentreResult, schemePreferences)
        // to here

        val finalResults = mergeResults2(onlineTestEvaluation, assessmentCentreEvaluation)
        println(s"**** AssessmentCentrePassmarkRulesEngine - finalResults = $finalResults")

        AssessmentRuleCategoryResultNEW(
          passedMinimumCompetencyLevelCheckOpt,
          competencyAverage,
          assessmentCentreEvaluation,
          finalResults
        )
    }
  }

  // TODO IS: here this should be deleted
  private def evaluateAssessmentPassmark(
    competencyAverage: CompetencyAverageResult,
    candidateScores: AssessmentPassmarkPreferencesAndScores
  ): (AssessmentRuleCategoryResult, SchemePreferences) = {
    val qualification = candidateScores.preferencesWithQualification
    val eligibleSchemesForQualification = eligibleSchemes(aLevel = qualification.aLevel, stemLevel = qualification.stemLevel)

    val overallScore = competencyAverage.overallScore
    val passmark = candidateScores.passmark
    val schemesEvaluation = evaluateSchemes(candidateScores.scores.applicationId, passmark, overallScore, eligibleSchemesForQualification)
    val schemesEvaluationMap = schemesEvaluation.map { x => x.schemeName -> x.result }.toMap

//    val preferences = candidateScores.preferencesWithQualification.preferences
//    val location1Scheme1 = preferences.firstLocation.firstFramework
//    val location1Scheme2 = preferences.firstLocation.secondFramework
//    val location2Scheme1 = preferences.secondLocation.map(s => s.firstFramework)
//    val location2Scheme2 = preferences.secondLocation.flatMap(s => s.secondFramework)
//    val location1Scheme1Result = Some(schemesEvaluationMap(location1Scheme1))
//    val location1Scheme2Result = location1Scheme2 map schemesEvaluationMap
//    val location2Scheme1Result = location2Scheme1 map schemesEvaluationMap
//    val location2Scheme2Result = location2Scheme2 map schemesEvaluationMap

//    val alternativeSchemeResult: Option[Result] = preferences.alternatives.map(_.framework).collect {
//      case true =>
//        val preferredSchemes = List(Some(location1Scheme1), location1Scheme2, location2Scheme1, location2Scheme2).flatten
//        val alternativeSchemes = eligibleSchemesForQualification.filterNot(preferredSchemes.contains(_))
//        evaluateAlternativeSchemes(schemesEvaluationMap, alternativeSchemes)
//    }

//    val schemePreferences = SchemePreferences(location1Scheme1, location1Scheme2, location2Scheme1, location2Scheme2)

//    (AssessmentRuleCategoryResult(None, location1Scheme1Result, location1Scheme2Result, location2Scheme1Result,
//      location2Scheme2Result, alternativeSchemeResult, Some(competencyAverage), Some(schemesEvaluation)), schemePreferences)

    val schemePreferences = SchemePreferences(location1Scheme1 = "", location1Scheme2 = None,
      location2Scheme1 = None, location2Scheme2 = None)


    AssessmentRuleCategoryResult(passedMinimumCompetencyLevel = None,
      location1Scheme1 = None, location1Scheme2 = None,
      location2Scheme1 = None, location2Scheme2 = None, alternativeScheme = None,
      competencyAverageResult = None, schemesEvaluation = None) -> schemePreferences
  }

  // TODO IS: here
  private def evaluateAssessmentPassmark2(competencyAverage: CompetencyAverageResult,
                                          candidateScores: AssessmentPassmarkPreferencesAndScores
                                         ): List[PerSchemeEvaluation] = {
//    val qualification = candidateScores.preferencesWithQualification
//    val eligibleSchemesForQualification = eligibleSchemes(aLevel = qualification.aLevel, stemLevel = qualification.stemLevel)
    val eligibleSchemesForQualification = candidateScores.preferencesWithQualification.schemes.map{ _.toString }

    val overallScore = competencyAverage.overallScore
    val passmark = candidateScores.passmark
    val schemesEvaluation = evaluateSchemes(candidateScores.scores.applicationId, passmark, overallScore, eligibleSchemesForQualification)
    //scalastyle:off
    println(s"**** evaluateAssessmentPassmark2 - schemesEvaluation = $schemesEvaluation")
    schemesEvaluation
//    val schemesEvaluationMap = schemesEvaluation.map { x => x.schemeName -> x.result }//.toMap

    //    val preferences = candidateScores.preferencesWithQualification.preferences
    //    val location1Scheme1 = preferences.firstLocation.firstFramework
    //    val location1Scheme2 = preferences.firstLocation.secondFramework
    //    val location2Scheme1 = preferences.secondLocation.map(s => s.firstFramework)
    //    val location2Scheme2 = preferences.secondLocation.flatMap(s => s.secondFramework)
    //    val location1Scheme1Result = Some(schemesEvaluationMap(location1Scheme1))
    //    val location1Scheme2Result = location1Scheme2 map schemesEvaluationMap
    //    val location2Scheme1Result = location2Scheme1 map schemesEvaluationMap
    //    val location2Scheme2Result = location2Scheme2 map schemesEvaluationMap

    //    val alternativeSchemeResult: Option[Result] = preferences.alternatives.map(_.framework).collect {
    //      case true =>
    //        val preferredSchemes = List(Some(location1Scheme1), location1Scheme2, location2Scheme1, location2Scheme2).flatten
    //        val alternativeSchemes = eligibleSchemesForQualification.filterNot(preferredSchemes.contains(_))
    //        evaluateAlternativeSchemes(schemesEvaluationMap, alternativeSchemes)
    //    }

    //    val schemePreferences = SchemePreferences(location1Scheme1, location1Scheme2, location2Scheme1, location2Scheme2)

    //    (AssessmentRuleCategoryResult(None, location1Scheme1Result, location1Scheme2Result, location2Scheme1Result,
    //      location2Scheme2Result, alternativeSchemeResult, Some(competencyAverage), Some(schemesEvaluation)), schemePreferences)

//    val schemePreferences = SchemePreferences(location1Scheme1 = "", location1Scheme2 = None,
//      location2Scheme1 = None, location2Scheme2 = None)


//    AssessmentRuleCategoryResultNEW(passedMinimumCompetencyLevel = None,
//      competencyAverageResult = None, schemesEvaluation = Nil) -> schemePreferences
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
