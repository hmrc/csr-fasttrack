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

package connectors

import config.WSHttp
import connectors.ExchangeObjects._
import org.joda.time.{ DateTime, LocalDate }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

object CSREmailClient extends CSREmailClient

trait CSREmailClient extends EmailClient {

  import Implicits._
  import config.MicroserviceAppConfig.emailConfig._

  private def sendEmail(to: String, template: String, parameters: Map[String, String])(implicit hc: HeaderCarrier) = {
    val emailRequest = SendEmailRequest(to :: Nil, template, Map("programme" -> "fasttrack") ++ parameters)
    POST(s"$url/fsetfasttrack/email/", emailRequest, Seq()).map(_ => (): Unit)
  }

  override def sendApplicationSubmittedConfirmation(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "csr_app_submit_confirmation", Map("name" -> name))

  override def sendAdjustmentsConfirmation(to: String, name: String, onlineTestsAdjustments: String,
    assessmentCenterAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_fasttrack_adjustments_confirmation",
      Map("name" -> name, "onlineTestsAdjustments" -> onlineTestsAdjustments,
        "assessmentCenterAdjustments" -> assessmentCenterAdjustments))

  override def sendAdjustmentsUpdateConfirmation(to: String, name: String, onlineTestsAdjustments: String,
    assessmentCenterAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(to, "fset_fasttrack_adjustments_changed",
      Map("name" -> name, "onlineTestsAdjustments" -> onlineTestsAdjustments,
        "assessmentCenterAdjustments" -> assessmentCenterAdjustments))

  override def sendOnlineTestInvitation(to: String, name: String, expireDateTime: DateTime)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_online_test_invitation",
      Map("expireDateTime" -> EmailDateFormatter.toExpiryTime(expireDateTime), "name" -> name)
    )

  override def sendOnlineTestExpired(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_online_test_expired",
      Map("name" -> name)
    )

  override def sendOnlineTestResultReady(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_online_test_failed",
      Map("name" -> name)
    )

  override def sendConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime, confirmByDate: LocalDate)(
    implicit
    hc: HeaderCarrier
  ): Future[Unit] =
    sendEmail(
      to,
      "csr_app_confirm_attendance",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )
  override def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_confirm_attendance_reminder",
      Map(
        "name" -> name,
        "assessmentDateTime" -> EmailDateFormatter.toConfirmTime(assessmentDateTime),
        "confirmByDate" -> EmailDateFormatter.toDate(confirmByDate)
      )
    )

  override def sendAssessmentCentreExpired(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] =
    sendEmail(
      to,
      "csr_app_assessment_centre_expired",
      Map("name" -> name)
    )

  override def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      "csr_app_assessment_centre_passed",
      Map("name" -> name)
    )
  }

  override def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      "csr_app_assessment_centre_failed",
      Map("name" -> name)
    )
  }

  override def sendExpiringReminder(template: String, to: String, name: String,
    expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      template,
      Map(
        "name" -> name,
        "expireDateTime" -> EmailDateFormatter.toExpiryTime(expiryDate)
      )
    )
  }

  override def sendAdminWithdrawnEmail(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    sendEmail(
      to,
      "fset_fasttrack_app_withdrawn_by_service_team",
      Map(
        "name" -> name
      )
    )
  }
}

trait EmailClient extends WSHttp {
  def sendApplicationSubmittedConfirmation(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAdjustmentsConfirmation(to: String, name: String, onlineTestsAdjustments: String,
    assessmentCenterAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAdjustmentsUpdateConfirmation(to: String, name: String, onlineTestsAdjustments: String,
    assessmentCenterAdjustments: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendOnlineTestInvitation(to: String, name: String, expireDateTime: DateTime)(implicit hc: HeaderCarrier): Future[Unit]
  def sendOnlineTestExpired(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendOnlineTestResultReady(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit]
  def sendReminderToConfirmAttendance(to: String, name: String, assessmentDateTime: DateTime,
    confirmByDate: LocalDate)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAssessmentCentreExpired(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAssessmentCentrePassed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAssessmentCentreFailed(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
  def sendExpiringReminder(template: String, to: String, name: String, expiryDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit]
  def sendAdminWithdrawnEmail(to: String, name: String)(implicit hc: HeaderCarrier): Future[Unit]
}

object EmailDateFormatter {

  def toDate(date: LocalDate): String = date.toString("d MMMM yyyy")

  def toExpiryTime(dateTime: DateTime): String = {
    dateTime.toString("d MMMM yyyy 'at' h:mma")
      .replace("AM", "am").replace("PM", "pm") // Joda time has no easy way to change the case of AM/PM
  }

  def toConfirmTime(dateTime: DateTime): String = {
    dateTime.toString("d MMMM yyyy, h:mma")
      .replace("AM", "am").replace("PM", "pm") // Joda time has no easy way to change the case of AM/PM
  }
}
