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

import model.Commands.Implicits._
import model.ReportExchangeObjects.{ CandidateProgressReportItem2, _ }
import model.ReportExchangeObjects.Implicits._
import model.{ AssessmentCentreIndicator, UniqueIdentifier }
import model.exchange.{ ApplicationForCandidateProgressReportItemExamples, CandidateProgressReportItem2Examples, LocationSchemesExamples }
import model.persisted.ContactDetailsWithIdExamples
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import play.api.test.Helpers._
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps

class CandidateProgressReportingControllerSpec extends BaseReportingControllerSpec {
  "Candidate progress report" should {
    "return empty list when there are no applications" in new CandidateProgressReportTestFixture {
      when(reportingRepoMock.applicationsForCandidateProgressReport(eqTo(frameworkId))).thenReturnAsync(Nil)

      val response = controller.createCandidateProgressReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[CandidateProgressReportItem2]]

      status(response) mustBe OK

      result mustBe List.empty
    }

    "return report if there are applications, contact details and location schemes" in new CandidateProgressReportTestFixture {
      val response = controller.createCandidateProgressReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[CandidateProgressReportItem2]]

      status(response) mustBe OK

      result mustBe CandidateProgressReportItem2Examples.Candidates
    }

    "return report without fsac indicator if there are applications, location schemes" +
      " but no contact details" in new CandidateProgressReportTestFixture {
      when(contactDetailsRepoMock.findAll).thenReturnAsync(List.empty)

      val response = controller.createCandidateProgressReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[CandidateProgressReportItem2]]

      status(response) mustBe OK

      result mustBe CandidateProgressReportItem2Examples.CandidatesWithoutFsac
    }

    "return report without location name if there are applications, contact details" +
      " but no location schemes" in new CandidateProgressReportTestFixture {
      when(locationSchemeServiceMock.getAllSchemeLocations).thenReturnAsync(List.empty)

      val response = controller.createCandidateProgressReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[CandidateProgressReportItem2]]

      status(response) mustBe OK

      result mustBe CandidateProgressReportItem2Examples.CandidatesWithoutLocationNames
    }
  }

  trait CandidateProgressReportTestFixture extends TestFixture {
    val appId = UniqueIdentifier.randomUniqueIdentifier
    val userId = UniqueIdentifier.randomUniqueIdentifier

    when(reportingRepoMock.applicationsForCandidateProgressReport(eqTo(frameworkId))).
      thenReturnAsync(ApplicationForCandidateProgressReportItemExamples.Applications)
    when(contactDetailsRepoMock.findAll).thenReturnAsync(ContactDetailsWithIdExamples.ContactDetailsList)
    when(locationSchemeServiceMock.getAllSchemeLocations).thenReturnAsync(LocationSchemesExamples.LocationsSchemesList)
    when(assessmentCentreIndicatorRepoMock.calculateIndicator(any())).thenReturn(AssessmentCentreIndicator("London", "London"))
    when(reportingFormatterMock.getOnlineAdjustments(eqTo(Some(true)), any())).thenReturn(Some("Yes"))
    when(reportingFormatterMock.getOnlineAdjustments(eqTo(Some(false)), any())).thenReturn(Some("No"))
    when(reportingFormatterMock.getOnlineAdjustments(eqTo(None), any())).thenReturn(None)
    when(reportingFormatterMock.getAssessmentCentreAdjustments(eqTo(Some(true)), any())).thenReturn(Some("Yes"))
    when(reportingFormatterMock.getAssessmentCentreAdjustments(eqTo(Some(false)), any())).thenReturn(Some("No"))
    when(reportingFormatterMock.getAssessmentCentreAdjustments(eqTo(None), any())).thenReturn(None)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createCandidateProgressReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
