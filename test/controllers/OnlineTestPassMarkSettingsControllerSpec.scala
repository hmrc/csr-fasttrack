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
import connectors.PassMarkExchangeObjects.Implicits._
import connectors.PassMarkExchangeObjects._
import factories.UUIDFactory
import model.Commands.PassMarkSettingsCreateResponse
import model.Scheme.{ Business, Commercial, DigitalAndTechnology, Finance, ProjectDelivery }
import org.joda.time.DateTime
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import repositories.{ FrameworkRepository, LocationSchemeRepository, PassMarkSettingsRepository, SchemeInfo }

import scala.concurrent.Future
import scala.language.postfixOps

class OnlineTestPassMarkSettingsControllerSpec extends PlaySpec with Results with MockitoSugar {
  "Try and get latest settings" should {
    "Return a settings objects with schemes but no thresholds if there are no settings saved" in new TestFixture {
      val passMarkSettingsRepositoryMockWithNoSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithNoSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(None))

      val passMarkSettingsControllerWithNoSettings = buildPMS(passMarkSettingsRepositoryMockWithNoSettings)

      val expectedEmptySettingsResponse = SettingsResponse(
        schemes = List(
          SchemeResponse(mockSchemes.head.schemeId.toString, mockSchemes.head.schemeName, None),
          SchemeResponse(mockSchemes(1).schemeId.toString, mockSchemes(1).schemeName, None),
          SchemeResponse(mockSchemes(2).schemeId.toString, mockSchemes(2).schemeName, None)
        ),
        None,
        None
      )

      val result = passMarkSettingsControllerWithNoSettings.getLatestVersion()(FakeRequest())

      status(result) must be(200)

      contentAsJson(result) must be(Json.toJson(expectedEmptySettingsResponse))
    }

    "Return a complete settings object if there are saved settings" in new TestFixture {

      val passMarkSettingsRepositoryMockWithSettings = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryMockWithSettings.tryGetLatestVersion(any())).thenReturn(Future.successful(
        Some(
          mockSettings
        )
      ))

      val passMarkSettingsControllerWithSettings = buildPMS(passMarkSettingsRepositoryMockWithSettings)

      val expectedSettingsResponse = SettingsResponse(
        schemes = List(
          SchemeResponse(mockSchemes.head.schemeId.toString, mockSchemes.head.schemeName, Some(mockSchemes.head.schemeThresholds)),
          SchemeResponse(mockSchemes(1).schemeId.toString, mockSchemes(1).schemeName, Some(mockSchemes(1).schemeThresholds)),
          SchemeResponse(mockSchemes(2).schemeId.toString, mockSchemes(2).schemeName, Some(mockSchemes(2).schemeThresholds))
        ),
        Some(mockCreateDate),
        Some(mockCreatedByUser)
      )

      val result = passMarkSettingsControllerWithSettings.getLatestVersion()(FakeRequest())

      status(result) must be(200)

      contentAsJson(result) must be(Json.toJson(expectedSettingsResponse))
    }
  }

  "Save new settings" should {
    def isValid(value: Settings) = true

    "Send a complete settings object to the repository with a version UUID appended" in new TestFixture {

      val passMarkSettingsRepositoryWithExpectations = mock[PassMarkSettingsRepository]

      when(passMarkSettingsRepositoryWithExpectations.create(any(), any())).thenReturn(Future.successful(
        PassMarkSettingsCreateResponse(
          "uuid-1",
          new DateTime()
        )
      ))

      // Call controller
      val passMarkSettingsController = buildPMS(passMarkSettingsRepositoryWithExpectations)

      val result = passMarkSettingsController.createPassMarkSettings()(createPassMarkSettingsRequest(validSettingsCreateRequestJSON))

      status(result) must be(200)

      verify(passMarkSettingsRepositoryWithExpectations).create(eqTo(mockSettings), eqTo(testSchemeInfos.map(_.id)))
    }
  }

  trait TestFixture extends TestFixtureBase {

    val testSchemeInfos = List(
      SchemeInfo(Business, "Business", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo(Commercial, "Commercial", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo(DigitalAndTechnology, "Digital and technology", requiresALevel = false, requiresALevelInStem = true)
    )

    val testSchemeNames = testSchemeInfos.map(_.name)

    val mockLocationSchemeRepository = mock[LocationSchemeRepository]

    when(mockLocationSchemeRepository.getSchemeInfo).thenReturn(Future.successful(testSchemeInfos))

    val defaultSchemeThreshold = SchemeThreshold(20d, 80d)

    val defaultSchemeThresholds = SchemeThresholds(
      defaultSchemeThreshold, defaultSchemeThreshold, defaultSchemeThreshold, defaultSchemeThreshold
    )

    val mockSchemes = List(
      Scheme(testSchemeInfos.head.id, testSchemeInfos.head.name, defaultSchemeThresholds),
      Scheme(testSchemeInfos(1).id, testSchemeInfos(1).name, defaultSchemeThresholds),
      Scheme(testSchemeInfos(2).id, testSchemeInfos(2).name, defaultSchemeThresholds)
    )
    val mockVersion = "uuid-1"
    val mockCreateDate = new DateTime(1459504800000L)
    val mockCreatedByUser = "TestUser"

    val mockSettings = Settings(
      schemes = mockSchemes,
      version = mockVersion,
      createDate = mockCreateDate,
      createdByUser = mockCreatedByUser
    )

    val mockUUIDFactory = mock[UUIDFactory]

    when(mockUUIDFactory.generateUUID()).thenReturn("uuid-1")

    def buildPMS(mockRepository: PassMarkSettingsRepository) = new OnlineTestPassMarkSettingsController {
      val pmsRepository = mockRepository
      val locSchemeRepository = mockLocationSchemeRepository
      val auditService = mockAuditService
      val uuidFactory = mockUUIDFactory
    }

    def createPassMarkSettingsRequest(jsonString: String) = {
      val json = Json.parse(jsonString)
      FakeRequest(Helpers.PUT, controllers.routes.OnlineTestPassMarkSettingsController.createPassMarkSettings.url, FakeHeaders(), json)
        .withHeaders("Content-Type" -> "application/json")
    }

    val validSettingsCreateRequestJSON = s"""
                     |{
                     |    "createDate": 1459504800000,
                     |    "createdByUser": "TestUser",
                     |    "schemes": [
                     |        {
                     |            "schemeId": "Business",
                     |            "schemeId": "Business",
"schemeName": "Business",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeId": "Commercial",
                     |            "schemeId": "Commercial",
"schemeName": "Commercial",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        },
                     |        {
                     |            "schemeId": "DigitalAndTechnology",
                     |            "schemeId": "DigitalAndTechnology",
"schemeName": "Digital and technology",
                     |            "schemeThresholds": {
                     |                "competency": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "numerical": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "situational": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                },
                     |                "verbal": {
                     |                    "failThreshold": 20.0,
                     |                    "passThreshold": 80.0
                     |                }
                     |            }
                     |        }
                     |    ],
                     |    "setting": "location1Scheme1"
                     |}
        """.stripMargin
  }
}
