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
import model.{ AssessmentExercise, EmptyRequestHeader }
import org.mockito.Mockito._
import play.api.libs.json.{ JsValue, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.applicationassessment.{ AssessmentCentreScoresService, AssessmentCentreService }
import testkit.UnitWithAppSpec
import org.mockito.Matchers._
import play.api.mvc.{ RequestHeader, Result }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

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
      when(mockAssessorAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[ExerciseScoresAndFeedback])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result: Future[Result] = TestCandidateScoresController.saveExerciseScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(exerciseScoresAndFeedback).toString())
      )

      status(result) must be(CREATED)
    }

    "return Bad Request when attendancy is not set" in new TestFixture {
      when(mockAssessorAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[ExerciseScoresAndFeedback])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(new IllegalStateException("blah")))

      val result: Future[Result] = TestCandidateScoresController.saveExerciseScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(exerciseScoresAndFeedback).toString())
      )

      status(result) must be(BAD_REQUEST)
    }
  }

  trait TestFixture {
    val mockAssessorAssessmentCentreScoresService: AssessmentCentreScoresService = mock[AssessmentCentreScoresService]
    val mockReviewerAssessmentCentreScoresService: AssessmentCentreScoresService = mock[AssessmentCentreScoresService]

    object TestCandidateScoresController extends CandidateScoresController {
      def assessorAssessmentCentreService: AssessmentCentreScoresService = mockAssessorAssessmentCentreScoresService
      def reviewerAssessmentCentreService: AssessmentCentreScoresService = mockReviewerAssessmentCentreScoresService

      val dateTimeFactory: DateTimeFactory = DateTimeFactory
    }

    def createSaveCandidateScoresAndFeedback(applicationId: String, jsonString: String): FakeRequest[JsValue] = {
      val json = Json.parse(jsonString)
      FakeRequest(
        Helpers.POST,
        controllers.routes.CandidateScoresController.saveExerciseScoresAndFeedback(applicationId).url, FakeHeaders(), json
      )
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
