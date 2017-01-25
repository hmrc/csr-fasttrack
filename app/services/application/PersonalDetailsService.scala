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

import model.Commands.UpdateGeneralDetails
import model.PersistedObjects.ContactDetails
import model.PersistedObjects.PersonalDetails
import play.api.mvc.RequestHeader
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait PersonalDetailsService {
  def appRepo: GeneralApplicationRepository
  def personalDetailsRepo: PersonalDetailsRepository
  def contactDetailsRepo: ContactDetailsRepository
  def assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository
  def auditService: AuditService

  def update(userId: String, applicationId: String, personalDetails: PersonalDetails, contactDetails: ContactDetails)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[Unit] = for {
     _ <- personalDetailsRepo.update(applicationId, userId, personalDetails)
     _ <- contactDetailsRepo.update(userId, contactDetails)
     _ <- appRepo.updateAssessmentCentreIndicator(applicationId, assessmentCentreIndicatorRepo.calculateIndicator(Some(contactDetails.postCode)))
  } yield {
    auditService.logEvent("PersonalDetailsSaved")
  }

  def find(userId: String, applicationId: String): Future[UpdateGeneralDetails] = for {
    pd <- personalDetailsRepo.find(applicationId)
    cd <- contactDetailsRepo.find(userId)
  } yield {
    UpdateGeneralDetails(
      pd.firstName, pd.lastName, pd.preferredName, cd.email, pd.dateOfBirth,
      cd.address, cd.postCode, cd.phone,
      pd.aLevel, pd.stemLevel
    )
  }
}

object PersonalDetailsService extends PersonalDetailsService {
  val appRepo = applicationRepository
  val personalDetailsRepo = personalDetailsRepository
  val contactDetailsRepo = contactDetailsRepository
  val assessmentCentreIndicatorRepo = AssessmentCentreIndicatorCSVRepository
  val auditService = AuditService
}
