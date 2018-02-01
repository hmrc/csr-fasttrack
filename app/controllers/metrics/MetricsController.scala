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

import model.ApplicationStatuses
import play.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.application.{ GeneralApplicationRepository, ReportingRepository }
import uk.gov.hmrc.play.microservice.controller.BaseController
import repositories._

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global

object MetricsController extends MetricsController {
  override val applicationRepo = applicationRepository
  override val reportingRepo = reportingRepository
}

trait MetricsController extends BaseController {
  val applicationRepo: GeneralApplicationRepository
  val reportingRepo: ReportingRepository

  def progressStatusCounts = Action.async {

    for {
      applicationCount <- applicationRepo.count
      createdCount <- applicationRepo.countByStatus(ApplicationStatuses.Created)
      list <- reportingRepo.getLatestProgressStatuses
    } yield {
      val listWithCounts = SortedMap[String, Int]() ++ list.groupBy(identity).mapValues(_.size).map {case (k, v) => (k.toUpperCase, v)}

      val data = listWithCounts ++
        Map("TOTAL_APPLICATION_COUNT" -> applicationCount) ++
        Map("CREATED" -> createdCount)

      Logger.info(s"progress status counts data = $data")

      Ok(Json.toJson(data))
    }
  }
}
