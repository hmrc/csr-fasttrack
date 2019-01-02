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

package model.persisted

import config.CubiksGatewayConfig
import org.joda.time.DateTime
import play.api.libs.json.Json
import reactivemongo.bson.{ BSONDocument, BSONHandler, Macros }
import repositories.BSONDateTimeHandler

case class CubiksTestProfile(
    cubiksUserId: Int,
    participantScheduleId: Int,
    invitationDate: DateTime,
    expirationDate: DateTime,
    onlineTestUrl: String,
    token: String,
    isOnlineTestEnabled: Boolean = true,
    startedDateTime: Option[DateTime] = None,
    completedDateTime: Option[DateTime] = None,
    xmlReportSaved: Boolean = false,
    pdfReportSaved: Boolean = false,
    completed: Option[List[Int]] = None
) {

  // There are two ways to determine if the test profile is completed
  // 1. If it has all assessments id in the "completed" List
  // 2. If there is completedDate set (by token completion)
  // There may be the case where completed List does not have all assessments,
  // but the test is considered as completed, as it may be completed by token
  def hasAllAssessmentsCompleted(allAssessmentIds: List[Int]): Boolean = {
    val completedAssessments = completed.getOrElse(Nil)
    allAssessmentIds.nonEmpty && allAssessmentIds.forall(id => completedAssessments.contains(id))
  }
}

object CubiksTestProfile {
  implicit val format = Json.format[CubiksTestProfile]
  implicit val bsonHandler: BSONHandler[BSONDocument, CubiksTestProfile] = Macros.handler[CubiksTestProfile]
}
