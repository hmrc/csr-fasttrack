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

package controllers.report

import connectors.AuthProviderClient
import controllers.ReportingController
import model.CandidateScoresCommands.{ CandidateScoreFeedback, CandidateScores, CandidateScoresAndFeedback }
import model.Commands.Implicits._
import model.Commands._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import play.api.test.Helpers._
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, DiversityReportRepository, QuestionnaireRepository, TestReportRepository }
import repositories.application.GeneralApplicationRepository
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps
import scala.util.Random

class AssessmentResultsReportingControllerSpec extends BaseReportingControllerSpec {
  "Assessment results report" should {
    "return results report" in new AssessmentResultsReportTestFixture {
      when(reportingRepoMock.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepoMock.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe List(AssessmentResultsReport(applicationPreference1, passMarks1, scores1))
    }

    "return nothing if no applications exist" in new AssessmentResultsReportTestFixture {
      when(reportingRepoMock.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(Nil)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepoMock.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no questionnaires exist" in new AssessmentResultsReportTestFixture {
      when(reportingRepoMock.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(Map.empty)
      when(assessmentScoresRepoMock.allScores).thenReturnAsync(scores)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no scores exist" in new AssessmentResultsReportTestFixture {
      when(reportingRepoMock.applicationsWithAssessmentScoresAccepted(any())).thenReturnAsync(appPreferences)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(passMarks)
      when(assessmentScoresRepoMock.allScores).thenReturnAsync(Map.empty)

      val response = controller.createAssessmentResultsReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentResultsReport]]

      status(response) mustBe OK

      result mustBe empty
    }
  }

  trait AssessmentResultsReportTestFixture extends TestFixture {
    val appId = rnd("appId")

    lazy val applicationPreference1 = newAppPreferences
    lazy val passMarks1 = newQuestionnaire
    lazy val scores1 = newScores

    lazy val appPreferences = List(applicationPreference1)
    lazy val passMarks = Map(appId -> passMarks1)
    lazy val scores = Map(appId -> scores1)

    private def someDouble = Some(Random.nextDouble())

    def newAppPreferences =
      ApplicationPreferences(rnd("userId"), appId, someRnd("location"), someRnd("location1scheme1-"),
        someRnd("location1scheme2-"), someRnd("location"), someRnd("location2scheme1-"), someRnd("location2scheme2-"),
        yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd,
        OnlineTestPassmarkEvaluationSchemes(Some("Pass"), Some("Fail"), Some("Pass"), Some("Fail"), Some("Amber")))

    def newQuestionnaire =
      PassMarkReportQuestionnaireData(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
        someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"), rnd("SES"))

    def newScores = CandidateScoresAndFeedback(applicationId = appId, attendancy = maybe(true),
      assessmentIncomplete = false,
      leadingAndCommunicating = CandidateScores(someDouble, someDouble, someDouble),
      collaboratingAndPartnering = CandidateScores(someDouble, someDouble, someDouble),
      deliveringAtPace = CandidateScores(someDouble, someDouble, someDouble),
      makingEffectiveDecisions = CandidateScores(someDouble, someDouble, someDouble),
      changingAndImproving = CandidateScores(someDouble, someDouble, someDouble),
      buildingCapabilityForAll = CandidateScores(someDouble, someDouble, someDouble),
      motivationFit = CandidateScores(someDouble, someDouble, someDouble),
      feedback = CandidateScoreFeedback(someRnd("feedback"), someRnd("feedback"), someRnd("feedback")))

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAssessmentResultsReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
