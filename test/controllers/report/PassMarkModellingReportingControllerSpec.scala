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
import model.Commands.Implicits._
import model.Commands._
import model.OnlineTestCommands.TestResult
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import play.api.test.Helpers._
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, DiversityReportRepository, QuestionnaireRepository, TestReportRepository }
import repositories.application.GeneralApplicationRepository
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps
import scala.util.Random

class PassMarkModellingReportingControllerSpec extends BaseReportingControllerSpec {
  "Pass mark modelling report" should {
    "return nothing if no applications exist" in new PassMarkReportTestFixture {
      when(reportingRepoMock.overallReportNotWithdrawn(any())).thenReturnAsync(Nil)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(Map.empty)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(Map.empty)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications exist, but no questionnaires" in new PassMarkReportTestFixture {
      when(reportingRepoMock.overallReportNotWithdrawn(any())).thenReturnAsync(reports)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(Map.empty)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(Map.empty)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications and questionnaires exist, but no test results" in new PassMarkReportTestFixture {
      when(reportingRepoMock.overallReportNotWithdrawn(any())).thenReturnAsync(reports)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(questionnaires)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(Map.empty)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications with questionnaire and test results" in new PassMarkReportTestFixture {
      when(reportingRepoMock.overallReportNotWithdrawn(any())).thenReturnAsync(reports)
      when(questionnaireRepoMock.passMarkReport).thenReturnAsync(questionnaires)
      when(testReportRepoMock.getOnlineTestReports).thenReturnAsync(testResults)

      val response = controller.createOnlineTestPassMarkModellingReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[PassMarkReport]]

      status(response) mustBe OK
      result mustBe List(
        PassMarkReport(report1, questionnaire1, testResults1),
        PassMarkReport(report2, questionnaire2, testResults2)
      )
    }
  }

  trait PassMarkReportTestFixture extends TestFixture {
    lazy val report1 = newReport
    lazy val report2 = newReport
    lazy val reports = List(report1, report2)

    lazy val questionnaire1 = newQuestionnaire
    lazy val questionnaire2 = newQuestionnaire
    lazy val questionnaires = Map(report1.applicationId -> questionnaire1, report2.applicationId -> questionnaire2)

    lazy val testResults1 = newTestResults
    lazy val testResults2 = newTestResults
    lazy val testResults = Map(report1.applicationId -> testResults1, report2.applicationId -> testResults2)

    def newReport =
      Report(rnd("AppId"), Some("ONLINE_TEST_COMPLETE"), someRnd("Location"), someRnd("Scheme"), maybeRnd("Scheme"),
        maybeRnd("Location"), maybeRnd("Scheme"), maybeRnd("Scheme"),
        yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, yesNoRnd, Some("issue"))

    def newQuestionnaire =
      PassMarkReportQuestionnaireData(someRnd("Gender"), someRnd("Orientation"), someRnd("Ethnicity"),
        someRnd("EmploymentStatus"), someRnd("Occupation"), someRnd("(Self)Employed"), someRnd("CompanySize"), rnd("SES"))

    def newTestResults =
      PassMarkReportTestResults(maybe(newTestResult), maybe(newTestResult), maybe(newTestResult), maybe(newTestResult))

    private def someDouble = Some(Random.nextDouble())

    def newTestResult = TestResult("Completed", "Example Norm", someDouble, someDouble, someDouble, someDouble)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createOnlineTestPassMarkModellingReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }

}
