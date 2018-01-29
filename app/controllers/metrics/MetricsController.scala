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

package controllers.metrics

import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.application.GeneralApplicationRepository
import uk.gov.hmrc.play.microservice.controller.BaseController
import repositories._

//import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ProgressStatusMetrics(
  SUBMITTED: Int,
  PHASE1_TESTS_INVITED: Int
)

object ProgressStatusMetrics {
  implicit val progressStatusMetricsFormat = Json.format[ProgressStatusMetrics]
}

object MetricsController extends MetricsController {
  override val applicationRepo = applicationRepository
}

trait MetricsController extends BaseController {
  val applicationRepo: GeneralApplicationRepository

  def progressStatusCounts = Action.async {

    val data =
      """
        |{
        |  "TOTAL_APPLICATION_COUNT": 3,
        |  "CREATED": 1,
        |  "IN_PROGRESS": 1,
        |  "SUBMITTED": 1,
        |  "WITHDRAWN": 0,
        |  "ONLINE_TEST_INVITED": 1,
        |  "ONLINE_TEST_STARTED": 1,
        |  "ONLINE_TEST_COMPLETED": 0
        |}
      """.stripMargin

    val result = Ok(Json.parse(data))
    Future.successful(result)
  }
}
