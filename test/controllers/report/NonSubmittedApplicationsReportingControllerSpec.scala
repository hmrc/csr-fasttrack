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
import mocks._
import mocks.application.{ DocumentRootInMemoryRepository, ReportingDocumentRootInMemoryRepository }
import model.Commands.Implicits._
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.ApplicationAssessmentScoresRepository
import repositories.application.ReportingRepository
import repositories.{ ApplicationAssessmentScoresRepository, LocationSchemeRepository, MediaRepository }
import repositories.application.{ GeneralApplicationRepository, ReportingRepository }
import services.reporting.SocioEconomicScoreCalculator

import scala.concurrent.Future
import scala.language.postfixOps

class NonSubmittedApplicationsReportingControllerSpec extends BaseReportingControllerSpec {
  "Reporting controller create non-submitted applications report" should {
    "return a list of non submitted applications with phone number if contact details exist" in new NonSubmittedTestFixture {
      override val controller = new ReportingController {
        override val locationSchemeService = locationSchemeServiceMock
        override val reportingFormatter = reportingFormatterMock
        override val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
        override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
        override val contactDetailsRepository = new ContactDetailsInMemoryRepository {
          override def findAll: Future[List[ContactDetailsWithId]] = {
            Future.successful(ContactDetailsWithId(
              "user1",
              Address("First Line", None, None, None), "HP18 9DN", "joe@bloggs.com", Some("123456789")
            ) :: Nil)
          }
        }
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val reportingRepository: ReportingRepository = ReportingDocumentRootInMemoryRepository
        override val testReportRepository = TestReportInMemoryRepository
        override val authProviderClient: AuthProviderClient = authProviderClientMock
        override val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
        override val locationSchemeRepository = locationSchemeRepositoryMock
        override val mediaRepository = mediaRepositoryMock
        override val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
      }
      val result = controller.createNonSubmittedApplicationsReports(frameworkId)(createNonSubmittedAppsReportRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[List[PreferencesWithContactDetails]]

      finalResult must have size 2
      val reportApp1 = finalResult.head
      reportApp1.firstName must be(Some("firstName1"))
      reportApp1.lastName must be(Some("lastName1"))
      reportApp1.preferredName must be(Some("preferredName1"))
      reportApp1.email must be(Some("email1@test.com"))
      reportApp1.location1 must be(Some("location1"))
      reportApp1.location1Scheme1 must be(Some("location1Scheme1"))
      reportApp1.location1Scheme2 must be(Some("location1Scheme2"))
      reportApp1.location2 must be(Some("location2"))
      reportApp1.location2Scheme1 must be(Some("location2Scheme1"))
      reportApp1.location2Scheme2 must be(Some("location2Scheme2"))
    }

    "return only applications based on auth provider in registered state if there is no applications created" in new NonSubmittedTestFixture {
      override val controller = new ReportingController {
        override val locationSchemeService = locationSchemeServiceMock
        override val reportingFormatter = reportingFormatterMock
        override val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
        override val assessmentScoresRepository: ApplicationAssessmentScoresRepository = ApplicationAssessmentScoresInMemoryRepository
        override val contactDetailsRepository = ContactDetailsInMemoryRepository
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val reportingRepository = new ReportingDocumentRootInMemoryRepository {
          override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
            Future.successful(Nil)
          }
        }
        override val testReportRepository = TestReportInMemoryRepository
        override val authProviderClient = authProviderClientMock
        override val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
        override val locationSchemeRepository = locationSchemeRepositoryMock
        override val mediaRepository = mediaRepositoryMock
        override val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
      }
      val result = controller.createNonSubmittedApplicationsReports(frameworkId)(createNonSubmittedAppsReportRequest(frameworkId)).run

      val finalResult = contentAsJson(result).as[List[JsValue]]

      finalResult must have size 2
      finalResult.foreach { headValue =>
        (headValue \ "progress").asOpt[String] mustBe Some("registered")
      }
    }
  }

  trait NonSubmittedTestFixture extends TestFixture {
    def createNonSubmittedAppsReportRequest(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createNonSubmittedApplicationsReports(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
