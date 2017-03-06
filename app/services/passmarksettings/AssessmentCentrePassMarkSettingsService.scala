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

package services.passmarksettings

import model.Commands.AssessmentCentrePassMarkSettingsResponse
import model.PassmarkPersistedObjects.{ AssessmentCentrePassMarkScheme }
import repositories._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
  val assessmentCentrePassmarkSettingRepository = repositories.assessmentCentrePassMarkSettingsRepository
  val locationSchemeRepository = repositories.fileLocationSchemeRepository
}

trait AssessmentCentrePassMarkSettingsService {
  val assessmentCentrePassmarkSettingRepository: AssessmentCentrePassMarkSettingsRepository
  val locationSchemeRepository: LocationSchemeRepository

  def getLatestVersion: Future[AssessmentCentrePassMarkSettingsResponse] = {
    for {
      latestVersionOpt <- assessmentCentrePassmarkSettingRepository.tryGetLatestVersion
      schemes = locationSchemeRepository.schemeInfoList.map(_.id)
    } yield {
      latestVersionOpt.map(latestVersion => {
        val responseSchemes = latestVersion.schemes.map(scheme => AssessmentCentrePassMarkScheme(scheme.scheme, scheme.overallPassMarks))
        AssessmentCentrePassMarkSettingsResponse(
          schemes = responseSchemes,
          info = Some(latestVersion.info)
        )
      }).getOrElse({
        val emptyPassMarkSchemes = schemes.map(scheme => AssessmentCentrePassMarkScheme(scheme, None))
        AssessmentCentrePassMarkSettingsResponse(emptyPassMarkSchemes, None)
      })
    }
  }
}
