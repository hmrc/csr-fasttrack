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

package model.report

import model.ReportExchangeObjects.{ ApplicationForCandidateProgressReport, DiversityReportDiversityAnswers }
import model.Scheme.Scheme
import model.UniqueIdentifier
import play.api.libs.json.Json

case class DiversityReportItem(
  applicationId: UniqueIdentifier,
  progress: Option[String],
  schemes: List[Scheme],
  locations: List[String],
  gender: String,
  sexualOrientation: String,
  ethnicity: String,
  disability: Option[String],
  gis: Option[Boolean],
  onlineAdjustments: Option[String],
  assessmentCentreAdjustments: Option[String],
  civilServant: Option[Boolean],
  socialEconomicScore: String,
  hearAboutUs: String,
  allocatedAssessmentCentre: Option[String]
)

object DiversityReportItem {
  def apply(application: ApplicationForCandidateProgressReport, diversityAnswers: DiversityReportDiversityAnswers,
    ses: String, hearAboutUs: String, allocatedAssessmentCentre: Option[String]): DiversityReportItem = {
    DiversityReportItem(
      applicationId = application.applicationId.get,
      progress = application.progress,
      schemes = application.schemes,
      locations = application.locationIds,
      gender = diversityAnswers.gender,
      sexualOrientation = diversityAnswers.sexualOrientation,
      ethnicity = diversityAnswers.ethnicity,
      disability = application.hasDisability,
      gis = application.gis,
      onlineAdjustments = application.onlineAdjustments.map(fromBooleanToYesNo(_)),
      assessmentCentreAdjustments = application.assessmentCentreAdjustments.map(fromBooleanToYesNo(_)),
      civilServant = application.civilServant,
      socialEconomicScore = ses,
      hearAboutUs = hearAboutUs,
      allocatedAssessmentCentre = allocatedAssessmentCentre
    )
  }
  val fromBooleanToYesNo: Boolean => String = (b: Boolean) => if (b) "Yes" else "No"

  implicit val diversityReportRowFormat = Json.format[DiversityReportItem]
}
