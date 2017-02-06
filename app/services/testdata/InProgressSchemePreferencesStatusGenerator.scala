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

import model._
import repositories._
import repositories.application.GeneralApplicationRepository
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object InProgressSchemePreferencesStatusGenerator extends InProgressSchemePreferencesStatusGenerator {
  override val previousStatusGenerator = InProgressPersonalDetailsStatusGenerator
  override val appRepository = applicationRepository
}

trait InProgressSchemePreferencesStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    def getFrameworkPrefs: Future[Preferences] = {
      for {
        randomRegion <- Random.region
        region = generatorConfig.region.getOrElse(randomRegion)
        firstLocation <- Random.location(region)
        secondLocation <- Random.location(region, List(firstLocation))
      } yield {
        Preferences(
          LocationPreference(region, firstLocation, "Commercial", None),
          None,
          None,
          Some(Alternatives(
            Random.bool,
            Random.bool
          ))
        )
      }
    }

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      frameworkPrefs <- getFrameworkPrefs
      _ <- appRepository.updateSchemeLocations(candidateInPreviousStatus.applicationId.get, List("2643743", "2657613"))
      _ <- appRepository.updateSchemes(candidateInPreviousStatus.applicationId.get,
        generatorConfig.schemeTypes.getOrElse(List(Scheme.Commercial, Scheme.Business)))
    } yield {
      candidateInPreviousStatus.copy(
        schemePreferences = Some(frameworkPrefs)
      )
    }
  }
  // scalastyle:on method.length
}
