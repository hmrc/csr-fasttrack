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

import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.EvaluationResults.CompetencyAverageResult

trait AssessmentScoreCalculator {

  def countAverage(scores: CandidateScoresAndFeedback): CompetencyAverageResult = {
    val interviewScores = scores.interview
    val groupScores = scores.groupExercise
    val writtenScores = scores.writtenExercise
    val leadingAndCommunicating = average(Seq(
      interviewScores.flatMap(_.leadingAndCommunicating),
      groupScores.flatMap(_.leadingAndCommunicating),
      writtenScores.flatMap(_.leadingAndCommunicating)
    ):_*)
    val collaboratingAndPartnering = average(Seq(
      interviewScores.flatMap(_.collaboratingAndPartnering),
      groupScores.flatMap(_.collaboratingAndPartnering),
      writtenScores.flatMap(_.collaboratingAndPartnering)
    ):_*)
    val deliveringAtPace = average(Seq(
      interviewScores.flatMap(_.deliveringAtPace),
      groupScores.flatMap(_.deliveringAtPace),
      writtenScores.flatMap(_.deliveringAtPace)
    ):_*)
    val makingEffectiveDecisions = average(Seq(
      interviewScores.flatMap(_.makingEffectiveDecisions),
      groupScores.flatMap(_.makingEffectiveDecisions),
      writtenScores.flatMap(_.makingEffectiveDecisions)
    ):_*)
    val changingAndImproving = average(Seq(
      interviewScores.flatMap(_.changingAndImproving),
      groupScores.flatMap(_.changingAndImproving),
      writtenScores.flatMap(_.changingAndImproving)
    ):_*)
    val buildingCapabilityForAll = average(Seq(
      interviewScores.flatMap(_.buildingCapabilityForAll),
      groupScores.flatMap(_.buildingCapabilityForAll),
      writtenScores.flatMap(_.buildingCapabilityForAll)
    ):_*)

    val motivationFit = average(Seq(
      interviewScores.flatMap(_.motivationFit),
      groupScores.flatMap(_.motivationFit),
      writtenScores.flatMap(_.motivationFit)
    ):_*)

    val overallScores = List(leadingAndCommunicating, collaboratingAndPartnering,
      deliveringAtPace, makingEffectiveDecisions, changingAndImproving,
      buildingCapabilityForAll, motivationFit).map(BigDecimal(_)).sum.toDouble

    CompetencyAverageResult(leadingAndCommunicating, collaboratingAndPartnering, deliveringAtPace, makingEffectiveDecisions,
      changingAndImproving, buildingCapabilityForAll, motivationFit, overallScores)
  }

  private def average(scores: Option[Double]*) = scores.flatten.sum / scores.flatten.length

  private def countOverallScore(scores: CompetencyAverageResult): Double =
    (scores.scoresWithWeightOne.map(BigDecimal(_)).sum + scores.scoresWithWeightTwo.map(BigDecimal(_)).sum).toDouble
}
