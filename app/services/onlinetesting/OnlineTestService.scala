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

package services.onlinetesting

import _root_.services.AuditService
import config.CubiksGatewayConfig
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, CubiksGatewayClient, EmailClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.ApplicationStatuses
import model.OnlineTestCommands._
import model.PersistedObjects.CandidateTestReport
import model.exchange.OnlineTest
import model.persisted.{ ApplicationAssistanceDetails, CubiksTestProfile }
import org.joda.time.DateTime
import play.api.Logger
import play.libs.Akka
import repositories._
import repositories.application.{ AssistanceDetailsRepository, GeneralApplicationRepository, OnlineTestRepository }
import model.exchange.CubiksTestResultReady

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

object OnlineTestService extends OnlineTestService {
  import config.MicroserviceAppConfig._
  val appRepository = applicationRepository
  val cdRepository = contactDetailsRepository
  val otRepository = onlineTestRepository
  val otprRepository = onlineTestPDFReportRepository
  val trRepository = testReportRepository
  val adRepository = assistanceDetailsRepository
  val cubiksGatewayClient = CubiksGatewayClient
  val tokenFactory = UUIDFactory
  val onlineTestInvitationDateFactory = DateTimeFactory
  val emailClient = CSREmailClient
  val auditService = AuditService
  val gatewayConfig = cubiksGatewayConfig
}

trait OnlineTestService {
  implicit def headerCarrier = new HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val appRepository: GeneralApplicationRepository
  val cdRepository: ContactDetailsRepository
  val otRepository: OnlineTestRepository
  val otprRepository: OnlineTestPDFReportRepository
  val trRepository: TestReportRepository
  val adRepository: AssistanceDetailsRepository
  val cubiksGatewayClient: CubiksGatewayClient
  val emailClient: EmailClient
  val auditService: AuditService
  val tokenFactory: UUIDFactory
  val onlineTestInvitationDateFactory: DateTimeFactory
  val gatewayConfig: CubiksGatewayConfig

  def norms: List[ReportNorm] = Seq(
    gatewayConfig.competenceAssessment,
    gatewayConfig.situationalAssessment,
    gatewayConfig.verbalAndNumericalAssessment
  ).map(a => ReportNorm(a.assessmentId, a.normId)).toList

  def nextApplicationReadyForOnlineTesting(): Future[Option[OnlineTestApplication]] = {
    otRepository.nextApplicationReadyForOnlineTesting
  }

  def getOnlineTest(userId: String): Future[OnlineTest] = {
    for {
      onlineTestDetails <- otRepository.getCubiksTestProfile(userId)
      candidate <- appRepository.findCandidateByUserId(userId)
      hasReport <- otprRepository.hasReport(candidate.get.applicationId.get)
    } yield {
      OnlineTest(
        onlineTestDetails.cubiksUserId,
        onlineTestDetails.invitationDate,
        onlineTestDetails.expirationDate,
        onlineTestDetails.onlineTestUrl,
        s"${onlineTestDetails.token}@${gatewayConfig.emailDomain}}",
        onlineTestDetails.isOnlineTestEnabled,
        hasReport,
        onlineTestDetails.startedDateTime,
        onlineTestDetails.completedDateTime
      )
    }
  }

  def registerAndInviteApplicant(application: OnlineTestApplication): Future[Unit] = {
    val token = tokenFactory.generateUUID()

    val invitationProcess = for {
      userId <- registerApplicant(application, token)
      invitation <- inviteApplicant(application, token, userId)
      (invitationDate, expirationDate) = onlineTestDates
      _ <- trRepository.remove(application.applicationId)
      _ <- otprRepository.remove(application.applicationId)
      emailAddress <- candidateEmailAddress(application)
      _ <- emailInviteToApplicant(application, emailAddress, invitationDate)
    } yield CubiksTestProfile(
      invitation.userId,
      invitation.participantScheduleId,
      invitationDate,
      expirationDate,
      invitation.authenticateUrl,
      token
    )

    invitationProcess.flatMap(
      markAsCompleted(application)
    )
  }

