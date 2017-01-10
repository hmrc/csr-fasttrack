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

import play.api.mvc.RequestHeader
import repositories._
import repositories.application.AssistanceDetailsRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressAssistanceDetailsStatusGenerator extends InProgressAssistanceDetailsStatusGenerator {
  val previousStatusGenerator = InProgressSchemePreferencesStatusGenerator
  override val adRepository = assistanceDetailsRepository
}

// scalastyle:off method.length
trait InProgressAssistanceDetailsStatusGenerator extends ConstructiveGenerator {
  val adRepository: AssistanceDetailsRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {
    val assistanceDetails = getAssistanceDetails(generatorConfig)

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = candidateInPreviousStatus.applicationId.get
      _ <- adRepository.update(appId, candidateInPreviousStatus.userId, assistanceDetails)
    } yield {
      candidateInPreviousStatus.copy(assistanceDetails = Some(assistanceDetails))
    }
  }

  def getAssistanceDetails(config: GeneratorConfig): model.exchange.AssistanceDetails = {
    val hasDisabilityFinalValue = config.assistanceDetails.hasDisability

    val hasDisabilityDescriptionFinalValue =
      if (hasDisabilityFinalValue == "Yes") {
        Some(config.assistanceDetails.hasDisabilityDescription)
      } else {
        None
      }
    val gisFinalValue = if (hasDisabilityFinalValue == "Yes" && config.assistanceDetails.setGis) {
      Some(true)
    } else { Some(false) }

    val onlineAdjustmentsFinalValue = config.assistanceDetails.onlineAdjustments
    val onlineAdjustmentsDescriptionFinalValue =
      if (onlineAdjustmentsFinalValue) {
        Some(config.assistanceDetails.onlineAdjustmentsDescription)
      } else {
        None
      }
    val assessmentCentreAdjustmentsFinalValue = config.assistanceDetails.assessmentCentreAdjustments
    val assessmentCentreAdjustmentsDescriptionFinalValue =
      if (assessmentCentreAdjustmentsFinalValue) {
        Some(config.assistanceDetails.assessmentCentreAdjustmentsDescription)
      } else {
        None
      }

    model.exchange.AssistanceDetails(
      hasDisabilityFinalValue,
      hasDisabilityDescriptionFinalValue,
      gisFinalValue,
      onlineAdjustmentsFinalValue,
      onlineAdjustmentsDescriptionFinalValue,
      assessmentCentreAdjustmentsFinalValue,
      assessmentCentreAdjustmentsDescriptionFinalValue,
      None,
      None,
      None
    )
  }
}
