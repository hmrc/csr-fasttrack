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

package model

import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

import scala.language.implicitConversions

object ApplicationStatuses extends Enum {

  type ApplicationStatus = EnumVal

  case class EnumVal private[ApplicationStatuses](name: String) extends Value

  implicit def appStatusToString(appStatus: EnumVal): String = appStatus.name

  object EnumVal {
    implicit val applicationStatusFormat = new Format[EnumVal] {
      def reads(json: JsValue) = JsSuccess(ApplicationStatuses.values.find(_.name == json.as[String].toUpperCase)
        .getOrElse(throw NoEnumWithNameException(json.as[String])))

      def writes(appStatus: EnumVal) = JsString(appStatus.name)
    }

    implicit object BSONEnumHandler extends BSONHandler[BSONString, EnumVal] {
      def read(doc: BSONString) = ApplicationStatuses.values.find(_.name == doc.value.toUpperCase)
          .getOrElse(throw NoEnumWithNameException(doc.value.toUpperCase))

      def write(appStatus: EnumVal) = BSON.write(appStatus.name)
    }
  }


  val Created = EnumVal("CREATED")

  val Withdrawn = EnumVal("WITHDRAWN")
  val InProgress = EnumVal("IN_PROGRESS")
  val Submitted = EnumVal("SUBMITTED")

  val OnlineTestInvited = EnumVal("ONLINE_TEST_INVITED")
  val OnlineTestStarted = EnumVal("ONLINE_TEST_STARTED")
  val OnlineTestExpired = EnumVal("ONLINE_TEST_EXPIRED")
  val OnlineTestCompleted = EnumVal("ONLINE_TEST_COMPLETED")
  val OnlineTestFailed = EnumVal("ONLINE_TEST_FAILED")
  val OnlineTestFailedNotified = EnumVal("ONLINE_TEST_FAILED_NOTIFIED")
  val AwaitingOnlineTestReevaluation = EnumVal("AWAITING_ONLINE_TEST_RE_EVALUATION")
  val AwaitingAllocation = EnumVal("AWAITING_ALLOCATION")
  val FailedToAttend = EnumVal("FAILED_TO_ATTEND")
  val AllocationUnconfirmed = EnumVal("ALLOCATION_UNCONFIRMED")
  val AllocationConfirmed = EnumVal("ALLOCATION_CONFIRMED")

  val AssessmentScoresEntered = EnumVal("ASSESSMENT_SCORES_ENTERED")
  val AssessmentScoresAccepted = EnumVal("ASSESSMENT_SCORES_ACCEPTED")
  val AwaitingAssessmentCentreReevaluation = EnumVal("AWAITING_ASSESSMENT_CENTRE_RE_EVALUATION")
  val AssessmentCentrePassed = EnumVal("ASSESSMENT_CENTRE_PASSED")
  val AssessmentCentrePassedNotified = EnumVal("ASSESSMENT_CENTRE_PASSED_NOTIFIED")
  val AssessmentCentreFailed = EnumVal("ASSESSMENT_CENTRE_FAILED")
  val AssessmentCentreFailedNotified = EnumVal("ASSESSMENT_CENTRE_FAILED_NOTIFIED")
}
