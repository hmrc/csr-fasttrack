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


import model.persisted.PersonalDetails
import model.exchange.AssistanceDetails
// scalastyle:off cyclomatic.complexity
case class ApplicationValidator(gd: PersonalDetails, ad: AssistanceDetails) {

  def validate: Boolean = validateGeneralDetails && validateAssistanceDetails

  def validateGeneralDetails: Boolean =
    !(gd.firstName.isEmpty || gd.lastName.isEmpty || gd.preferredName.isEmpty)

  def validateAssistanceDetails: Boolean = {

    def isValid(requireValidation: Boolean)(validate: AssistanceDetails => Boolean) = if (requireValidation) {
      validate(ad)
    } else {
      true
    }

    val validDisability = isValid(ad.hasDisability=="Yes") _
    val validOnlineAdjustments = isValid(ad.needsSupportForOnlineAssessment) _
    val validVenueAdjustments = isValid(ad.needsSupportAtVenue) _

    def hasGis(ad: AssistanceDetails): Boolean = ad.guaranteedInterview match {
      case Some(_) => true
      case _ => false
    }

    def hasOnlineAdjustmentDescription(ad: AssistanceDetails): Boolean = ad.needsSupportForOnlineAssessmentDescription match {
      case Some(x) => x.nonEmpty
      case _ => false
    }


    def hasVenueAdjustmentDescription(ad: AssistanceDetails): Boolean = ad.needsSupportAtVenueDescription match {
      case Some(x) => x.nonEmpty
      case _ => false
    }

    validOnlineAdjustments(hasOnlineAdjustmentDescription) && validVenueAdjustments(hasVenueAdjustmentDescription) &&
      validDisability(hasGis)

  }

}
