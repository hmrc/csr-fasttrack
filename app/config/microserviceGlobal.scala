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

package config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{ Application, Logger, Play }
import scheduler.allocation.{ ConfirmAttendanceReminderJob, ExpireAllocationJob }
import scheduler.assessment._
import scheduler.onlinetesting._
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig }
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.scheduling.{ RunningOfScheduledJobs, ScheduledJob }
import uk.gov.hmrc.play.microservice.filters.{ AuditFilter, LoggingFilter, MicroserviceFilterSupport }

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) =
    false // Disable implicit _inbound_ auditing.
  override def appNameConfiguration = Play.current.configuration
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

trait Scheduler extends RunningOfScheduledJobs {
  import config.MicroserviceAppConfig._
  val sendInvitationJob: SendInvitationJob
  val expireOnlineTestJob: ExpireOnlineTestJob
  val sendOnlineTestResultJob: SendOnlineTestResultJob
  val firstReminderExpiryTestsJob: ReminderExpiryTestsJob
  val secondReminderExpiryTestsJob: ReminderExpiryTestsJob
  val retrieveOnlineTestPDFReportJob: RetrieveOnlineTestPDFReportJob
  val evaluationCandidateScoreJob: EvaluateCandidateScoreJob
  val confirmAttendanceReminderJob: ConfirmAttendanceReminderJob
  val expireAllocationJob: ExpireAllocationJob
  val evaluateAssessmentScoreJob: EvaluateAssessmentScoreJob
  val notifyAssessmentCentrePassedOrFailedJob: NotifyAssessmentCentrePassedOrFailedJob

  private lazy val maybeSendInvitationJob: Option[ScheduledJob] =
    if (sendInvitationJobConfigValues.enabled) Some(sendInvitationJob) else {
      Logger.warn("Send invitation job is disabled")
      None
    }

  private lazy val maybeExpireOnlineTestJob: Option[ScheduledJob] =
    if (expireOnlineTestJobConfigValues.enabled) Some(expireOnlineTestJob) else {
      Logger.warn("Expire online test job is disabled")
      None
    }

  private lazy val maybeSendOnlineTestResultJob: Option[ScheduledJob] =
    if (sendOnlineTestResultJobConfigValues.enabled) Some(sendOnlineTestResultJob) else {
      Logger.warn("Send online test result job is disabled")
      None
    }

  private lazy val maybeFirstReminderOnlineTestJob: Option[ScheduledJob] =
    if (firstReminderOnlineTestJobConfigValues.enabled) Some(firstReminderExpiryTestsJob) else {
      Logger.warn("first reminder online test job is disabled")
      None
    }

  private lazy val maybeSecondReminderOnlineTestJob: Option[ScheduledJob] =
    if (secondReminderOnlineTestJobConfigValues.enabled) Some(secondReminderExpiryTestsJob) else {
      Logger.warn("second reminder online test job is disabled")
      None
    }

  private lazy val maybeRetrieveOnlineTestPDFReportJob: Option[ScheduledJob] =
    if (retrieveOnlineTestPDFReportJobConfigValues.enabled) Some(retrieveOnlineTestPDFReportJob) else {
      Logger.warn("Retrieve Online Test PDF report job is disabled")
      None
    }

  private lazy val maybeEvaluateCandidateScoreJob: Option[ScheduledJob] =
    if (evaluateCandidateScoreJobConfigValues.enabled) Some(evaluationCandidateScoreJob) else {
      Logger.warn("Evaluate Candidate Score job is disabled")
      None
    }

  private lazy val maybeConfirmAttendanceReminderJob: Option[ScheduledJob] =
    if (confirmAttendanceReminderJobConfigValues.enabled) Some(confirmAttendanceReminderJob) else {
      Logger.warn("confirm attendance reminder job is disabled")
      None
    }

  private lazy val maybeExpireAllocationJob: Option[ScheduledJob] =
    if (expireAllocationJobConfigValues.enabled) Some(expireAllocationJob) else {
      Logger.warn("confirm attendance reminder job is disabled")
      None
    }

