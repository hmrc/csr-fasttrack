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

import connectors.AuthProviderClient
import model._
import model.Commands._
import model.PersistedObjects.ContactDetailsWithId
import model.PersistedObjects.Implicits._
import model.ReportExchangeObjects.Implicits._
import model.ReportExchangeObjects.{ Implicits => _, _ }
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Request }
import repositories.application.ReportingRepository
import repositories.{ QuestionnaireRepository, application, _ }
import services.locationschemes.LocationSchemeService
import services.reporting.{ ReportingFormatter, SocioEconomicScoreCalculator }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ReportingController extends ReportingController {
  val locationSchemeService = LocationSchemeService
  val reportingFormatter = ReportingFormatter
  val assessmentCentreIndicatorRepository = AssessmentCentreIndicatorCSVRepository
  val assessmentScoresRepository = repositories.applicationAssessmentScoresRepository
  val contactDetailsRepository = repositories.contactDetailsRepository
  val questionnaireRepository = repositories.questionnaireRepository
  val reportingRepository = repositories.reportingRepository
  val testReportRepository = repositories.testReportRepository
  val authProviderClient = AuthProviderClient
  val locationSchemeRepository = FileLocationSchemeRepository
  val mediaRepository = repositories.mediaRepository
  val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
}

trait ReportingController extends BaseController {

  import Implicits._

  val locationSchemeService: LocationSchemeService
  val reportingFormatter: ReportingFormatter
  val assessmentCentreIndicatorRepository: AssessmentCentreIndicatorRepository
  val assessmentScoresRepository: ApplicationAssessmentScoresRepository
  val contactDetailsRepository: ContactDetailsRepository
  val questionnaireRepository: QuestionnaireRepository
  val reportingRepository: ReportingRepository
  val testReportRepository: TestReportRepository
  val authProviderClient: AuthProviderClient
  val locationSchemeRepository: LocationSchemeRepository
  val mediaRepository: MediaRepository
  val socioEconomicScoreCalculator: SocioEconomicScoreCalculator

