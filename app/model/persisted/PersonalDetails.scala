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

import org.joda.time.LocalDate
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class PersonalDetails(
    firstName: String,
    lastName: String,
    preferredName: String,
    dateOfBirth: LocalDate,
    aLevel: Boolean = false,
    stemLevel: Boolean = false,
    civilServant: Boolean = false,
    department: Option[String] = None,
    departmentOther: Option[String] = None
) {
  require((civilServant && department.isDefined) || (!civilServant && department.isEmpty), "Civil Servants must have a department")
}

case object PersonalDetails {
  import repositories.BSONLocalDateHandler

  implicit val persistedPersonalDetailsFormats = Json.format[PersonalDetails]
  implicit val persistedPersonalDetailsMacro = Macros.handler[PersonalDetails]
}
