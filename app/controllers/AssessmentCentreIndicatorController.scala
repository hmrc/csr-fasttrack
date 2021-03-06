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

import common.FutureEx
import controllers.AssessmentCentreIndicatorController.ApplicationReadyForUpdate
import model.AssessmentCentreIndicator
import play.api.mvc.Action
import repositories.{ AssessmentCentreIndicatorCSVRepository, AssessmentCentreIndicatorRepository, ContactDetailsRepository }
import repositories.application.GeneralApplicationRepository
import services.AuditService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreIndicatorController extends AssessmentCentreIndicatorController {
  val appRepository: GeneralApplicationRepository = repositories.applicationRepository
  val cdRepository: ContactDetailsRepository = repositories.contactDetailsRepository
  val assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository = AssessmentCentreIndicatorCSVRepository
  val auditService = AuditService

  case class ApplicationReadyForUpdate(appId: String, postCode: Option[String])
}

trait AssessmentCentreIndicatorController extends BaseController {
  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val assessmentCentreIndicatorRepo: AssessmentCentreIndicatorRepository
  val auditService: AuditService

  def applyNewAssessmentMapping(batchSize: Int) = Action.async { implicit request =>
    def updateIndicator(applicationToUpdate: ApplicationReadyForUpdate): Future[Unit] = {
      val assessmentCentreIndicator = assessmentCentreIndicatorRepo.calculateIndicator(applicationToUpdate.postCode)
      appRepository.updateAssessmentCentreIndicator(applicationToUpdate.appId, assessmentCentreIndicator).map { _ =>
        auditService.logEvent("AssessmentCentreIndicatorUpdated")
      }
    }

    val mappingVersion = AssessmentCentreIndicatorCSVRepository.AssessmentCentreIndicatorVersion
    val toUpdate = for {
      usersToApp <- appRepository.nextUserAndAppIdsReadyForAssessmentIndicatorUpdate(batchSize, mappingVersion)
      contactDetails <- cdRepository.findByUserIds(usersToApp.keys.toList)
    } yield {
      contactDetails.map { cd =>
        ApplicationReadyForUpdate(usersToApp(cd.userId), cd.postCode)
      }
    }

    toUpdate.flatMap { applications =>
      FutureEx.traverseSerial(applications) { app =>
        updateIndicator(app)
      }
    }.map { result =>
      Ok(s"Updated ${result.length} applications")
    }
  }

  def assignCandidateToLondonAssessmentCentre(applicationId: String) = Action.async { implicit request =>
    val assessmentCentre = "London"
    val msg = s"Candidate whose application id = $applicationId cannot be updated to $assessmentCentre " +
      "because there is no assessment-centre-indicator"

    val future = for {
      assessmentCentreIndicator <- appRepository.findAssessmentCentreIndicator(applicationId)

      aci = assessmentCentreIndicator.getOrElse(throw new IllegalStateException(msg))
      londonAci = AssessmentCentreIndicator(aci.area, assessmentCentre, aci.version)
      _ <- appRepository.updateAssessmentCentreIndicator(applicationId, londonAci)
    } yield ()

    future.map( _ => Ok(s"Updated candidate's assessment centre to $assessmentCentre whose applicationId = $applicationId") )
      .recover{ case _ => Forbidden(msg) }
  }
}
