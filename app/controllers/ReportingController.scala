/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.AuthProviderClient
import model.ApplicationStatusOrder
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.PersistedObjects.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Request }
import repositories.application.GeneralApplicationRepository
import repositories.{ QuestionnaireRepository, _ }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object ReportingController extends ReportingController {
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val reportingRepository = diversityReportRepository
  val authProviderClient = AuthProviderClient
  val questionnaireRepository = repositories.questionnaireRepository
  val testReportRepository = repositories.testReportRepository
  val assessmentScoresRepository = repositories.applicationAssessmentScoresRepository
}

trait ReportingController extends BaseController {

  import Implicits._

  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val reportingRepository: ReportingRepository
  val authProviderClient: AuthProviderClient
  val questionnaireRepository: QuestionnaireRepository
  val testReportRepository: TestReportRepository
  val assessmentScoresRepository: ApplicationAssessmentScoresRepository

  def createReport(frameworkId: String) = Action.async { implicit request =>
    appRepository.overallReport(frameworkId).map(r => Ok(Json.toJson(r)))
  }

  def retrieveDiversityReport = Action.async { implicit request =>
    reportingRepository.findLatest().map {
      case Some(report) => Ok(Json.toJson(report))
      case None => NotFound
    }
  }

  def createAdjustmentReports(frameworkId: String) = Action.async { implicit request =>
    val reports =
      for {
        applications <- appRepository.adjustmentReport(frameworkId)
        allCandidates <- cdRepository.findAll
        candidates = allCandidates.groupBy(_.userId).mapValues(_.head)
      } yield {
        applications.map { application =>
          candidates
            .get(application.userId)
            .fold(application)(cd =>
              application.copy(email = Some(cd.email), telephone = cd.phone))
        }
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createAssessmentCentreAllocationReport(frameworkId: String) = Action.async { implicit request =>
    val reports =
      for {
        applications <- appRepository.candidatesAwaitingAllocation(frameworkId)
        allCandidates <- cdRepository.findAll
        candidates = allCandidates.groupBy(_.userId).mapValues(_.head)
      } yield {
        for {
          a <- applications
          c <- candidates.get(a.userId)
        } yield AssessmentCentreAllocationReport(
          a.firstName,
          a.lastName,
          a.preferredName,
          c.email,
          c.phone.getOrElse(""),
          a.preferredLocation1,
          a.adjustments,
          a.dateOfBirth
        )
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createAssessmentResultsReport(frameworkId: String) = Action.async { implicit request =>

    val applications = appRepository.applicationsWithAssessmentScoresAccepted(frameworkId)
    val allQuestions = questionnaireRepository.passMarkReport
    val allScores = assessmentScoresRepository.allScores

    val reports = for {
      apps <- applications
      quests <- allQuestions
      scores <- allScores
    } yield {
      for {
        app <- apps
        quest <- quests.get(app.applicationId)
        appscore <- scores.get(app.applicationId)
      } yield {
        AssessmentResultsReport(app, quest, appscore)
      }
    }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createSuccessfulCandidatesReport(frameworkId: String) = Action.async { implicit request =>

    val applications = appRepository.applicationsPassedInAssessmentCentre(frameworkId)
    val allCandidates = cdRepository.findAll

    val reports = for {
      apps <- applications
      acs <- allCandidates
      candidates = acs.map(c => c.userId -> c).toMap
    } yield {
      for {
        a <- apps
        c <- candidates.get(a.userId)
      } yield {
        ApplicationPreferencesWithTestResultsAndContactDetails(
          a,
          ContactDetails(c.phone, c.email, c.address, c.postCode)
        )
      }
    }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createPassMarkModellingReport(frameworkId: String) = Action.async { implicit request =>
    val reports =
      for {
        applications <- appRepository.overallReportNotWithdrawn(frameworkId)
        questionnaires <- questionnaireRepository.passMarkReport
        testResults <- testReportRepository.getOnlineTestReports
      } yield {
        for {
          a <- applications
          q <- questionnaires.get(a.applicationId)
          t <- testResults.get(a.applicationId)
        } yield PassMarkReport(a, q, t)
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createPassMarkWithPersonalDataReport(frameworkId: String) = Action.async { implicit request =>
    val reports =
      for {
        applications <- appRepository.overallReportNotWithdrawnWithPersonalDetails(frameworkId)
        testResults <- testReportRepository.getOnlineTestReports
        contactDetails <- cdRepository.findAll
        cDetails = contactDetails.map(c => c.userId -> c).toMap
      } yield {
        for {
          a <- applications
          t <- testResults.get(a.applicationId)
          c <- cDetails.get(a.userId)
        } yield PassMarkReportWithPersonalData(a, t, ContactDetails(c.phone, c.email, c.address, c.postCode))
      }
    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createNonSubmittedAppsReports(frameworkId: String) =
    preferencesAndContactReports(nonSubmittedOnly = true)(frameworkId)

  def createPreferencesAndContactReports(frameworkId: String) =
    preferencesAndContactReports(nonSubmittedOnly = false)(frameworkId)

  def applicationAndUserIdsReport(frameworkId: String) = Action.async { implicit request =>
    appRepository.allApplicationAndUserIds(frameworkId).map { list =>
      Ok(Json.toJson(list))
    }
  }

  private def preferencesAndContactReports(nonSubmittedOnly: Boolean)(frameworkId: String) = Action.async { implicit request =>
    for {
      applications <- appRepository.applicationsReport(frameworkId)
      applicationsToExclude = getApplicationsNotToIncludeInReport(applications, nonSubmittedOnly)
      users <- getAppsFromAuthProvider(applicationsToExclude)
      contactDetails <- cdRepository.findAll
      reports = mergeApplications(users, contactDetails, applications)
    } yield {
      Ok(Json.toJson(reports.values))
    }
  }

  private def mergeApplications(
    users: Map[String, PreferencesWithContactDetails],
    contactDetails: List[ContactDetailsWithId],
    applications: List[(String, IsNonSubmitted, PreferencesWithContactDetails)]
  ) = {

    val contactDetailsMap = contactDetails.groupBy(_.userId).mapValues(_.headOption)
    val applicationsMap = applications
      .groupBy { case (userId, _, _) => userId }
      .mapValues(_.headOption.map { case (_, _, app) => app })

    users.map {
      case (userId, user) =>
        val cd = contactDetailsMap.getOrElse(userId, None)
        val app = applicationsMap.getOrElse(userId, None)
        val noAppProgress: Option[String] = Some(ApplicationStatusOrder.getStatus(None))

        (userId, PreferencesWithContactDetails(
          user.firstName,
          user.lastName,
          user.preferredName,
          user.email,
          cd.flatMap(_.phone),
          app.flatMap(_.location1),
          app.flatMap(_.location1Scheme1),
          app.flatMap(_.location1Scheme2),
          app.flatMap(_.location2),
          app.flatMap(_.location2Scheme1),
          app.flatMap(_.location2Scheme2),
          app.fold(noAppProgress)(_.progress),
          app.flatMap(_.timeApplicationCreated)
        ))
    }
  }

  private def getApplicationsNotToIncludeInReport(
    createdApplications: List[(String, IsNonSubmitted, PreferencesWithContactDetails)],
    nonSubmittedOnly: Boolean
  ) = {
    if (nonSubmittedOnly) {
      createdApplications.collect { case (userId, false, _) => userId }.toSet
    } else {
      Set.empty[String]
    }
  }

  private def getAppsFromAuthProvider(candidateExclusionSet: Set[String])(implicit request: Request[AnyContent]) = {
    for {
      allCandidates <- authProviderClient.candidatesReport
    } yield {
      allCandidates.filterNot(c => candidateExclusionSet.contains(c.userId)).map(c =>
        c.userId -> PreferencesWithContactDetails(Some(c.firstName), Some(c.lastName), c.preferredName, Some(c.email),
          None, None, None, None, None, None, None, None, None)).toMap
    }
  }
}
