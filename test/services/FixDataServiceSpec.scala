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

package services

import config.DataFixupConfig
import connectors.EmailClient
import connectors.PassMarkExchangeObjects.OnlineTestPassmarkSettings
import model.Commands.ProgressResponse
import model.Exceptions.PassMarkSettingsNotFound
import model.OnlineTestCommands.OnlineTestApplication
import model.commands.OnlineTestProgressResponse
import model.{ ApplicationStatuses, EmptyRequestHeader, Scheme }
import model.persisted.SchemeEvaluationResult
import org.joda.time.DateTime
import play.api.test.Helpers._
import repositories.{ AssessmentCentreAllocationMongoRepository, AssessorApplicationAssessmentScoresMongoRepository, ContactDetailsRepository, OnlineTestPassMarkSettingsRepository }
import repositories.application.{ FlagCandidateRepository, GeneralApplicationRepository, OnlineTestRepository }
import testkit.{ MockitoSugar, UnitSpec }
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.mvc.RequestHeader
import org.mockito.Matchers.{ eq => eqTo, _ }
import services.onlinetesting.OnlineTestExtensionService

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class FixDataServiceSpec extends UnitSpec {

  "Progress to assessment centre" must {

    "throw an exception if the app Id does not match the configured one" in new TestFixture {
      val actual = service.progressToAssessmentCentre("blah").failed.futureValue
      actual mustBe an[IllegalArgumentException]
    }

    "throw an exception if the candidate has already moved to assessment centre allocation" in new TestFixture {
      when(mockAppRepo.findProgress(any[String])).thenReturn(Future.successful(
        ProgressResponse("appId").copy(
          onlineTest = OnlineTestProgressResponse(awaitingAllocation = true, awaitingAllocationNotified = true
        ))
      ))
    }

    "progress a candidate to awaiting assessment centre allocation" in new TestFixture {
      when(mockPassMarkSettingsRepo.tryGetLatestVersion()).thenReturn(
        Future.successful(Some(OnlineTestPassmarkSettings(Nil, "version", DateTime.now, "user")))
      )
      when(mockAppRepo.getSchemes(any[String])).thenReturn(Future.successful(
        List(Scheme.Finance, Scheme.Business, Scheme.DigitalAndTechnology)
      ))
      when(mockAppRepo.progressToAssessmentCentre(any[String], any[List[SchemeEvaluationResult]], any[String]))
        .thenReturn(Future.successful(()))

      val actual = service.progressToAssessmentCentre("appId").futureValue

      verify(mockAuditService).logEvent(any[String], any[Map[String, String]])(any[HeaderCarrier], any[RequestHeader])

      actual mustBe unit
    }

    "throw an exception if pass marks have not been set" in new TestFixture {
      when(mockPassMarkSettingsRepo.tryGetLatestVersion()).thenReturn(
        Future.successful(None)
      )
      when(mockAppRepo.getSchemes(any[String])).thenReturn(Future.successful(
        List(Scheme.Finance, Scheme.Business, Scheme.DigitalAndTechnology)
      ))
      when(mockAppRepo.updateStatus(any[String], any[ApplicationStatuses.EnumVal])).thenReturn(Future.successful(()))

      val actual = service.progressToAssessmentCentre("appId").failed.futureValue
      actual mustBe a[PassMarkSettingsNotFound]
      verify(mockAuditService, times(0)).logEvent(any[String], any[Map[String, String]])(any[HeaderCarrier], any[RequestHeader])
    }
  }

  "Extend expired online tests" must {
    "Return Ok when a valid application is extended" in new TestFixture {
      when(mockOnlineTestRepository.getOnlineTestApplication(any[String]())).thenReturn(Future.successful(Some(
        OnlineTestApplication(
          "appId1",
          ApplicationStatuses.OnlineTestExpired,
          "userId1",
          guaranteedInterview = false,
          needsAdjustments = false,
          "Pref1",
          None
        )
      )))

      when(mockOnlineTestExtensionService.extendExpiryTimeForExpiredTests(any(), any())).thenReturn(Future.successful(unit))

      val result = service.extendExpiredOnlineTests("appId1", 7)

      status(result) mustBe OK

      verify(mockOnlineTestExtensionService, times(1)).extendExpiryTimeForExpiredTests(any(), eqTo(7))
    }

    "Return NotFound when an application is invalid" in new TestFixture {
      when(mockOnlineTestRepository.getOnlineTestApplication(any[String]())).thenReturn(Future.successful(None))

      val result = service.extendExpiredOnlineTests("appId1", 7)

      status(result) mustBe NOT_FOUND

      verify(mockOnlineTestExtensionService, times(0)).extendExpiryTimeForExpiredTests(any(), eqTo(7))
    }
  }

  trait TestFixture {
    implicit val hc = HeaderCarrier()
    implicit val rh = EmptyRequestHeader
    val mockAppRepo = mock[GeneralApplicationRepository]
    val mockPassMarkSettingsRepo = mock[OnlineTestPassMarkSettingsRepository]
    val mockAuditService = mock[AuditService]
    val mockConfig = mock[DataFixupConfig]
    val mockAssessorApplicationAssessmentScoresMongoRepository = mock[AssessorApplicationAssessmentScoresMongoRepository]
    val mockAssessmentCentreAllocationMongoRepository = mock[AssessmentCentreAllocationMongoRepository]
    val mockOnlineTestRepository = mock[OnlineTestRepository]
    val mockOnlineTestExtensionService = mock[OnlineTestExtensionService]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]
    val mockEmailClient = mock[EmailClient]
    val mockFlagCandidateRepository = mock[FlagCandidateRepository]

    val service = new FixDataService {
      val appRepo = mockAppRepo
      val passmarkSettingsRepo = mockPassMarkSettingsRepo
      val auditService = mockAuditService
      val progressToAssessmentCentreConfig = mockConfig
      val assessmentScoresRepo: AssessorApplicationAssessmentScoresMongoRepository = mockAssessorApplicationAssessmentScoresMongoRepository
      val assessmentCentreAllocationRepo: AssessmentCentreAllocationMongoRepository = mockAssessmentCentreAllocationMongoRepository
      val onlineTestingRepo: OnlineTestRepository = mockOnlineTestRepository
      val onlineTestExtensionService: OnlineTestExtensionService = mockOnlineTestExtensionService
      val cdRepo: ContactDetailsRepository = mockContactDetailsRepository
      val emailClient: EmailClient = mockEmailClient
      val flagCandidateRepo: FlagCandidateRepository = mockFlagCandidateRepository
    }

    when(mockConfig.isValid("appId")).thenReturn(true)
    when(mockAppRepo.findProgress(any[String])).thenReturn(Future.successful(
      ProgressResponse("appId")
    ))
  }
}