  def tryToDownloadOnlineTestResult(cubiksUserId: Int, testResultReady: CubiksTestResultReady): Future[Unit] = {
    (testResultReady.reportId, testResultReady.requestReportId) match {
      case (Some(reportId), Some(requestReportId)) if reportId == gatewayConfig.reportConfig.xmlReportId =>
        downloadAndValidateXmlReport(cubiksUserId, requestReportId)
      case (_, None) =>
        Logger.warn(s"Cannot download online test report without request report Id for cubiks user id=$cubiksUserId")
        Future.successful(())
      case (None, _) =>
        Logger.warn(s"Cannot download online test report without report Id for cubiks user id=$cubiksUserId")
        Future.successful(())
      case _ =>
        Logger.debug(s"Ignoring the callback with non-xml report id: ${testResultReady.reportId}")
        Future.successful(())
    }
  }

  private def downloadAndValidateXmlReport(cubiksUserId: Int, reportId: Int): Future[Unit] = {
    import ApplicationStatuses._

    def saveOnlineTestReport(appId: String, candidateTestReport: CandidateTestReport) = {
      for {
        _ <- trRepository.saveOnlineTestReport(candidateTestReport)
        _ <- otRepository.updateXMLReportSaved(appId)
      } yield {
        Logger.info(s"Report has been saved for applicationId: $appId")
        auditApp("OnlineTestXmlReportSaved", appId)
      }
    }

    (for {
      app <- adRepository.findApplication(cubiksUserId)
      testResultMap <- cubiksGatewayClient.downloadXmlReport(reportId)
    } yield {
      app.applicationStatus match {
        case OnlineTestInvited | OnlineTestStarted | OnlineTestCompleted =>
          val appId = app.applicationId
          val isGis = app.assistanceDetails.isGis
          val candidateTestReport = toCandidateTestReport(appId, testResultMap, isGis)
          if (candidateTestReport.isValid(isGis)) {
            saveOnlineTestReport(appId, candidateTestReport)
          } else {
            Logger.warn(s"Ignoring invalid/partial online test report with id=$reportId for cubiks user id=$cubiksUserId")
            Future.successful(())
          }
        case appStatus =>
          Logger.warn(s"Ignoring online test report callback for application status=$appStatus" +
            s" for cubiks user id=$cubiksUserId")
          Future.successful(())
      }
    }).flatMap(identity)
  }

  def startOnlineTest(cubiksUserId: Int): Future[Unit] = {
    otRepository.getCubiksTestProfile(cubiksUserId) flatMap { cubiksTest =>
      if (cubiksTest.startedDateTime.isDefined) {
        Future.successful(())
      } else {
        otRepository.startOnlineTest(cubiksUserId)
      }
    }
  }

  private def registerApplicant(application: OnlineTestApplication, token: String): Future[Int] = {
    val preferredName = CubiksSanitizer.sanitizeFreeText(application.preferredName)
    val registerApplicant = RegisterApplicant(preferredName, "", token + "@" + gatewayConfig.emailDomain)
    cubiksGatewayClient.registerApplicant(registerApplicant).map { registration =>
      audit("UserRegisteredForOnlineTest", application.userId)
      registration.userId
    }
  }

  private def inviteApplicant(application: OnlineTestApplication, token: String, userId: Int): Future[Invitation] = {
    val scheduleId = getScheduleIdForApplication(application)
    val inviteApplicant = buildInviteApplication(application, token, userId, scheduleId)
    cubiksGatewayClient.inviteApplicant(inviteApplicant).map { invitation =>
      audit("UserInvitedToOnlineTest", application.userId)
      invitation
    }
  }

  private def emailInviteToApplicant(application: OnlineTestApplication, emailAddress: String, invitationDate: DateTime): Future[Unit] = {
    val expirationDate = calculateExpireDate(invitationDate)
    val preferredName = application.preferredName
    emailClient.sendOnlineTestInvitation(emailAddress, preferredName, expirationDate).map { _ =>
      audit("OnlineTestInvitationEmailSent", application.userId, Some(emailAddress))
    }
  }

  private def markAsCompleted(application: OnlineTestApplication)(onlineTestProfile: CubiksTestProfile): Future[Unit] = {
    otRepository.storeOnlineTestProfileAndUpdateStatusToInvite(application.applicationId, onlineTestProfile).map { _ =>
      audit("OnlineTestInvitationProcessComplete", application.userId)
    }
  }

