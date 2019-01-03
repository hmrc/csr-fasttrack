/*
 * Copyright 2019 HM Revenue & Customs
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

package model

import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ScoresAndFeedback }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec
import CandidateScoresAndFeedbackSpec._

class CandidateScoresAndFeedbackSpec extends PlaySpec with TableDrivenPropertyChecks {
  // format: OFF
  val leadingAndCommunicatingCompetency = Table(
    ("Interview", "Group Exercise", "Written Exercise", "Expected Average"),
    (Some(1.0),    Some(2.0),        Some(3.0),         2.0),
    (Some(1.0),    Some(1.1),        Some(1.2),         1.1),
    (Some(0.0),    Some(0.0),        Some(0.1),         0.03333333333333333),
    (Incomplete,   Some(3.9),        Some(0.1),         1.3333333333333333),
    (Incomplete,   Incomplete,       Some(0.1),         0.03333333333333333),
    (Incomplete,   Incomplete,       Incomplete,        0.0)
  )

  val competenciesWithTwoExercises = Table(
    ("First Exercise", "Second Exercise", "Expected Average"),
    (Some(1.0),         Some(2.0),        1.5),
    (Some(1.0),         Some(1.1),        1.05),
    (Some(1.1),         Some(1.2),        1.15),
    (Incomplete,        Some(1.0),        0.5),
    (Incomplete,        Incomplete,       0.0)
  )

  val motivationalFitCompetency = Table(
    ("Interview", "Group Exercise", "Expected Average"),
    (Some(1.0),    Some(3.0),       4.0),
    (Some(4.34),    Some(3.12),     7.46),
    (Incomplete,    Some(3.12),     3.12),
    (Incomplete,    Incomplete,     0)
  )
  // format: ON

  "Count average" should {
    "count average for 'leading and communicating'" in {
      forAll(leadingAndCommunicatingCompetency) { (interviewScores, groupScores, writtenScores, expectedAverage) =>
        val newInterview = CandidateScores.interview.map(_.copy(leadingAndCommunicating = interviewScores))
        val newGroupExercise = CandidateScores.groupExercise.map(_.copy(leadingAndCommunicating = groupScores))
        val newWrittenExercise = CandidateScores.writtenExercise.map(_.copy(leadingAndCommunicating = writtenScores))
        val candidate = CandidateScores.copy(
          interview = newInterview,
          groupExercise = newGroupExercise,
          writtenExercise = newWrittenExercise
        )

        candidate.leadingAndCommunicatingAvg mustBe expectedAverage
      }
    }

    "count average for: 'delivering at pace' and 'changing and improving'" in {
      forAll(competenciesWithTwoExercises) { (interviewScores, writtenScores, expectedAverage) =>
        val deliveringAtPaceCandidate = CandidateScores.copy(
          interview = CandidateScores.interview.map(_.copy(deliveringAtPace = interviewScores)),
          writtenExercise = CandidateScores.writtenExercise.map(_.copy(deliveringAtPace = writtenScores))
        )

        val changingAndImprovingCandidate = CandidateScores.copy(
          interview = CandidateScores.interview.map(_.copy(changingAndImproving = interviewScores)),
          writtenExercise = CandidateScores.writtenExercise.map(_.copy(changingAndImproving = writtenScores))
        )

        deliveringAtPaceCandidate.deliveringAtPaceAvg mustBe expectedAverage
        changingAndImprovingCandidate.changingAndImprovingAvg mustBe expectedAverage
      }
    }

    "count average for: 'building capability for all'" in {
      forAll(competenciesWithTwoExercises) { (interviewScores, groupScores, expectedAverage) =>
        val buildingCapabilityForAllCandidate = CandidateScores.copy(
          interview = CandidateScores.interview.map(_.copy(buildingCapabilityForAll = interviewScores)),
          groupExercise = CandidateScores.groupExercise.map(_.copy(buildingCapabilityForAll = groupScores))
        )

        buildingCapabilityForAllCandidate.buildingCapabilityForAllAvg mustBe expectedAverage
      }
    }

    "count average for: 'collaborating and partnering' and 'making effective decisions'" in {
      forAll(competenciesWithTwoExercises) { (groupScores, writtenScores, expectedAverage) =>
        val collaboratingAndPartneringCandidate = CandidateScores.copy(
          groupExercise = CandidateScores.groupExercise.map(_.copy(collaboratingAndPartnering = groupScores)),
          writtenExercise = CandidateScores.writtenExercise.map(_.copy(collaboratingAndPartnering = writtenScores))
        )
        val makingEffectiveDecisionsCandidate = CandidateScores.copy(
          groupExercise = CandidateScores.groupExercise.map(_.copy(makingEffectiveDecisions = groupScores)),
          writtenExercise = CandidateScores.writtenExercise.map(_.copy(makingEffectiveDecisions = writtenScores))
        )

        collaboratingAndPartneringCandidate.collaboratingAndPartneringAvg mustBe expectedAverage
        makingEffectiveDecisionsCandidate.makingEffectiveDecisionsAvg mustBe expectedAverage
      }

    }

    "count average for: 'motivational fit'" in {
      forAll(motivationalFitCompetency) { (interviewScores, groupScores, expectedAverage) =>
        val motivationFitCandidate = CandidateScores.copy(
          interview = CandidateScores.interview.map(_.copy(motivationFit = interviewScores)),
          groupExercise = CandidateScores.groupExercise.map(_.copy(motivationFit = groupScores))
        )

        motivationFitCandidate.motivationalFitDoubledAvg mustBe expectedAverage
      }
    }
  }
}

object CandidateScoresAndFeedbackSpec {
  val Scores = ScoresAndFeedback(attended = true, assessmentIncomplete = false, updatedBy = "appId")
  val Incomplete: Option[Double] = None

  val CandidateScores = CandidateScoresAndFeedback("appId", Some(Scores), Some(Scores), Some(Scores))

}
