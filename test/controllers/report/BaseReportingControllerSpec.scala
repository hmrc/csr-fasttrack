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

import config.TestFixtureBase
import connectors.AuthProviderClient
import connectors.ExchangeObjects.Candidate
import controllers.ReportingController
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import repositories.application.ReportingRepository
import repositories.{ ApplicationAssessmentScoresRepository, AssessmentCentreIndicatorRepository, ContactDetailsRepository, LocationSchemeRepository, MediaRepository, QuestionnaireRepository, TestReportRepository }
import services.locationschemes.LocationSchemeService
import services.reporting.ReportingFormatter
import testkit.MockitoImplicits.OngoingStubbingExtension
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class BaseReportingControllerSpec extends UnitWithAppSpec {

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastTrack-2015"

    val locationSchemeServiceMock = mock[LocationSchemeService]
    val reportingFormatterMock = mock[ReportingFormatter]
    val assessmentCentreIndicatorRepoMock = mock[AssessmentCentreIndicatorRepository]
    val assessmentScoresRepoMock = mock[ApplicationAssessmentScoresRepository]
    val contactDetailsRepoMock = mock[ContactDetailsRepository]
    val questionnaireRepoMock = mock[QuestionnaireRepository]
    val reportingRepoMock = mock[ReportingRepository]
    val testReportRepoMock = mock[TestReportRepository]
    val authProviderClientMock = mock[AuthProviderClient]
    val locationSchemeRepositoryMock = mock[LocationSchemeRepository]
    val mediaRepositoryMock = mock[MediaRepository]
    when(authProviderClientMock.candidatesReport(any())).thenReturn(Future.successful(
      Candidate("firstName1", "lastName1", Some("preferredName1"), "email1@test.com", "user1") ::
        Candidate("firstName2", "lastName2", None, "email2@test.com", "user2") ::
        Nil
    ))

    trait ReportingControllerDefaultMocks {
      val locationSchemeService = locationSchemeServiceMock
      val reportingFormatter = reportingFormatterMock
      val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
      val assessmentScoresRepository = assessmentScoresRepoMock
      val contactDetailsRepository = contactDetailsRepoMock
      val questionnaireRepository = questionnaireRepoMock
      val reportingRepository = reportingRepoMock
      val testReportRepository = testReportRepoMock
      val authProviderClient = authProviderClientMock
    }

    val controller = new ReportingController {
      val locationSchemeService = locationSchemeServiceMock
      val reportingFormatter = reportingFormatterMock
      val assessmentCentreIndicatorRepository = assessmentCentreIndicatorRepoMock
      val assessmentScoresRepository = assessmentScoresRepoMock
      val contactDetailsRepository = contactDetailsRepoMock
      val questionnaireRepository = questionnaireRepoMock
      val reportingRepository = reportingRepoMock
      val testReportRepository = testReportRepoMock
      val authProviderClient = authProviderClientMock
      val locationSchemeRepository = locationSchemeRepositoryMock
      val mediaRepository = mediaRepositoryMock
    }

  }
}
