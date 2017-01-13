/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import _root_.services.onlinetesting.OnlineTestRetrievePDFReportService
import config.MicroserviceAppConfig._
import connectors.CubiksGatewayClient
import model.OnlineTestCommands._
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers._
import reactivemongo.json.collection.JSONCollection
import repositories._
import repositories.application.GeneralApplicationMongoRepository
import testkit.IntegrationSpec
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestRetrievePDFReportServiceIntegrationSpec extends IntegrationSpec with MockitoSugar with OneAppPerSuite {

  private implicit def db: () => DefaultDB = {
    MongoDbConnection.mongoConnector.db
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  "Online test retrieve pdf report service" should {
    "retrieve online test pdf report and audit 'OnlineTestPDFReportSaved' " in new TestFixture {
      clearDatabase()
      when(gatewayClientMock.getReport(any[OnlineTestApplicationForReportRetrieving])(any[HeaderCarrier]))
        .thenReturn(Future.successful(OnlineTestReportAvailability(pdfReportId, true)))
      when(gatewayClientMock.downloadPdfReport(eqTo(pdfReportId))(any[HeaderCarrier])).thenReturn(Future.successful(pdfReport))
      val application = mock[OnlineTestApplicationWithCubiksUser]
      when(application.applicationId).thenReturn("appId")
      when(application.userId).thenReturn("userId")

      createApplication("appId", "userId", "frameworkId", "CREATED")
      service.retrievePDFReport(application, waitSecs = Some(0)).futureValue

      val reportOpt = service.otReportPDFRepository.get("appId").futureValue

      reportOpt must not be None
      val report = reportOpt.get

      report mustBe pdfReport

      verify(auditMock).logEventNoRequest("OnlineTestPDFReportSaved", Map("userId" -> "userId"))
    }
  }

  trait TestFixture {
    val auditMock = mock[AuditService]
    val gatewayClientMock = mock[CubiksGatewayClient]

    val pdfReportId = 2
    val pdfReport = Array[Byte](0x20, 0x21)

    lazy val service = new OnlineTestRetrievePDFReportService {
      val auditService = auditMock
      val appRepository = applicationRepository
      val otRepository = onlineTestRepository
      val otReportPDFRepository = onlineTestPDFReportRepository
      val cubiksGatewayClient = gatewayClientMock
      val gatewayConfig = cubiksGatewayConfig
    }

    lazy val failingService = new OnlineTestRetrievePDFReportService {
      val auditService = auditMock
      val appRepository = applicationRepository
      val otRepository = onlineTestRepository
      val otReportPDFRepository = onlineTestPDFReportRepository
      val cubiksGatewayClient = gatewayClientMock
      val gatewayConfig = cubiksGatewayConfig
    }

    def helperRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)

    def createApplication(appId: String, userId: String, frameworkId: String, appStatus: String) = {
      helperRepo.collection.insert(BSONDocument(
        "userId" -> userId,
        "frameworkId" -> frameworkId,
        "applicationId" -> appId,
        "applicationStatus" -> appStatus,
        "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
        "assistance-details" -> BSONDocument(
          "hasDisability" -> "No",
          "needsSupportForOnlineAssessment" -> false,
          "needsSupportAtVenue" -> false
        )
      )).futureValue
    }

    def clearDatabase() = {
      val reportCollection = db().collection[JSONCollection]("online-test-pdf-report")
      reportCollection.remove(Json.obj()).futureValue
      val collection = db().collection[JSONCollection]("application")
      collection.remove(Json.obj()).futureValue
    }
  }
}
