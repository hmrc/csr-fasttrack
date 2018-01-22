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

package model

import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class Adjustments(
  typeOfAdjustments: Option[List[String]],
  adjustmentsConfirmed: Option[Boolean] = None,
  onlineTests: Option[AdjustmentDetail] = None,
  assessmentCenter: Option[AdjustmentDetail] = None
)

case class AdjustmentDetail(
  extraTimeNeeded: Option[Int] = None,
  extraTimeNeededNumerical: Option[Int] = None,
  otherInfo: Option[String] = None
)

case class AdjustmentsComment(
  comment: String
)

object AdjustmentDetail {
  implicit val adjustmentDetailFormat = Json.format[AdjustmentDetail]
  implicit val adjustmentDetailMacro = Macros.handler[AdjustmentDetail]
}

object AdjustmentsComment {
  implicit val adjustmentsCommentFormat = Json.format[AdjustmentsComment]
  implicit val adjustmentsCommentMacro = Macros.handler[AdjustmentsComment]
}

object Adjustments {
  implicit val adjustmentsFormat = Json.format[Adjustments]
  implicit val adjustmentsMacro = Macros.handler[Adjustments]
}

