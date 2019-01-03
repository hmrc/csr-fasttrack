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

package controllers

import factories.DateTimeFactory
import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ScoresAndFeedback }
import model.EmptyRequestHeader
import model.Exceptions.ApplicationNotFound
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ RequestHeader, Result }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import services.applicationassessment.AssessorAssessmentScoresService._
import services.applicationassessment.{ AssessmentCentreScoresRemovalService, AssessmentCentreScoresService, AssessmentCentreService }
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class ReviewerScoresControllerSpec extends UnitWithAppSpec {

  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

  private val candidateScoresAndFeedback = CandidateScoresAndFeedback("app1",
    interview = Some(
      ScoresAndFeedback(
        attended = true,
        assessmentIncomplete = false,
        leadingAndCommunicating = Some(4.0),
        collaboratingAndPartnering = Some(4.0),
        deliveringAtPace = Some(4.0),
        makingEffectiveDecisions = Some(4.0),
        changingAndImproving = Some(4.0),
        buildingCapabilityForAll = Some(4.0),
        motivationFit = Some(4.0),
        feedback = Some("xyz"),
        updatedBy = "xyz"
      )
    )
  )

  "Reviewer saving candidate scores" should {
    "save candidate scores & feedback" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result: Future[Result] = TestReviewerScoresController.saveCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe OK
    }

    "return Bad Request when attendancy is not set" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(new IllegalStateException("blah")))

      val result: Future[Result] = TestReviewerScoresController.saveExerciseScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe BAD_REQUEST
    }
  }

  "Reviewer accepting candidate scores" should {
    "accept candidate scores & feedback" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.acceptScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
        (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result: Future[Result] = TestReviewerScoresController.acceptCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe OK
    }

    "return a conflict if another reviewer has already accepted candidate scores and feedback" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.acceptScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ReviewerAlreadyAcceptedScoresException("Boom")))

      val result: Future[Result] = TestReviewerScoresController.acceptCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe CONFLICT
    }

    "return method not allowed if the reviewed scores are out of date" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.acceptScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ReviewerScoresOutOfDateException("Boom")))

      val result: Future[Result] = TestReviewerScoresController.acceptCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe METHOD_NOT_ALLOWED
    }
  }

  "Super reviewer accepting scores" should {
    "accept candidate scores & feedback" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.acceptScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result: Future[Result] = TestReviewerScoresController.acceptSuperReviewerCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe OK
    }

    "return method not allowed if the accepted scores are out of date" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.acceptScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ReviewerScoresOutOfDateException("Boom")))

      val result: Future[Result] = TestReviewerScoresController.acceptSuperReviewerCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe METHOD_NOT_ALLOWED
    }
  }

  "Super reviewer saving scores" should {
    "save candidate scores & feedback" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(()))

      val result: Future[Result] = TestReviewerScoresController.saveSuperReviewerCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe OK
    }

    "return method not allowed if the saved scores are out of date" in new TestFixture {
      when(mockReviewerAssessmentCentreScoresService.saveScoresAndFeedback(any[String], any[CandidateScoresAndFeedback], any[Boolean])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ReviewerScoresOutOfDateException("Boom")))

      val result: Future[Result] = TestReviewerScoresController.saveSuperReviewerCandidateScoresAndFeedback("app1")(
        createSaveCandidateScoresAndFeedback("app1", Json.toJson(candidateScoresAndFeedback))
      )
      status(result) mustBe METHOD_NOT_ALLOWED
    }
  }

  "Unlock exercise" should {
    "unlock an exercise when no exceptions occur" in new TestFixture {
      when(mockAssessmentCentreScoresRemovalService.removeScoresAndFeedback(any[String], any[AssessmentExercise])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.successful(unit))

      val result: Future[Result] = TestReviewerScoresController.unlockExercise("app1", "interview").apply(FakeRequest())
      status(result) mustBe OK
    }

    "return a bad request when exercise is invalid" in new TestFixture {
      when(mockAssessmentCentreScoresRemovalService.removeScoresAndFeedback(any[String], any[AssessmentExercise])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(new NoSuchElementException))

      val result: Future[Result] = TestReviewerScoresController.unlockExercise("app1", "interview").apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
    }

    "return a bad request when no such application exists" in new TestFixture {
      when(mockAssessmentCentreScoresRemovalService.removeScoresAndFeedback(any[String], any[AssessmentExercise])
      (any[HeaderCarrier], any[RequestHeader])).thenReturn(Future.failed(ApplicationNotFound("app1")))

      val result: Future[Result] = TestReviewerScoresController.unlockExercise("app1", "interview").apply(FakeRequest())
      status(result) mustBe BAD_REQUEST
    }
  }

  trait TestFixture {
    val mockReviewerAssessmentCentreScoresService: AssessmentCentreScoresService = mock[AssessmentCentreScoresService]
    val mockAssessmentCentreScoresRemovalService: AssessmentCentreScoresRemovalService = mock[AssessmentCentreScoresRemovalService]
    val mockAssessmentCentreService: AssessmentCentreService = mock[AssessmentCentreService]

    object TestReviewerScoresController extends ReviewerScoresController {
      val dateTimeFactory: DateTimeFactory = DateTimeFactory
      val assessmentCentreScoresService: AssessmentCentreScoresService = mockReviewerAssessmentCentreScoresService
      val assessmentCentreService: AssessmentCentreService = mockAssessmentCentreService
      val assessmentCentreScoresRemovalService: AssessmentCentreScoresRemovalService = mockAssessmentCentreScoresRemovalService
    }

    def createSaveCandidateScoresAndFeedback(applicationId: String, json: JsValue): FakeRequest[JsValue] = {
      FakeRequest(
        Helpers.POST,
        controllers.routes.ReviewerScoresController.saveCandidateScoresAndFeedback(applicationId).url, FakeHeaders(), json
      ).withHeaders("Content-Type" -> "application/json")
    }
  }
}