  def retrieveDiversityReport(frameworkId: String) = Action.async { implicit request =>
    val applicationsFut = reportingRepository.applicationsForCandidateProgressReport(frameworkId)
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.groupBy(_.userId).mapValues(_.head))
    val allLocationsFut = locationSchemeService.getAllSchemeLocations
    val allQuestionsFut = questionnaireRepository.diversityReport
    val reportFut: Future[List[DiversityReportRow]] = for {
      applications <- applicationsFut
      allContactDetails <- allContactDetailsFut
      allLocations <- allLocationsFut
      allDiversityQuestions <- allQuestionsFut
      allMedia <- mediaRepository.findAll()
      report <- buildDiversityReportRows(applications, allContactDetails, allLocations, allDiversityQuestions, allMedia)
    } yield report
    reportFut.map { report => Ok(Json.toJson(report)) }
  }

  private def extractDiversityAnswers(application: ApplicationForCandidateProgressReport,
                                      allDiversityQuestions: Map[String, Map[String, String]]) = {
    def getDiversityAnswerForQuestion(applicationId: String, questionText: String,
                                              allDiversityQuestions: Map[String, Map[String, String]]) = {
      allDiversityQuestions.get(applicationId).map { questionMap => questionMap.getOrElse(questionText, "") }.getOrElse("")
    }

    val genderAnswer = getDiversityAnswerForQuestion(application.applicationId.toString,
      QuestionnaireRepository.genderQuestionText, allDiversityQuestions)

    val sexualOrientationAnswer = getDiversityAnswerForQuestion(application.applicationId.toString,
      QuestionnaireRepository.sexualOrientationQuestionText, allDiversityQuestions)

    val ethnicityAnswer = getDiversityAnswerForQuestion(application.applicationId.toString,
      QuestionnaireRepository.ethnicityQuestionText, allDiversityQuestions)

    DiversityReportDiversityAnswers(genderAnswer, sexualOrientationAnswer, ethnicityAnswer)
  }

  private def buildDiversityReportRows(applications: List[ApplicationForCandidateProgressReport],
                                   allContactDetails: Map[String, ContactDetailsWithId],
                                   allLocations: List[LocationSchemes],
                                   allDiversityQuestions: Map[String, Map[String, String]],
                                   allMedia: Map[UniqueIdentifier, String]): Future[List[DiversityReportRow]] = {
    Future{
      applications.map { application =>
        val diversityAnswers = extractDiversityAnswers(application, allDiversityQuestions)
        val locationIds = application.locationIds
        val onlineAdjustmentsVal = reportingFormatter.getOnlineAdjustments(application.onlineAdjustments, application.adjustments)
        val assessmentCentreAdjustmentsVal = reportingFormatter.getAssessmentCentreAdjustments(
          application.assessmentCentreAdjustments,
          application.adjustments)
        val locationNames = locationIds.flatMap(locationId => allLocations.find(_.id == locationId).map{_.locationName})
        val ses = socioEconomicScoreCalculator.calculate(allDiversityQuestions(application.applicationId.toString))
        val hearAboutUs = allMedia.getOrElse(application.userId, "")
        val allocatedAssessmentCentre = allContactDetails.get(application.userId.toString()).map { contactDetails =>
            assessmentCentreIndicatorRepository.calculateIndicator(Some(contactDetails.postCode.toString)).assessmentCentre
        }
        DiversityReportRow(application, diversityAnswers, ses, hearAboutUs, allocatedAssessmentCentre).copy(locations = locationNames,
          onlineAdjustments = onlineAdjustmentsVal, assessmentCentreAdjustments = assessmentCentreAdjustmentsVal)
      }
    }
  }

  def createApplicationAndUserIdsReport(frameworkId: String) = Action.async { implicit request =>
    reportingRepository.allApplicationAndUserIds(frameworkId).map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createAdjustmentReports(frameworkId: String) = Action.async { implicit request =>
    val reports =
      for {
        applications <- reportingRepository.adjustmentReport(frameworkId)
        allCandidates <- contactDetailsRepository.findAll
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
        applications <- reportingRepository.candidatesAwaitingAllocation(frameworkId)
        allCandidates <- contactDetailsRepository.findAll
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

    val applications = reportingRepository.applicationsWithAssessmentScoresAccepted(frameworkId)
    val allQuestions = questionnaireRepository.passMarkReport
    val allScores = assessmentScoresRepository.allScores

    val reports = for {
      apps <- applications
      quests <- allQuestions
      scores <- allScores
    } yield {
      for {
        app <- apps
        quest <- quests.get(app.applicationId.toString)
        appscore <- scores.get(app.applicationId.toString)
      } yield {
        AssessmentResultsReport(app, quest, appscore)
      }
    }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createCandidateProgressReport(frameworkId: String) = Action.async { implicit request =>
    val applicationsFut = reportingRepository.applicationsForCandidateProgressReport(frameworkId)
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.groupBy(_.userId).mapValues(_.head))
    val allLocationsFut = locationSchemeService.getAllSchemeLocations
    val reportFut: Future[List[CandidateProgressReportItem]] = for {
      applications <- applicationsFut
      allContactDetails <- allContactDetailsFut
      allLocations <- allLocationsFut
      report <- giveCandidateProgressReports(applications, allContactDetails, allLocations)
    } yield report
    reportFut.map { report => Ok(Json.toJson(report)) }
  }

  private def giveCandidateProgressReports(
                                            applications: List[ApplicationForCandidateProgressReport],
                                            allContactDetails: Map[String, ContactDetailsWithId],
                                            allLocations: List[LocationSchemes]): Future[List[CandidateProgressReportItem]] = {
    Future{
      applications.map { application =>

        val fsacIndicatorVal = allContactDetails.get(application.userId.toString()).map { contactDetails =>
          assessmentCentreIndicatorRepository.calculateIndicator(Some(contactDetails.postCode.toString)).assessmentCentre
        }
        val locationIds = application.locationIds
        val onlineAdjustmentsVal = reportingFormatter.getOnlineAdjustments(application.onlineAdjustments, application.adjustments)
        val assessmentCentreAdjustmentsVal = reportingFormatter.getAssessmentCentreAdjustments(
          application.assessmentCentreAdjustments,
          application.adjustments)
        val locationNames = locationIds.flatMap(locationId => allLocations.filter(_.id == locationId).headOption.map{_.locationName})

        CandidateProgressReportItem(application).copy(fsacIndicator = fsacIndicatorVal, locations = locationNames,
          onlineAdjustments = onlineAdjustmentsVal, assessmentCentreAdjustments = assessmentCentreAdjustmentsVal)
      }
    }
  }

  def createNonSubmittedApplicationsReports(frameworkId: String) =
    preferencesAndContactReports(nonSubmittedOnly = true)(frameworkId)

  def createOnlineTestPassMarkModellingReport(frameworkId: String) = Action.async { implicit request =>
      val reports =
        for {
          applications <- reportingRepository.candidateProgressReportNotWithdrawn(frameworkId)
          questionnaires <- questionnaireRepository.passMarkReport
          testResults <- testReportRepository.getOnlineTestReports
        } yield {
          for {
            a <- applications
            q <- questionnaires.get(a.applicationId.toString)
            t <- testResults.get(a.applicationId.toString)
          } yield PassMarkReport(a, q, t)
        }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createOnlineTestPassMarkWithPersonalDataReport(frameworkId: String) = Action.async { implicit request =>
      val reports =
        for {
          applications <- reportingRepository.candidateProgressReportNotWithdrawnWithPersonalDetails(frameworkId)
          testResults <- testReportRepository.getOnlineTestReports
          contactDetails <- contactDetailsRepository.findAll
          cDetails = contactDetails.map(c => c.userId -> c).toMap
        } yield {
          for {
            a <- applications
            t <- testResults.get(a.applicationId.toString)
            c <- cDetails.get(a.userId.toString)
          } yield PassMarkReportWithPersonalData(a, t, ContactDetails(c.phone, c.email, c.address, c.postCode))
        }
      reports.map { list =>
        Ok(Json.toJson(list))
      }
  }

  def createPreferencesAndContactReports(frameworkId: String) =
    preferencesAndContactReports(nonSubmittedOnly = false)(frameworkId)


  def createSuccessfulCandidatesReport(frameworkId: String) = Action.async { implicit request =>

      val applications = reportingRepository.applicationsPassedInAssessmentCentre(frameworkId)
      val allCandidates = contactDetailsRepository.findAll

      val reports = for {
        apps <- applications
        acs <- allCandidates
        candidates = acs.map(c => c.userId -> c).toMap
      } yield {
        for {
          a <- apps
          c <- candidates.get(a.userId.toString)
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

  private def preferencesAndContactReports(nonSubmittedOnly: Boolean)(frameworkId: String) = Action.async { implicit request =>
      for {
        applications <- reportingRepository.applicationsReport(frameworkId)
        applicationsToExclude = getApplicationsNotToIncludeInReport(applications, nonSubmittedOnly)
        users <- getAppsFromAuthProvider(applicationsToExclude)
        contactDetails <- contactDetailsRepository.findAll
        reports = mergeApplications(users, contactDetails, applications)
      } yield {
        Ok(Json.toJson(reports.values))
      }
  }

  private def mergeApplications(users: Map[String, PreferencesWithContactDetails],
                                 contactDetails: List[ContactDetailsWithId],
                                 applications: List[(String, IsNonSubmitted, PreferencesWithContactDetails)]) = {

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
                                                 )

  = {
    if (nonSubmittedOnly) {
      createdApplications.collect { case (userId, false, _) => userId }.toSet
    } else {
      Set.empty[String]
    }
  }

  private def getAppsFromAuthProvider(candidateExclusionSet: Set[String])(implicit request: Request[AnyContent])

  = {
    for {
      allCandidates <- authProviderClient.candidatesReport
    } yield {
      allCandidates.filterNot(c => candidateExclusionSet.contains(c.userId)).map(c =>
        c.userId -> PreferencesWithContactDetails(Some(c.firstName), Some(c.lastName), c.preferredName, Some(c.email),
          None, None, None, None, None, None, None, None, None)).toMap
    }
  }
}
