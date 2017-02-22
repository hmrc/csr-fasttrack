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

package services.evaluation

import model.Schemes._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec

class EligibleSchemeSelectorSpec extends PlaySpec with TableDrivenPropertyChecks {

  val eligibleSchemeSelector = new EligibleSchemeSelector {}

  // format: OFF
  val CandidatesQualifications = Table(
    ("candidate's aLevel", "candidate's stemLevel", "eligible schemes"),
    (true,                 true,                    Business :: Commercial :: DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil),
    (false,                true,                    Business :: Commercial :: DigitalAndTechnology :: Finance :: Nil),
    (true,                 false,                   Business :: Commercial :: Finance :: ProjectDelivery :: Nil),
    (false,                false,                   Business :: Commercial :: Finance :: Nil)
  )
  // format: ON

  "Eligible schemes" should {
    "be returned based on candidate's qualifications" in {
      forAll(CandidatesQualifications) { (aLevel: Boolean, stemLevel: Boolean, expectedSchemes: List[String]) =>
        val actualSchemes = eligibleSchemeSelector.eligibleSchemes(aLevel = aLevel, stemLevel = stemLevel)
        actualSchemes mustBe expectedSchemes
      }
    }
  }

}
