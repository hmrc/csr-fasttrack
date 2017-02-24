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

package controllers

import connectors.{ CSREmailClient, EmailClient }
import model.ApplicationValidator
import play.api.mvc.Action
import repositories._
import repositories.application.{ GeneralApplicationRepository, PersonalDetailsRepository }
import services.AuditService
import services.assistancedetails.AssistanceDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SubmitApplicationController extends SubmitApplicationController {
  override val adService: AssistanceDetailsService = AssistanceDetailsService
  override val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  override val cdRepository: ContactDetailsMongoRepository = contactDetailsRepository
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val emailClient = CSREmailClient
  override val auditService = AuditService
  override val assessmentCentreIndicatorRepo = AssessmentCentreIndicatorCSVRepository
}

trait SubmitApplicationController extends BaseController {
  val adService: AssistanceDetailsService
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val appRepository: GeneralApplicationRepository
  val emailClient: EmailClient
  val auditService: AuditService
  val assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository

  def submitApplication(userId: String, applicationId: String) = Action.async { implicit request =>

    val result = for {
      personalDetails <- pdRepository.find(applicationId)
      assistanceDetails <- adService.find(applicationId, userId)
      contactDetails <- cdRepository.find(userId)
      schemePreferences <- appRepository.getSchemes(applicationId)
      schemeLocationPreferences <- appRepository.getSchemeLocations(applicationId)
    } yield {

      if (ApplicationValidator(personalDetails, assistanceDetails).validate) {
        appRepository.submit(applicationId).flatMap { _ =>
          val assessmentCentreIndicator = assessmentCentreIndicatorRepo.calculateIndicator(contactDetails.postCode)
          auditService.logEvent("ApplicationSubmitted")
          appRepository.updateAssessmentCentreIndicator(applicationId, assessmentCentreIndicator).flatMap { _ =>
            auditService.logEvent("AssessmentCentreIndicatorSet")
            emailClient.sendApplicationSubmittedConfirmation(contactDetails.email, personalDetails.preferredName).map { _ =>
              Ok
            }
          }
        }
      } else {
        Future.successful(BadRequest)
      }
    }

    result flatMap identity
  }

}
