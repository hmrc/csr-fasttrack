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

package controllers

import model.Commands._
import model.Exceptions.CannotAddMedia
import play.api.mvc.Action
import repositories._
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object MediaController extends MediaController {
  val mRepository = mediaRepository
  val auditService = AuditService
}

trait MediaController extends BaseController {
  import Implicits._

  val mRepository: MediaRepository
  val auditService: AuditService

  def addMedia() = Action.async(parse.json) { implicit request =>
    withJsonBody[AddMedia] { media =>

      (for {
        _ <- mRepository.create(media)
      } yield {
        auditService.logEvent("CampaignReferrerSaved")
        Created
      }).recover {
        case e: CannotAddMedia => BadRequest(s"cannot add media details for user: ${e.userId}")
      }
    }
  }
}
