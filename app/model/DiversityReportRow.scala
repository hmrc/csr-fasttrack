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

import model.Scheme.Scheme
import model.ReportExchangeObjects.{ DiversityReportDiversityAnswers, ApplicationForCandidateProgressReport }
import play.api.libs.json.Json
/*
case class DiversityReportRow(progress: String,
                              selectedSchemes: List[String],
                              selectedLocations: List[String],
                              gender: String,
                              sexuality: String,
                              ethnicity: String,
                              disability: String,
                              gis: String,
                              onlineAdjustments: String,
                              assessmentCentreAdjustment: String,
                              civilServant: String,
                              ses: String,
                              hearAboutUs: String,
                              allocatedAssessmentCentre: String)

object DiversityReportRow {
  implicit val diversityReportRowFormat = Json.format[DiversityReportRow]
}
*/

case class DiversityReportRow(applicationId: UniqueIdentifier,
                              progress: Option[String],
                              schemes: List[Scheme],
                              locations: List[String],
                              disability: Option[String],
                              gis: Option[Boolean],
                              onlineAdjustments: Option[String],
                              assessmentCentreAdjustments: Option[String],
                              civilServant: Option[Boolean],
                              gender: String,
                              sexualOrientation: String,
                              ethnicity: String,
                              socialEconomicScore: String,
                              hearAboutUs: String
                             )

case object DiversityReportRow {
  def apply(application: ApplicationForCandidateProgressReport, diversityAnswers: DiversityReportDiversityAnswers,
            ses: String, hearAboutUs: String): DiversityReportRow = {
    DiversityReportRow(applicationId = application.applicationId,
      progress = application.progress,
      schemes = application.schemes,
      locations = application.locationIds,
      disability = application.hasDisability,
      gis = application.gis,
      onlineAdjustments = application.onlineAdjustments.map(fromBooleanToYesNo(_)),
      assessmentCentreAdjustments = application.assessmentCentreAdjustments.map(fromBooleanToYesNo(_)),
      civilServant = application.civilServant,
      gender = diversityAnswers.gender,
      sexualOrientation = diversityAnswers.sexualOrientation,
      ethnicity = diversityAnswers.ethnicity,
      socialEconomicScore = ses,
      hearAboutUs = hearAboutUs
    )
  }
  val fromBooleanToYesNo: Boolean => String = (b: Boolean) => if (b) "Yes" else "No"

  implicit val diversityReportRowFormat = Json.format[DiversityReportRow]
}
