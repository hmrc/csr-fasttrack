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

package services

import model.persisted.PersonalDetails
import model.exchange.AssistanceDetailsExamples
import model.ApplicationValidator
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class ApplicationValidatorSpec extends PlaySpec {

  import ApplicationValidatorSpec._

  "given valid personal details" should {
    "return true when details are valid" in {
      val validator = ApplicationValidator(personalDetails, assistanceDetails)
      validator.validateGeneralDetails must be(true)
    }
  }

  "given assistance details" should {
    "return true when details are all there" in {
      val validator = ApplicationValidator(personalDetails, assistanceDetails)
      validator.validateAssistanceDetails must be(true)
    }

    "return false if we don't have a description for at venue adjustment" in {
      val validator = ApplicationValidator(
        personalDetails,
        assistanceDetails.copy(needsSupportAtVenueDescription = None)
      )
      validator.validateAssistanceDetails must be(false)
    }

    "return false if we don't have a description for online adjustment" in {
      val validator = ApplicationValidator(
        personalDetails,
        assistanceDetails.copy(needsSupportForOnlineAssessmentDescription = None)
      )
      validator.validateAssistanceDetails must be(false)
    }

    "return false if we don't have gis setting and we have disability" in {
      val validator = ApplicationValidator(
        personalDetails,
        assistanceDetails.copy(guaranteedInterview = None)
      )
      validator.validateAssistanceDetails must be(false)
    }
  }
}

object ApplicationValidatorSpec {
  def personalDetails = PersonalDetails("firstName", "lastName", "preferredName", new LocalDate(), aLevel = true,
    stemLevel = true, civilServant = false, department = None)

  def assistanceDetails = AssistanceDetailsExamples.DisabilityGisAndAdjustments
}
