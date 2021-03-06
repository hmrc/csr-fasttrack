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

package services.application

import model.Commands.UpdateGeneralDetails
import model.PersistedObjects.ContactDetails
import model.persisted.PersonalDetails
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

trait PersonalDetailsService {
  def appRepo: GeneralApplicationRepository
  def personalDetailsRepo: PersonalDetailsRepository
  def contactDetailsRepo: ContactDetailsRepository
  def auditService: AuditService

  def update(
    userId: String,
    applicationId: String,
    personalDetails: PersonalDetails,
    contactDetails: ContactDetails
  )(implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = for {
    _ <- personalDetailsRepo.updatePersonalDetailsAndStatus(applicationId, userId, personalDetails)
    _ <- contactDetailsRepo.update(userId, contactDetails)
  } yield {
    auditService.logEvent("PersonalDetailsSaved")
  }

  def find(userId: String, applicationId: String): Future[UpdateGeneralDetails] = for {
    pd <- personalDetailsRepo.find(applicationId)
    cd <- contactDetailsRepo.find(userId)
  } yield {
    UpdateGeneralDetails(
      pd.firstName, pd.lastName, pd.preferredName, cd.email, pd.dateOfBirth,
      cd.outsideUk, cd.address, cd.postCode, cd.country, cd.phone,
      pd.aLevel, pd.stemLevel, civilServant = pd.civilServant, pd.department,
      pd.departmentOther
    )
  }
}

object PersonalDetailsService extends PersonalDetailsService {
  val appRepo = applicationRepository
  val personalDetailsRepo = personalDetailsRepository
  val contactDetailsRepo = contactDetailsRepository
  val auditService = AuditService
}
