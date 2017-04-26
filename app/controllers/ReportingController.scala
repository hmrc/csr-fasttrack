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

import akka.stream.scaladsl.Source
import connectors.{ AuthProviderClient, ExchangeObjects }
import model.AssessmentExercise.AssessmentExercise
import model.CandidateScoresCommands.{ CandidateScoresAndFeedback, ScoresAndFeedback }
import model.Commands.{ CsvExtract, _ }
import model.PersistedObjects.ContactDetailsWithId
import model.ReportExchangeObjects.Implicits._
import model.ReportExchangeObjects.{ Implicits => _, _ }
import model.Scheme.Scheme
import model.persisted.SchemeEvaluationResult
import model.report.{ AssessmentCentreScoresReportItem, DiversityReportItem, PassMarkReportItem }
import model.{ ApplicationStatusOrder, AssessmentExercise, ProgressStatuses, UniqueIdentifier }
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.libs.streams.Streams
import play.api.mvc.{ Action, AnyContent, Request, Result }
import repositories.application.{ OnlineTestRepository, PreviousYearCandidatesDetailsRepository, ReportingRepository }
import repositories.{ QuestionnaireRepository, _ }
import services.locationschemes.LocationSchemeService
import services.reporting.{ ReportingFormatter, SocioEconomicScoreCalculator }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ReportingController extends ReportingController {
  val locationSchemeService = LocationSchemeService
  val reportingFormatter = ReportingFormatter
  val assessmentCentreIndicatorRepository = AssessmentCentreIndicatorCSVRepository
  val assessorAssessmentScoresRepository = repositories.assessorAssessmentScoresRepository
  val reviewerAssessmentScoresRepository = repositories.reviewerAssessmentScoresRepository
  val contactDetailsRepository = repositories.contactDetailsRepository
  val questionnaireRepository = repositories.questionnaireRepository
  val reportingRepository = repositories.reportingRepository
  val testReportRepository = repositories.testReportRepository
  val prevYearCandidatesDetailsRepository = repositories.prevYearCandidatesDetailsRepository
  val authProviderClient = AuthProviderClient
  val locationSchemeRepository = FileLocationSchemeRepository
  val mediaRepository = repositories.mediaRepository
  val socioEconomicScoreCalculator = SocioEconomicScoreCalculator
  val onlineTestRepository = repositories.onlineTestRepository
  val assessmentCentreAllocationRepository = repositories.assessmentCentreAllocationRepository
}

trait ReportingController extends BaseController {

  import Implicits._

  val locationSchemeService: LocationSchemeService
  val reportingFormatter: ReportingFormatter
  val assessmentCentreIndicatorRepository: AssessmentCentreIndicatorRepository
  val assessorAssessmentScoresRepository: ApplicationAssessmentScoresRepository
  val reviewerAssessmentScoresRepository: ApplicationAssessmentScoresRepository
  val contactDetailsRepository: ContactDetailsRepository
  val questionnaireRepository: QuestionnaireRepository
  val reportingRepository: ReportingRepository
  val testReportRepository: TestReportRepository
  val prevYearCandidatesDetailsRepository: PreviousYearCandidatesDetailsRepository
  val authProviderClient: AuthProviderClient
  val locationSchemeRepository: LocationSchemeRepository
  val mediaRepository: MediaRepository
  val socioEconomicScoreCalculator: SocioEconomicScoreCalculator
  val onlineTestRepository: OnlineTestRepository
  val assessmentCentreAllocationRepository: AssessmentCentreAllocationRepository

