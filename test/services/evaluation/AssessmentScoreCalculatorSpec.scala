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

import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ScoresAndFeedback }
import model.EvaluationResults.CompetencyAverageResult
import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec

class AssessmentScoreCalculatorSpec extends PlaySpec with MustMatchers {

  val calculator = new AssessmentScoreCalculator {}
  "Assessment Score Calculator" should {
    "count scores" in {
      val Scores = CandidateScoresAndFeedback("appId",
        interview = Some(
          ScoresAndFeedback(
            attended = false,
            assessmentIncomplete = false,
            leadingAndCommunicating = Some(4),
            collaboratingAndPartnering = None,
            deliveringAtPace = Some(3.05),
            makingEffectiveDecisions = None,
            changingAndImproving = Some(3.1),
            buildingCapabilityForAll = Some(3.98),
            motivationFit = Some(2.99),
            feedback = Some("feedback"),
            updatedBy = "xyz"
          )),
        groupExercise = Some(
          ScoresAndFeedback(
            attended = false,
            assessmentIncomplete = false,
            leadingAndCommunicating = Some(4),
            collaboratingAndPartnering = Some(3.25),
            deliveringAtPace = None,
            makingEffectiveDecisions = Some(3.12),
            changingAndImproving = None,
            buildingCapabilityForAll = Some(3.99),
            motivationFit = Some(2.76),
            feedback = Some("feedback"),
            updatedBy = "xyz"
          )),
        writtenExercise = Some(
          ScoresAndFeedback(
            attended = false,
            assessmentIncomplete = false,
            leadingAndCommunicating = Some(4),
            collaboratingAndPartnering = Some(3.98),
            deliveringAtPace = Some(2.98),
            makingEffectiveDecisions = Some(3.66),
            changingAndImproving = Some(3.09),
            buildingCapabilityForAll = None,
            motivationFit = None,
            feedback = Some("feedback"),
            updatedBy = "xyz"
          ))
      )

      val result = calculator.countAverage(Scores)
      result must be(CompetencyAverageResult(4.00, 3.615, 3.015, 3.39, 3.095, 3.985, 5.75, 26.85))
    }
  }
}
