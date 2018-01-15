/*
 * Copyright 2018 HM Revenue & Customs
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
import model.EmptyRequestHeader
import model.EvaluationResults.CompetencyAverageResult
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, Helpers }
import play.api.test.Helpers._
import services.applicationassessment.AssessmentCentreService
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class EvaluatedAssessmentCentreScoresControllerSpec extends UnitWithAppSpec {

  implicit val hc = HeaderCarrier()
  implicit val rh = EmptyRequestHeader

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
    val mockAssessmentCentreService: AssessmentCentreService = mock[AssessmentCentreService]

    object TestCandidateScoresController extends EvaluatedAssessmentCentreScoresController {
      val dateTimeFactory: DateTimeFactory = DateTimeFactory
      val assessmentCentreService: AssessmentCentreService = mockAssessmentCentreService
    }

    def createGetCompetencyAverageResultRequest(applicationId: String) = {
      FakeRequest(
        Helpers.GET,
        controllers.routes.EvaluatedAssessmentCentreScoresController.getCompetencyAverageResult(applicationId).url
      ).withHeaders("Content-Type" -> "application/json")
    }
  }
}
