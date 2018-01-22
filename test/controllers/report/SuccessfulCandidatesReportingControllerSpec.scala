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

package controllers.report

import model.Commands.Implicits._
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.ReportExchangeObjects.{ SuccessfulCandidatesReportItem, _ }
import model.ReportExchangeObjects.Implicits._
import model.exchange.LocationSchemesExamples
import model.UniqueIdentifier
import org.joda.time.LocalDate
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import testkit.MockitoImplicits.OngoingStubbingExtension

import scala.concurrent.Future
import scala.language.postfixOps

class SuccessfulCandidatesReportingControllerSpec extends BaseReportingControllerSpec {
  "Successful candidates report" should {
    "return results report" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferencesList)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(allContactDetailsList)
      when(controller.locationSchemeService.getAllSchemeLocations).thenReturnAsync(allLocationSchemes)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[SuccessfulCandidatesReportItem]]

      status(response) mustBe OK

      result mustBe List(
        SuccessfulCandidatesReportItem(expectedApplicationPreference1, contactDetails1),
        SuccessfulCandidatesReportItem(expectedApplicationPreference2, contactDetails2)
      )
    }

    "return results report without locations if locations cannot be found" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferencesList)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(allContactDetailsList)
      when(controller.locationSchemeService.getAllSchemeLocations).thenReturnAsync(Nil)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[SuccessfulCandidatesReportItem]]

      status(response) mustBe OK

      result mustBe List(
        SuccessfulCandidatesReportItem(expectedApplicationPreference1.copy(locations = Nil), contactDetails1),
        SuccessfulCandidatesReportItem(expectedApplicationPreference2.copy(locations = Nil), contactDetails2)
      )
    }

    "return nothing if no applications exist" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(Nil)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(allContactDetailsList)
      when(controller.locationSchemeService.getAllSchemeLocations).thenReturnAsync(allLocationSchemes)

      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[SuccessfulCandidatesReportItem]]

      status(response) mustBe OK

      result mustBe empty
    }

    "return nothing if no contact details exist" in new SuccessfulCandidatesReportTestFixture {
      when(reportingRepoMock.applicationsPassedInAssessmentCentre(any())).thenReturnAsync(appPreferencesList)
      when(contactDetailsRepoMock.findAll).thenReturnAsync(Nil)
      when(controller.locationSchemeService.getAllSchemeLocations).thenReturnAsync(allLocationSchemes)


      val response = controller.createSuccessfulCandidatesReport(frameworkId)(request).run
      val result = contentAsJson(response).as[List[SuccessfulCandidatesReportItem]]

      status(response) mustBe OK

      result mustBe empty
    }
  }

  trait SuccessfulCandidatesReportTestFixture extends TestFixture {
    val appId1 = UniqueIdentifier.randomUniqueIdentifier
    val appId2 = UniqueIdentifier.randomUniqueIdentifier
    val userId1 = UniqueIdentifier.randomUniqueIdentifier
    val userId2 = UniqueIdentifier.randomUniqueIdentifier

    lazy val applicationPreference1 = newAppPreferences(userId1, appId1)
    lazy val applicationPreference2 = newAppPreferences(userId2, appId2).copy(locations = List(LocationSchemesExamples.Manchester.id))
    lazy val expectedApplicationPreference1 = applicationPreference1.copy(locations = List(LocationSchemesExamples.London.locationName))
    lazy val expectedApplicationPreference2 = applicationPreference2.copy(locations = List(LocationSchemesExamples.Manchester.locationName))
    lazy val contactDetailsWithId1 = newContactDetailsWithId(userId1)
    lazy val contactDetailsWithId2 = newContactDetailsWithId(userId2)
    lazy val appPreferencesList = List(applicationPreference1, applicationPreference2)
    lazy val contactDetails1 = newContactDetails(contactDetailsWithId1)
    lazy val contactDetails2 = newContactDetails(contactDetailsWithId2)

    lazy val allContactDetailsList = List(contactDetailsWithId1, contactDetailsWithId2)
    lazy val allLocationSchemes = LocationSchemesExamples.LocationsSchemesList
    lazy val summaryScores = CandidateScoresSummary(Some(10d), Some(10d), Some(10d),
      Some(10d), Some(10d), Some(10d), Some(20d), Some(80d))
    lazy val schemeEvaluationsLondon = SchemeEvaluation(Some("Pass"), None, Some("Amber"), Some("Pass"),
      None)
    lazy val overallEvaluationsLondon = SchemeEvaluation(Some("Green"), None, Some("Amber"), Some("Green"),
      None)

    def newAppPreferences(userId: UniqueIdentifier, appId: UniqueIdentifier) =
      ApplicationPreferencesWithTestResults(userId, appId,
        LocationSchemesExamples.schemesLondon,
        List(LocationSchemesExamples.London.id),
        PersonalInfo(someRnd("firstname-"), someRnd("lastName-"), someRnd("preferredName-"),
          yesNoRnd, yesNoRnd, Some(new LocalDate("2001-01-01"))),
        summaryScores, schemeEvaluationsLondon, overallEvaluationsLondon)

    def newContactDetailsWithId(userId: UniqueIdentifier) = ContactDetailsWithId(
      userId.toString(),
      false,
      Address(rnd("Line 1"), None, None, None),
      Some(rnd("PostCode")),
      None,
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
