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

package services.testdata

import model.testdata.GeneratorConfig
import repositories._
import repositories.application.GeneralApplicationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object InProgressSchemePreferencesStatusGenerator extends InProgressSchemePreferencesStatusGenerator {
  override val previousStatusGenerator = InProgressPersonalDetailsStatusGenerator
  override val appRepository = applicationRepository
}

trait InProgressSchemePreferencesStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val schemesData = generatorConfig.schemesData.schemes
    val schemeLocationsData = generatorConfig.schemeLocationsData.schemeLocations

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- appRepository.updateSchemeLocations(candidateInPreviousStatus.applicationId.get, schemeLocationsData)
      _ <- appRepository.updateSchemes(candidateInPreviousStatus.applicationId.get, schemesData)
    } yield {
      candidateInPreviousStatus.copy(
        schemes = Some(schemesData.map { scheme => SchemeInfo(scheme, scheme.toString, false, false) }),
        schemeLocations = Some(schemeLocationsData)
      )
    }
  }
  // scalastyle:on method.length
}
