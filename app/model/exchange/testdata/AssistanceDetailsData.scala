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

package model.commands.exchange.testdata

import services.testdata.faker.DataFaker.Random

// Default value must be hard coded instead of randomly generated because for instance hasDisabilityDescription value
// only makes sense if hasDisability is "Yes" and there is no way to find out if hasDisability is "Yes" if we randomly
// generate it.
case class AssistanceDetailsData(
                                  hasDisability: String = "Yes",
                                  hasDisabilityDescription: Option[String] = Some("I have one arm shorter"),
                                  setGis: Option[Boolean] = Some(true),
                                  onlineAdjustments: Boolean = true,
                                  onlineAdjustmentsDescription: Option[String] = Some("I think 50% more time"),
                                  assessmentCentreAdjustments: Boolean = true,
                                  assessmentCentreAdjustmentsDescription: Option[String] = Some("I need coloured paper")
                                ) {
  def getAssistanceDetails(): model.exchange.AssistanceDetails = {
    model.exchange.AssistanceDetails(hasDisability, hasDisabilityDescription, setGis, onlineAdjustments,
      onlineAdjustmentsDescription, assessmentCentreAdjustments, assessmentCentreAdjustmentsDescription)
  }
}

case object AssistanceDetailsData {
  def apply(adr: model.exchange.testdata.AssistanceDetailsRequest, generatorId: Int): AssistanceDetailsData = {
    val hasDisabilityFinalValue = adr.hasDisability.getOrElse(Random.yesNoPreferNotToSay)
    val hasDisabilityDescriptionFinalValue: Option[String] =
      if (hasDisabilityFinalValue == "Yes") {
        Some(adr.hasDisabilityDescription.getOrElse(Random.hasDisabilityDescription))
      } else {
        None
      }
    val gisFinalValue =
      if (hasDisabilityFinalValue == "Yes") {
        Some(adr.setGis.getOrElse(Random.bool))
      } else {
        None
      }

    val onlineAdjustmentsFinalValue = adr.onlineAdjustments.getOrElse(Random.bool)
    val onlineAdjustmentsDescriptionFinalValue =
      if (onlineAdjustmentsFinalValue) {
        Some(adr.onlineAdjustmentsDescription.getOrElse(Random.onlineAdjustmentsDescription))
      } else {
        None
      }

    val assessmentCentreAdjustmentsFinalValue = adr.assessmentCentreAdjustments.getOrElse(Random.bool)
    val assessmentCentreAdjustmentsDescriptionFinalValue =
      if (assessmentCentreAdjustmentsFinalValue) {
        Some(adr.assessmentCentreAdjustmentsDescription.getOrElse(Random.assessmentCentreAdjustmentDescription))
      } else {
        None
      }

    AssistanceDetailsData(
      hasDisabilityFinalValue,
      hasDisabilityDescriptionFinalValue,
      gisFinalValue,
      onlineAdjustmentsFinalValue,
      onlineAdjustmentsDescriptionFinalValue,
      assessmentCentreAdjustmentsFinalValue,
      assessmentCentreAdjustmentsDescriptionFinalValue
    )
  }
}
