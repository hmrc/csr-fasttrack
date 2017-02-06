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

package services.reporting

import common.Constants.Yes

case class EmploymentStatus(answer: Map[String, String]) {
  import EmploymentStatus._

  private val employedOrSelfEmployed = answer.get(Question1).contains("Employed")
  private val unemployedOrUnknown = !employedOrSelfEmployed
  private val smallCompany = answer.get(Question4).contains("Small (1 to 24 employees)")
  private val superviseAnyEmployees = answer.get(Question5).contains(Yes)

  def employmentStatusSize = answer.get(Question3) match {
    case _ if unemployedOrUnknown => NotApplicable
    case (Some("Self-employed/freelancer without employees")) => SelfEmployedNoEmployees
    case (Some("Self-employed with employees")) if smallCompany => EmployersSmallOrganisations
    case (Some("Self-employed with employees")) => EmployersLargeOrnanisations
    case (Some("Employee")) => calculateForEmployee
  }

  private def calculateForEmployee = answer.get(Question2) match {
    case (Some("Senior managers and administrators")) if smallCompany => ManagersSmallOrganisations
    case (Some("Senior managers and administrators")) => ManagersLargeOrganisations
    case (Some(_)) if superviseAnyEmployees => Supervisors
    case (Some(_)) => OtherEmployees
  }
}

case object EmploymentStatus {
  val Question1 = "When you were 14, was your highest-earning parent or guardian employed?"
  val Question2 = "Which type of occupation did they have?"
  val Question3 = "Did they work as an employee or were they self-employed?"
  val Question4 = "Which size would best describe their place of work?"
  val Question5 = "Did they supervise any other employees?"

  val NotApplicable = 0
  val EmployersLargeOrnanisations = 1
  val EmployersSmallOrganisations = 2
  val SelfEmployedNoEmployees = 3
  val ManagersLargeOrganisations = 4
  val ManagersSmallOrganisations = 5
  val Supervisors = 6
  val OtherEmployees = 7
}
