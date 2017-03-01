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

package repositories

import factories.DateTimeFactory
import model.AssessmentExercise
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ExerciseScoresAndFeedback, ScoresAndFeedback }
import testkit.MongoRepositorySpec

class ApplicationAssessmentScoresRepositorySpec extends MongoRepositorySpec {

  override val collectionName = CollectionNames.APPLICATION_ASSESSMENT_SCORES

  def repository = new ApplicationAssessmentScoresMongoRepository(DateTimeFactory)

  "Application Scores Repository" should {
    "create indexes for the repository" in {
      val repo = repositories.applicationAssessmentScoresRepository

      val indexes = indexesWithFields(repo)
      indexes must contain (List("_id"))
      indexes must contain (List("applicationId"))
      indexes.size mustBe 2
    }

    val exerciseScoresAndFeedback = ExerciseScoresAndFeedback("app1", AssessmentExercise.interview,
      ScoresAndFeedback(
        attended = true,
        assessmentIncomplete = false,
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some(4.0),
        Some("xyz"),
        "xyz"
      ))

    "create a new application scores and feedback document" in {
      repository.save(exerciseScoresAndFeedback, None).futureValue
      repository.tryFind("app1").futureValue mustBe Some(CandidateScoresAndFeedback(applicationId = "app1",
        interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))
    }

    "return already stored application scores" in {
      repository.save(exerciseScoresAndFeedback, None).futureValue
      val result = repository.tryFind("app1").futureValue
      result mustBe Some(CandidateScoresAndFeedback(applicationId = "app1",
        interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))
    }

    "return no application score if it does not exist" in {
      val result = repository.tryFind("app1").futureValue
      result mustBe None
    }

    "update already saved application scores and feedback document" in {
      repository.save(exerciseScoresAndFeedback, None).futureValue
      val updatedApplicationScores = exerciseScoresAndFeedback.copy(scoresAndFeedback =
        exerciseScoresAndFeedback.scoresAndFeedback.copy(attended = false))
      val result = repository.save(updatedApplicationScores, None).futureValue
      repository.tryFind("app1").futureValue mustBe Some(CandidateScoresAndFeedback(applicationId = "app1",
        interview = Some(updatedApplicationScores.scoresAndFeedback)))
    }

    "retrieve all application scores and feedback documents" in {
      val exerciseScoresAndFeedback2 = ExerciseScoresAndFeedback("app2", AssessmentExercise.interview,
        ScoresAndFeedback(
          attended = true,
          assessmentIncomplete = false,
          Some(1.0),
          Some(1.0),
          Some(1.0),
          Some(1.0),
          Some(1.0),
          Some(1.0),
          Some(1.0),
          Some("xyz"),
          "xyz"
        ))

      repository.save(exerciseScoresAndFeedback, None).futureValue
      repository.save(exerciseScoresAndFeedback2, None).futureValue
      repository.save(exerciseScoresAndFeedback2.copy(exercise = AssessmentExercise.groupExercise), None).futureValue
      repository.save(exerciseScoresAndFeedback2.copy(exercise = AssessmentExercise.writtenExercise), None).futureValue

      val result = repository.allScores.futureValue

      result must have size 2
      result must contain ("app1" -> CandidateScoresAndFeedback(applicationId = "app1",
        interview = Some(exerciseScoresAndFeedback.scoresAndFeedback)))
      result must contain ("app2" -> CandidateScoresAndFeedback(applicationId = "app2",
        interview = Some(exerciseScoresAndFeedback2.scoresAndFeedback),
        groupExercise = Some(exerciseScoresAndFeedback2.scoresAndFeedback),
        writtenExercise = Some(exerciseScoresAndFeedback2.scoresAndFeedback)))
    }
  }
}