  def createDiversityReport(frameworkId: String) = Action.async { implicit request =>
    val applicationsFut = reportingRepository.diversityReport(frameworkId)
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.groupBy(_.userId).mapValues(_.head))
    val allLocationsFut = locationSchemeService.getAllSchemeLocations
    val allQuestionsFut = questionnaireRepository.diversityReport
    val reportFut: Future[List[DiversityReportItem]] = for {
      applications <- applicationsFut
      allContactDetails <- allContactDetailsFut
      allLocations <- allLocationsFut
      allDiversityQuestions <- allQuestionsFut
      allMedia <- mediaRepository.findAll
    } yield {
      buildDiversityReportRows(applications, allContactDetails, allLocations, allDiversityQuestions, allMedia)
    }
    reportFut.map { report => Ok(Json.toJson(report)) }
  }

  private def extractDiversityAnswers(appId: UniqueIdentifier, allDiversityQuestions: Map[String, Map[String, String]]) = {
    def getDiversityAnswerForQuestion(applicationId: String, questionText: String,
      allDiversityQuestions: Map[String, Map[String, String]]) = {
      allDiversityQuestions.get(applicationId).map { questionMap => questionMap.getOrElse(questionText, "") }.getOrElse("")
    }

    val genderAnswer = getDiversityAnswerForQuestion(
      appId.toString(),
      QuestionnaireRepository.genderQuestionText, allDiversityQuestions
    )

    val sexualOrientationAnswer = getDiversityAnswerForQuestion(
      appId.toString(),
      QuestionnaireRepository.sexualOrientationQuestionText, allDiversityQuestions
    )

    val ethnicityAnswer = getDiversityAnswerForQuestion(
      appId.toString(),
      QuestionnaireRepository.ethnicityQuestionText, allDiversityQuestions
    )

    DiversityReportDiversityAnswers(genderAnswer, sexualOrientationAnswer, ethnicityAnswer)
  }

  private def buildDiversityReportRows(
    applications: List[ApplicationForCandidateProgressReport],
    allContactDetails: Map[String, ContactDetailsWithId],
    allLocations: List[LocationSchemes],
    allDiversityQuestions: Map[String, Map[String, String]],
    allMedia: Map[UniqueIdentifier, String]
  ): List[DiversityReportItem] = {
      applications.map { application =>
        val diversityReportItem = application.applicationId.map { appId =>
          val diversityAnswers = extractDiversityAnswers(appId, allDiversityQuestions)
          val locationIds = application.locationIds
          val onlineAdjustmentsVal = reportingFormatter.getOnlineAdjustments(application.onlineAdjustments, application.adjustments)
          val assessmentCentreAdjustmentsVal = reportingFormatter.getAssessmentCentreAdjustments(
            application.assessmentCentreAdjustments,
            application.adjustments
          )
          val locationNames = locationIds.flatMap(locationId => allLocations.find(_.id == locationId).map { _.locationName })
          val ses = socioEconomicScoreCalculator.calculate(allDiversityQuestions(appId.toString()))
          val hearAboutUs = allMedia.getOrElse(application.userId, "")
          val allocatedAssessmentCentre = allContactDetails.get(application.userId.toString()).map { contactDetails =>
            assessmentCentreIndicatorRepository.calculateIndicator(contactDetails.postCode).assessmentCentre
          }
          DiversityReportItem(application, diversityAnswers, ses, hearAboutUs, allocatedAssessmentCentre).copy(
            locations = locationNames,
            onlineAdjustments = onlineAdjustmentsVal, assessmentCentreAdjustments = assessmentCentreAdjustmentsVal
          )
        }
        diversityReportItem.getOrElse(throw new IllegalStateException(s"Application Id does not exist in diversity report generation " +
          s"for the user Id = ${application.userId}"))
      }
  }

  def createOnlineTestPassMarkModellingReport(frameworkId: String) = Action.async { implicit request =>
    // Start the futures running
    val applicationsFut = reportingRepository.passMarkReport(frameworkId)
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.groupBy(_.userId).mapValues(_.head))
    val allLocationsFut = locationSchemeService.getAllSchemeLocations
    val allQuestionsFut = questionnaireRepository.passMarkReport
    val allTestResultsFut = testReportRepository.getOnlineTestReports
    val allMediaFut = mediaRepository.findAll
    val allScoresFut = reviewerAssessmentScoresRepository.allScores
    val allPassMarkEvaluationsFut = onlineTestRepository.findAllPassMarkEvaluations
    val allAssessmentCentreEvaluationsFut = onlineTestRepository.findAllAssessmentCentreEvaluations
    // Process the futures
    val reportFut: Future[List[PassMarkReportItem]] = for {
      allApplications <- applicationsFut
      allContactDetails <- allContactDetailsFut
      allLocations <- allLocationsFut
      allDiversityQuestions <- allQuestionsFut
      allTestScores <- allTestResultsFut
      allMedia <- allMediaFut
      allScores <- allScoresFut
      allPassMarkEvaluations <- allPassMarkEvaluationsFut
      allAssessmentCentreEvaluations <- allAssessmentCentreEvaluationsFut
    } yield {
      val passMarkData = PassMarkData(
        allApplications,
        allContactDetails,
        allLocations,
        allDiversityQuestions,
        allMedia,
        allTestScores,
        allScores,
        allPassMarkEvaluations,
        allAssessmentCentreEvaluations
      )
      buildPassMarkReportRows(passMarkData)
    }
    reportFut.map { report => Ok(Json.toJson(report)) }
  }

  case class PassMarkData(allApplications: List[ApplicationForCandidateProgressReport],
                          allContactDetails: Map[String, ContactDetailsWithId],
                          allLocations: List[LocationSchemes],
                          allDiversityQuestions: Map[String, Map[String, String]],
                          allMedia: Map[UniqueIdentifier, String],
                          allTestResults: Map[String, PassMarkReportTestResults],
                          allAssessmentScores: Map[String, CandidateScoresAndFeedback],
                          allPassMarkEvaluations: Map[String, List[SchemeEvaluationResult]],
                          allAssessmentCentreEvaluations: Map[String, List[SchemeEvaluationResult]]
                         )

  private def buildPassMarkReportRows(data: PassMarkData): List[PassMarkReportItem] = {
    val passMarkResultsEmpty = PassMarkReportTestResults(competency = None, numerical = None, verbal = None, situational = None)
    data.allApplications.map { application =>
      val passMarkReportItem = application.applicationId.map { appId =>
        val diversityAnswers = extractDiversityAnswers(appId, data.allDiversityQuestions)
        val locationIds = application.locationIds
        val onlineAdjustmentsVal = reportingFormatter.getOnlineAdjustments(application.onlineAdjustments, application.adjustments)
        val assessmentCentreAdjustmentsVal = reportingFormatter.getAssessmentCentreAdjustments(
          application.assessmentCentreAdjustments,
          application.adjustments
        )
        val locationNames = locationIds.flatMap(locationId => data.allLocations.find(_.id == locationId).map { _.locationName })
        val ses = data.allDiversityQuestions.get(appId.toString()).map {
          questions => socioEconomicScoreCalculator.calculate(questions)
        }.getOrElse("N/A")
        val hearAboutUs = data.allMedia.getOrElse(application.userId, "")
        val allocatedAssessmentCentre = data.allContactDetails.get(application.userId.toString()).map { contactDetails =>
          assessmentCentreIndicatorRepository.calculateIndicator(contactDetails.postCode).assessmentCentre
        }
        val onlineTestResults = data.allTestResults.getOrElse(appId.toString(), passMarkResultsEmpty)
        val assessmentScores = data.allAssessmentScores.get(appId.toString())

        val schemeOnlineTestResults = processEvaluations(appId, application.schemes, data.allPassMarkEvaluations)
        val assessmentCentreTestResults = processEvaluations(appId, application.schemes, data.allAssessmentCentreEvaluations)

        PassMarkReportItem(application, diversityAnswers, ses, hearAboutUs, allocatedAssessmentCentre, onlineTestResults,
          schemeOnlineTestResults, assessmentScores, assessmentCentreTestResults)
          .copy(
            locations = locationNames,
            onlineAdjustments = onlineAdjustmentsVal,
            assessmentCentreAdjustments = assessmentCentreAdjustmentsVal
          )
      }
      passMarkReportItem.getOrElse(throw new IllegalStateException(s"Application Id does not exist in pass mark report generation " +
        s"for the user Id = ${application.userId}"))
    }
  }

  private def processEvaluations(appId: UniqueIdentifier, schemes: List[Scheme], allEvaluations: Map[String, List[SchemeEvaluationResult]]) = {
    schemes.map { scheme =>
      val schemeEvaluationResultList: List[SchemeEvaluationResult] = allEvaluations.getOrElse(appId.toString(), Nil)
      val maybeSchemeEvaluationResult: Option[SchemeEvaluationResult] = schemeEvaluationResultList.find(_.scheme == scheme)
      maybeSchemeEvaluationResult.fold("") {_.result.toString}
    }
  }

  def createApplicationAndUserIdsReport(frameworkId: String) = Action.async { implicit request =>
    reportingRepository.allApplicationAndUserIds(frameworkId).map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createAdjustmentReports(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
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

  def createAssessmentCentreAllocationReport(frameworkId: String): Action[AnyContent] = Action.async { implicit request =>
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
          a.dateOfBirth,
          a.adjustments,
          a.assessmentCentreLocation
        )
      }

    reports.map { list =>
      Ok(Json.toJson(list))
    }
  }

  def createCandidateProgressReport(frameworkId: String) = Action.async { implicit request =>
    val usersFut = authProviderClient.candidatesReport
    val applicationsFut = reportingRepository.applicationsForCandidateProgressReport(frameworkId)
    val allContactDetailsFut = contactDetailsRepository.findAll.map(x => x.groupBy(_.userId).mapValues(_.head))
    val allLocationsFut = locationSchemeService.getAllSchemeLocations
    val reportFut: Future[List[CandidateProgressReportItem]] = for {
      users <- usersFut
      applications <- applicationsFut
      allContactDetails <- allContactDetailsFut
      allLocations <- allLocationsFut
    } yield {
      buildCandidateProgressReports(users, applications, allContactDetails, allLocations)
    }
    reportFut.map { report => Ok(Json.toJson(report)) }
  }

  private def buildCandidateProgressReports(
    users: List[ExchangeObjects.Candidate],
    applications: List[ApplicationForCandidateProgressReport],
    allContactDetails: Map[String, ContactDetailsWithId],
    allLocations: List[LocationSchemes]
  ): List[CandidateProgressReportItem] = {
    val applicationsMap = applications.map(application => application.userId -> application).toMap
    users.map { user =>
      {
        val reportItem = applicationsMap.get(UniqueIdentifier(user.userId)).map { application =>
          {
            val fsacIndicatorVal = allContactDetails.get(user.userId.toString()).flatMap { contactDetails =>
              application.assessmentCentreIndicator.map(_.assessmentCentre)
            }
            val locationIds = application.locationIds
            val onlineAdjustmentsVal = reportingFormatter.getOnlineAdjustments(application.onlineAdjustments, application.adjustments)
            val assessmentCentreAdjustmentsVal = reportingFormatter.getAssessmentCentreAdjustments(
              application.assessmentCentreAdjustments,
              application.adjustments
            )
            val locationNames = locationIds.flatMap(locationId => allLocations.find(_.id == locationId).map {
              _.locationName
            })
            CandidateProgressReportItem(application).copy(fsacIndicator = fsacIndicatorVal, locations = locationNames,
              onlineAdjustments = onlineAdjustmentsVal, assessmentCentreAdjustments = assessmentCentreAdjustmentsVal)
          }
        }
        val defaultReportItem = CandidateProgressReportItem(ApplicationForCandidateProgressReport(
          None,
          UniqueIdentifier(user.userId), Some(ProgressStatuses.Registered), List.empty, List.empty, None, None, None, None, None, None, None
        ))
        reportItem.getOrElse(defaultReportItem)
      }
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

  private def extractAssessorUserIds(assessorAssessmentScoresData: List[CandidateScoresAndFeedback]) = {
    assessorAssessmentScoresData.flatMap { data =>
      List(data.interview, data.groupExercise, data.writtenExercise).map { opt =>
        opt.map(_.updatedBy).getOrElse("")
      }
    }.toSet
  }

  def createAssessmentCentreScoresReport(frameworkId: String) = Action.async { implicit request =>
    def extractApplicationIds(apps: List[ApplicationForAssessmentScoresReport]) = {
      apps.map(app => app.applicationId.getOrElse {
        val msg = s"Application Id does not exist in assessment centre scores report generation (extracting from " +
          s"ApplicationForAssessmentScoresReport object) for userId = ${app.userId}"
        throw new IllegalStateException(msg)
      })
    }
    val applicationsFut = reportingRepository.applicationsForAssessmentScoresReport(frameworkId)

    val reportItems = for {
      apps <- applicationsFut
      applicationIds = extractApplicationIds(apps)
      assessmentCentreData <- assessmentCentreAllocationRepository.findByApplicationIds(applicationIds)
      assessmentCentreMap = assessmentCentreData.map(a => a.applicationId -> a).toMap
      assessorAssessmentScoresData <- assessorAssessmentScoresRepository.findByApplicationIds(applicationIds)
      assessorAssessmentScoresMap = assessorAssessmentScoresData.map(a => a.applicationId -> a).toMap
      reviewerAssessmentScoresData <- reviewerAssessmentScoresRepository.findByApplicationIds(applicationIds)
      reviewerAssessmentScoresMap = reviewerAssessmentScoresData.map(r => r.applicationId -> r).toMap
      assessorUserIds = extractAssessorUserIds(assessorAssessmentScoresData)
      assessorsData <- authProviderClient.findByUserIds(assessorUserIds)
      assessorsMap = assessorsData.map(a => a.userId -> a).toMap
    } yield {
      buildAssessmentCentreScoresReportRows(apps, assessmentCentreMap, assessorAssessmentScoresMap,
        reviewerAssessmentScoresMap, assessorsMap)
    }

    reportItems.map( items => Ok(Json.toJson(items)) )
  }

  private def buildFullName(firstNameOpt: Option[String], lastNameOpt: Option[String]) =
    for {
      firstName <- firstNameOpt
      lastName <- lastNameOpt
    } yield {
      s"$firstName $lastName"
    }

  private def getAssessorForExercise(appId: String,
                                     exerciseType: AssessmentExercise,
                                     assessorAssessmentScoresData: Map[String, CandidateScoresAndFeedback],
                                     assessorsMap: Map[String, ExchangeObjects.Candidate]): Option[ExchangeObjects.Candidate] = {

    val assessorUserId = assessorAssessmentScoresData.get(appId).flatMap { data =>
      exerciseType match {
        case AssessmentExercise.interview =>
            data.interview.map(_.updatedBy)
        case AssessmentExercise.groupExercise =>
            data.groupExercise.map(_.updatedBy)
        case AssessmentExercise.writtenExercise =>
            data.writtenExercise.map(_.updatedBy)
        case _ =>
          val msg = s"Error generating assessment centre scores report for appId = $appId. Exercise type not recognised: $exerciseType"
          throw new IllegalStateException(msg)
      }
    }
    assessorUserId.flatMap(userId => assessorsMap.get(userId))
  }

  private def buildAssessmentCentreScoresReportRows(applications: List[ApplicationForAssessmentScoresReport],
                                                    assessmentCentreData: Map[String, AssessmentCentreAllocation],
                                                    assessorAssessmentScoresData: Map[String, CandidateScoresAndFeedback],
                                                    reviewerAssessmentScoresData: Map[String, CandidateScoresAndFeedback],
                                                    assessorsMap: Map[String, ExchangeObjects.Candidate]
                                                   ): List[AssessmentCentreScoresReportItem] = {

    applications.map { application =>
      val reportItem = application.applicationId.map{ appId =>

        val candidateFullName = buildFullName(application.firstName, application.lastName)

        val interviewStatus =
          evaluateAssessmentStatus(appId, AssessmentExercise.interview, assessorAssessmentScoresData, reviewerAssessmentScoresData)
        val groupExerciseStatus =
          evaluateAssessmentStatus(appId, AssessmentExercise.groupExercise, assessorAssessmentScoresData, reviewerAssessmentScoresData)
        val writtenExerciseStatus =
          evaluateAssessmentStatus(appId, AssessmentExercise.writtenExercise, assessorAssessmentScoresData, reviewerAssessmentScoresData)

        val interviewAssessor = getAssessorForExercise(appId, AssessmentExercise.interview, assessorAssessmentScoresData, assessorsMap)
        val groupExerciseAssessor = getAssessorForExercise(appId, AssessmentExercise.groupExercise, assessorAssessmentScoresData, assessorsMap)
        val writtenExerciseAssessor =
          getAssessorForExercise(appId, AssessmentExercise.writtenExercise, assessorAssessmentScoresData, assessorsMap)

        AssessmentCentreScoresReportItem(
          assessmentCentreLocation = application.assessmentCentreIndicator.map(_.area),
          assessmentCentreVenue = assessmentCentreData.get(appId).map(_.venue),
          assessmentCentreDate = assessmentCentreData.get(appId).map(_.date.toString()),
          amOrPm = assessmentCentreData.get(appId).map(_.session),
          candidateName = candidateFullName,
          interview = Some(interviewStatus),
          interviewAssessor = buildFullName(interviewAssessor.map(_.firstName), interviewAssessor.map(_.lastName)),
          groupExercise = Some(groupExerciseStatus),
          groupExerciseAssessor = buildFullName(groupExerciseAssessor.map(_.firstName), groupExerciseAssessor.map(_.lastName)),
          writtenExercise = Some(writtenExerciseStatus),
          writtenExerciseAssessor = buildFullName(writtenExerciseAssessor.map(_.firstName), writtenExerciseAssessor.map(_.lastName))
        )
      }
      reportItem.getOrElse(throw new IllegalStateException(s"Application Id does not exist in assessment centre scores report generation " +
        s"for the user Id = ${application.userId}"))
    }
  }

  private def evaluateAssessmentStatus(applicationId: String,
                             exerciseType: AssessmentExercise,
                             assessorAssessmentScoresData: Map[String, CandidateScoresAndFeedback],
                             reviewerAssessmentScoresData: Map[String, CandidateScoresAndFeedback]
                            ): String = {

    def evaluateAssessorStatus(scoresAndFeedback: Option[ScoresAndFeedback], exerciseType: AssessmentExercise) = {
      scoresAndFeedback.map { sAndF =>
        (sAndF.submittedDate, sAndF.savedDate) match {
          case (Some(_), _) => "Submitted" // Presence of submitted date indicates the exercise has been submitted
          case (_, Some(_)) => "Saved" // Presence of saved date indicates the exercise has been saved
          case _ =>
            val msg = s"Error generating assessment centre scores report for appId = $applicationId. " +
              s"No saved or submitted date found when evaluating $exerciseType"
            throw new IllegalStateException(msg)
        }
      }.getOrElse("Not entered")
    }

    val reviewerFeedback: Option[CandidateScoresAndFeedback] = reviewerAssessmentScoresData.get(applicationId)
    val assessorFeedback: Option[CandidateScoresAndFeedback] = assessorAssessmentScoresData.get(applicationId)
    val status = (reviewerFeedback, assessorFeedback) match {
      case (Some(rf), _) => "Accepted" // Presence of reviewer feedback means it has been accepted
      case (_, Some(af)) => // We have assessor feedback
        exerciseType match {
          case AssessmentExercise.interview => evaluateAssessorStatus(af.interview, exerciseType)
          case AssessmentExercise.groupExercise => evaluateAssessorStatus(af.groupExercise, exerciseType)
          case AssessmentExercise.writtenExercise => evaluateAssessorStatus(af.writtenExercise, exerciseType)
          case _ =>
            val msg = s"Error generating assessment centre scores report for appId = $applicationId. Exercise type not recognised: $exerciseType"
            throw new IllegalStateException(msg)
        }
      case _ => "Not entered"
    }
    status
  }

  def prevYearCandidatesDetailsReport = Action.async { implicit request =>
    val applicationFut = prevYearCandidatesDetailsRepository.findApplicationDetails()
    for {
      applications <- applicationFut
      result <- enrichPreviousYearCandidateDetails {
        (contactDetails, questionnaireDetails, onlineTestReports, assessmentCenterDetails, assessmentScores) =>
          {
            val header = (
              applications.header ::
              contactDetails.header ::
              questionnaireDetails.header ::
              onlineTestReports.header ::
              assessmentCenterDetails.header ::
              assessmentScores.header :: Nil
            ).mkString(",")

            val records = applications.records.values.map { app =>
              createCandidateInfoBackUpRecord(app, contactDetails, questionnaireDetails,
                onlineTestReports, assessmentCenterDetails, assessmentScores)
            }
            Ok((header :: records.toList).mkString("\n"))
          }
      }
    } yield {
      result
    }
  }

  def streamPrevYearCandidatesDetailsReport = Action.async { implicit request =>
    enrichPreviousYearCandidateDetails {
      (contactDetails, questionnaireDetails, onlineTestReports, assessmentCenterDetails, assessmentScores) =>
        {
          val header = Enumerator(
            (prevYearCandidatesDetailsRepository.applicationDetailsHeader ::
              prevYearCandidatesDetailsRepository.contactDetailsHeader ::
              prevYearCandidatesDetailsRepository.questionnaireDetailsHeader ::
              prevYearCandidatesDetailsRepository.onlineTestReportHeader ::
              prevYearCandidatesDetailsRepository.assessmentCenterDetailsHeader ::
              prevYearCandidatesDetailsRepository.assessmentScoresHeader :: Nil).mkString(",") + "\n"
          )
          val candidatesStream = prevYearCandidatesDetailsRepository.applicationDetailsStream().map { app =>
            createCandidateInfoBackUpRecord(app, contactDetails, questionnaireDetails,
              onlineTestReports, assessmentCenterDetails, assessmentScores) + "\n"
          }
          Ok.chunked(Source.fromPublisher(Streams.enumeratorToPublisher(header.andThen(candidatesStream))))
        }
    }
  }

  // scalastyle:off line.size.limit
  private def enrichPreviousYearCandidateDetails(block: (CsvExtract[String], CsvExtract[String], CsvExtract[String], CsvExtract[String], CsvExtract[String]) => Result) = {
    val candidateDetailsFut = prevYearCandidatesDetailsRepository.findContactDetails()
    val questionnaireDetailsFut = prevYearCandidatesDetailsRepository.findQuestionnaireDetails()
    val onlineTestReportsFut = prevYearCandidatesDetailsRepository.findOnlineTestReports()
    val assessmentCenterDetailsFut = prevYearCandidatesDetailsRepository.findAssessmentCenterDetails()
    val assessmentScoresFut = prevYearCandidatesDetailsRepository.findAssessmentScores()
    for {
      contactDetails <- candidateDetailsFut
      questionnaireDetails <- questionnaireDetailsFut
      onlineTestReports <- onlineTestReportsFut
      assessmentCenterDetails <- assessmentCenterDetailsFut
      assessmentScores <- assessmentScoresFut
    } yield {
      block(contactDetails, questionnaireDetails, onlineTestReports, assessmentCenterDetails, assessmentScores)
    }
  }
  // scalastyle:on

  private def createCandidateInfoBackUpRecord(candidateDetails: CandidateDetailsReportItem, contactDetails: CsvExtract[String],
    questionnaireDetails: CsvExtract[String], onlineTestReports: CsvExtract[String],
    assessmentCenterDetails: CsvExtract[String], assessmentScores: CsvExtract[String]) = {
    (candidateDetails.csvRecord ::
      contactDetails.records.getOrElse(candidateDetails.userId, contactDetails.emptyRecord()) ::
      questionnaireDetails.records.getOrElse(candidateDetails.appId, questionnaireDetails.emptyRecord()) ::
      onlineTestReports.records.getOrElse(candidateDetails.appId, onlineTestReports.emptyRecord()) ::
      assessmentCenterDetails.records.getOrElse(candidateDetails.appId, assessmentCenterDetails.emptyRecord()) ::
      assessmentScores.records.getOrElse(candidateDetails.appId, assessmentScores.emptyRecord()) :: Nil).mkString(",")
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
