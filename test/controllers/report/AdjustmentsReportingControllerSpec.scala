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
import mocks.application.ReportingDocumentRootInMemoryRepository
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.report.AdjustmentReportItem
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.libs.json.{ JsArray, JsValue }
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository, ReportingRepository }
import repositories._
import services.reporting.SocioEconomicScoreCalculator

import scala.concurrent.Future
import scala.language.postfixOps

class AdjustmentsReportingControllerSpec extends BaseReportingControllerSpec {

  "Reporting controller create adjustment report" should {
    "return the adjustment report when we execute adjustment reports" in new AdjustmentsTestFixture {
      override val controller = new ReportingController {
        override val reportingFormatter = reportingFormatterMock
        override val locationSchemeService = locationSchemeServiceMock
        override val authProviderClient: AuthProviderClient = authProviderClientMock
        override val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
        override val assessorAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val reviewerAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val contactDetailsRepository = contactDetailsRepoMock
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val reportingRepository: ReportingRepository = ReportingDocumentRootInMemoryRepository
        override val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
        override val testReportRepository = TestReportInMemoryRepository
        override val locationSchemeRepository = mock[LocationSchemeRepository]
        override val mediaRepository = mock[MediaRepository]
        override val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
        override val onlineTestRepository = mock[OnlineTestRepository]
        override val assessmentCentreAllocationRepository = mock[AssessmentCentreAllocationRepository]
      }
      when(contactDetailsRepoMock.findAll).thenReturn(
        Future.successful(List(
          ContactDetailsWithId("1", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None),
          ContactDetailsWithId("2", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None),
          ContactDetailsWithId("3", outsideUk = false, Address("First Line", None, None, None), Some("HP18 9DN"),
            None, "joe@bloggs.com", None)
        ))
      )
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]

      finalResult.size mustBe 3
      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe Some("joe@bloggs.com")
    }

    "return the adjustment report without contact details data" in new AdjustmentsTestFixture {
      override val controller = new ReportingController {
        override val reportingFormatter = reportingFormatterMock
        override val locationSchemeService = locationSchemeServiceMock
        override val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
        override val assessorAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val reviewerAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val contactDetailsRepository = contactDetailsRepoMock
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val reportingRepository: ReportingRepository = ReportingDocumentRootInMemoryRepository
        override val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
        override val testReportRepository = TestReportInMemoryRepository
        override val authProviderClient: AuthProviderClient = authProviderClientMock
        override val locationSchemeRepository = mock[LocationSchemeRepository]
        override val mediaRepository = mock[MediaRepository]
        override val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
        override val onlineTestRepository = mock[OnlineTestRepository]
        override val assessmentCentreAllocationRepository = mock[AssessmentCentreAllocationRepository]
      }
      when(contactDetailsRepoMock.findAll).thenReturn(Future.successful(Nil))
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult.size mustBe 3

      val headValue = finalResult.head
      (headValue \ "email").asOpt[String] mustBe None
      (headValue \ "telephone").asOpt[String] mustBe None
    }

    "return no adjustments if there's no data on the server" in new AdjustmentsTestFixture {
      override val controller = new ReportingController {
        override val reportingFormatter = reportingFormatterMock
        override val locationSchemeService = locationSchemeServiceMock
        override val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
        override val assessorAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val reviewerAssessmentScoresRepository: ApplicationAssessmentScoresRepository = mock[ApplicationAssessmentScoresRepository]
        override val contactDetailsRepository = contactDetailsRepoMock
        override val questionnaireRepository = QuestionnaireInMemoryRepository
        override val reportingRepository = new ReportingDocumentRootInMemoryRepository {
          override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {
            Future.successful(List.empty[AdjustmentReportItem])
          }
        }
        override val testReportRepository = TestReportInMemoryRepository
        override val authProviderClient: AuthProviderClient = authProviderClientMock
        override val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
        override val locationSchemeRepository = mock[LocationSchemeRepository]
        override val mediaRepository = mock[MediaRepository]
        override val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
        override val onlineTestRepository = mock[OnlineTestRepository]
        override val assessmentCentreAllocationRepository = mock[AssessmentCentreAllocationRepository]
      }
      when(contactDetailsRepoMock.findAll).thenReturn(Future.successful(Nil))
      val result = controller.createAdjustmentReports(frameworkId)(createAdjustmentsReport(frameworkId)).run

      val finalResult = contentAsJson(result).as[JsArray].value

      finalResult mustBe a[Seq[_]]
      finalResult mustBe empty
    }
  }

  trait AdjustmentsTestFixture extends TestFixture {
    def createAdjustmentsReport(frameworkId: String) = {
      FakeRequest(Helpers.GET, controllers.routes.ReportingController.createAdjustmentReports(frameworkId).url, FakeHeaders(), "")
        .withHeaders("Content-Type" -> "application/json")
    }
  }
}
