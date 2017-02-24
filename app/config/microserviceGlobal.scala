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

package config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{ Application, Logger, Play }
import scheduler.allocation.ConfirmAttendanceReminderJob
import scheduler.assessment._
import scheduler.onlinetesting._
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.config.{ AppName, ControllerConfig }
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.scheduling.{ RunningOfScheduledJobs, ScheduledJob }

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) =
    false // Disable implicit _inbound_ auditing.
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

trait Scheduler extends RunningOfScheduledJobs {
  import config.MicroserviceAppConfig._

  private lazy val sendInvitationJob: Option[ScheduledJob] =
    if (sendInvitationJobConfigValues.enabled) Some(SendInvitationJob) else {
      Logger.warn("Send invitation job is disabled")
      None
    }

  private lazy val expireOnlineTestJob: Option[ScheduledJob] =
    if (expireOnlineTestJobConfigValues.enabled) Some(ExpireOnlineTestJob) else {
      Logger.warn("Expire online test job is disabled")
      None
    }

  private lazy val sendOnlineTestResultJob: Option[ScheduledJob] =
    if (sendOnlineTestResultJobConfigValues.enabled) Some(SendOnlineTestResultJob) else {
      Logger.warn("Send online test result job is disabled")
      None
    }

  private lazy val firstReminderOnlineTestJob: Option[ScheduledJob] =
    if (firstReminderOnlineTestJobConfigValues.enabled) Some(FirstReminderExpiryTestsJob) else {
      Logger.warn("first reminder online test job is disabled")
      None
    }

  private lazy val secondReminderOnlineTestJob: Option[ScheduledJob] =
    if (secondReminderOnlineTestJobConfigValues.enabled) Some(SecondReminderExpiryTestsJob) else {
      Logger.warn("second reminder online test job is disabled")
      None
    }

  private lazy val retrieveOnlineTestPDFReportJob: Option[ScheduledJob] =
    if (retrieveOnlineTestPDFReportJobConfigValues.enabled) Some(RetrieveOnlineTestPDFReportJob) else {
      Logger.warn("Retrieve Online Test PDF report job is disabled")
      None
    }

  private lazy val evaluateCandidateScoreJob: Option[ScheduledJob] =
    if (evaluateCandidateScoreJobConfigValues.enabled) Some(EvaluateCandidateScoreJob) else {
      Logger.warn("Evaluate Candidate Score job is disabled")
      None
    }

  private lazy val confirmAttendanceReminderJob: Option[ScheduledJob] =
    if (confirmAttendanceReminderJobConfigValues.enabled) Some(ConfirmAttendanceReminderJob) else {
      Logger.warn("confirm attendance reminder job is disabled")
      None
    }

  private lazy val evaluateAssessmentScoreJob: Option[ScheduledJob] =
    if (evaluateAssessmentScoreJobConfigValues.enabled) Some(EvaluateAssessmentScoreJob) else {
      Logger.warn("evaluate assessment score job is disabled")
      None
    }

  private lazy val notifyAssessmentCentrePassedOrFailedJob: Option[ScheduledJob] =
    if (notifyAssessmentCentrePassedOrFailedJobConfigValues.enabled) Some(NotifyAssessmentCentrePassedOrFailedJob) else {
      Logger.warn("notify assessment centre passsed or failed job is disabled")
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
  private[config] def evaluateAssessmentScoreJobConfigValues = evaluateAssessmentScoreJobConfig
  private[config] def notifyAssessmentCentrePassedOrFailedJobConfigValues = notifyAssessmentCentrePassedOrFailedJobConfig

  lazy val scheduledJobs = List(sendInvitationJob, expireOnlineTestJob, sendOnlineTestResultJob,
    retrieveOnlineTestPDFReportJob, evaluateCandidateScoreJob, confirmAttendanceReminderJob,
    evaluateAssessmentScoreJob, notifyAssessmentCentrePassedOrFailedJob, firstReminderOnlineTestJob, secondReminderOnlineTestJob).flatten
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with Scheduler {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application) = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