  private lazy val maybeEvaluateAssessmentScoreJob: Option[ScheduledJob] =
    if (evaluateAssessmentScoreJobConfigValues.enabled) Some(evaluateAssessmentScoreJob) else {
      Logger.warn("evaluate assessment score job is disabled")
      None
    }

  private lazy val maybeNotifyAssessmentCentrePassedOrFailedJob: Option[ScheduledJob] =
    if (notifyAssessmentCentrePassedOrFailedJobConfigValues.enabled) Some(notifyAssessmentCentrePassedOrFailedJob) else {
      Logger.warn("notify assessment centre passed or failed job is disabled")
      None
    }

  private[config] def sendInvitationJobConfigValues = sendInvitationJobConfig
  private[config] def expireOnlineTestJobConfigValues = expireOnlineTestJobConfig
  private[config] def sendOnlineTestResultJobConfigValues = sendOnlineTestResultJobConfig
  private[config] def firstReminderOnlineTestJobConfigValues = firstReminderOnlineTestJobConfig
  private[config] def secondReminderOnlineTestJobConfigValues = secondReminderOnlineTestJobConfig
  private[config] def retrieveResultsJobConfigValues = retrieveResultsJobConfig
  private[config] def retrieveOnlineTestPDFReportJobConfigValues = retrieveOnlineTestPDFReportJobConfig
  private[config] def evaluateCandidateScoreJobConfigValues = evaluateCandidateScoreJobConfig
  private[config] def confirmAttendanceReminderJobConfigValues = confirmAttendanceReminderJobConfig
  private[config] def expireAllocationJobConfigValues = expireAllocationJobConfig
  private[config] def evaluateAssessmentScoreJobConfigValues = evaluateAssessmentScoreJobConfig
  private[config] def notifyAssessmentCentrePassedOrFailedJobConfigValues = notifyAssessmentCentrePassedOrFailedJobConfig

  lazy val scheduledJobs = List(maybeSendInvitationJob, maybeExpireOnlineTestJob, maybeSendOnlineTestResultJob,
    maybeRetrieveOnlineTestPDFReportJob, maybeEvaluateCandidateScoreJob, maybeConfirmAttendanceReminderJob, maybeExpireAllocationJob,
    maybeEvaluateAssessmentScoreJob, maybeNotifyAssessmentCentrePassedOrFailedJob, maybeFirstReminderOnlineTestJob,
    maybeSecondReminderOnlineTestJob).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application) = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None

  lazy val sendInvitationJob: SendInvitationJob = SendInvitationJob
  lazy val expireOnlineTestJob: ExpireOnlineTestJob = ExpireOnlineTestJob
  lazy val sendOnlineTestResultJob: SendOnlineTestResultJob = SendOnlineTestResultJob
  lazy val firstReminderExpiryTestsJob: ReminderExpiryTestsJob = FirstReminderExpiryTestsJob
  lazy val secondReminderExpiryTestsJob: ReminderExpiryTestsJob = SecondReminderExpiryTestsJob
  lazy val retrieveOnlineTestPDFReportJob: RetrieveOnlineTestPDFReportJob = RetrieveOnlineTestPDFReportJob
  lazy val evaluationCandidateScoreJob: EvaluateCandidateScoreJob = EvaluateCandidateScoreJob
  lazy val confirmAttendanceReminderJob: ConfirmAttendanceReminderJob = ConfirmAttendanceReminderJob
  lazy val expireAllocationJob: ExpireAllocationJob = ExpireAllocationJob
  lazy val evaluateAssessmentScoreJob: EvaluateAssessmentScoreJob = EvaluateAssessmentScoreJob
  lazy val notifyAssessmentCentrePassedOrFailedJob: NotifyAssessmentCentrePassedOrFailedJob = NotifyAssessmentCentrePassedOrFailedJob
}
