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

package controllers.testdata

import java.io.File

import com.typesafe.config.ConfigFactory
import connectors.AuthProviderClient
import connectors.testdata.ExchangeObjects.Implicits._
import model._
import model.EvaluationResults.Result
import model.Exceptions.EmailTakenException
import model.exchange.testdata._
import play.api.Play
import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.mvc.{ Action, RequestHeader }
import services.testdata._
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestDataGeneratorController extends TestDataGeneratorController

trait TestDataGeneratorController extends BaseController {

  def ping = Action { implicit request =>
    Ok("OK")
  }

  def clearDatabase() = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase().map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  def requestExample = Action { implicit request =>
    val example = CreateCandidateInStatusRequest(
      statusData = StatusDataRequest(
        applicationStatus = "SUBMITTED",
        previousApplicationStatus = Some("REGISTERED"),
        progressStatus = Some("SUBMITTED")
      ),
      personalData = Some(PersonalDataRequest(
        emailPrefix = Some(s"testf${Random.number()}"),
        firstName = Some("Kathryn"),
        lastName = Some("Janeway"),
        preferredName = Some("Captain"),
        dateOfBirth = Some("2328-05-20"),
        postCode = Some("QQ1 1QQ"),
        country = Some("America")
      )),
      assistanceDetailsData = Some(AssistanceDetailsRequest(
        hasDisability = Some("false"),
        hasDisabilityDescription = Some(Random.hasDisabilityDescription),
        setGis = Some(false),
        onlineAdjustments = Some(false),
        onlineAdjustmentsDescription = Some(Random.onlineAdjustmentsDescription),
        assessmentCentreAdjustments = Some(false),
        assessmentCentreAdjustmentsDescription = Some(Random.assessmentCentreAdjustmentDescription)
      )),
      schemeTypes = Some(List(Scheme.Commercial, Scheme.Business)),
      isCivilServant = Some(Random.bool),
      hasDegree = Some(Random.bool),
      region = Some("region"),
      loc1scheme1EvaluationResult = Some("loc1 scheme1 result1"),
      loc1scheme2EvaluationResult = Some("loc1 scheme2 result2"),
      confirmedAllocation = Some(Random.bool)
    )
    Ok(Json.toJson(example))
  }

  // scalastyle:on method.length

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String], role: String) = Action.async { implicit request =>
    try {
      TestDataGeneratorService.createAdminUsers(numberToGenerate, emailPrefix, AuthProviderClient.getRole(role)).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
        JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  val secretsFileCubiksUrlKey = "microservice.services.cubiks-gateway.testdata.url"
  lazy val cubiksUrlFromConfig = Play.current.configuration.getString(secretsFileCubiksUrlKey)
    .getOrElse(fetchSecretConfigKeyFromFile("cubiks.url"))

  private def fetchSecretConfigKeyFromFile(key: String): String = {
    val path = System.getProperty("user.home") + "/.csr/.secrets"
    val testConfig = ConfigFactory.parseFile(new File(path))
    if (testConfig.isEmpty) {
      throw new IllegalArgumentException(s"No key found at '$secretsFileCubiksUrlKey' and .secrets file does not exist.")
    } else {
      testConfig.getString(s"testdata.$key")
    }
  }

  // scalastyle:off parameter.number
  @deprecated("Use 'createCandidatesInStatusPOST' version instead", "30/12/2016")
  def createCandidatesInStatus(status: String,
                               numberToGenerate: Int,
                               emailPrefix: String,
                               setGis: Boolean,
                               region: Option[String],
                               loc1scheme1EvaluationResult: Option[String],
                               loc1scheme2EvaluationResult: Option[String],
                               previousStatus: Option[String] = None,
                               confirmedAllocation: Boolean) = Action.async { implicit request =>

    val initialConfig = GeneratorConfig(
      personalData = PersonalData(emailPrefix = emailPrefix),
      setGis = setGis,
      cubiksUrl = cubiksUrlFromConfig,
      region = region,
      loc1scheme1Passmark = loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = previousStatus,
      confirmedAllocation = status match {
        case ApplicationStatuses.AllocationUnconfirmed => false
        case ApplicationStatuses.AllocationConfirmed => true
        case _ => confirmedAllocation
      }
    )
    // scalastyle:on

    TestDataGeneratorService.createCandidatesInSpecificStatus(numberToGenerate, _ => StatusGeneratorFactory.getGenerator(status),
      _ => initialConfig).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  def createCandidatesInStatusPOST(numberToGenerate: Int) = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateCandidateInStatusRequest] { body =>
      createCandidateInStatus(body.statusData.applicationStatus, GeneratorConfig.apply(cubiksUrlFromConfig, body), numberToGenerate)
    }
  }

  private def createCandidateInStatus(status: String, config: (Int) => GeneratorConfig, numberToGenerate: Int)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader) = {
    try {
      TestDataGeneratorService.createCandidatesInSpecificStatus(
        numberToGenerate,
        _ => StatusGeneratorFactory.getGenerator(status),
        config
      ).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case _: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
        JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }
}
