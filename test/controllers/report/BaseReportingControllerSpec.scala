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
import org.mockito.Matchers._
import org.mockito.Mockito._
import repositories.application.{ PreviousYearCandidatesDetailsRepository, ReportingRepository }
import repositories.{ ApplicationAssessmentScoresRepository, ContactDetailsRepository, DiversityReportRepository,
QuestionnaireRepository, TestReportRepository }
import testkit.UnitWithAppSpec

import scala.concurrent.Future
import scala.language.postfixOps

class BaseReportingControllerSpec extends UnitWithAppSpec {

  trait TestFixture extends TestFixtureBase {
    val frameworkId = "FastTrack-2015"

    val authProviderClientMock = mock[AuthProviderClient]
    val previousYearContactDetailsRepositoryMock = mock[PreviousYearCandidatesDetailsRepository]

    when(authProviderClientMock.candidatesReport(any())).thenReturn(Future.successful(
      Candidate("firstName1", "lastName1", Some("preferredName1"), "email1@test.com", "user1") ::
        Candidate("firstName2", "lastName2", None, "email2@test.com", "user2") ::
        Nil
    ))

    val assessmentScoresRepoMock = mock[ApplicationAssessmentScoresRepository]
    val contactDetailsRepoMock = mock[ContactDetailsRepository]
    val diversityReportRepoMock = mock[DiversityReportRepository]
    val questionnaireRepoMock = mock[QuestionnaireRepository]
    val reportingRepoMock = mock[ReportingRepository]
    val testReportRepoMock = mock[TestReportRepository]

    val controller = new ReportingController {
      val diversityReportRepository = diversityReportRepoMock
      val cdRepository = contactDetailsRepoMock
      val authProviderClient = authProviderClientMock
      val questionnaireRepository = questionnaireRepoMock
      val testReportRepository = testReportRepoMock
      val assessmentScoresRepository = assessmentScoresRepoMock
      val reportingRepository = reportingRepoMock
      val prevYearCandidatesDetailsRepository = previousYearContactDetailsRepositoryMock
    }
  }
}
