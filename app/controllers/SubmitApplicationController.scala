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

import connectors.{CSREmailClient, EmailClient}
import model.ApplicationValidator
import play.api.mvc.Action
import repositories.FrameworkRepository.CandidateHighestQualification
import repositories._
import repositories.application.{AssistanceDetailsRepository, GeneralApplicationRepository, PersonalDetailsRepository}
import services.AuditService
import services.assistancedetails.AssistanceDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SubmitApplicationController extends SubmitApplicationController {
  override val adService: AssistanceDetailsService = AssistanceDetailsService
  override val pdRepository: PersonalDetailsRepository = personalDetailsRepository
  override val cdRepository = contactDetailsRepository
  override val frameworkPrefRepository: FrameworkPreferenceMongoRepository = frameworkPreferenceRepository
  override val frameworkRegionsRepository: FrameworkRepository = frameworkRepository
  override val appRepository: GeneralApplicationRepository = applicationRepository
  override val emailClient = CSREmailClient
  override val auditService = AuditService
}

trait SubmitApplicationController extends BaseController {
  val adService: AssistanceDetailsService
  val pdRepository: PersonalDetailsRepository
  val cdRepository: ContactDetailsRepository
  val frameworkPrefRepository: FrameworkPreferenceRepository
  val frameworkRegionsRepository: FrameworkRepository
  val appRepository: GeneralApplicationRepository
  val emailClient: EmailClient
  val auditService: AuditService

  def submitApplication(userId: String, applicationId: String) = Action.async { implicit request =>
    val generalDetailsFuture = pdRepository.find(applicationId)
    val assistanceDetailsFuture = adService.find(applicationId, userId)
    val contactDetailsFuture = cdRepository.find(userId)
    val schemesLocationsFuture = frameworkPrefRepository.tryGetPreferences(applicationId)

    val result = for {
        gd <- generalDetailsFuture
        ad <- assistanceDetailsFuture
        cd <- contactDetailsFuture
        sl <- schemesLocationsFuture
        availableRegions <- frameworkRegionsRepository.getFrameworksByRegionFilteredByQualification(CandidateHighestQualification.from(gd))
      } yield {

        if (ApplicationValidator(gd, ad, sl, availableRegions).validate) {
          appRepository.submit(applicationId).flatMap { _ =>
            emailClient.sendApplicationSubmittedConfirmation(cd.email, gd.preferredName).flatMap { _ =>
              auditService.logEvent("ApplicationSubmitted")
              Future.successful(Ok)
            }
          }
        } else {
          Future.successful(BadRequest)
        }
    }

    result flatMap identity
  }

}
