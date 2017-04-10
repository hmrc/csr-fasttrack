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

import model.exchange.AssessmentScoresReportExamples._
import model.report.AssessmentCentreScoresReportItem
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import testkit.MockitoImplicits.OngoingStubbingExtension
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.language.postfixOps

class AssessmentScoresReportingControllerSpec extends BaseReportingControllerSpec {
  "Candidate progress report" should {
    "return empty report when no data is are returned" in new AssessmentScoresReportTestFixture {
      when(reportingRepoMock.applicationsForAssessmentScoresReport(eqTo(frameworkId))).thenReturnAsync(Nil)
      when(assessmentCentreAllocationRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)
      when(assessorAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)
      when(reviewerAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)
      when(authProviderClientMock.findByUserIds(any[List[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreScoresReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreScoresReportItem]]

      status(response) mustBe OK

      result mustBe List.empty
    }

    "return report with accepted statuses when the candidate has been reviewed" in new AssessmentScoresReportTestFixture {
      when(assessorAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(
        assessorSavedAndSubmittedScoresAndFeedbackForCandidate)

      // Presence of the reviewer data means the candidate was accepted
      when(reviewerAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(
        reviewerInterviewScoresAndFeedbackForCandidate)

      when(authProviderClientMock.findByUserIds(any[List[String]])(any[HeaderCarrier])).thenReturnAsync(authProviderAssessors)

      val response = controller.createAssessmentCentreScoresReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreScoresReportItem]]

      status(response) mustBe OK

      val expectedData = List(AssessmentCentreScoresReportItem(
        assessmentCentreLocation = Some("London"),
        assessmentCentreVenue = Some("London FSAC"),
        assessmentCentreDate = Some("2017-04-03"),
        amOrPm = Some("PM"),
        candidateName = Some("Joe Bloggs"),
        interview = Some("Accepted"),
        interviewAssessor = Some("John Doe"),
        groupExercise = Some("Accepted"),
        groupExerciseAssessor = Some("Jane Doe"),
        writtenExercise = Some("Accepted"),
        writtenExerciseAssessor = Some("Jenny Jackson")
      ))
      result mustBe expectedData
    }

    "return report with submitted statuses when the candidate has been assessed and submitted " +
      "but not reviewed" in new AssessmentScoresReportTestFixture {
      when(assessorAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(
        assessorSavedAndSubmittedScoresAndFeedbackForCandidate)

      // Absence of reviewer data means the candidate's statuses are determined by assessor data
      when(reviewerAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)

      when(authProviderClientMock.findByUserIds(any[List[String]])(any[HeaderCarrier])).thenReturnAsync(authProviderAssessors)

      val response = controller.createAssessmentCentreScoresReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreScoresReportItem]]

      status(response) mustBe OK

      val expectedData = List(AssessmentCentreScoresReportItem(
        assessmentCentreLocation = Some("London"),
        assessmentCentreVenue = Some("London FSAC"),
        assessmentCentreDate = Some("2017-04-03"),
        amOrPm = Some("PM"),
        candidateName = Some("Joe Bloggs"),
        interview = Some("Submitted"),
        interviewAssessor = Some("John Doe"),
        groupExercise = Some("Submitted"),
        groupExerciseAssessor = Some("Jane Doe"),
        writtenExercise = Some("Submitted"),
        writtenExerciseAssessor = Some("Jenny Jackson")
      ))
      result mustBe expectedData
    }

    "return report with saved statuses when the candidate has been assessed and saved but not submitted " +
      "and not reviewed" in new AssessmentScoresReportTestFixture {
      when(assessorAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(
        assessorSavedScoresAndFeedbackForCandidate)

      // Absence of reviewer data means the candidate's statuses are determined by assessor data
      when(reviewerAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)

      when(authProviderClientMock.findByUserIds(any[List[String]])(any[HeaderCarrier])).thenReturnAsync(authProviderAssessors)

      val response = controller.createAssessmentCentreScoresReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreScoresReportItem]]

      status(response) mustBe OK

      val expectedData = List(AssessmentCentreScoresReportItem(
        assessmentCentreLocation = Some("London"),
        assessmentCentreVenue = Some("London FSAC"),
        assessmentCentreDate = Some("2017-04-03"),
        amOrPm = Some("PM"),
        candidateName = Some("Joe Bloggs"),
        interview = Some("Saved"),
        interviewAssessor = Some("John Doe"),
        groupExercise = Some("Saved"),
        groupExerciseAssessor = Some("Jane Doe"),
        writtenExercise = Some("Saved"),
        writtenExerciseAssessor = Some("Jenny Jackson")
      ))
      result mustBe expectedData
    }

    "return report with not entered statuses when the candidate has not been assessed" in new AssessmentScoresReportTestFixture {
      when(assessorAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)
      when(reviewerAssessmentScoresRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(Nil)
      when(authProviderClientMock.findByUserIds(any[List[String]])(any[HeaderCarrier])).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreScoresReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreScoresReportItem]]

      status(response) mustBe OK

      val expectedData = List(AssessmentCentreScoresReportItem(
        assessmentCentreLocation = Some("London"),
        assessmentCentreVenue = Some("London FSAC"),
        assessmentCentreDate = Some("2017-04-03"),
        amOrPm = Some("PM"),
        candidateName = Some("Joe Bloggs"),
        interview = Some("Not entered"),
        interviewAssessor = None,
        groupExercise = Some("Not entered"),
        groupExerciseAssessor = None,
        writtenExercise = Some("Not entered"),
        writtenExerciseAssessor = None
      ))
      result mustBe expectedData
    }
  }

  trait AssessmentScoresReportTestFixture extends TestFixture {
    when(reportingRepoMock.applicationsForAssessmentScoresReport(eqTo(frameworkId))).thenReturnAsync(applicationForAssessmentScoresReport)
    when(assessmentCentreAllocationRepoMock.findByApplicationIds(any[List[String]])).thenReturnAsync(assessmentCentreAllocations)

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAssessmentCentreScoresReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
