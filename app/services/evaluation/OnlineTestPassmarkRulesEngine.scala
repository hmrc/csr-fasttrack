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

import connectors.PassMarkExchangeObjects.{ SchemeThreshold, SchemeThresholds }
import model.EvaluationResults._
import model.OnlineTestCommands.{ CandidateEvaluationData, TestResult }
import model.PersistedObjects.CandidateTestReport
import model.Scheme.Scheme
import model.persisted.SchemeEvaluationResult
import play.api.Logger

trait OnlineTestPassmarkRulesEngine {

  def evaluate(score: CandidateEvaluationData): List[SchemeEvaluationResult]
}

object OnlineTestPassmarkRulesEngine extends OnlineTestPassmarkRulesEngine {

  def evaluate(candidateData: CandidateEvaluationData): List[SchemeEvaluationResult] = {
    candidateData.schemes.map { scheme =>
      SchemeEvaluationResult(scheme, evaluateScore(candidateData, scheme))
    }
  }

  private def evaluateScore(candidateScores: CandidateEvaluationData, scheme: Scheme) = {
    val passmark = candidateScores.passmarkSettings.schemes.find(_.schemeName == scheme)
      .getOrElse(throw new IllegalStateException(s"schemeName=$scheme is not set in Passmark settings"))
    determineResult(candidateScores.scores, passmark.schemeThresholds)
  }

  private def determineResult(scores: CandidateTestReport, thresholds: SchemeThresholds): Result = {
    val resultsToPassmark = List(
      (scores.competency, thresholds.competency),
      (scores.verbal, thresholds.verbal),
      (scores.numerical, thresholds.numerical),
      (scores.situational, thresholds.situational)
    )

    val testResults: Seq[Result] = resultsToPassmark.map {
      case (None, _) => Green
      case (Some(TestResult(_, _, Some(tScore), _, _, _)), passMark) => schemeResult(tScore, passMark)
      case (Some(TestResult(_, _, None, _, _, _)), _) =>
        throw new IllegalArgumentException(s"Candidate report does not have tScore: $scores")
    }

    if (testResults.contains(Red)) {
      Red
    } else if (testResults.forall(_ == Green)) {
      Green
    } else {
      Amber
    }
  }

  private def schemeResult(tScore: Double, passMark: SchemeThreshold) = {
    if (tScore >= passMark.passThreshold) {
      Green
    } else if (tScore >= passMark.failThreshold) {
      Amber
    } else {
      Red
    }
  }
}
