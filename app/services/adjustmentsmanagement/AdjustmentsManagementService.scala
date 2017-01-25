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
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  def confirmAdjustment(applicationId: String, adjustments: Adjustments, actionTriggeredBy: String)
                       (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = {
    def auditEvents(candidate: Candidate,
                            contactDetails: ContactDetails,
                            prevAdjustments: Option[Adjustments],
                            adjustments: Adjustments): Future[Unit] = {
      val hasNewAdjustments = adjustments.typeOfAdjustments.exists(_.nonEmpty)
      val hasPreviousAdjustments = prevAdjustments.nonEmpty
      val sendEmail = hasNewAdjustments || hasPreviousAdjustments

      if(adjustments.typeOfAdjustments.isEmpty) {
        auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsRejected by $actionTriggeredBy")
      }

      sendEmail match {
        case true if hasPreviousAdjustments =>
          auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsUpdated by $actionTriggeredBy")
          emailClient.sendAdjustmentsUpdateConfirmation(
            contactDetails.email,
            candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
            onlineTestsAdjustmentsString("Online tests:", adjustments.onlineTests),
            assessmentCenterAdjustmentsString("Assessment center:", adjustments)
          )
        case true =>
          auditService.logEvent(s"Candidate ${candidate.userId} AdjustmentsConfirmed by $actionTriggeredBy")
          emailClient.sendAdjustmentsConfirmation(
            contactDetails.email,
            candidate.preferredName.getOrElse(candidate.firstName.getOrElse("")),
            onlineTestsAdjustmentsString("Online tests:", adjustments.onlineTests),
            assessmentCenterAdjustmentsString("Assessment center:", adjustments)
          )
        case _ => Future.successful(())
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

  private def onlineTestsAdjustmentsString(header: String, onlineTestsAdjustments: Option[AdjustmentDetail]): String = {
    def mkString(ad: Option[AdjustmentDetail]): Option[String] =
      ad.map(e => List(e.extraTimeNeeded.map( tn => s"$tn% extra time (Verbal)"),
        e.extraTimeNeededNumerical.map( tn => s"$tn% extra time (Numerical)"),
        e.otherInfo).flatten.mkString(", "))

    mkString(onlineTestsAdjustments) match {
      case Some(txt) if !txt.isEmpty => s"$header $txt"
      case _ => ""
    }
  }

  private def assessmentCenterAdjustmentsString(header: String, adjustments: Adjustments): String = {
    def mkString(ad: Option[AdjustmentDetail]): Option[String] =
      ad.map(e => List(e.extraTimeNeeded.map( tn => s"$tn% extra time"),
        adjustments.typeOfAdjustments.map(_.filter(_.contains(" ")).mkString(", ")),
        e.otherInfo).flatten.mkString(", "))

    mkString(adjustments.assessmentCenter) match {
      case Some(txt) if !txt.isEmpty => s"$header $txt"
      case _ => ""
    }
  }
}
