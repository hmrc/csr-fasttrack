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

import controllers.ReportingController
import model.Commands.Implicits._
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.ReportExchangeObjects._
import model.ReportExchangeObjects.Implicits._
import model.UniqueIdentifier
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import play.api.test.Helpers._
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.language.postfixOps
import scala.util.Random

class SuccessfulCandidatesReportingControllerSpec extends BaseReportingControllerSpec {
  "Successful candidates report" should {
    "return results report" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferences)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(contactDetailsList)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[ApplicationPreferencesWithTestResultsAndContactDetails]]

      status(response) mustBe OK

      result mustBe List(ApplicationPreferencesWithTestResultsAndContactDetails(applicationPreference1, contactDetails))
    }

    "return nothing if no applications exist" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(Nil)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(contactDetailsList)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[ApplicationPreferencesWithTestResultsAndContactDetails]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no contact details exist" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferences)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[ApplicationPreferencesWithTestResultsAndContactDetails]]

      status(response) mustBe OK

      result mustBe empty
    }
  }


  trait SuccessfulCandidatesReportTestFixture extends TestFixture {
    val appId = UniqueIdentifier.randomUniqueIdentifier
    val userId = UniqueIdentifier.randomUniqueIdentifier

    lazy val applicationPreference1 = newAppPreferences
    lazy val contactDetails1 = newContactDetails

    lazy val appPreferences = List(applicationPreference1)
    lazy val contactDetailsList = List(contactDetails1)
    lazy val contactDetails = newContactDetails(contactDetails1)
    lazy val summaryScores = CandidateScoresSummary(Some(10d), Some(10d), Some(10d),
      Some(10d), Some(10d), Some(10d), Some(20d), Some(80d))
    lazy val schemeEvaluations = SchemeEvaluation(Some("Pass"), Some("Fail"), Some("Amber"), Some("Pass"),
      Some("Fail"))

    private def someDouble = Some(Random.nextDouble())

    def newAppPreferences =
      ApplicationPreferencesWithTestResults(userId, appId, someRnd("location"), someRnd("location1scheme1-"),
        someRnd("location1scheme2-"), someRnd("location"), someRnd("location2scheme1-"), someRnd("location2scheme2-"),
        yesNoRnd, yesNoRnd,
        PersonalInfo(someRnd("firstname-"), someRnd("lastName-"), someRnd("preferredName-"),
          yesNoRnd, yesNoRnd, Some(new LocalDate("2001-01-01"))),
        summaryScores, schemeEvaluations)

    def newContactDetails = ContactDetailsWithId(
      userId.toString(),
      Address(rnd("Line 1"), None, None, None),
      rnd("PostCode"),
      rnd("Email"),
      someRnd("Phone")
    )

    def newContactDetails(cd: ContactDetailsWithId) = {
      ContactDetails(cd.phone, cd.email, cd.address, cd.postCode)
    }

    def request = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createSuccessfulCandidatesReport(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