  private def candidateEmailAddress(application: OnlineTestApplication): Future[String] =
    cdRepository.find(application.userId).map(_.email)

  private def onlineTestDates: (DateTime, DateTime) = {
    val invitationDate = onlineTestInvitationDateFactory.nowLocalTimeZone
    val expirationDate = calculateExpireDate(invitationDate)
    (invitationDate, expirationDate)
  }

  private def audit(event: String, userId: String, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for user $userId")

    auditService.logEventNoRequest(
      event,
      Map("userId" -> userId) ++ emailAddress.map("email" -> _).toMap
    )
  }

  private def auditApp(event: String, appId: String, emailAddress: Option[String] = None): Unit = {
    Logger.info(s"$event for the application $appId")

    auditService.logEventNoRequest(
      event,
      Map("applicationId" -> appId)
    )
  }

  private def calculateExpireDate(invitationDate: DateTime) = invitationDate.plusDays(7)

  private[services] def getScheduleIdForApplication(application: OnlineTestApplication) = {
    if (application.guaranteedInterview) {
      gatewayConfig.scheduleIds.gis
    } else {
      gatewayConfig.scheduleIds.standard
    }
  }

  private[services] def getTimeAdjustments(application: OnlineTestApplication): List[TimeAdjustments] = {
    (for {
      extraTimeNeededForVerbal <- application.adjustmentDetail.flatMap(_.extraTimeNeeded)
      extraTimeNeededForNumerical <- application.adjustmentDetail.flatMap(_.extraTimeNeededNumerical)
    } yield {
      val config = gatewayConfig.verbalAndNumericalAssessment
      val verbalTimeAdjustment = TimeAdjustments(config.assessmentId, config.verbalSectionId,
        getAdjustedTime(
          config.verbalTimeInMinutesMinimum,
          config.verbalTimeInMinutesMaximum,
          extraTimeNeededForVerbal
        ))

      val numericalTimeAdjustment = TimeAdjustments(config.assessmentId, config.numericalSectionId,
        getAdjustedTime(
          config.numericalTimeInMinutesMinimum,
          config.numericalTimeInMinutesMaximum,
          extraTimeNeededForNumerical
        ))
      List(verbalTimeAdjustment, numericalTimeAdjustment)
    }).getOrElse(Nil)
  }

  private[services] def getAdjustedTime(minimum: Int, maximum: Int, percentageToIncrease: Int): Int = {
    val adjustedValue = math.ceil(minimum.toDouble * (1 + percentageToIncrease / 100.0))
    math.min(adjustedValue, maximum).toInt
  }

  private[services] def buildInviteApplication(application: OnlineTestApplication, token: String, userId: Int, scheduleId: Int) = {
    val onlineTestCompletedUrl = gatewayConfig.candidateAppUrl + s"/fset-fast-track/online-tests/by-token/$token/complete"
    if (application.guaranteedInterview) {
      InviteApplicant(scheduleId, userId, onlineTestCompletedUrl, None)
    } else {
      val timeAdjustments = getTimeAdjustments(application)
      InviteApplicant(scheduleId, userId, onlineTestCompletedUrl, None, timeAdjustments)
    }
  }

  private def toCandidateTestReport(appId: String, tests: Map[String, TestResult], isGis: Boolean) = {
    val VerbalTestName = "Logiks Verbal and Numerical (Intermediate) - Verbal"
    val NumericalTestName = "Logiks Verbal and Numerical (Intermediate) - Numerical"
    val CompetencyTestName = "Cubiks Factors"
    val SituationalTestName = "Civil Service Fast Track Apprentice SJQ"

    val candidateOnlineTestReport = CandidateTestReport(
      appId, "XML",
      tests.get(CompetencyTestName),
      tests.get(NumericalTestName),
      tests.get(VerbalTestName),
      tests.get(SituationalTestName)
    )

    isGis && tests.size > 2 match {
      case true => Logger.warn(s"Remove unwanted test results for gis application $appId")
        candidateOnlineTestReport.copy(numerical = None, verbal = None)
      case false =>
        candidateOnlineTestReport
    }
  }
}
