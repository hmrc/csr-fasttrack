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

package controllers

import factories.DateTimeFactory
import model.CandidateScoresCommands.{ ExerciseScoresAndFeedback, ScoresAndFeedback }
import model.CandidateScoresCommands.Implicits._
import model.EvaluationResults.CompetencyAverageResult
import model.{ AssessmentExercise, EmptyRequestHeader }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.applicationassessment.AssessmentCentreService
import testkit.UnitWithAppSpec
import org.mockito.Matchers._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import org.mockito.Matchers.{ eq => eqTo, _ }

class CandidateScoresControllerSpec extends UnitWithAppSpec {

  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

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

  "Save Candidate Scores" should {
    "save candidate scores & feedback and update application status" in new TestFixture {
      when(mockAssessmentCentreService.saveScoresAndFeedback(any[String], any[ExerciseScoresAndFeedback])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result = TestCandidateScoresController.saveExerciseScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedbackRequest("app1", Json.toJson(exerciseScoresAndFeedback).toString())
      )

      status(result) must be(CREATED)
    }

    "return Bad Request when attendancy is not set" in new TestFixture {
      when(mockAssessmentCentreService.saveScoresAndFeedback(any[String], any[ExerciseScoresAndFeedback])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(new IllegalStateException("blah")))

      val result = TestCandidateScoresController.saveExerciseScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedbackRequest("app1", Json.toJson(exerciseScoresAndFeedback).toString())
      )

      status(result) must be(BAD_REQUEST)
    }
  }

  "get competency average result" should {
    "Not Found if it cannot be found" in new TestFixture {
      val NotFoundAppId = "notFoundApplicationId1"
      when(mockAssessmentCentreService.getCompetencyAverageResult(eqTo(NotFoundAppId))).thenReturn(Future.successful(None))
      val result = TestCandidateScoresController.getCompetencyAverageResult(NotFoundAppId)(
        createGetCompetencyAverageResultRequest(NotFoundAppId))
      status(result) must be(NOT_FOUND)
      contentAsString(result) must include(s"Competency average result for $NotFoundAppId could not be found")
    }

    "OK with corresponding Competency average result if it can be found" in new TestFixture {
      val FoundAppId = "FoundApplicationId1"
      val CompetencyAverageResultExample = CompetencyAverageResult(2.0, 2.0, 3.0, 3.1, 1.5, 2.5, 2.7, 3.3)
      when(mockAssessmentCentreService.getCompetencyAverageResult(eqTo(FoundAppId))).
        thenReturn(Future.successful(Some(CompetencyAverageResultExample)))
      val result = TestCandidateScoresController.getCompetencyAverageResult(FoundAppId)(
        createGetCompetencyAverageResultRequest(FoundAppId))
      status(result) must be(OK)
      contentAsJson(result) must be (Json.toJson(CompetencyAverageResultExample))
    }
  }

  trait TestFixture {
    val mockAssessmentCentreService = mock[AssessmentCentreService]

    object TestCandidateScoresController extends CandidateScoresController {
      override def assessmentCentreService: AssessmentCentreService = mockAssessmentCentreService

      val dateTimeFactory: DateTimeFactory = DateTimeFactory
    }

    def createSaveCandidateScoresAndFeedbackRequest(applicationId: String, jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(
        Helpers.POST,
        controllers.routes.CandidateScoresController.saveExerciseScoresAndFeedback(applicationId).url, FakeHeaders(), json
      ).withHeaders("Content-Type" -> "application/json")
    }

    def createGetCompetencyAverageResultRequest(applicationId: String) = {
      FakeRequest(
        Helpers.POST,
        controllers.routes.CandidateScoresController.getCandidateScores(applicationId).url
      ).withHeaders("Content-Type" -> "application/json")
    }
  }
}
