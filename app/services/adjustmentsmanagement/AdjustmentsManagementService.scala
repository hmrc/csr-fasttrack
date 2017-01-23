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

package services.adjustmentsmanagement

import model.{ Adjustments, AdjustmentsComment }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AdjustmentsManagementService extends AdjustmentsManagementService {
  val appRepository = applicationRepository
  val auditService = AuditService
}

trait AdjustmentsManagementService {
  val appRepository: GeneralApplicationRepository
  val auditService: AuditService

  def confirmAdjustment(applicationId: String, adjustments: Adjustments)
                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    adjustments.typeOfAdjustments match {
      case Some(list) if list.nonEmpty => auditService.logEvent("AdjustmentsConfirmed")
      case _ => auditService.logEvent("AdjustmentsRejected")
    }
    appRepository.confirmAdjustments(applicationId, adjustments)
  }

  def find(applicationId: String): Future[Option[Adjustments]] = {
    appRepository.findAdjustments(applicationId)
  }

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment)
                              (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.updateAdjustmentsComment(applicationId, adjustmentsComment).map { _ =>
      auditService.logEvent("AdjustmentsCommentUpdated")
    }
  }

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
    appRepository.findAdjustmentsComment(applicationId)
  }

  def removeAdjustmentsComment(applicationId: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    appRepository.removeAdjustmentsComment(applicationId).map { _ =>
      auditService.logEvent("AdjustmentsCommentRemoved")
    }
  }
}
