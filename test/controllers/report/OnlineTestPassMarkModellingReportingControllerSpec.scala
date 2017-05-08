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

import model.Commands._
import model.EvaluationResults.Result
import model.PersistedObjects.ContactDetailsWithId
import model.Scheme._
import model.persisted.SchemeEvaluationResult
import model.{ AssessmentCentreIndicator, UniqueIdentifier, Adjustments }
import model.CandidateScoresCommands.CandidateScoresAndFeedback
import model.ReportExchangeObjects.{ PassMarkReportQuestionnaireData, PassMarkReportTestResults, TestResult }
import model.exchange.{ LocationSchemesExamples, ApplicationForCandidateProgressReportItemExamples }
import model.report.PassMarkReportItem
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.QuestionnaireRepository
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps
import scala.util.Random

class OnlineTestPassMarkModellingReportingControllerSpec extends BaseReportingControllerSpec {
  "Online Test Pass mark modelling report" should {
    "return nothing if no applications exist" in new PassMarkReportTestFixture {
      when(reportingRepoMock.passMarkReport(any())).thenReturnAsync(Nil)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)
      when(locationSchemeServiceMock.getAllSchemeLocations).thenReturnAsync(Nil)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(Map.empty)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(Map.empty)
      when(mediaRepositoryMock.findAll).thenReturnAsync(Map.empty)
      when(reviewerAssessmentScoresRepoMock.allScores).thenReturnAsync(Map.empty)
      when(onlineTestRepositoryMock.findAllPassMarkEvaluations).thenReturnAsync(Map.empty)
      when(onlineTestRepositoryMock.findAllAssessmentCentreEvaluations).thenReturnAsync(Map.empty)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReportItem]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return populated report if applications and some data exist" in new PassMarkReportTestFixture {
      when(reportingRepoMock.passMarkReport(any())).thenReturnAsync(List(report2))
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)
      when(locationSchemeServiceMock.getAllSchemeLocations).thenReturnAsync(Nil)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(Map.empty)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(Map.empty)
      when(mediaRepositoryMock.findAll).thenReturnAsync(Map.empty)
      when(reviewerAssessmentScoresRepoMock.allScores).thenReturnAsync(Map.empty)
      when(reportingFormatterMock.getOnlineAdjustments(any[Option[Boolean]], any[Option[Adjustments]])).thenReturn(Some("Yes"))
      when(reportingFormatterMock.getAssessmentCentreAdjustments(any[Option[Boolean]], any[Option[Adjustments]])).thenReturn(Some("Yes"))
      when(onlineTestRepositoryMock.findAllPassMarkEvaluations).thenReturnAsync(Map.empty)
      when(onlineTestRepositoryMock.findAllAssessmentCentreEvaluations).thenReturnAsync(Map.empty)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReportItem]]

      status(response) mustBe OK
      result.size mustBe 1
    }

    "return populated report if applications and all data exist" in new PassMarkReportTestFixture {
      val appId = "3dee295c-6154-42b7-8353-472ae94ec980"
      val userId = UniqueIdentifier("e0b28a6e-9092-4aa2-a19e-c6a8cb13d349")
      when(reportingRepoMock.passMarkReport(any())).thenReturnAsync(List(report3))

      val contactDetails = List(ContactDetailsWithId(
        userId = userId.toString(), outsideUk = false, address = Address(line1 = "line1"), postCode = Some("AB111BB"),
        country = None, email = "joe@bloggs.com", phone = Some("07720809809")
      ))
      when(contactDetailsRepoMock.findAll).thenReturnAsync(contactDetails)
      when(locationSchemeServiceMock.getAllSchemeLocations).thenReturnAsync(List(LocationSchemesExamples.London))
      val diversityQuestions = Map(appId -> Map(
        QuestionnaireRepository.genderQuestionText -> "Male",
        QuestionnaireRepository.sexualOrientationQuestionText -> "Gay",
        QuestionnaireRepository.ethnicityQuestionText -> "Irish"
      ))
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(diversityQuestions)
      val passMarkTestResults = PassMarkReportTestResults(
        competency = Some(TestResult(tScore = Some(1.01), percentile = Some(2.02), raw = Some(3.03), sten = Some(4.04))),
        numerical = Some(TestResult(tScore = Some(1.01), percentile = Some(2.02), raw = Some(3.03), sten = Some(4.04))),
        verbal = Some(TestResult(tScore = Some(1.01), percentile = Some(2.02), raw = Some(3.03), sten = Some(4.04))),
        situational = Some(TestResult(tScore = Some(1.01), percentile = Some(2.02), raw = Some(3.03), sten = Some(4.04)))
      )

      val onlineTestReports = Map(appId -> passMarkTestResults)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(onlineTestReports)
      when(mediaRepositoryMock.findAll).thenReturnAsync(Map(userId -> "Google"))
      when(reviewerAssessmentScoresRepoMock.allScores).thenReturnAsync(Map.empty)
      when(reportingFormatterMock.getOnlineAdjustments(any[Option[Boolean]], any[Option[Adjustments]])).thenReturn(Some("Yes"))
      when(reportingFormatterMock.getAssessmentCentreAdjustments(any[Option[Boolean]], any[Option[Adjustments]])).thenReturn(Some("Yes"))
      // Schemes results deliberately in different order to the preferred schemes list
      val passMarkEvaluations = Map(appId -> List(
        SchemeEvaluationResult(scheme = model.Scheme.ProjectDelivery, result = model.EvaluationResults.Amber),
        SchemeEvaluationResult(scheme = model.Scheme.Business, result = model.EvaluationResults.Green)
      ))
      when(onlineTestRepositoryMock.findAllPassMarkEvaluations).thenReturnAsync(passMarkEvaluations)

      val assessmentCentreEvaluations = Map(appId -> List(
        SchemeEvaluationResult(scheme = model.Scheme.ProjectDelivery, result = model.EvaluationResults.Green),
        SchemeEvaluationResult(scheme = model.Scheme.Business, result = model.EvaluationResults.Red)
      ))
      when(onlineTestRepositoryMock.findAllAssessmentCentreEvaluations).thenReturnAsync(assessmentCentreEvaluations)

      when(assessmentCentreIndicatorRepoMock.calculateIndicator(any[Option[String]]))
        .thenReturn(AssessmentCentreIndicator(area = "South", assessmentCentre = "London AC"))

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReportItem]]

      status(response) mustBe OK
      result.size mustBe 1
      val passMarkReportItem = result.head
      passMarkReportItem.progress mustBe Some("assessment_scores_accepted")
      passMarkReportItem.schemes mustBe List(model.Scheme.Business, model.Scheme.ProjectDelivery)
      passMarkReportItem.locations mustBe List("London")
      passMarkReportItem.gender mustBe "Male"
      passMarkReportItem.sexualOrientation mustBe "Gay"
      passMarkReportItem.ethnicity mustBe "Irish"
      passMarkReportItem.disability mustBe Some("Yes")
      passMarkReportItem.gis mustBe Some(false)
      passMarkReportItem.onlineAdjustments mustBe Some("Yes")
      passMarkReportItem.assessmentCentreAdjustments mustBe Some("Yes")
      passMarkReportItem.civilServant mustBe Some(false)
      passMarkReportItem.socialEconomicScore mustBe "N/A"
      passMarkReportItem.hearAboutUs mustBe "Google"
      passMarkReportItem.allocatedAssessmentCentre mustBe Some("London AC")
      passMarkReportItem.testResults mustBe passMarkTestResults
      passMarkReportItem.schemeOnlineTestResults mustBe List("Green", "Amber")

      passMarkReportItem.candidateScores mustBe None
      passMarkReportItem.schemeAssessmentCentreTestResults mustBe List("Red", "Green")
    }
  }

  trait PassMarkReportTestFixture extends TestFixture {
    lazy val report1 = ApplicationForCandidateProgressReportItemExamples.PersonalDetailsCompleted
    lazy val report2 = ApplicationForCandidateProgressReportItemExamples.ReviewCompleted
    lazy val report3 = ApplicationForCandidateProgressReportItemExamples.AssessmentScoresCompleted
    lazy val reports = List(report1, report2)

    lazy val questionnaire1 = newQuestionnaire
    lazy val questionnaire2 = newQuestionnaire
    lazy val questionnaires = Map(report1.applicationId.toString -> questionnaire1, report2.applicationId.toString -> questionnaire2)

    lazy val testResults1 = newTestResults
    lazy val testResults2 = newTestResults
    lazy val testResults = Map(report1.applicationId.toString -> testResults1, report2.applicationId.toString -> testResults2)

    def newQuestionnaire =
      PassMarkReportQuestionnaireData(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
        someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"), rnd("SES"))

    def newTestResults =
      PassMarkReportTestResults(maybe(newTestResult), maybe(newTestResult), maybe(newTestResult), maybe(newTestResult))

    private def someDouble = Some(Random.nextDouble())

    def newTestResult = TestResult(someDouble, someDouble, someDouble, someDouble)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createOnlineTestPassMarkModellingReport(frameworkId).url,
        FakeHeaders(), "").withHeaders("Content-Type" -> "application/json")
    }
  }
}
