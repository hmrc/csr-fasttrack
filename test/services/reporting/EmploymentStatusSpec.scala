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

package services.reporting

import org.scalatestplus.play.PlaySpec

class EmploymentStatusSpec extends PlaySpec {
  import EmploymentStatus._
  import SocioEconomicCalculatorSpec._

  def employmentSize(answers: Map[String, String]) = EmploymentStatus(answers).employmentStatusSize

  "The employment status/size calculator" should {

    "calculate the score of a self employed with 24 or more employees" in {
      employmentSize(employersLargeOrnanisations) must be(EmployersLargeOrnanisations)
    }

    "calculate the score of a self employed with less than 24 employees" in {
      employmentSize(employersSmallOrganisations) must be(EmployersSmallOrganisations)
    }

    "calculate the score of a self employed/freelancer without employees" in {
      employmentSize(selfEmployedNoEmployees) must be(SelfEmployedNoEmployees)
    }

    "calculate the score of managers of large organizations" in {
      employmentSize(managersLargeOrganisations) must be(ManagersLargeOrganisations)
    }

    "calculate the score of managers of small organizations" in {
      employmentSize(managersSmallOrganisations) must be(ManagersSmallOrganisations)
    }

    "calculate the score of supervisors" in {
      employmentSize(supervisors) must be(Supervisors)
    }

    "calculate the score of other employees" in {
      employmentSize(otherEmployees) must be(OtherEmployees)
    }

    "calculate the score of unemployed as a N/A" in {
      employmentSize(unemployed) must be(NotApplicable)
    }

    "calculate the score of unemployed but seeking work" in {
      employmentSize(unemployedSeekingWork) must be(NotApplicable)
    }

    "calculate the score of Unknown" in {
      employmentSize(prefersNotToSay) must be(NotApplicable)
    }
  }
}
