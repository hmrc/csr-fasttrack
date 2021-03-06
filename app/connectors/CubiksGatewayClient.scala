/*
 * Copyright 2019 HM Revenue & Customs
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

package connectors

import _root_.config.WSHttp
import config.MicroserviceAppConfig._
import connectors.ExchangeObjects.Implicits._
import connectors.ExchangeObjects.{ Invitation, InviteApplicant, RegisterApplicant, Registration }
import model.Exceptions.ConnectorException
import model.OnlineTestCommands.Implicits._
import model.OnlineTestCommands._
import play.api.http.Status._
import play.api.libs.iteratee.Iteratee

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait CubiksGatewayClient {
  val http: WSHttp
  val url: String
  val root = "fset-online-tests-gateway"

  def registerApplicant(registerApplicant: RegisterApplicant)(implicit hc: HeaderCarrier): Future[Registration] = {
    http.POST(s"$url/$root/register", registerApplicant).map { response =>
      if (response.status == OK) {
        response.json.as[Registration]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to the Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def inviteApplicant(inviteApplicant: InviteApplicant)(implicit hc: HeaderCarrier): Future[Invitation] = {
    http.POST(s"$url/$root/invite", inviteApplicant).map { response =>
      if (response.status == OK) {
        response.json.as[Invitation]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to the Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def getReport(application: OnlineTestApplicationForReportRetrieving)(implicit hc: HeaderCarrier): Future[OnlineTestReportAvailability] = {
    http.POST(s"$url/$root/report", application).map { response =>
      if (response.status == OK) {
        response.json.as[OnlineTestReportAvailability]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to the Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def downloadXmlReport(reportId: Int)(implicit hc: HeaderCarrier): Future[Map[String, TestResult]] = {
    http.GET(s"$url/$root/report-xml/$reportId").map { response =>
      if (response.status == OK) {
        response.json.as[Map[String, TestResult]]
      } else {
        throw new ConnectorException(s"There was a general problem connecting to the Online Tests Gateway. HTTP response was $response")
      }
    }
  }

  def downloadPdfReport(reportId: Int)(implicit hc: HeaderCarrier): Future[Array[Byte]] = {
    http.playWS.url(s"$url/$root/report-pdf/$reportId").get(respHeaders =>
      if (respHeaders.status == OK) {
        Iteratee.consume[Array[Byte]]()
      } else {
        throw new ConnectorException(
          s"There was a general problem connecting to the Online Tests Gateway to download the PDF report '$reportId'. " +
            s"HTTP response headers were $respHeaders"
        )
      }).flatMap { iteratee => iteratee.run }
  }
}

object CubiksGatewayClient extends CubiksGatewayClient {
  val http: WSHttp = WSHttp
  val url = cubiksGatewayConfig.url
}
