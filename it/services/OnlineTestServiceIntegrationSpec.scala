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

import _root_.services.onlinetesting.{ CubiksSanitizer, OnlineTestService }
import config.MicroserviceAppConfig._
import connectors.{ CSREmailClient, CubiksGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.ApplicationStatuses
import model.OnlineTestCommands._
import model.exchange.CubiksTestResultReady
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONDocument
//import reactivemongo.json.ImplicitBSONHandlers
//import reactivemongo.json.collection.JSONCollection
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.ImplicitBSONHandlers
import repositories._
import testkit.MongoRepositorySpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class OnlineTestServiceIntegrationSpec extends MongoRepositorySpec with MockitoSugar {
  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION
  val reportCollectionName = CollectionNames.ONLINE_TEST_REPORT

  "Online test service" should {
    "retrieve online test result" in new TestFixture {
      mongo().collection[JSONCollection](reportCollectionName).drop().futureValue
      createApplication("appId", "userId", "frameworkId", ApplicationStatuses.OnlineTestCompleted).futureValue
      service.tryToDownloadOnlineTestResult(1, CubiksTestResultReady(Some(384), Some(1), "Ready", None)).futureValue
      val reportOpt = service.trRepository.getReportByApplicationId("appId").futureValue
      reportOpt mustNot be (empty)

      val report = reportOpt.get
      report.numerical mustBe Some(TestResult("Completed", "numerical norm", Some(30.1d), Some(30.2d), Some(30.3d), Some(30.4d)))
      report.verbal mustBe Some(TestResult("Completed", "verbal norm", Some(40.1d), Some(40.2d), Some(40.3d), Some(40.4d)))
      report.situational mustBe Some(TestResult("Completed", "situational norm", Some(50.1d), Some(50.2d), Some(50.3d), Some(50.4d)))
      report.competency mustBe Some(TestResult("Completed", "competency norm", Some(20.1d), Some(20.2d), Some(20.3d), Some(20.4d)))
      report.reportType mustBe "XML"

      verify(auditMock).logEventNoRequest("OnlineTestXmlReportSaved", Map("applicationId" -> "appId"))
    }
  }

  trait TestFixture {
    val auditMock = mock[AuditService]
    val cubiksGatewayClientMock = mock[CubiksGatewayClient]
    when(cubiksGatewayClientMock.getReport(any[OnlineTestApplicationForReportRetrieving])(any[HeaderCarrier]))
      .thenReturn(Future.successful(OnlineTestReportAvailability(1, available = false)))
    when(cubiksGatewayClientMock.downloadXmlReport(any[Int])(any[HeaderCarrier]))
      .thenReturn(Future.successful {
        val VerbalTestName = "Logiks Verbal and Numerical (Intermediate) - Verbal"
        val NumericalTestName = "Logiks Verbal and Numerical (Intermediate) - Numerical"
        val CompetencyTestName = "Cubiks Factors"
        val SituationalTestName = "Civil Service Fast Track Apprentice SJQ"

        Map(
          CompetencyTestName -> TestResult("Completed", "competency norm", Some(20.1d), Some(20.2d), Some(20.3d), Some(20.4d)),
          NumericalTestName -> TestResult("Completed", "numerical norm", Some(30.1d), Some(30.2d), Some(30.3d), Some(30.4d)),
          VerbalTestName -> TestResult("Completed", "verbal norm", Some(40.1d), Some(40.2d), Some(40.3d), Some(40.4d)),
          SituationalTestName -> TestResult("Completed", "situational norm", Some(50.1d), Some(50.2d), Some(50.3d), Some(50.4d))
        )}
      )

    val service = new OnlineTestService {
      val appRepository = applicationRepository
      val cdRepository = contactDetailsRepository
      val otRepository = onlineTestRepository
      val otprRepository = onlineTestPDFReportRepository
      val trRepository = new TestReportMongoRepository()
      val adRepository = assistanceDetailsRepository
      val cubiksGatewayClient = cubiksGatewayClientMock
      val cubiksSanitizer = CubiksSanitizer
      val tokenFactory = UUIDFactory
      val onlineTestInvitationDateFactory = DateTimeFactory
      val emailClient = CSREmailClient
      val auditService = auditMock
      val gatewayConfig = cubiksGatewayConfig
    }

    def createApplication(appId: String, userId: String, frameworkId: String, appStatus: ApplicationStatuses.EnumVal) = {
      applicationRepository.collection.insert(BSONDocument(
        "userId" -> userId,
        "frameworkId" -> frameworkId,
        "applicationId" -> appId,
        "applicationStatus" -> appStatus,
        "personal-details" -> BSONDocument("preferredName" -> "Test Preferred Name"),
        "assistance-details" -> BSONDocument(
          "hasDisability" -> "No",
          "needsSupportForOnlineAssessment" -> false,
          "needsSupportAtVenue" -> false
        ),
        "online-tests" -> BSONDocument(
          "cubiksUserId" -> 1
        )
      ))
    }

  }
}
