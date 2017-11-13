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

import connectors.{ CSREmailClient, EmailClient }
import model.Commands.Candidate
import model.Exceptions.ApplicationNotFound
import model.PersistedObjects.ContactDetails
import model.{ AdjustmentDetail, Adjustments, AdjustmentsComment }
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.GeneralApplicationRepository
import services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object AdjustmentsManagementService extends AdjustmentsManagementService {
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val emailClient = CSREmailClient
  val auditService = AuditService
}

trait AdjustmentsManagementService {
  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val emailClient: EmailClient
  val auditService: AuditService

  def confirmAdjustment(applicationId: String, adjustments: Adjustments,
    actionTriggeredBy: String)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    def auditEvents(
      candidate: Candidate,
      contactDetails: ContactDetails,
      prevAdjustments: Option[Adjustments],
      adjustments: Adjustments
    ): Future[Unit] = {

      val hasPreviousAdjustments = prevAdjustments.flatMap(_.adjustmentsConfirmed).contains(true)
      val adjustmentsRejected = adjustments.typeOfAdjustments.forall(_.isEmpty)

      (adjustmentsRejected, hasPreviousAdjustments) match {
        case (true, _) => auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsRejected by $actionTriggeredBy")
        case (_, true) => auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsUpdated by $actionTriggeredBy")
        case (_, false) => auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsConfirmed by $actionTriggeredBy")
      }

      hasPreviousAdjustments match {
        case true =>
          emailClient.sendAdjustmentsUpdateConfirmation(
            contactDetails.email,
            candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
            onlineTestsAdjustmentsString("Online tests:", adjustments.onlineTests),
            assessmentCenterAdjustmentsString("Assessment center:", adjustments)
          )
        case false =>
          emailClient.sendAdjustmentsConfirmation(
            contactDetails.email,
            candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
            onlineTestsAdjustmentsString("Online tests:", adjustments.onlineTests),
            assessmentCenterAdjustmentsString("Assessment center:", adjustments)
          )
      }
    }

    appRepository.find(applicationId).flatMap {
      case Some(candidate) =>
        for {
          cd <- cdRepository.find(candidate.userId)
          previousAdjustments <- appRepository.findAdjustments(applicationId)
          _ <- appRepository.confirmAdjustments(applicationId, adjustments)
          _ <- auditEvents(candidate, cd, previousAdjustments, adjustments)
        } yield {}
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def find(applicationId: String): Future[Option[Adjustments]] = {
    appRepository.findAdjustments(applicationId)
  }

  def updateAdjustmentsComment(
    applicationId: String,
    adjustmentsComment: AdjustmentsComment
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
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

  private def onlineTestsAdjustmentsString(header: String, onlineTestsAdjustments: Option[AdjustmentDetail]): String = {
    def mkString(ad: Option[AdjustmentDetail]): Option[String] =
      ad.map(e => List(
        e.extraTimeNeeded.map(tn => s"$tn% extra time (Verbal)"),
        e.extraTimeNeededNumerical.map(tn => s"$tn% extra time (Numerical)"),
        e.otherInfo
      ).flatten.mkString(", "))
    toEmailString(header, onlineTestsAdjustments, mkString)
  }

  private def assessmentCenterAdjustmentsString(header: String, adjustments: Adjustments): String = {

    def assessmentCenterAdjustments() = adjustments.typeOfAdjustments.map(_.filter(_.contains(" ")).mkString(", "))

    def mkString(ad: Option[AdjustmentDetail]): Option[String] =
      ad.map(e => List(
        e.extraTimeNeeded.map(tn => s"$tn% extra time"),
        assessmentCenterAdjustments().filter(_.trim.nonEmpty),
        e.otherInfo
      ).flatten.mkString(", "))
    toEmailString(header, adjustments.assessmentCenter, mkString)
  }

  private def toEmailString(header: String, adjustmentDetail: Option[AdjustmentDetail],
    mkString: (Option[AdjustmentDetail]) => Option[String]): String = {
    mkString(adjustmentDetail) match {
      case Some(txt) if !txt.isEmpty => s"$header $txt"
      case _ => ""
    }
  }
}
