/*
 * Copyright 2018 HM Revenue & Customs
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

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Play.{ configuration, current }
import play.api.libs.json.Json
import uk.gov.hmrc.play.config.{ RunMode, ServicesConfig }

import scala.concurrent.Future

case class FrameworksConfig(yamlFilePath: String)

case class EmailConfig(url: String)

case class UserManagementConfig(url: String)

trait ScheduledJobConfigurable {
  val enabled: Boolean
  val lockId: Option[String]
  val initialDelaySecs: Option[Int]
  val intervalSecs: Option[Int]
}

case class ScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int]
) extends ScheduledJobConfigurable

case class WaitingScheduledJobConfig(
  enabled: Boolean,
  lockId: Option[String],
  initialDelaySecs: Option[Int],
  intervalSecs: Option[Int],
  waitSecs: Option[Int]
) extends ScheduledJobConfigurable

case class CubiksGatewayConfig(url: String, scheduleIds: CubiksGatewaysScheduleIds,
    verbalAndNumericalAssessment: CubiksGatewayVerbalAndNumericalAssessment,
    competenceAssessment: CubiksGatewayStandardAssessment,
    situationalAssessment: CubiksGatewayStandardAssessment,
    reportConfig: ReportConfig, candidateAppUrl: String, emailDomain: String) {

  val nonGisAssessmentIds: List[Int] = List(
    verbalAndNumericalAssessment.assessmentId,
    competenceAssessment.assessmentId,
    situationalAssessment.assessmentId
  )

  val gisAssessmentIds: List[Int] = List(
    competenceAssessment.assessmentId,
    situationalAssessment.assessmentId
  )
}

case class CubiksGatewaysScheduleIds(standard: Int, gis: Int)

trait CubiksGatewayAssessment {
  val assessmentId: Int
  val normId: Int
}

case class CubiksGatewayStandardAssessment(assessmentId: Int, normId: Int) extends CubiksGatewayAssessment

case class CubiksGatewayVerbalAndNumericalAssessment(
  assessmentId: Int,
  normId: Int,
  verbalSectionId: Int,
  verbalTimeInMinutesMinimum: Int,
  verbalTimeInMinutesMaximum: Int,
  numericalSectionId: Int,
  numericalTimeInMinutesMinimum: Int,
  numericalTimeInMinutesMaximum: Int
) extends CubiksGatewayAssessment

case class ReportConfig(xmlReportId: Int, pdfReportId: Int, localeCode: String, suppressValidation: Boolean = false)

case class AssessmentCentresLocationsConfig(yamlFilePath: String)
case class AssessmentCentresConfig(yamlFilePath: String)

case class AssessmentEvaluationMinimumCompetencyLevel(enabled: Boolean, minimumCompetencyLevelScore: Option[Double],
    motivationalFitMinimumCompetencyLevelScore: Option[Double]) {
  require(!enabled || (minimumCompetencyLevelScore.isDefined && motivationalFitMinimumCompetencyLevelScore.isDefined))
}

object AssessmentEvaluationMinimumCompetencyLevel {
  implicit val assessmentEvaluationMinimumCompetencyLevelFormat = Json.format[AssessmentEvaluationMinimumCompetencyLevel]
}

case class DataFixupConfig(
  appId: String,
  forceAppIdCheck: Boolean
) {
  def isValid(id: String): Boolean = if (forceAppIdCheck) { appId == id } else { true }
}

object MicroserviceAppConfig extends ServicesConfig {
  lazy val emailConfig = configuration.underlying.as[EmailConfig]("microservice.services.email")
  lazy val frameworksConfig = configuration.underlying.as[FrameworksConfig]("microservice.frameworks")
  lazy val userManagementConfig = configuration.underlying.as[UserManagementConfig]("microservice.services.user-management")
  lazy val cubiksGatewayConfig = configuration.underlying.as[CubiksGatewayConfig]("microservice.services.cubiks-gateway")
  lazy val maxNumberOfDocuments = configuration.underlying.as[Int]("maxNumberOfDocuments")
  lazy val sendInvitationJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.send-invitation-job")
  lazy val expireOnlineTestJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.expiry-job")
  lazy val sendOnlineTestResultJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.send-online-test-result-job")
  lazy val firstReminderOnlineTestJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.first-reminder-expiry-tests-job")
  lazy val secondReminderOnlineTestJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.second-reminder-expiry-tests-job")
  lazy val retrieveResultsJobConfig =
    configuration.underlying.as[WaitingScheduledJobConfig]("scheduling.online-testing.retrieve-results-job")
  lazy val retrieveOnlineTestPDFReportJobConfig =
    configuration.underlying.as[WaitingScheduledJobConfig]("scheduling.online-testing.retrieve-pdf-report-job")
  lazy val evaluateCandidateScoreJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.online-testing.evaluate-candidate-score-job")
  lazy val confirmAttendanceReminderJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.confirm-attendance-reminder-job")
  lazy val evaluateAssessmentScoreJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.evaluate-assessment-score-job")
  lazy val notifyAssessmentCentrePassedOrFailedJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.notify-assessment-centre-passed-or-failed-job")

  lazy val expireAllocationJobConfig =
    configuration.underlying.as[ScheduledJobConfig]("scheduling.allocation-expiry-job")

  lazy val assessmentCentresLocationsConfig =
    configuration.underlying.as[AssessmentCentresLocationsConfig]("scheduling.online-testing.assessment-centres-locations")
  lazy val assessmentCentresConfig =
    configuration.underlying.as[AssessmentCentresConfig]("scheduling.online-testing.assessment-centres")
  lazy val assessmentEvaluationMinimumCompetencyLevelConfig =
    configuration.underlying
      .as[AssessmentEvaluationMinimumCompetencyLevel]("microservice.services.assessment-evaluation.minimum-competency-level")

  lazy val progressToAssessmentCentreConfig =
    configuration.underlying.as[DataFixupConfig]("microservice.dataFixup.progressToAssessmentCentre")
}
