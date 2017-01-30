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
import model.PersistedObjects.ContactDetailsWithId
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import play.api.test.Helpers._
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, DiversityReportRepository, QuestionnaireRepository, TestReportRepository }
import repositories.application.GeneralApplicationRepository
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps

class AssessmentCentreAllocationReportingControllerSpec extends BaseReportingControllerSpec {
  "Assessment centre allocation report" should {
    "return nothing if no applications exist" in new AssessmentCentreAllocationReportTestFixture {
      when(reportingRepoMock.candidatesAwaitingAllocation(any())).thenReturnAsync(Nil)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return nothing if applications exist, but no contact details" in new AssessmentCentreAllocationReportTestFixture {
      when(reportingRepoMock.candidatesAwaitingAllocation(any())).thenReturnAsync(candidates)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe empty
    }

    "return applications with contact details" in new AssessmentCentreAllocationReportTestFixture {
      when(reportingRepoMock.candidatesAwaitingAllocation(any())).thenReturnAsync(candidates)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(contactDetails)

      val response = controller.createAssessmentCentreAllocationReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[AssessmentCentreAllocationReport]]

      status(response) mustBe OK
      result mustBe List(
        toReport(candidate1, contactDetails1),
        toReport(candidate2, contactDetails2)
      )

      def toReport(candidate: CandidateAwaitingAllocation, contact: ContactDetailsWithId) = {
        import candidate._
        import contact._
        AssessmentCentreAllocationReport(firstName, lastName, preferredName, email, phone.getOrElse(""),
          preferredLocation1, adjustments, dateOfBirth)
      }

    }
  }

  trait AssessmentCentreAllocationReportTestFixture extends TestFixture {
    lazy val candidate1 = newCandidate
    lazy val candidate2 = newCandidate
    lazy val candidates = List(candidate1, candidate2)

    lazy val contactDetails1 = newContactDetails(candidate1.userId)
    lazy val contactDetails2 = newContactDetails(candidate2.userId)
    lazy val contactDetails = List(contactDetails1, contactDetails2)

    def newCandidate = CandidateAwaitingAllocation(
      rnd("UserId"),
      rnd("FirstName"),
      rnd("LastName"),
      rnd("PreferredName"),
      rnd("PrefLocation1"),
      someRnd("Adjustments"),
      new LocalDate(2000, 1, 1)
    )

    def newContactDetails(id: String) = ContactDetailsWithId(
      id,
      Address(rnd("Line 1"), None, None, None),
      rnd("PostCode"),
      rnd("Email"),
      someRnd("Phone")
    )

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAssessmentCentreAllocationReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }

}
