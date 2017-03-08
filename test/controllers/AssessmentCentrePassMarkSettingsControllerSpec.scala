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

package controllers

import config.TestFixtureBase
import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.PassmarkPersistedObjects.Implicits._
import model.PassmarkPersistedObjects._
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.Helpers.{ contentAsJson, _ }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories._
import services.passmarksettings.AssessmentCentrePassMarkSettingsService
import model.Scheme
import services.AuditService
import org.mockito.Matchers.{ eq => eqTo, _ }
import model.Commands.Implicits._
import scala.concurrent.Future

class AssessmentCentrePassMarkSettingsControllerSpec extends PlaySpec with MockitoSugar {

  "create a passmark settings" should {
    "save the passmark settings in the db" in new TestFixture {
      when(mockAssessmentCentrePassMarkSettingsRepository.create(Settings)).thenReturn(Future.successful(()))

      val result = TestableAssessmentCentrePassMarkSettingsController.create()(createRequest(Json.toJson(Settings).toString))

      status(result) must be(CREATED)
      verify(mockAssessmentCentrePassMarkSettingsRepository).create(Settings)
      verify(mockAuditService).logEvent(eqTo("AssessmentCentrePassMarkSettingsCreated"), eqTo(Map(
        "Version" -> Version,
        "CreatedByUserId" -> Username,
        "StoredCreateDate" -> Now.toString)))(any(), any())
    }
  }

  "get latest version" should {
    "get latest version" in new TestFixture {
      when(mockAssessmentCentrePassmarkSettingsService.getLatestVersion).thenReturn(Future.successful(SettingsResponse))
      val result = TestableAssessmentCentrePassMarkSettingsController.getLatestVersion()(FakeRequest())
      contentAsJson(result) must be(Json.toJson(SettingsResponse))
    }
  }

  def createRequest(jsonString: String) = {
    val json = Json.parse(jsonString)
    FakeRequest(
      Helpers.POST,
      controllers.routes.AssessmentCentrePassMarkSettingsController.create.url, FakeHeaders(), json
    ).withHeaders("Content-Type" -> "application/json")
  }

}

trait TestFixture extends TestFixtureBase {
  val mockAssessmentCentrePassMarkSettingsRepository = mock[AssessmentCentrePassMarkSettingsMongoRepository]
  val mockAssessmentCentrePassmarkSettingsService = mock[AssessmentCentrePassMarkSettingsService]

  val Version = "version1"
  val Username = "userName"
  val Now = DateTime.now()

  object TestableAssessmentCentrePassMarkSettingsController extends AssessmentCentrePassMarkSettingsController {
    val assessmentCentrePassMarkRepository = mockAssessmentCentrePassMarkSettingsRepository
    val assessmentCentrePassmarkService = mockAssessmentCentrePassmarkSettingsService
    val auditService = mockAuditService
  }

  val AllAssessmentCentrePassMarkSchemes = List(
    AssessmentCentrePassMarkScheme(Scheme.Business),
    AssessmentCentrePassMarkScheme(Scheme.Commercial),
    AssessmentCentrePassMarkScheme(Scheme.DigitalAndTechnology),
    AssessmentCentrePassMarkScheme(Scheme.Finance),
    AssessmentCentrePassMarkScheme(Scheme.ProjectDelivery)
  )

  val Info = AssessmentCentrePassMarkInfo(Version, Now, Username)

  val Settings = AssessmentCentrePassMarkSettings(
    AllAssessmentCentrePassMarkSchemes.map(_.copy(overallPassMarks = Some(PassMarkSchemeThreshold(10.0, 20.0)))),
    Info
  )

  val SettingsResponse = AssessmentCentrePassMarkSettingsResponse(AllAssessmentCentrePassMarkSchemes, Some(Info))
}
