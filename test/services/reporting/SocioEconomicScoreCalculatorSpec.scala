/*
 * Copyright 2019 HM Revenue & Customs
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

package services.reporting

import common.Constants.{ Yes, No }
import org.scalatestplus.play.PlaySpec

class SocioEconomicScoreCalculatorSpec extends PlaySpec {

  import SocioEconomicCalculatorSpec._

  val calculator = new SocioEconomicScoreCalculator {}

  "The socio-economic score calculator" should {

    "calculate the score of a self employed with 24 or more employees on a Modern professional occupation" in {
      calculator.calculate(employersLargeOrnanisations) must be("SE-1")
    }

    "calculate the score of a self employed with less than 24 employees on a Clerical and intermediate occupation" in {
      calculator.calculate(employersSmallOrganisations) must be("SE-3")
    }

    "calculate the score of a self employed/freelancer without employees on a Technical and craft occupation" in {
      calculator.calculate(selfEmployedNoEmployees) must be("SE-3")
    }

    "calculate the score of managers of large organizations on a Senior managers and administrators occupation" in {
      calculator.calculate(managersLargeOrganisations) must be("SE-1")
    }

    "calculate the score of managers of small organizations on a Senior managers and administrators occupation" in {
      calculator.calculate(managersSmallOrganisations) must be("SE-1")
    }

    "calculate the score of supervisors on a Semi-routine manual and service occupation" in {
      calculator.calculate(supervisors) must be("SE-4")
    }

    "calculate the score of other employees on a routine manual and service occupation" in {
      calculator.calculate(otherEmployees) must be("SE-5")
    }

    "calculate the score of unemployed" in {
      calculator.calculate(unemployed) must be("N/A")
    }

    "calculate the score of Unknown if the answer to question number 3 is Unknown" in {
      calculator.calculate(prefersNotToSayForQuestion3) must be("N/A")
    }

    "calculate the score of unemployed but seeking work" in {
      calculator.calculate(unemployedSeekingWork) must be("N/A")
    }

    "calculate the score of Unknown" in {
      calculator.calculate(prefersNotToSay) must be("N/A")
    }
  }
}

object SocioEconomicCalculatorSpec {
  val employersLargeOrnanisations: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Modern professional",
    "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
    "Which type of occupation did they have?" -> "Modern professional",
    "Which size would best describe their place of work?" -> "Large (over 24 employees)"
  )

  val employersSmallOrganisations: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Clerical (office work) and intermediate",
    "Did they work as an employee or were they self-employed?" -> "Self-employed with employees",
    "Which type of occupation did they have?" -> "Clerical (office work) and intermediate",
    "Which size would best describe their place of work?" -> "Small (1 to 24 employees)"
  )

  val selfEmployedNoEmployees: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Technical and craft",
    "Did they work as an employee or were they self-employed?" -> "Self-employed/freelancer without employees",
    "Which type of occupation did they have?" -> "Technical and craft"
  )

  val managersLargeOrganisations: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
    "Did they work as an employee or were they self-employed?" -> "Employee",
    "Which type of occupation did they have?" -> "Senior managers and administrators",
    "Which size would best describe their place of work?" -> "Large (over 24 employees)"
  )

  val managersSmallOrganisations: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Senior managers and administrators",
    "Did they work as an employee or were they self-employed?" -> "Employee",
    "Which type of occupation did they have?" -> "Senior managers and administrators",
    "Which size would best describe their place of work?" -> "Small (1 to 24 employees)"
  )

  val supervisors: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Semi-routine manual and service",
    "Did they work as an employee or were they self-employed?" -> "Employee",
    "Which type of occupation did they have?" -> "Semi-routine manual and service",
    "Which size would best describe their place of work?" -> "N/A",
    "Did they supervise any other employees?" -> Yes
  )

  val otherEmployees: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
    "Did they work as an employee or were they self-employed?" -> "Employee",
    "Which type of occupation did they have?" -> "Routine manual and service",
    "Which size would best describe their place of work?" -> "N/A",
    "Did they supervise any other employees?" -> No
  )

  val prefersNotToSayForQuestion3: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Routine manual and service",
    "Did they work as an employee or were they self-employed?" -> "Unknown"
  )

  val unemployed: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unemployed"
  )

  val unemployedSeekingWork: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unemployed but seeking work"
  )

  val prefersNotToSay: Map[String, String] = Map(
    "When you were 14, what kind of work did your highest-earning parent or guardian do?" -> "Unknown"
  )

}
