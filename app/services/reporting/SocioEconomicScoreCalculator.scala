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

trait SocioEconomicScoreCalculator {
  private val TypeOfOccupation: Map[String, Int] = Map(
    "Modern professional" -> 1,
    "Clerical (office work) and intermediate" -> 2,
    "Senior managers and administrators" -> 3,
    "Technical and craft" -> 4,
    "Semi-routine manual and service" -> 5,
    "Routine manual and service" -> 6,
    "Middle or junior managers" -> 7,
    "Traditional professional" -> 8
  )

  private val socioEconomicScoreMatrix: Array[Array[Int]] = Array(
    Array(1, 1, 1, 1, 1, 1, 1),
    Array(1, 3, 3, 1, 1, 1, 2),
    Array(1, 3, 3, 1, 1, 1, 1),
    Array(1, 3, 3, 1, 1, 4, 4),
    Array(1, 3, 3, 1, 1, 4, 5),
    Array(1, 3, 3, 1, 1, 4, 5),
    Array(1, 3, 3, 1, 1, 1, 1),
    Array(1, 1, 1, 1, 1, 1, 1)
  )

  def calculate(answer: Map[String, String]): String = {
    val employmentStatusSizeValue = EmploymentStatus(answer).employmentStatusSize
    if (employmentStatusSizeValue > 0) {
      val typeOfOccupation = getTypeOfOccupation(answer)
      buildScoreString(employmentStatusSizeValue, typeOfOccupation)
    } else {
      "N/A"
    }
  }

  private def buildScoreString(employmentStatusSizeValue: Int, typeOfOccupation: Int): String = {
    s"SE-${socioEconomicScoreMatrix(typeOfOccupation - 1)(employmentStatusSizeValue - 1)}"
  }

  private def getTypeOfOccupation(answer: Map[String, String]) = {
    TypeOfOccupation(answer(EmploymentStatus.Question1AndQuestion2))
  }
}

object SocioEconomicScoreCalculator extends SocioEconomicScoreCalculator
