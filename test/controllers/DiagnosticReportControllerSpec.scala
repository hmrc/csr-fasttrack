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

import config.TestFixtureBase
import model.CandidateScoresCommands.{ CandidateScoreFeedback, CandidateScores, CandidateScoresAndFeedback }
import model.Exceptions.ApplicationNotFound
import model.OnlineTestCommands.TestResult
import model.PersistedObjects.CandidateTestReport
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, JsObject, JsValue, Json }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.{ ApplicationAssessmentScoresRepository, TestReportRepository }
import repositories.application.DiagnosticReportingRepository
import testkit.UnitWithAppSpec

import scala.concurrent.Future

class DiagnosticReportControllerSpec extends UnitWithAppSpec {

  "Get application by id" should {
    "return all non-sensitive information about the user application" in new TestFixture {
      val expectedApplication = Json.obj("applicationId" -> "app1", "userId" -> "user1", "frameworkId" -> "FastTrack-2017",
        "online-test-results" -> Json.obj("applicationId" -> "app1", "reportType" -> "XML",
          "competency" -> Json.obj("status" -> "complete", "norm" -> "norm", "tScore" -> 1, "percentile" -> 22, "raw" -> 5, "sten" -> 55)),
        "assessment-centre-results" -> Json.obj("applicationId" -> "app1", "attendancy" -> true, "assessmentIncomplete" -> false,
          "leadingAndCommunicating" -> Json.obj("interview" -> 1), "collaboratingAndPartnering" -> Json.obj("interview" -> 1),
          "deliveringAtPace" -> Json.obj("interview" -> 1), "makingEffectiveDecisions" -> Json.obj("interview" -> 1),
          "changingAndImproving" -> Json.obj("interview" -> 1), "buildingCapabilityForAll" -> Json.obj("interview" -> 1),
          "motivationFit" -> Json.obj("interview" -> 1), "feedback" -> Json.obj("interviewFeedback" -> "blah")))

      val testResult = TestResult("complete", "norm", Some(1.0), Some(22.0), Some(5.0), Some(55.0))
      val candidateScore = CandidateScores(Some(1.0))
      val candidateScoreFeedback = CandidateScoreFeedback(Some("blah"))

      when(mockDiagnosticReportRepository.findByApplicationId("app1")).thenReturn(Future.successful(expectedApplication))
      when(mockTestResultRepo.getReportByApplicationId("app1")).thenReturn(Future.successful(
        Some(CandidateTestReport("app1", "XML", Some(testResult), None, None, None))
      ))
      when(mockAssessmentResultRepo.tryFind("app1")).thenReturn(Future.successful(
        Some(CandidateScoresAndFeedback("app1", attendancy = Some(true), assessmentIncomplete = false, leadingAndCommunicating = candidateScore,
          collaboratingAndPartnering = candidateScore, deliveringAtPace = candidateScore, makingEffectiveDecisions = candidateScore,
          changingAndImproving = candidateScore, buildingCapabilityForAll = candidateScore, motivationFit = candidateScore,
          feedback = candidateScoreFeedback))
      ))

      val result = TestableDiagnosticReportingController.getApplicationByApplicationId("app1")(createOnlineTestRequest(
        "app1"
      )).run

      val resultJson = contentAsJson(result)

      val actualApplications = resultJson.as[JsValue]
      status(result) mustBe OK
      resultJson mustBe expectedApplication
    }

    "return NotFound if the user cannot be found" in new TestFixture {
      val IncorrectUserId = "1234"
      when(mockDiagnosticReportRepository.findByApplicationId(IncorrectUserId)).thenReturn(Future.failed(
        ApplicationNotFound(IncorrectUserId)
      ))
      val result = TestableDiagnosticReportingController
        .getApplicationByApplicationId(IncorrectUserId)(createOnlineTestRequest(IncorrectUserId)).run

      status(result) mustBe NOT_FOUND
    }
  }

  trait TestFixture extends TestFixtureBase {
    val mockDiagnosticReportRepository = mock[DiagnosticReportingRepository]
    val mockTestResultRepo = mock[TestReportRepository]
    val mockAssessmentResultRepo = mock[ApplicationAssessmentScoresRepository]

    object TestableDiagnosticReportingController extends DiagnosticReportController {
      val drRepository = mockDiagnosticReportRepository
      override val trRepository: TestReportRepository = mockTestResultRepo
      override val assessmentScoresRepo: ApplicationAssessmentScoresRepository = mockAssessmentResultRepo
    }

    def createOnlineTestRequest(userId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.DiagnosticReportController.getApplicationByApplicationId(userId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
