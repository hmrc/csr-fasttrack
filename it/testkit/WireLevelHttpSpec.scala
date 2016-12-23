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

package testkit

import config.WSHttp
import org.scalatestplus.play.{ OneServerPerSuite, PlaySpec, PortNumber }
import play.api.http.{ HeaderNames, HttpProtocol, MimeTypes, Status }
import play.api.libs.ws.WSRequest
import play.api.mvc.Results
import play.api.test._
import uk.gov.hmrc.play.http.HeaderCarrier

/**
 * Both PlaySpec and PlaySpecification (former maintained in the ScalaTestPlus lib, latter in Play Framework)
 * offer different but complementary base functionality. Unfortunately they are both also abstract classes, so cannot
 * be mixed-in together. As a result, we've 'in-lined' the traits added by PlaySpecification into the trait's
 * inheritance list, so do not inherit from PlaySpecification directly.
 *
 * PlaySpec provides all the standard ScalaTest traits, in addition to `wsUrl()` which allows absolute URLs to be
 * constructed from only a path (it adds the hostname and port automatically).
 *
 * PlaySpecification provides additional helpers, such as a blocking `await()` method for handling `Future`s, and
 * named values for all the HTTP status codes (`OK`, etc.) and methods (`GET`, etc.).
 */
trait WireLevelHttpSpec
  extends PlaySpec
  with PlayRunners
  with HeaderNames
  with Status
  with HttpProtocol
  with DefaultAwaitTimeout
  with ResultExtractors
  with Writeables
  with RouteInvokers
  with FutureAwaits
  with OneServerPerSuite
  with MimeTypes
  with Results {

  val JSON_CONTENT_TYPE = "application/json"

  implicit val hc: HeaderCarrier = new HeaderCarrier()

  // Overridden to inject our prefixed application route.
  def wsUrl(url: String)(implicit portNumber: PortNumber): WSRequest =
    WSHttp.buildRequest(s"http://localhost:${portNumber.value}/candidate-application$url")
}
