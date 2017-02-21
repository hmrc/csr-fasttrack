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

package services.application

import model.Commands.{ CandidateEditableDetails, WithdrawApplicationRequest }
import model.Exceptions.NotFoundException
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import services.applicationassessment.AssessmentCentreService

import scala.concurrent.{ ExecutionContext, Future }

object ApplicationService extends ApplicationService {
  val appRepository = applicationRepository
  val appAssessService = AssessmentCentreService
  val auditService = AuditService
  val personalDetailsRepository = repositories.personalDetailsRepository
  val contactDetailsRepository = repositories.contactDetailsRepository
}

trait ApplicationService {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appRepository: GeneralApplicationRepository
  val appAssessService: AssessmentCentreService
  val auditService: AuditService
  val contactDetailsRepository: ContactDetailsRepository
  val personalDetailsRepository: PersonalDetailsRepository

  def withdraw(applicationId: String, withdrawRequest: WithdrawApplicationRequest): Future[Unit] = {
    appRepository.withdraw(applicationId, withdrawRequest).flatMap { result =>
      auditService.logEventNoRequest(
        "ApplicationWithdrawn",
        Map("applicationId" -> applicationId, "withdrawRequest" -> withdrawRequest.toString)
      )
      appAssessService.deleteAssessmentCentreAllocation(applicationId).recover {
        case _: NotFoundException => ()
      }
    }
  }

  def editDetails(userId: String, applicationId: String, editRequest: CandidateEditableDetails): Future[Unit] = {
    val currentCdFut = contactDetailsRepository.find(userId)
    val currentPdFut = personalDetailsRepository.find(applicationId)
    for {
      currentCd <- currentCdFut
      currentPd <- currentPdFut
      _ <- Future.sequence(
            personalDetailsRepository.updatePersonalDetailsOnly(applicationId, userId, currentPd.copy(
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

}
