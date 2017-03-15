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

import model.persisted.AssessmentCentrePassMarkSettings
import repositories._

import scala.concurrent.Future

object AssessmentCentrePassMarkSettingsService extends AssessmentCentrePassMarkSettingsService {
  val assessmentCentrePassmarkSettingRepository = repositories.assessmentCentrePassMarkSettingsRepository
}

trait AssessmentCentrePassMarkSettingsService {
  val assessmentCentrePassmarkSettingRepository: AssessmentCentrePassMarkSettingsRepository

  def getLatestVersion: Future[Option[AssessmentCentrePassMarkSettings]] = {
    assessmentCentrePassmarkSettingRepository.tryGetLatestVersion
  }
}
