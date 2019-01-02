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

import common.Constants.Yes

case class EmploymentStatus(answer: Map[String, String]) {
  import EmploymentStatus._

  private val unemployedOrUnknown = answer.get(Question1AndQuestion2).contains("Unknown") ||
    answer.get(Question1AndQuestion2).forall(_.startsWith("Unemployed"))

  private val smallCompany = answer.get(Question4).contains("Small (1 to 24 employees)")
  private val superviseAnyEmployees = answer.get(Question5).contains(Yes)

  def employmentStatusSize = answer.get(Question3) match {
    case _ if unemployedOrUnknown => NotApplicable
    case Some(Question3AnswerSelfEmployedWithoutEmployees) => SelfEmployedNoEmployees
    case Some(Question3AnswerSelfEmployedWithEmployees) if smallCompany => EmployersSmallOrganisations
    case Some(Question3AnswerSelfEmployedWithEmployees) => EmployersLargeOrnanisations
    case Some(Question3AnswerEmployee) => calculateForEmployee
    case None | Some(AnswerUnknown) => NotApplicable
    case unsupportedValue =>
      throw new IllegalStateException(s"Unsupported answer to the question 3: $unsupportedValue")
  }

  private def calculateForEmployee = answer.get(Question1AndQuestion2) match {
    case Some(Question1AndQuestion2AnswerSeniorManagers) if smallCompany => ManagersSmallOrganisations
    case Some(Question1AndQuestion2AnswerSeniorManagers) => ManagersLargeOrganisations
    case Some(_) if superviseAnyEmployees => Supervisors
    case Some(_) => OtherEmployees
    case unsupportedValue =>
      throw new IllegalStateException(s"Unsupported answer to the question 1 or 2: $unsupportedValue")
  }
}

case object EmploymentStatus {
  val AnswerUnknown = "Unknown"
  // The answer for both: question #1 and question #2 is kept under the question #1
  val Question1AndQuestion2 = "When you were 14, what kind of work did your highest-earning parent or guardian do?"
  val Question1AndQuestion2AnswerSeniorManagers = "Senior managers and administrators"
  val Question3 = "Did they work as an employee or were they self-employed?"
  val Question3AnswerSelfEmployedWithoutEmployees = "Self-employed/freelancer without employees"
  val Question3AnswerSelfEmployedWithEmployees = "Self-employed with employees"
  val Question3AnswerEmployee = "Employee"
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
