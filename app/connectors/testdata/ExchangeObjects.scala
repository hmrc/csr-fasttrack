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

package connectors.testdata

import model.Commands.AssessmentCentreAllocation
import model.PersistedObjects.ContactDetails
import model.exchange.AssistanceDetails
import model.{ ApplicationStatuses, AssessmentCentreIndicator }
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import play.api.libs.json.Json
import repositories.SchemeInfo

object ExchangeObjects {

  case class DataGenerationResponse(
    generationId: Int,
    userId: String,
    applicationId: Option[String],
    applicationStatus: ApplicationStatuses.EnumVal,
    email: String,
    firstName: String,
    lastName: String,
    onlineTestProfile: Option[OnlineTestProfileResponse] = None,
    applicationAssessment: Option[AssessmentCentreAllocation] = None,
    contactDetails: Option[ContactDetails] = None,
    personalDetails: Option[PersonalDetails] = None,
    assistanceDetails: Option[AssistanceDetails] = None,
    schemes: Option[List[SchemeInfo]] = None,
    schemeLocations: Option[List[String]] = None,
    assessmentCentreIndicator: Option[AssessmentCentreIndicator] = None,
    allocationExpireDate: Option[LocalDate] = None
  )

  case class OnlineTestProfileResponse(cubiksUserId: Int, token: String, onlineTestUrl: String)

  object Implicits {
    import model.Commands.Implicits._
    import model.PersistedObjects.Implicits._
    implicit val onlineTestProfileResponseFormat = Json.format[OnlineTestProfileResponse]
    implicit val dataGenerationResponseFormat = Json.format[DataGenerationResponse]
  }
}
