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

package services.application

import connectors.{ AuthProviderClient, CSREmailClient }
import connectors.ExchangeObjects.{ AuthProviderUserDetails, UpdateDetailsRequest }
import model.ApplicationStatuses
import model.Commands.{ CandidateEditableDetails, WithdrawApplicationRequest }
import model.Exceptions.NotFoundException
import play.api.Logger
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import services.applicationassessment.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val appAssessService = AssessmentCentreService
  val auditService = AuditService
  val personalDetailsRepository = repositories.personalDetailsRepository
  val contactDetailsRepository = repositories.contactDetailsRepository
  val authProviderClient = AuthProviderClient
  val emailClient = CSREmailClient
}

trait ApplicationService {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appRepository: GeneralApplicationRepository
  val appAssessService: AssessmentCentreService
  val auditService: AuditService
  val contactDetailsRepository: ContactDetailsRepository
  val personalDetailsRepository: PersonalDetailsRepository
  val authProviderClient: AuthProviderClient
  val emailClient: CSREmailClient
  private val UserNotFoundError = Future.failed(new RuntimeException("User not found for the given userId"))

  def withdraw(applicationId: String, withdrawRequest: WithdrawApplicationRequest): Future[Unit] = {
    appRepository.withdraw(applicationId, withdrawRequest).flatMap { _ =>
      auditService.logEventNoRequest(
        "ApplicationWithdrawn",
        Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)
      )
      appAssessService.deleteAssessmentCentreAllocation(applicationId).recover {
        case _: NotFoundException => ()
      }
    }
  }

  def editDetails(userId: String, applicationId: String, editRequest: CandidateEditableDetails)(implicit hc: HeaderCarrier): Future[Unit] = {

    val userFut: Future[AuthProviderUserDetails] = authProviderClient.findByUserId(userId).flatMap {
      case Some(u) => Future.successful(u)
      case _ => UserNotFoundError
    }
    val currentCdFut = contactDetailsRepository.find(userId)
    val currentPdFut = personalDetailsRepository.find(applicationId)

    for {
      currentCd <- currentCdFut
      currentPd <- currentPdFut
      user <- userFut
      _ <- Future.sequence(
        authProviderClient.update(userId, toUpdateDetailsRequest(editRequest, user)) ::
          personalDetailsRepository.update(applicationId, userId, currentPd.copy(
            firstName = editRequest.firstName,
            lastName = editRequest.lastName,
            preferredName = editRequest.preferredName,
            dateOfBirth = editRequest.dateOfBirth
          )) ::
          contactDetailsRepository.update(userId, currentCd.copy(
            outsideUk = editRequest.outsideUk.getOrElse(editRequest.country.isDefined),
            address = editRequest.address,
            postCode = editRequest.postCode,
            country = editRequest.country,
            phone = editRequest.phone
          )) :: Nil
      )

    } yield {
      auditService.logEventNoRequest(
        "ApplicationEdited",
        Map("applicationId" -> applicationId, "editRequest" -> editRequest.toString)
      )
    }
  }

  def processExpiredApplications(): Future[Unit] = {
    implicit val headerCarrier = HeaderCarrier()
    Logger.info("Processing expired candidates")
    appRepository.nextApplicationPendingAllocationExpiry.map { expiringAllocationOpt =>
      expiringAllocationOpt.map { expiringAllocation =>

        Logger.info(s"Expiring candidate: $expiringAllocation")
        authProviderClient.findByUserId(expiringAllocation.userId).map { userOpt =>
          userOpt.map { user =>
            for {
              _ <- appRepository.updateStatus(expiringAllocation.applicationId, ApplicationStatuses.AllocationExpired)
              msg = s"No email found for user ${expiringAllocation.userId}"
              _ <- emailClient.sendAssessmentCentreExpired(to = user.email.getOrElse(throw new IllegalStateException(msg)),
                name = user.preferredName.getOrElse(s"${user.firstName} ${user.lastName}"))
            } yield ()
          }
        }
      }
    }
  }

  private def toUpdateDetailsRequest(editRequest: CandidateEditableDetails, user: AuthProviderUserDetails): UpdateDetailsRequest = {
    UpdateDetailsRequest(editRequest.firstName, editRequest.lastName, user.email, Some(editRequest.preferredName), user.role, user.disabled)
  }
}
