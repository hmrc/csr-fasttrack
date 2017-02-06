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

package model.exchange.testdata

import model.Scheme.Scheme
import play.api.libs.json.Json

case class CreateCandidateInStatusRequest(statusData: StatusDataRequest = new StatusDataRequest,
                                          personalData: Option[PersonalDataRequest],
                                          assistanceDetailsData: Option[AssistanceDetailsDataRequest],
                                          schemesData: Option[SchemesDataRequest],
                                          schemeLocationsData: Option[SchemeLocationsDataRequest],
                                          isCivilServant: Option[Boolean],
                                          hasDegree: Option[Boolean],
                                          region: Option[String],
                                          loc1scheme1EvaluationResult: Option[String],
                                          loc1scheme2EvaluationResult: Option[String],
                                          confirmedAllocation: Option[Boolean],
                                          onlineTestScores: Option[OnlineTestScoresRequest])

object CreateCandidateInStatusRequest {
  implicit val createCandidateInStatusRequestFormat = Json.format[CreateCandidateInStatusRequest]

  def create(status: String, progressStatus: Option[String]): CreateCandidateInStatusRequest = {
    CreateCandidateInStatusRequest(
      statusData = StatusDataRequest(applicationStatus = status, progressStatus = progressStatus),
      assistanceDetailsData = None,
      personalData = None,
      schemesData = None,
      schemeLocationsData = None,
      isCivilServant = None,
      hasDegree = None,
      region = None,
      loc1scheme1EvaluationResult = None,
      loc1scheme2EvaluationResult = None,
      confirmedAllocation = None,
      onlineTestScores = None
    )
  }
}
