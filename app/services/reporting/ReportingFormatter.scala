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

import model.Adjustments

object ReportingFormatter extends ReportingFormatter

trait ReportingFormatter {
  def getOnlineAdjustments(onlineAdjustments: Option[Boolean], adjustments: Option[Adjustments]): Option[String] = {
    onlineAdjustments match {
      case Some(false) => Some("No")
      case Some(true) => {
        adjustments.flatMap(_.onlineTests.map { onlineTests =>
          List(onlineTests.extraTimeNeeded.map(value => s"Numerical Extra time needed: ${value}%").getOrElse(""),
            onlineTests.extraTimeNeededNumerical.map(value => s"Verbal extra time needed: ${value}%").getOrElse(""),
            onlineTests.otherInfo.map(value => s"$value").getOrElse("")).mkString(", ")
        }).orElse(Some("Yes"))
      }
      case None => None
    }
  }

  def getAssessmentCentreAdjustments(assessmentCentreAdjustments: Option[Boolean], adjustments: Option[Adjustments]) = {
    assessmentCentreAdjustments match {
      case Some(false) => Some("No")
      case Some(true) => {
        adjustments.flatMap( adjustments =>
          adjustments.assessmentCenter.map { assessmentCenter =>
            val extraTimeItems = List(
              assessmentCenter.extraTimeNeeded.map(value => s"Extra time needed: ${value}%"),
              assessmentCenter.otherInfo.map(value => s"$value"))
            val typeOfAdjustmentsItems: List[Option[String]] = adjustments.typeOfAdjustments.map { typeOfAdjustments =>
              typeOfAdjustments.map(Some(_))
            }.getOrElse(List.empty)
            val allAdjustments: List[Option[String]] = (extraTimeItems ++ typeOfAdjustmentsItems)
            allAdjustments.flatten.mkString(", ")
          }).orElse(Some("Yes"))
      }
      case None => None
    }
  }
}
