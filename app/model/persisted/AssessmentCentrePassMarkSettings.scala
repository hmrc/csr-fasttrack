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

import model.Scheme
import play.api.libs.json.Json
import reactivemongo.bson.Macros

case class AssessmentCentrePassMarkSettings(schemes: List[AssessmentCentrePassMarkScheme],
                                            info: AssessmentCentrePassMarkInfo) {

  def isDefined = schemes.nonEmpty && schemes.forall(_.overallPassMarks.isDefined)

  def currentVersion = info.version

  def toPassmarkResponse = AssessmentCentrePassMarkSettingsResponse(schemes, Some(info))
}

object AssessmentCentrePassMarkSettings {
  implicit val assessmentCentrePassMarkSettingsFormat = Json.format[AssessmentCentrePassMarkSettings]
  implicit val assessmentCentrePassMarkSettingsHandler = Macros.handler[AssessmentCentrePassMarkSettings]

  val EmptyPassmarkResponse = AssessmentCentrePassMarkSettingsResponse(
    Scheme.AllSchemes.map(s => AssessmentCentrePassMarkScheme(s)),
    None
  )

}

case class AssessmentCentrePassMarkSettingsResponse(schemes: List[AssessmentCentrePassMarkScheme],
                                                    info: Option[AssessmentCentrePassMarkInfo])

object AssessmentCentrePassMarkSettingsResponse {
  implicit val assessmentCentrePassMarkSettingsResponseFormat = Json.format[AssessmentCentrePassMarkSettingsResponse]
}

